package com.anxineat.module.admin.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.OrderDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.admin.entity.AdminUser;
import com.anxineat.module.admin.mapper.AdminUserMapper;
import com.anxineat.module.coupon.entity.CouponTemplate;
import com.anxineat.module.coupon.entity.CouponScope;
import com.anxineat.module.coupon.mapper.CouponTemplateMapper;
import com.anxineat.module.coupon.mapper.CouponScopeMapper;
import com.anxineat.module.order.entity.Order;
import com.anxineat.module.order.enums.OrderStatus;
import com.anxineat.module.order.mapper.OrderMapper;
import com.anxineat.module.order.service.OrderService;
import com.anxineat.module.product.entity.Product;
import com.anxineat.module.product.entity.ProductSku;
import com.anxineat.module.product.entity.Category;
import com.anxineat.module.product.entity.SpecGroup;
import com.anxineat.module.product.entity.SpecOption;
import com.anxineat.module.product.entity.Topping;
import com.anxineat.module.product.mapper.ProductMapper;
import com.anxineat.module.product.mapper.ProductSkuMapper;
import com.anxineat.module.product.mapper.CategoryMapper;
import com.anxineat.module.product.mapper.SpecGroupMapper;
import com.anxineat.module.product.mapper.SpecOptionMapper;
import com.anxineat.module.product.mapper.ToppingMapper;
import com.anxineat.module.store.entity.Store;
import com.anxineat.module.store.mapper.StoreMapper;
import com.anxineat.module.store.mapper.StoreProductMapper;
import com.anxineat.module.system.service.SysConfigService;
import com.anxineat.security.AuthContext;
import com.anxineat.security.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商家后台服务。
 *
 * <p>店员（CLERK）调用任何带 storeId 的接口时，后端通过 {@link AuthContext#requireAdmin()}
 * 强制覆盖为绑定门店，越权返回 {@code 4010}（ADMIN_FORBIDDEN）。超管（SUPER_ADMIN）可跨门店。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminUserMapper adminUserMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final StoreMapper storeMapper;
    private final StoreProductMapper storeProductMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final SpecGroupMapper specGroupMapper;
    private final SpecOptionMapper specOptionMapper;
    private final ToppingMapper toppingMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponScopeMapper couponScopeMapper;
    private final SysConfigService config;
    private final JwtUtil jwtUtil;

    private static final BCryptPasswordEncoder BCrypt = new BCryptPasswordEncoder();

    // ==================== 登录 ====================

    public MemberDTO.AdminLoginVO login(RequestDTO.AdminLoginRequest req) {
        AdminUser admin = adminUserMapper.selectByUsername(req.getUsername());
        if (admin == null || admin.getPassword() == null || !BCrypt.matches(req.getPassword(), admin.getPassword())) {
            throw new BizException(ResultCode.ADMIN_AUTH_FAILED);
        }
        String token = jwtUtil.createAdminToken(admin.getId(), admin.getRole(), admin.getStoreId());
        MemberDTO.AdminLoginVO vo = new MemberDTO.AdminLoginVO();
        vo.setToken(token);
        vo.setAdmin(toAdminVO(admin));
        return vo;
    }

    // ==================== 概览 ====================

    public MemberDTO.OverviewVO overview(Long requestedStoreId) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        LocalDateTime from = java.time.LocalDate.now().atStartOfDay();
        OrderMapper.TodayStat stat = orderMapper.selectTodayStat(storeId, from);
        Long paidCount = orderMapper.selectPaidOrderCount(storeId, from);
        List<OrderMapper.StatusCount> counts = orderMapper.selectStatusCounts(storeId, from);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            statusCounts.put(s.name(), 0L);
        }
        long completedCount = 0;
        for (OrderMapper.StatusCount c : counts) {
            statusCounts.put(c.status, c.c);
            if (OrderStatus.COMPLETED.name().equals(c.status)) {
                completedCount = c.c;
            }
        }

        long completedAmount = stat == null || stat.completedAmount == null ? 0L : stat.completedAmount;
        long avg = completedCount > 0 ? completedAmount / completedCount : 0L;

        MemberDTO.TodayVO today = new MemberDTO.TodayVO();
        today.setOrderCount(stat == null || stat.orderCount == null ? 0L : stat.orderCount);
        today.setSalesAmount(stat == null || stat.salesAmount == null ? 0L : stat.salesAmount);
        today.setCompletedAmount(completedAmount);
        today.setAvgAmount(avg);

        MemberDTO.OverviewVO vo = new MemberDTO.OverviewVO();
        vo.setToday(today);
        vo.setStatusCounts(statusCounts);

        // 近 7 日销售趋势（含今日，缺失日补 0）
        LocalDate todayDate = LocalDate.now();
        LocalDateTime from7 = todayDate.minusDays(6).atStartOfDay();
        List<OrderMapper.TrendDay> trendRows = orderMapper.select7DayTrend(storeId, from7);
        Map<LocalDate, OrderMapper.TrendDay> trendMap = new LinkedHashMap<>();
        for (OrderMapper.TrendDay r : trendRows) {
            if (r.date != null) trendMap.put(r.date, r);
        }
        List<MemberDTO.TrendDayVO> trend7d = new ArrayList<>();
        DateTimeFormatter mmdd = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = todayDate.minusDays(i);
            OrderMapper.TrendDay r = trendMap.get(d);
            MemberDTO.TrendDayVO tv = new MemberDTO.TrendDayVO();
            tv.setDate(d.format(mmdd));
            tv.setOrderCount(r == null || r.orderCount == null ? 0L : r.orderCount);
            tv.setSalesAmount(r == null || r.salesAmount == null ? 0L : r.salesAmount);
            trend7d.add(tv);
        }
        vo.setTrend7d(trend7d);

        // 热销商品 TOP5（已完成订单明细销量聚合）
        List<OrderMapper.TopProduct> tops = orderMapper.selectTopProducts(storeId, 5);
        List<MemberDTO.TopProductVO> topProducts = tops.stream().map(t -> {
            MemberDTO.TopProductVO p = new MemberDTO.TopProductVO();
            p.setProductId(t.productId);
            p.setProductName(t.productName);
            p.setQty(t.qty == null ? 0L : t.qty);
            p.setAmount(t.amount == null ? 0L : t.amount);
            return p;
        }).toList();
        vo.setTopProducts(topProducts);

        return vo;
    }

    // ==================== 订单 ====================

    public IPage<OrderDTO.OrderListVO> orders(Long requestedStoreId, String status, String keyword, int page, int size) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        List<String> statuses = status == null || status.isBlank() ? null
                : java.util.Arrays.stream(status.split(",")).map(String::trim).map(String::toUpperCase).collect(Collectors.toList());
        IPage<Order> p = orderMapper.selectStoreOrders(new Page<>(page, size), storeId, statuses, keyword);
        List<OrderDTO.OrderListVO> list = p.getRecords().stream().map(o -> {
            o.setStoreName(storeName(storeId));
            return orderService.toListVO(o);
        }).toList();
        Page<OrderDTO.OrderListVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(list);
        return vo;
    }

    @Transactional
    public void accept(Long requestedStoreId, Long orderId, String operator) {
        assertStore(orderId, requestedStoreId);
        orderService.accept(orderId, operator);
    }

    @Transactional
    public void ready(Long requestedStoreId, Long orderId, String operator) {
        assertStore(orderId, requestedStoreId);
        orderService.ready(orderId, operator);
    }

    @Transactional
    public void delivering(Long requestedStoreId, Long orderId, String operator) {
        assertStore(orderId, requestedStoreId);
        orderService.delivering(orderId, operator);
    }

    @Transactional
    public void complete(Long requestedStoreId, Long orderId, String operator) {
        assertStore(orderId, requestedStoreId);
        orderService.complete(orderId, operator);
    }

    @Transactional
    public void refund(Long requestedStoreId, Long orderId, String reason, String operator) {
        assertStore(orderId, requestedStoreId);
        orderService.refund(orderId, reason, operator);
    }

    @Transactional
    public void redeem(Long requestedStoreId, String pickupCode, String operator) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        orderService.redeem(storeId, pickupCode, operator);
    }

    private void assertStore(Long orderId, Long requestedStoreId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        AuthContext.requireAdmin().assertStoreAccess(order.getStoreId());
    }

    // ==================== 门店 ====================

    public List<Store> stores() {
        Long storeId = AuthContext.requireAdmin().getStoreId();
        if (storeId != null) {
            Store s = storeMapper.selectById(storeId);
            return s == null ? List.of() : List.of(s);
        }
        return storeMapper.selectList(new LambdaQueryWrapper<Store>().orderByAsc(Store::getSort).orderByAsc(Store::getId));
    }

    @Transactional
    public void updateStore(Long requestedStoreId, RequestDTO.StoreAdminRequest req) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        Store s = new Store();
        s.setId(storeId);
        s.setBusinessStatus(req.getBusinessStatus());
        s.setSupportPickup(req.getSupportPickup());
        s.setSupportDelivery(req.getSupportDelivery());
        s.setDeliveryFee(req.getDeliveryFee());
        s.setMinDeliveryAmount(req.getMinDeliveryAmount());
        s.setFreeDeliveryThreshold(req.getFreeDeliveryThreshold());
        storeMapper.updateById(s);
    }

    // ==================== 商品 ====================

    public List<MemberDTO.AdminProductVO> products(Long requestedStoreId, Long categoryId, String keyword) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        // 超管「全部门店」(storeId=null) 时不能 eq(null)（会生成 WHERE store_id = NULL 恒不成立），改为查全部门店关系
        List<StoreProductLink> links = (storeId == null
                ? storeProductMapper.selectList(null)
                : storeProductMapper.selectList(new LambdaQueryWrapper<com.anxineat.module.store.entity.StoreProduct>()
                        .eq(com.anxineat.module.store.entity.StoreProduct::getStoreId, storeId)))
                .stream().map(StoreProductLink::new).toList();

        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .like(keyword != null && !keyword.isBlank(), Product::getName, keyword)
                .orderByAsc(Product::getSort).orderByAsc(Product::getId));

        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getProductId, productIds));
        Map<Long, List<ProductSku>> skuMap = skus.stream().collect(Collectors.groupingBy(ProductSku::getProductId));

        List<MemberDTO.AdminProductVO> result = new ArrayList<>();
        for (Product p : products) {
            List<StoreProductLink> matches = links.stream()
                    .filter(l -> Objects.equals(l.productId, p.getId())).toList();
            // 优先取「上架」的门店关系；全部门店视图下有任一门店上架即视为上架
            StoreProductLink link = matches.stream()
                    .filter(l -> l.status != null && l.status == 1).findFirst()
                    .orElse(matches.isEmpty() ? null : matches.get(0));
            boolean anyOnSale = matches.stream().anyMatch(l -> l.status != null && l.status == 1);
            boolean allSoldOut = !matches.isEmpty() && matches.stream()
                    .allMatch(l -> l.soldOut != null && l.soldOut == 1);
            MemberDTO.AdminProductVO vo = new MemberDTO.AdminProductVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setCategoryId(p.getCategoryId());
            vo.setBasePrice(p.getBasePrice());
            vo.setSales(p.getSales());
            vo.setSort(p.getSort());
            vo.setStatus(p.getStatus());
            vo.setManualSoldOut(Integer.valueOf(1).equals(p.getSoldOut()));
            if (link != null) {
                vo.setOnSale(anyOnSale);
                vo.setStoreSoldOut(allSoldOut);
                vo.setStorePriceDiff(link.priceDiff);
            } else {
                vo.setOnSale(false);
                vo.setStoreSoldOut(true);
                vo.setStorePriceDiff(0L);
            }
            vo.setSkus(skuMap.getOrDefault(p.getId(), List.of()).stream().map(sk -> {
                MemberDTO.AdminSkuVO sv = new MemberDTO.AdminSkuVO();
                sv.setId(sk.getId());
                sv.setCupSize(sk.getCupSize());
                sv.setPrice(sk.getPrice() + (link == null || link.priceDiff == null ? 0 : link.priceDiff));
                sv.setStock(sk.getStock());
                sv.setUnlimited(sk.getStock() == null || sk.getStock() == -1);
                sv.setStatus(sk.getStatus());
                return sv;
            }).toList());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public void productUpsert(RequestDTO.ProductAdminRequest req) {
        Product p = new Product();
        p.setName(req.getName());
        p.setCategoryId(req.getCategoryId());
        p.setBasePrice(req.getBasePrice());
        p.setDescription(req.getDescription());
        p.setImage(req.getImage());
        p.setCalories(req.getCalories());
        p.setSales(req.getSales() == null ? 0 : req.getSales());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setSort(req.getSort());
        if (req.getId() == null) {
            productMapper.insert(p);
        } else {
            p.setId(req.getId());
            productMapper.updateById(p);
        }
    }

    @Transactional
    public void setOnSale(Long requestedStoreId, Long productId, int onSale) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        AuthContext.requireAdmin().assertStoreAccess(storeId);
        LambdaUpdateWrapper<com.anxineat.module.store.entity.StoreProduct> uw =
                new LambdaUpdateWrapper<com.anxineat.module.store.entity.StoreProduct>()
                        .set(com.anxineat.module.store.entity.StoreProduct::getStatus, onSale)
                        .eq(com.anxineat.module.store.entity.StoreProduct::getProductId, productId);
        if (storeId != null) uw.eq(com.anxineat.module.store.entity.StoreProduct::getStoreId, storeId);
        storeProductMapper.update(null, uw);
    }

    @Transactional
    public void setSoldOut(Long requestedStoreId, Long productId, int soldOut) {
        Long storeId = AuthContext.requireAdmin().effectiveStoreId(requestedStoreId);
        AuthContext.requireAdmin().assertStoreAccess(storeId);
        LambdaUpdateWrapper<com.anxineat.module.store.entity.StoreProduct> uw =
                new LambdaUpdateWrapper<com.anxineat.module.store.entity.StoreProduct>()
                        .set(com.anxineat.module.store.entity.StoreProduct::getSoldOut, soldOut)
                        .eq(com.anxineat.module.store.entity.StoreProduct::getProductId, productId);
        if (storeId != null) uw.eq(com.anxineat.module.store.entity.StoreProduct::getStoreId, storeId);
        storeProductMapper.update(null, uw);
    }

    @Transactional
    public void setSkuStock(Long skuId, int stock) {
        skuMapper.setStock(skuId, stock);
    }

    /** 商品级手动售罄（全局生效，作用于 product.sold_out，优先于门店级） */
    @Transactional
    public void setProductSoldOut(Long productId, int soldOut) {
        Product p = productMapper.selectById(productId);
        if (p == null) throw new BizException(ResultCode.BIZ_ERROR, "商品不存在");
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .set(Product::getSoldOut, soldOut)
                .eq(Product::getId, productId);
        productMapper.update(null, uw);
    }

    // ==================== 商品详情（含 SKU / 规格 / 小料） ====================

    public MemberDTO.AdminProductDetailVO getProductDetail(Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null) throw new BizException(ResultCode.BIZ_ERROR, "商品不存在");

        MemberDTO.AdminProductDetailVO vo = new MemberDTO.AdminProductDetailVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setCategoryId(p.getCategoryId());
        vo.setDescription(p.getDescription());
        vo.setImage(p.getImage());
        vo.setBasePrice(p.getBasePrice());
        vo.setCalories(p.getCalories());
        vo.setSort(p.getSort());
        vo.setStatus(p.getStatus());

        vo.setSkus(skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getProductId, productId)
                        .eq(ProductSku::getStatus, 1)
                        .orderByAsc(ProductSku::getPrice)).stream().map(s -> {
            MemberDTO.AdminSkuDetailVO sv = new MemberDTO.AdminSkuDetailVO();
            sv.setId(s.getId());
            sv.setCupSize(s.getCupSize());
            sv.setPrice(s.getPrice());
            sv.setStock(s.getStock());
            sv.setStatus(s.getStatus());
            return sv;
        }).toList());

        vo.setSpecGroups(specGroupMapper.selectList(new LambdaQueryWrapper<SpecGroup>()
                        .eq(SpecGroup::getProductId, productId)
                        .orderByAsc(SpecGroup::getSort).orderByAsc(SpecGroup::getId)).stream().map(g -> {
            MemberDTO.AdminSpecGroupDetailVO gv = new MemberDTO.AdminSpecGroupDetailVO();
            gv.setId(g.getId());
            gv.setCode(g.getCode());
            gv.setName(g.getName());
            gv.setIsRequired(g.getIsRequired());
            gv.setSort(g.getSort());
            gv.setOptions(specOptionMapper.selectList(new LambdaQueryWrapper<SpecOption>()
                            .eq(SpecOption::getGroupId, g.getId())
                            .eq(SpecOption::getStatus, 1)
                            .orderByAsc(SpecOption::getSort).orderByAsc(SpecOption::getId)).stream().map(o -> {
                MemberDTO.AdminSpecOptionDetailVO ov = new MemberDTO.AdminSpecOptionDetailVO();
                ov.setId(o.getId());
                ov.setLabel(o.getLabel());
                ov.setExtraPrice(o.getExtraPrice());
                ov.setIsDefault(o.getIsDefault());
                ov.setSort(o.getSort());
                ov.setStatus(o.getStatus());
                return ov;
            }).toList());
            return gv;
        }).toList());

        vo.setToppings(toppingMapper.selectList(new LambdaQueryWrapper<Topping>()
                        .eq(Topping::getProductId, productId)
                        .eq(Topping::getStatus, 1)
                        .orderByAsc(Topping::getSort).orderByAsc(Topping::getId)).stream().map(t -> {
            MemberDTO.AdminToppingDetailVO tv = new MemberDTO.AdminToppingDetailVO();
            tv.setId(t.getId());
            tv.setName(t.getName());
            tv.setPrice(t.getPrice());
            tv.setMaxCount(t.getMaxCount());
            tv.setStock(t.getStock());
            tv.setSort(t.getSort());
            tv.setStatus(t.getStatus());
            return tv;
        }).toList());

        return vo;
    }

    @Transactional
    public void saveProductDetail(RequestDTO.ProductDetailAdminRequest req) {
        boolean isNew = req.getId() == null;
        Product p;
        if (isNew) {
            if (req.getName() == null || req.getName().isBlank()) {
                throw new BizException(ResultCode.BIZ_ERROR, "商品名称不能为空");
            }
            p = new Product();
        } else {
            p = productMapper.selectById(req.getId());
            if (p == null) throw new BizException(ResultCode.BIZ_ERROR, "商品不存在");
        }

        // ---- 基础信息 ----
        if (req.getName() != null) {
            if (req.getName().isBlank()) throw new BizException(ResultCode.BIZ_ERROR, "商品名称不能为空");
            p.setName(req.getName().trim());
        }
        p.setCategoryId(req.getCategoryId());
        p.setDescription(req.getDescription());
        p.setImage(req.getImage());
        p.setCalories(req.getCalories());
        p.setSort(req.getSort());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());

        // 新建商品需先落库拿到 id，SKU/规格/小料才能挂上
        if (isNew) {
            p.setBasePrice(0L);
            p.setSales(0);
            productMapper.insert(p);
        }

        // ---- SKU（杯型维度）----
        List<RequestDTO.SkuItem> skus = req.getSkus() == null ? List.of() : req.getSkus();
        if (skus.isEmpty()) throw new BizException(ResultCode.BIZ_ERROR, "商品至少需要一个 SKU（杯型）");
        List<ProductSku> savedSkus = new ArrayList<>();
        for (RequestDTO.SkuItem s : skus) {
            if (s.getCupSize() == null || s.getCupSize().isBlank())
                throw new BizException(ResultCode.BIZ_ERROR, "杯型名称不能为空");
            ProductSku sk = new ProductSku();
            sk.setProductId(p.getId());
            sk.setCupSize(s.getCupSize().trim());
            sk.setPrice(s.getPrice() == null ? 0L : s.getPrice());
            sk.setStock(s.getStock() == null ? -1 : s.getStock());
            sk.setStatus(s.getStatus() == null ? 1 : s.getStatus());
            if (s.getId() == null) {
                sk.setVersion(0);
                skuMapper.insert(sk);
            } else {
                sk.setId(s.getId());
                skuMapper.updateById(sk);
            }
            savedSkus.add(sk);
        }
        List<ProductSku> activeSkus = savedSkus.stream()
                .filter(sk -> Integer.valueOf(1).equals(sk.getStatus())).toList();
        long minPrice = activeSkus.isEmpty() ? 0L
                : activeSkus.stream().mapToLong(ProductSku::getPrice).min().orElse(0L);
        p.setBasePrice(minPrice);
        productMapper.updateById(p);

        // 禁用被移除的 SKU（保留历史订单引用，不去硬删）
        List<Long> keepSkuIds = savedSkus.stream().map(ProductSku::getId)
                .filter(Objects::nonNull).toList();
        for (ProductSku e : skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, p.getId()))) {
            if (!keepSkuIds.contains(e.getId())) { e.setStatus(0); skuMapper.updateById(e); }
        }

        // CUP 规格组由 SKU 派生同步（label = cupSize，extraPrice = 差价）
        syncCupSpecGroup(p.getId(), activeSkus, minPrice);

        // 非 CUP 规格组：按请求 upsert，移除的选项禁用
        List<RequestDTO.AdminSpecGroup> groups = req.getSpecGroups() == null ? List.of() : req.getSpecGroups();
        for (RequestDTO.AdminSpecGroup g : groups) {
            if ("CUP".equals(g.getCode())) continue; // CUP 由 SKU 派生，跳过
            SpecGroup sg;
            if (g.getId() == null) {
                sg = new SpecGroup();
                sg.setProductId(p.getId());
                sg.setCode(g.getCode());
                sg.setName(g.getName());
                sg.setIsRequired(g.getIsRequired() == null ? 1 : g.getIsRequired());
                sg.setSort(g.getSort() == null ? 0 : g.getSort());
                specGroupMapper.insert(sg);
            } else {
                sg = specGroupMapper.selectById(g.getId());
                if (sg == null) {
                    sg = new SpecGroup();
                    sg.setProductId(p.getId());
                    sg.setCode(g.getCode());
                }
                sg.setName(g.getName());
                sg.setIsRequired(g.getIsRequired());
                sg.setSort(g.getSort());
                specGroupMapper.updateById(sg);
            }
            List<Long> keepOptIds = new ArrayList<>();
            if (g.getOptions() != null) {
                for (RequestDTO.AdminSpecOption o : g.getOptions()) {
                    SpecOption so = new SpecOption();
                    so.setGroupId(sg.getId());
                    so.setLabel(o.getLabel() == null ? "" : o.getLabel().trim());
                    so.setExtraPrice(o.getExtraPrice() == null ? 0L : o.getExtraPrice());
                    so.setIsDefault(o.getIsDefault() == null ? 0 : o.getIsDefault());
                    so.setSort(o.getSort() == null ? 0 : o.getSort());
                    so.setStatus(o.getStatus() == null ? 1 : o.getStatus());
                    if (o.getId() == null) specOptionMapper.insert(so);
                    else { so.setId(o.getId()); specOptionMapper.updateById(so); }
                    if (so.getId() != null) keepOptIds.add(so.getId());
                }
            }
            for (SpecOption eo : specOptionMapper.selectList(new LambdaQueryWrapper<SpecOption>()
                    .eq(SpecOption::getGroupId, sg.getId()))) {
                if (!keepOptIds.contains(eo.getId())) { eo.setStatus(0); specOptionMapper.updateById(eo); }
            }
        }

        // ---- 小料 ----
        List<RequestDTO.ToppingItem> tops = req.getToppings() == null ? List.of() : req.getToppings();
        List<Long> keepTopIds = new ArrayList<>();
        for (RequestDTO.ToppingItem t : tops) {
            Topping tt = new Topping();
            tt.setProductId(p.getId());
            tt.setName(t.getName() == null ? "" : t.getName().trim());
            tt.setPrice(t.getPrice() == null ? 0L : t.getPrice());
            tt.setMaxCount(t.getMaxCount() == null ? 1 : t.getMaxCount());
            tt.setStock(t.getStock() == null ? -1 : t.getStock());
            tt.setSort(t.getSort() == null ? 0 : t.getSort());
            tt.setStatus(t.getStatus() == null ? 1 : t.getStatus());
            if (t.getId() == null) toppingMapper.insert(tt);
            else { tt.setId(t.getId()); toppingMapper.updateById(tt); }
            if (tt.getId() != null) keepTopIds.add(tt.getId());
        }
        for (Topping et : toppingMapper.selectList(new LambdaQueryWrapper<Topping>()
                .eq(Topping::getProductId, p.getId()))) {
            if (!keepTopIds.contains(et.getId())) { et.setStatus(0); toppingMapper.updateById(et); }
        }

        // 新建商品：自动挂到所有启用门店（priceDiff=0、上架、未售罄；门店维度差异后续可调）
        if (isNew) {
            for (Store s : storeMapper.selectList(new LambdaQueryWrapper<Store>().eq(Store::getStatus, 1))) {
                com.anxineat.module.store.entity.StoreProduct sp = new com.anxineat.module.store.entity.StoreProduct();
                sp.setStoreId(s.getId());
                sp.setProductId(p.getId());
                sp.setPriceDiff(0L);
                sp.setSoldOut(0);
                sp.setStatus(1);
                storeProductMapper.insert(sp);
            }
        }
    }

    /** CUP 规格组的选项与激活 SKU 同步：label = cupSize，extraPrice = sku.price - minPrice。 */
    private void syncCupSpecGroup(Long productId, List<ProductSku> activeSkus, long minPrice) {
        SpecGroup cup = specGroupMapper.selectList(new LambdaQueryWrapper<SpecGroup>()
                        .eq(SpecGroup::getProductId, productId).eq(SpecGroup::getCode, "CUP"))
                .stream().findFirst().orElse(null);
        if (cup == null) {
            cup = new SpecGroup();
            cup.setProductId(productId);
            cup.setCode("CUP");
            cup.setName("杯型");
            cup.setIsRequired(1);
            cup.setSort(1);
            specGroupMapper.insert(cup);
        }
        List<SpecOption> existing = specOptionMapper.selectList(
                new LambdaQueryWrapper<SpecOption>().eq(SpecOption::getGroupId, cup.getId()));
        Map<String, SpecOption> byLabel = existing.stream()
                .collect(Collectors.toMap(SpecOption::getLabel, o -> o, (a, b) -> a));
        List<Long> keepIds = new ArrayList<>();
        for (ProductSku s : activeSkus) {
            SpecOption o = byLabel.get(s.getCupSize());
            if (o == null) {
                o = new SpecOption();
                o.setGroupId(cup.getId());
                o.setLabel(s.getCupSize());
            }
            o.setExtraPrice(s.getPrice() - minPrice);
            o.setIsDefault(s.getPrice().equals(minPrice) ? 1 : 0);
            o.setSort(0);
            o.setStatus(1);
            if (o.getId() == null) specOptionMapper.insert(o);
            else specOptionMapper.updateById(o);
            keepIds.add(o.getId());
        }
        for (SpecOption eo : existing) {
            if (!keepIds.contains(eo.getId())) { eo.setStatus(0); specOptionMapper.updateById(eo); }
        }
    }

    // ==================== 商品分类 ====================

    public List<Category> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort).orderByAsc(Category::getId));
    }

    @Transactional
    public void categoryUpsert(RequestDTO.CategoryAdminRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ResultCode.BIZ_ERROR, "分类名称不能为空");
        }
        Category c = new Category();
        c.setName(req.getName().trim());
        c.setIcon(req.getIcon());
        c.setSort(req.getSort() == null ? 0 : req.getSort());
        c.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        if (req.getId() == null) {
            categoryMapper.insert(c);
        } else {
            c.setId(req.getId());
            categoryMapper.updateById(c);
        }
    }

    @Transactional
    public void categorySetStatus(Long id, int status) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.BIZ_ERROR, "分类不存在");
        c.setStatus(status);
        categoryMapper.updateById(c);
    }

    @Transactional
    public void categoryDelete(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.BIZ_ERROR, "分类不存在");
        long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, id));
        if (productCount > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "该分类下还有 " + productCount + " 个商品，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    // ==================== 优惠券模板 ====================

    public List<CouponTemplate> couponTemplates() {
        List<CouponTemplate> list = couponTemplateMapper.selectList(
                new LambdaQueryWrapper<CouponTemplate>().orderByAsc(CouponTemplate::getId));
        // 回显 coupon_scope 子表（逗号分隔 id）
        if (!list.isEmpty()) {
            Map<Long, String> scopeIdsMap = couponScopeMapper.selectList(null).stream()
                    .collect(Collectors.groupingBy(CouponScope::getCouponId,
                            Collectors.mapping(s -> String.valueOf(s.getRefId()),
                                    Collectors.joining(","))));
            list.forEach(t -> t.setScopeIds(scopeIdsMap.getOrDefault(t.getId(), "")));
        }
        return list;
    }

    @Transactional
    public void couponUpsert(RequestDTO.CouponAdminRequest req) {
        CouponTemplate t = new CouponTemplate();
        t.setName(req.getName());
        t.setType(req.getType());
        t.setAmount(req.getAmount());
        t.setMinAmount(req.getMinAmount());
        t.setDiscount(req.getDiscount());
        t.setMaxDiscount(req.getMaxDiscount());
        t.setScopeType(req.getScopeType());
        t.setChannels(req.getChannels());
        t.setValidType(req.getValidType() == null ? "DAYS" : req.getValidType());
        t.setValidDays(req.getValidDays());
        t.setPointCost(req.getPointCost());
        t.setTotal(req.getTotal());
        t.setLimitPerUser(req.getLimitPerUser());
        t.setStackable(req.getStackable());
        t.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        // 固定有效期（RANGE）：起止时间必须齐且止 > 起
        if ("RANGE".equals(t.getValidType())) {
            LocalDateTime st = parseAdminTime(req.getStartTime());
            LocalDateTime et = parseAdminTime(req.getEndTime());
            if (st == null || et == null || !et.isAfter(st)) {
                throw new BizException(ResultCode.BIZ_ERROR, "固定有效期需要填写起止时间，且结束时间晚于开始时间");
            }
            t.setStartTime(st);
            t.setEndTime(et);
        }
        if (req.getId() == null) {
            t.setIssued(0);
            couponTemplateMapper.insert(t);
        } else {
            t.setId(req.getId());
            couponTemplateMapper.updateById(t);
        }
        // 适用范围子表：全删重插（id 稳定、无外部引用，安全）
        couponScopeMapper.delete(new LambdaQueryWrapper<CouponScope>()
                .eq(CouponScope::getCouponId, t.getId()));
        if (!"ALL".equals(t.getScopeType()) && req.getScopeIds() != null && !req.getScopeIds().isBlank()) {
            for (String part : req.getScopeIds().split(",")) {
                String s = part.trim();
                if (s.isEmpty()) continue;
                try {
                    CouponScope cs = new CouponScope();
                    cs.setCouponId(t.getId());
                    cs.setScopeType(t.getScopeType());
                    cs.setRefId(Long.parseLong(s));
                    couponScopeMapper.insert(cs);
                } catch (NumberFormatException e) {
                    throw new BizException(ResultCode.BIZ_ERROR, "适用范围 id 必须为数字，逗号分隔：" + s);
                }
            }
        }
    }

    /** 解析控制台上传的时间串（"yyyy-MM-dd HH:mm:ss" 或 ISO "yyyy-MM-ddTHH:mm:ss"） */
    private LocalDateTime parseAdminTime(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().replace('T', ' ');
        if (s.length() == 16) s = s + ":00";
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            throw new BizException(ResultCode.BIZ_ERROR, "时间格式不正确：" + v);
        }
    }

    @Transactional
    public void setCouponStatus(Long id, int status) {
        CouponTemplate t = new CouponTemplate();
        t.setId(id);
        t.setStatus(status);
        couponTemplateMapper.updateById(t);
    }

    // ==================== 系统配置 ====================

    public Map<String, String> configs() {
        return config.all();
    }

    @Transactional
    public void updateConfig(String key, String value) {
        config.update(key, value);
    }

    // ==================== 内部 ====================

    private String storeName(Long storeId) {
        Store s = storeMapper.selectById(storeId);
        return s == null ? "" : s.getName();
    }

    private MemberDTO.AdminVO toAdminVO(AdminUser a) {
        MemberDTO.AdminVO vo = new MemberDTO.AdminVO();
        vo.setId(a.getId());
        vo.setUsername(a.getUsername());
        vo.setRealName(a.getRealName());
        vo.setRole(a.getRole());
        vo.setStoreId(a.getStoreId());
        vo.setStoreName(a.getStoreId() == null ? null : storeName(a.getStoreId()));
        return vo;
    }

    /** store_product 行的中间视图 */
    private static class StoreProductLink {
        final Long productId;
        final Integer status;
        final Integer soldOut;
        final Long priceDiff;

        StoreProductLink(com.anxineat.module.store.entity.StoreProduct sp) {
            this.productId = sp.getProductId();
            this.status = sp.getStatus();
            this.soldOut = sp.getSoldOut();
            this.priceDiff = sp.getPriceDiff() == null ? 0L : sp.getPriceDiff();
        }
    }
}
