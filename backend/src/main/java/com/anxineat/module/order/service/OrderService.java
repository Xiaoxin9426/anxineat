package com.anxineat.module.order.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.cart.entity.CartItem;
import com.anxineat.module.cart.service.CartService;
import com.anxineat.module.coupon.service.CouponService;
import com.anxineat.module.order.entity.Order;
import com.anxineat.module.order.entity.OrderItem;
import com.anxineat.module.order.entity.OrderStatusLog;
import com.anxineat.module.order.enums.OrderStatus;
import com.anxineat.module.order.enums.OrderType;
import com.anxineat.module.order.mapper.OrderItemMapper;
import com.anxineat.module.order.mapper.OrderMapper;
import com.anxineat.module.order.mapper.OrderStatusLogMapper;
import com.anxineat.module.payment.entity.Payment;
import com.anxineat.module.payment.mapper.PaymentMapper;
import com.anxineat.module.point.service.PointService;
import com.anxineat.module.product.mapper.ProductSkuMapper;
import com.anxineat.module.product.service.ProductService;
import com.anxineat.module.marketing.entity.ActivityRecord;
import com.anxineat.module.marketing.entity.FlashSale;
import com.anxineat.module.marketing.entity.MarketingActivity;
import com.anxineat.module.marketing.mapper.ActivityRecordMapper;
import com.anxineat.module.marketing.mapper.FlashSaleMapper;
import com.anxineat.module.marketing.mapper.MarketingActivityMapper;
import com.anxineat.module.store.entity.Store;
import com.anxineat.module.store.service.StoreService;
import com.anxineat.module.system.service.SysConfigService;
import com.anxineat.module.user.entity.Address;
import com.anxineat.module.user.entity.MemberLevel;
import com.anxineat.module.user.entity.User;
import com.anxineat.module.user.mapper.AddressMapper;
import com.anxineat.module.user.mapper.MemberLevelMapper;
import com.anxineat.module.user.mapper.UserMapper;
import com.anxineat.util.Money;
import com.anxineat.util.NoGenerator;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单服务：交易核心编排。
 *
 * <p>所有金额一律服务端重算（{@link #computePlan}），前端传入的金额只作可选校验，绝不采用。
 * 关键并发与一致性保证：
 * <ul>
 *   <li>库存扣减 —— {@link ProductSkuMapper#deductStock} 条件更新（CAS）。</li>
 *   <li>券占用 —— {@link CouponService#lock} 内部 tryLock 条件更新。</li>
 *   <li>订单状态流转 —— {@link OrderMapper#tryTransit} 条件更新，配合 {@link OrderStatus#assertTransition}。</li>
 *   <li>幂等 —— idempotent_key 唯一索引 + 支付回调 tryMarkSuccess 闸门。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductService productService;
    private final StoreService storeService;
    private final CartService cartService;
    private final CouponService couponService;
    private final PointService pointService;
    private final UserMapper userMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final AddressMapper addressMapper;
    private final PaymentMapper paymentMapper;
    private final SysConfigService config;
    private final ObjectMapper objectMapper;
    private final MarketingActivityMapper marketingActivityMapper;
    private final FlashSaleMapper flashSaleMapper;
    private final ActivityRecordMapper activityRecordMapper;

    // ==================== 金额试算 ====================

    public OrderDTO.PreviewVO preview(Long userId, OrderDTO.PreviewRequest req) {
        List<ProductService.ResolvedItem> items = resolveItems(userId, req);
        Plan plan = computePlan(userId, items, req.getStoreId(), req.getOrderType(), req.getUsePoint());
        OrderDTO.PreviewVO vo = new OrderDTO.PreviewVO();
        vo.setGoodsAmount(plan.goodsAmount);
        vo.setLevelDiscountAmount(plan.levelDiscountAmount);
        vo.setCouponAmount(plan.couponAmount);
        vo.setPointAmount(plan.appliedPointAmount);
        vo.setPointUsed((long) plan.appliedPointUsed);
        vo.setDiscountAmount(plan.discountAmount);
        vo.setDeliveryFee(plan.deliveryFee);
        vo.setPayAmount(plan.payAmount);
        vo.setItems(plan.items.stream().map(this::toPreviewItem).toList());
        vo.setAvailableCoupons(couponService.availableCoupons(userId, req.getOrderType(),
                plan.items, plan.goodsAmount, plan.couponBase));
        vo.setBestCouponId(plan.couponId);
        vo.setPointAvailable(pointService.balance(userId));
        vo.setPointUsable((long) plan.pointUsable);
        vo.setPointDeductAmount(plan.pointDeductCents);
        vo.setBelowMinDeliveryAmount(plan.belowMinDeliveryAmount);
        return vo;
    }

    // ==================== 提交订单 ====================

    @Transactional
    public OrderDTO.SubmitVO submit(Long userId, OrderDTO.SubmitRequest req) {
        List<ProductService.ResolvedItem> items = resolveItems(userId, req);
        Plan plan = computePlan(userId, items, req.getStoreId(), req.getOrderType(), req.getUsePoint());

        if (plan.belowMinDeliveryAmount != null && plan.belowMinDeliveryAmount > 0) {
            throw new BizException(ResultCode.DELIVERY_BELOW_MIN);
        }

        String idempotentKey = req.getIdempotentKey();
        if (idempotentKey != null && !idempotentKey.isBlank()) {
            Order existed = orderMapper.selectByIdempotentKey(idempotentKey);
            if (existed != null) {
                return toSubmitVO(existed);
            }
        }

        Store store = storeService.getById(req.getStoreId());
        storeService.assertOrderable(store, OrderType.of(req.getOrderType()));

        Order order = new Order();
        order.setOrderNo(NoGenerator.orderNo());
        order.setUserId(userId);
        order.setStoreId(store.getId());
        order.setOrderType(req.getOrderType());
        order.setStatusEnum(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(plan.goodsAmount);
        order.setDiscountAmount(plan.discountAmount);
        order.setLevelDiscountAmount(plan.levelDiscountAmount);
        order.setCouponAmount(plan.couponAmount);
        order.setPointAmount((long) plan.appliedPointAmount);
        order.setPointUsed((long) plan.appliedPointUsed);
        order.setDeliveryFee(plan.deliveryFee);
        order.setPayAmount(plan.payAmount);
        order.setUserCouponId(plan.couponId);
        order.setIdempotentKey(idempotentKey);
        order.setRemark(req.getRemark());
        order.setExpectTime(parseTime(req.getExpectTime()));
        order.setExpireTime(LocalDateTime.now().plusMinutes(config.payExpireMinutes()));

        if (OrderType.PICKUP.name().equals(req.getOrderType())) {
            long seq = orderMapper.selectMaxPickupCode(store.getId(), LocalDate.now()) + 1;
            order.setPickupCode(NoGenerator.formatPickupCode((int) Math.min(seq, 999)));
            order.setPickupDate(LocalDate.now());
        } else {
            if (req.getAddressId() == null) {
                throw new BizException(ResultCode.ADDRESS_REQUIRED);
            }
            Address addr = addressMapper.selectById(req.getAddressId());
            if (addr == null || !Objects.equals(addr.getUserId(), userId)) {
                throw new BizException(ResultCode.ADDRESS_REQUIRED);
            }
            order.setAddressSnapshot(toJson(Map.of(
                    "name", addr.getName(), "phone", addr.getPhone(),
                    "province", addr.getProvince(), "city", addr.getCity(),
                    "district", addr.getDistrict(), "detail", addr.getDetail(),
                    "tag", addr.getTag())));
        }

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            if (idempotentKey != null && !idempotentKey.isBlank()) {
                Order first = orderMapper.selectByIdempotentKey(idempotentKey);
                if (first != null) {
                    return toSubmitVO(first);
                }
            }
            throw e;
        }

        for (ProductService.ResolvedItem it : items) {
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setProductId(it.getProductId());
            oi.setProductName(it.getProductName());
            oi.setProductImage(it.getProductImage());
            oi.setSkuId(it.getSkuId());
            oi.setSkuSnapshot(toJson(Map.of("id", it.getSkuId(), "cupSize", it.getSkuName())));
            oi.setQuantity(it.getQuantity());
            oi.setUnitPrice(it.getUnitPrice());
            oi.setSpecSnapshot(it.getSpecSnapshot());
            oi.setToppingSnapshot(it.getToppingSnapshot());
            oi.setSpecText(it.getSpecText());
            oi.setToppingText(it.getToppingText());
            oi.setTotalPrice(it.getTotalPrice());
            oi.setActivityId(it.getActivityId());
            oi.setActivityType(it.isFlash() ? "FLASH_SALE" : null);
            orderItemMapper.insert(oi);
        }

        if (plan.couponId != null) {
            couponService.lock(plan.couponId, order.getId());
        }
        if (plan.appliedPointUsed > 0) {
            pointService.deductForOrder(userId, order.getId(), plan.appliedPointUsed);
        }
        for (ProductService.ResolvedItem it : items) {
            if (it.isFlash()) {
                deductFlashStock(it, userId, order.getOrderNo());
            } else {
                int rows = skuMapper.deductStock(it.getSkuId(), it.getQuantity());
                if (rows != 1) {
                    throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "「" + it.getProductName() + "」库存不足");
                }
            }
        }

        Payment payment = new Payment();
        payment.setPayNo(NoGenerator.payNo());
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setChannel(config.get("pay.channel", "MOCK"));
        payment.setAmount(order.getPayAmount());
        payment.setStatus("PAYING");
        paymentMapper.insert(payment);

        log(order.getId(), null, OrderStatus.PENDING_PAYMENT, "USER", "创建订单");

        if (Boolean.TRUE.equals(req.getFromCart())) {
            cartService.clear(userId, store.getId());
        }
        return toSubmitVO(order, payment.getPayNo());
    }

    /**
     * 秒杀库存扣减（独立库存池 CAS）+ 限购校验 + 参与记录（幂等）。
     */
    private void deductFlashStock(ProductService.ResolvedItem it, Long userId, String orderNo) {
        FlashSale fs = flashSaleMapper.selectById(it.getFlashSaleId());
        if (fs == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        int qty = it.getQuantity();
        if (fs.getPerLimit() != null && fs.getPerLimit() > 0) {
            int got = activityRecordMapper.sumQuantity(fs.getActivityId(), userId, "FLASH_SALE");
            if (got + qty > fs.getPerLimit()) {
                throw new BizException(ResultCode.FLASH_PER_LIMIT);
            }
        }
        int rows = flashSaleMapper.deductSoldStock(fs.getId(), fs.getVersion(), qty);
        if (rows != 1) {
            throw new BizException(ResultCode.FLASH_SOLD_OUT);
        }
        ActivityRecord rec = new ActivityRecord();
        rec.setActivityId(fs.getActivityId());
        rec.setUserId(userId);
        rec.setType("FLASH_SALE");
        rec.setQuantity(qty);
        rec.setRewardDetail("order:" + orderNo);
        rec.setBizNo("FLASH:" + orderNo + ":" + it.getSkuId());
        try {
            activityRecordMapper.insert(rec);
        } catch (DuplicateKeyException ex) {
            // 并发幂等：同一订单同一 sku 仅记一次
        }
    }

    // ==================== 取消 / 超时 ====================

    @Transactional
    public void cancel(Long userId, Long orderId) {
        Order order = getOwned(userId, orderId);
        order.statusEnum().assertTransition(OrderStatus.CANCELLED);
        int rows = orderMapper.tryTransit(orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.CANCELLED.name());
        if (rows != 1) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "订单状态已变化，无法取消");
        }
        orderMapper.setCancelled(orderId, LocalDateTime.now(), "用户取消");
        releaseResources(order);
        closePayment(orderId);
        log(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, "USER", "用户取消");
    }

    @Scheduled(fixedDelay = 60_000)
    public void cancelExpired() {
        List<Order> expired = orderMapper.selectExpiredPending(LocalDateTime.now(), 50);
        for (Order o : expired) {
            try {
                cancelInternal(o.getId());
            } catch (Exception e) {
                log.warn("超时取消订单 {} 失败: {}", o.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void cancelInternal(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return;
        }
        int rows = orderMapper.tryTransit(orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.CANCELLED.name());
        if (rows != 1) {
            return;
        }
        orderMapper.setCancelled(orderId, LocalDateTime.now(), "支付超时");
        releaseResources(order);
        closePayment(orderId);
        log(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, "SYSTEM", "支付超时自动取消");
    }

    private void releaseResources(Order order) {
        couponService.release(order.getUserCouponId());
        if (order.getPointUsed() != null && order.getPointUsed() > 0) {
            pointService.rollbackDeduct(order.getUserId(), order.getId(), order.getPointUsed());
        }
        for (OrderItem oi : orderItemMapper.selectByOrder(order.getId())) {
            if ("FLASH_SALE".equals(oi.getActivityType())) {
                flashSaleMapper.restoreSoldStockByActivitySku(oi.getActivityId(), oi.getSkuId(), oi.getQuantity());
            } else {
                skuMapper.restoreStock(oi.getSkuId(), oi.getQuantity());
            }
        }
    }

    private void closePayment(Long orderId) {
        Payment p = paymentMapper.selectLatestByOrder(orderId);
        if (p != null) {
            paymentMapper.tryMarkClosed(p.getPayNo());
        }
    }

    // ==================== 支付成功（幂等） ====================

    @Transactional
    public void markPaid(Long orderId, String transactionId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        int rows = orderMapper.tryTransit(orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name());
        if (rows != 1) {
            return;
        }
        orderMapper.setPaid(orderId, LocalDateTime.now());

        if (order.getUserCouponId() != null) {
            couponService.confirmUse(order.getUserCouponId(), orderId);
        }

        User user = userMapper.selectById(order.getUserId());
        MemberLevel level = levelOf(user == null ? null : user.getMemberLevelId());
        int rate = level == null || level.getPointRate() == null ? 100 : level.getPointRate();
        long awarded = pointService.awardConsume(order.getUserId(), orderId, order.getPayAmount(), rate);
        if (awarded > 0) {
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .set(Order::getPointAwarded, (int) awarded)
                    .eq(Order::getId, orderId));
        }

        userMapper.addTotalAmount(order.getUserId(), order.getPayAmount());
        if (user != null) {
            long newTotal = (user.getTotalAmount() == null ? 0 : user.getTotalAmount()) + order.getPayAmount();
            MemberLevel newLevel = memberLevelMapper.matchLevel(newTotal);
            if (newLevel != null && !Objects.equals(newLevel.getId(), user.getMemberLevelId())) {
                User u2 = new User();
                u2.setId(user.getId());
                u2.setMemberLevelId(newLevel.getId());
                userMapper.updateById(u2);
            }
        }
        log(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, "SYSTEM",
                transactionId == null ? "支付成功" : "支付成功 " + transactionId);
    }

    // ==================== 列表 / 详情 ====================

    public IPage<OrderDTO.OrderListVO> list(Long userId, String statusGroup, int page, int size) {
        List<String> statuses = resolveStatuses(statusGroup);
        IPage<Order> pageResult = orderMapper.selectUserOrders(new Page<>(page, size), userId, statuses);
        return pageResult.convert(this::toListVO);
    }

    public OrderDTO.OrderDetailVO detail(Long userId, Long orderId) {
        Order order = getOwned(userId, orderId);
        return toDetailVO(order);
    }

    // ==================== 再来一单 ====================

    @Transactional
    public OrderDTO.ReorderVO reorder(Long userId, Long orderId) {
        Order order = getOwned(userId, orderId);
        List<OrderItem> items = orderItemMapper.selectByOrder(orderId);
        int added = 0;
        List<OrderDTO.SkippedVO> skipped = new ArrayList<>();
        for (OrderItem oi : items) {
            try {
                CartService.AddRequest ar = new CartService.AddRequest(
                        oi.getProductId(), oi.getSkuId(), oi.getQuantity(),
                        parseSpecOptionIds(oi.getSpecSnapshot()),
                        parseToppings(oi.getToppingSnapshot()));
                cartService.add(userId, order.getStoreId(), ar);
                added++;
            } catch (BizException e) {
                OrderDTO.SkippedVO sv = new OrderDTO.SkippedVO();
                sv.setProductName(oi.getProductName());
                sv.setReason(e.getMessage());
                skipped.add(sv);
            }
        }
        OrderDTO.ReorderVO vo = new OrderDTO.ReorderVO();
        vo.setAddedCount(added);
        vo.setSkipped(skipped);
        return vo;
    }

    // ==================== 后台状态流转 ====================

    @Transactional
    public void accept(Long orderId, String operator) {
        transit(orderId, OrderStatus.PAID, OrderStatus.MAKING, operator, "接单");
    }

    @Transactional
    public void ready(Long orderId, String operator) {
        transit(orderId, OrderStatus.MAKING, OrderStatus.READY, operator, "出餐");
    }

    @Transactional
    public void delivering(Long orderId, String operator) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!OrderType.DELIVERY.name().equals(order.getOrderType())) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "仅外卖订单可配送");
        }
        transit(orderId, OrderStatus.READY, OrderStatus.DELIVERING, operator, "开始配送");
    }

    @Transactional
    public void complete(Long orderId, String operator) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (OrderStatus.READY.name().equals(order.getStatus())) {
            transit(orderId, OrderStatus.READY, OrderStatus.COMPLETED, operator, "完成");
        } else {
            transit(orderId, OrderStatus.DELIVERING, OrderStatus.COMPLETED, operator, "完成");
        }
    }

    @Transactional
    public void refund(Long orderId, String reason, String operator) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        boolean ok = orderMapper.tryTransit(orderId, OrderStatus.PAID.name(), OrderStatus.REFUNDED.name()) == 1
                || orderMapper.tryTransit(orderId, OrderStatus.MAKING.name(), OrderStatus.REFUNDED.name()) == 1
                || orderMapper.tryTransit(orderId, OrderStatus.READY.name(), OrderStatus.REFUNDED.name()) == 1
                || orderMapper.tryTransit(orderId, OrderStatus.DELIVERING.name(), OrderStatus.REFUNDED.name()) == 1;
        if (!ok) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "当前订单状态不可退款");
        }
        orderMapper.setRefunded(orderId, LocalDateTime.now());
        couponService.handleRefund(order.getUserCouponId());
        if (order.getPointAwarded() != null && order.getPointAwarded() > 0) {
            pointService.rollbackConsume(order.getUserId(), orderId, order.getPointAwarded());
        }
        for (OrderItem oi : orderItemMapper.selectByOrder(orderId)) {
            skuMapper.restoreStock(oi.getSkuId(), oi.getQuantity());
        }
        log(orderId, order.statusEnum(), OrderStatus.REFUNDED, operator, reason);
    }

    /** 后台核销取餐码（自取订单） */
    @Transactional
    public void redeem(Long storeId, String pickupCode, String operator) {
        Order order = orderMapper.selectByStoreAndPickup(storeId, pickupCode, LocalDate.now());
        if (order == null) {
            throw new BizException(ResultCode.PICKUP_CODE_INVALID, "取餐码无效");
        }
        if (OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.PICKUP_CODE_USED, "该取餐码已核销");
        }
        if (!OrderStatus.READY.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "订单尚未出餐，无法核销");
        }
        transit(order.getId(), OrderStatus.READY, OrderStatus.COMPLETED, operator, "核销取餐码");
    }

    // ==================== 内部工具 ====================

    private void transit(Long orderId, OrderStatus from, OrderStatus to, String operator, String remark) {
        int rows = orderMapper.tryTransit(orderId, from.name(), to.name());
        if (rows != 1) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单状态不允许从「" + from.getLabel() + "」变更为「" + to.getLabel() + "」");
        }
        if (to == OrderStatus.MAKING) {
            orderMapper.setAccepted(orderId, LocalDateTime.now());
        } else if (to == OrderStatus.READY) {
            orderMapper.setReady(orderId, LocalDateTime.now());
        } else if (to == OrderStatus.DELIVERING) {
            orderMapper.setDelivering(orderId, LocalDateTime.now());
        } else if (to == OrderStatus.COMPLETED) {
            orderMapper.setCompleted(orderId, LocalDateTime.now());
        }
        log(orderId, from, to, operator, remark);
    }

    private void log(Long orderId, OrderStatus from, OrderStatus to, String operator, String remark) {
        log(orderId, from == null ? null : from.name(), to == null ? null : to.name(), operator, remark);
    }

    private void log(Long orderId, String from, String to, String operator, String remark) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setOperator(operator);
        l.setRemark(remark);
        statusLogMapper.insert(l);
    }

    private Order getOwned(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private List<ProductService.ResolvedItem> resolveItems(Long userId, OrderDTO.PreviewRequest req) {
        List<ProductService.ItemRequest> items;
        if (Boolean.TRUE.equals(req.getFromCart())) {
            List<CartItem> cart = cartService.availableItems(userId, req.getStoreId());
            if (cart.isEmpty()) {
                throw new BizException(ResultCode.CART_EMPTY);
            }
            items = cart.stream().map(ci -> {
                ProductService.ItemRequest ir = new ProductService.ItemRequest();
                ir.setProductId(ci.getProductId());
                ir.setSkuId(ci.getSkuId());
                ir.setQuantity(ci.getQuantity());
                ir.setSpecOptionIds(parseSpecOptionIds(ci.getSpecSnapshot()));
                ir.setToppings(parseToppings(ci.getToppingSnapshot()));
                return ir;
            }).toList();
        } else {
            if (req.getItems() == null || req.getItems().isEmpty()) {
                throw new BizException(ResultCode.CART_EMPTY);
            }
            items = req.getItems();
        }
        List<ProductService.ResolvedItem> resolved = productService.resolveItems(req.getStoreId(), items);
        applyFlashPricing(resolved, items);
        return resolved;
    }

    /**
     * 将命中秒杀的条目价格改写为活动价并打 flash 标记。
     * 秒杀价覆盖成交单价（含规格/小料的兜底），之后在 computePlan 中跳过会员折扣/券/积分。
     */
    private void applyFlashPricing(List<ProductService.ResolvedItem> resolved, List<ProductService.ItemRequest> requests) {
        if (requests == null) {
            return;
        }
        int n = Math.min(resolved.size(), requests.size());
        for (int i = 0; i < n; i++) {
            Long flashSaleId = requests.get(i).getFlashSaleId();
            if (flashSaleId == null) {
                continue;
            }
            ProductService.ResolvedItem it = resolved.get(i);
            FlashSale fs = flashSaleMapper.selectById(flashSaleId);
            if (fs == null) {
                throw new BizException(ResultCode.ACTIVITY_NOT_FOUND, "秒杀不存在");
            }
            MarketingActivity act = marketingActivityMapper.selectById(fs.getActivityId());
            if (act == null || !"ONGOING".equals(act.getStatus())) {
                throw new BizException(ResultCode.ACTIVITY_NOT_ONGOING, "秒杀未开始或已结束");
            }
            if (!Objects.equals(fs.getSkuId(), it.getSkuId())) {
                throw new BizException(ResultCode.PARAM_ERROR, "秒杀规格与下单规格不一致");
            }
            it.setFlash(true);
            it.setFlashSaleId(fs.getId());
            it.setActivityId(fs.getActivityId());
            it.setUnitPrice(fs.getActivityPrice());
            it.setTotalPrice(fs.getActivityPrice() * it.getQuantity());
        }
    }

    /** 金额与优惠计算（预览/下单共用，保证两端一致） */
    private Plan computePlan(Long userId, List<ProductService.ResolvedItem> items,
                              Long storeId, String orderType, Boolean usePoint) {
        long goodsAmount = items.stream().mapToLong(ProductService.ResolvedItem::getTotalPrice).sum();
        // 秒杀价已内嵌在 totalPrice 中；秒杀与会员折扣/券/积分互斥，折扣只作用于非秒杀部分
        long flashGoods = items.stream().filter(ProductService.ResolvedItem::isFlash)
                .mapToLong(ProductService.ResolvedItem::getTotalPrice).sum();
        long normalGoods = goodsAmount - flashGoods;

        User user = userMapper.selectById(userId);
        MemberLevel level = levelOf(user == null ? null : user.getMemberLevelId());
        int levelDiscount = level == null || level.getDiscount() == null ? 10000 : level.getDiscount();
        long levelDiscountAmount = Money.discountAmount(normalGoods, levelDiscount);

        long couponBase = normalGoods - levelDiscountAmount;
        long couponAmount = 0;
        Long couponId = null;
        List<ProductService.ResolvedItem> couponItems = items.stream()
                .filter(it -> !it.isFlash()).toList();
        List<OrderDTO.AvailableCouponVO> coupons = couponService.availableCoupons(
                userId, orderType, couponItems, normalGoods, couponBase);
        OrderDTO.AvailableCouponVO best = coupons.stream().filter(OrderDTO.AvailableCouponVO::getUsable)
                .max(java.util.Comparator.comparing(OrderDTO.AvailableCouponVO::getDeductAmount))
                .orElse(null);
        if (best != null) {
            couponAmount = best.getDeductAmount();
            couponId = best.getUserCouponId();
        }

        long afterCoupon = couponBase - couponAmount;

        long balance = pointService.balance(userId);
        int centsPerPoint = config.pointDeductCentsPerPoint();
        int maxPercent = config.pointDeductMaxPercent();
        int minPoint = config.pointDeductMinPoint();
        long maxDeductCents = Money.percentOf(afterCoupon, maxPercent);
        long usableCents = Math.min(balance * centsPerPoint, maxDeductCents);
        int pointUsable = (int) (usableCents / centsPerPoint);
        if (pointUsable < minPoint) {
            pointUsable = 0;
            usableCents = 0;
        }
        boolean applyPoint = Boolean.TRUE.equals(usePoint) && pointUsable > 0;
        int appliedPointUsed = applyPoint ? pointUsable : 0;
        long appliedPointAmount = applyPoint ? usableCents : 0;

        long discountAmount = levelDiscountAmount + couponAmount + appliedPointAmount;

        Store store = storeService.getById(storeId);
        long deliveryFee = 0;
        Long belowMin = null;
        if (OrderType.DELIVERY.name().equals(orderType)) {
            long fee = store.getDeliveryFee() == null ? 0 : store.getDeliveryFee();
            long free = store.getFreeDeliveryThreshold() == null ? 0 : store.getFreeDeliveryThreshold();
            if (free > 0 && goodsAmount >= free) {
                fee = 0;
            }
            deliveryFee = fee;
            long min = store.getMinDeliveryAmount() == null ? 0 : store.getMinDeliveryAmount();
            belowMin = min > 0 ? Math.max(0, min - goodsAmount) : 0;
        }
        long payAmount = Math.max(0, goodsAmount - discountAmount + deliveryFee);

        Plan plan = new Plan();
        plan.items = items;
        plan.goodsAmount = goodsAmount;
        plan.couponBase = couponBase;
        plan.levelDiscountAmount = levelDiscountAmount;
        plan.couponAmount = couponAmount;
        plan.couponId = couponId;
        plan.pointUsable = pointUsable;
        plan.pointDeductCents = usableCents;
        plan.appliedPointUsed = appliedPointUsed;
        plan.appliedPointAmount = appliedPointAmount;
        plan.discountAmount = discountAmount;
        plan.deliveryFee = deliveryFee;
        plan.belowMinDeliveryAmount = belowMin;
        plan.payAmount = payAmount;
        return plan;
    }

    private MemberLevel levelOf(Long levelId) {
        if (levelId != null) {
            MemberLevel ml = memberLevelMapper.selectById(levelId);
            if (ml != null) {
                return ml;
            }
        }
        List<MemberLevel> all = memberLevelMapper.selectAllAsc();
        return all.isEmpty() ? null : all.get(0);
    }

    private List<String> resolveStatuses(String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String part : group.split(",")) {
            switch (part.trim().toUpperCase()) {
                case "UNPAID" -> result.add(OrderStatus.PENDING_PAYMENT.name());
                case "ONGOING" -> result.addAll(List.of(OrderStatus.PAID.name(), OrderStatus.MAKING.name(),
                        OrderStatus.READY.name(), OrderStatus.DELIVERING.name()));
                case "DONE" -> result.addAll(List.of(OrderStatus.COMPLETED.name(),
                        OrderStatus.CANCELLED.name(), OrderStatus.REFUNDED.name()));
                default -> result.add(part.trim().toUpperCase());
            }
        }
        return result.isEmpty() ? null : result;
    }

    // ==================== VO 转换 ====================

    private OrderDTO.PreviewItemVO toPreviewItem(ProductService.ResolvedItem it) {
        OrderDTO.PreviewItemVO vo = new OrderDTO.PreviewItemVO();
        vo.setProductId(it.getProductId());
        vo.setProductName(it.getProductName());
        vo.setProductImage(it.getProductImage());
        vo.setSkuId(it.getSkuId());
        vo.setSkuName(it.getSkuName());
        vo.setQuantity(it.getQuantity());
        vo.setUnitPrice(it.getUnitPrice());
        vo.setTotalPrice(it.getTotalPrice());
        vo.setSpecText(it.getSpecText());
        vo.setToppingText(it.getToppingText());
        return vo;
    }

    public OrderDTO.OrderListVO toListVO(Order order) {
        OrderDTO.OrderListVO vo = new OrderDTO.OrderListVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreId(order.getStoreId());
        vo.setStoreName(order.getStoreName());
        vo.setOrderType(order.getOrderType());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.of(order.getStatus()).getLabel());
        vo.setPayAmount(order.getPayAmount());
        vo.setPickupCode(order.getPickupCode());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setExpireTime(order.getExpireTime());
        List<OrderItem> items = orderItemMapper.selectByOrder(order.getId());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        vo.setItemCount(items.stream().mapToInt(OrderItem::getQuantity).sum());
        vo.setCanCancel(canCancel(order));
        vo.setCanPay(canPay(order));
        return vo;
    }

    private OrderDTO.OrderDetailVO toDetailVO(Order order) {
        OrderDTO.OrderDetailVO vo = new OrderDTO.OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreId(order.getStoreId());
        Store store = storeService.getByIdSafe(order.getStoreId());
        vo.setStoreName(store == null ? "" : store.getName());
        vo.setStoreAddress(store == null ? "" : store.getAddress());
        vo.setOrderType(order.getOrderType());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.of(order.getStatus()).getLabel());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setCouponAmount(order.getCouponAmount());
        vo.setPointAmount(order.getPointAmount());
        vo.setPointUsed(order.getPointUsed());
        vo.setLevelDiscountAmount(order.getLevelDiscountAmount());
        vo.setDeliveryFee(order.getDeliveryFee());
        vo.setPayAmount(order.getPayAmount());
        vo.setPickupCode(order.getPickupCode());
        vo.setPickupDate(order.getPickupDate() == null ? null : order.getPickupDate().toString());
        vo.setAddressSnapshot(order.getAddressSnapshot());
        vo.setExpectTime(order.getExpectTime());
        vo.setRemark(order.getRemark());
        vo.setExpireTime(order.getExpireTime());
        vo.setPaidAt(order.getPaidAt());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setCancelAt(order.getCancelAt());
        vo.setCancelReason(order.getCancelReason());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(orderItemMapper.selectByOrder(order.getId()).stream().map(this::toItemVO).toList());
        vo.setStatusLog(statusLogMapper.selectByOrder(order.getId()).stream().map(l -> {
            OrderDTO.StatusLogVO s = new OrderDTO.StatusLogVO();
            s.setFromStatus(l.getFromStatus());
            s.setToStatus(l.getToStatus());
            s.setFromStatusLabel(l.getFromStatus() == null ? "" : OrderStatus.of(l.getFromStatus()).getLabel());
            s.setToStatusLabel(l.getToStatus() == null ? "" : OrderStatus.of(l.getToStatus()).getLabel());
            s.setOperator(l.getOperator());
            s.setRemark(l.getRemark());
            s.setCreatedAt(l.getCreatedAt());
            return s;
        }).toList());
        vo.setCanCancel(canCancel(order));
        vo.setCanPay(canPay(order));
        return vo;
    }

    private OrderDTO.OrderItemVO toItemVO(OrderItem oi) {
        OrderDTO.OrderItemVO vo = new OrderDTO.OrderItemVO();
        vo.setProductId(oi.getProductId());
        vo.setProductName(oi.getProductName());
        vo.setProductImage(oi.getProductImage());
        vo.setSkuId(oi.getSkuId());
        vo.setSkuSnapshot(oi.getSkuSnapshot());
        vo.setQuantity(oi.getQuantity());
        vo.setUnitPrice(oi.getUnitPrice());
        vo.setTotalPrice(oi.getTotalPrice());
        vo.setActivityType(oi.getActivityType());
        vo.setActivityId(oi.getActivityId());
        vo.setSpecSnapshot(oi.getSpecSnapshot());
        vo.setToppingSnapshot(oi.getToppingSnapshot());
        vo.setSpecText(oi.getSpecText());
        vo.setToppingText(oi.getToppingText());
        return vo;
    }

    private boolean canCancel(Order order) {
        return OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus());
    }

    private boolean canPay(Order order) {
        return OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus());
    }

    private OrderDTO.SubmitVO toSubmitVO(Order order) {
        return toSubmitVO(order, null);
    }

    private OrderDTO.SubmitVO toSubmitVO(Order order, String payNo) {
        OrderDTO.SubmitVO vo = new OrderDTO.SubmitVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayNo(payNo);
        vo.setPayAmount(order.getPayAmount());
        vo.setExpireTime(order.getExpireTime());
        vo.setPickupCode(order.getPickupCode());
        return vo;
    }

    // ==================== JSON / 解析 ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Long> parseSpecOptionIds(String specSnapshot) {
        if (specSnapshot == null || specSnapshot.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(specSnapshot,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return list.stream().map(m -> ((Number) m.get("optionId")).longValue()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ProductService.ToppingRequest> parseToppings(String toppingSnapshot) {
        if (toppingSnapshot == null || toppingSnapshot.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(toppingSnapshot,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return list.stream().map(m -> {
                ProductService.ToppingRequest tr = new ProductService.ToppingRequest();
                tr.setToppingId(((Number) m.get("toppingId")).longValue());
                tr.setCount(((Number) m.get("count")).intValue());
                return tr;
            }).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 试算/下单的金额计划 */
    private static class Plan {
        List<ProductService.ResolvedItem> items;
        long goodsAmount;
        long couponBase;
        long levelDiscountAmount;
        long couponAmount;
        Long couponId;
        int pointUsable;
        long pointDeductCents;
        int appliedPointUsed;
        long appliedPointAmount;
        long discountAmount;
        long deliveryFee;
        Long belowMinDeliveryAmount;
        long payAmount;
    }
}
