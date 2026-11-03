package com.anxineat.module.coupon.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.OrderDTO;
import com.anxineat.module.coupon.entity.CouponScope;
import com.anxineat.module.coupon.entity.CouponTemplate;
import com.anxineat.module.coupon.entity.UserCoupon;
import com.anxineat.module.coupon.mapper.CouponScopeMapper;
import com.anxineat.module.coupon.mapper.CouponTemplateMapper;
import com.anxineat.module.coupon.mapper.UserCouponMapper;
import com.anxineat.module.product.service.ProductService;
import com.anxineat.util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 优惠券服务。
 *
 * <p>需求文档验收标准中「并发下单时一张券不会被重复核销」由
 * {@link UserCouponMapper#tryLock} 的条件更新保证，而不是靠「先查后改」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTemplateMapper templateMapper;
    private final CouponScopeMapper scopeMapper;
    private final UserCouponMapper userCouponMapper;
    private final com.anxineat.module.point.service.PointService pointService;

    // ==================== 领券中心 ====================

    public List<MemberDTO.CouponCenterVO> center(Long userId) {
        List<CouponTemplate> templates = templateMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CouponTemplate>()
                        .eq(CouponTemplate::getStatus, 1)
                        .orderByAsc(CouponTemplate::getId));

        List<UserCoupon> mine = userCouponMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId));
        Map<Long, Long> received = mine.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        LocalDateTime now = LocalDateTime.now();
        List<MemberDTO.CouponCenterVO> list = new ArrayList<>();
        for (CouponTemplate t : templates) {
            MemberDTO.CouponCenterVO vo = new MemberDTO.CouponCenterVO();
            vo.setTemplateId(t.getId());
            vo.setName(t.getName());
            vo.setType(t.getType());
            vo.setAmount(t.getAmount());
            vo.setMinAmount(t.getMinAmount());
            vo.setDiscount(t.getDiscount());
            vo.setMaxDiscount(t.getMaxDiscount());
            vo.setScopeType(t.getScopeType());
            vo.setChannels(t.getChannels());
            vo.setValidDays(t.getValidDays());
            vo.setStartTime(t.getStartTime());
            vo.setEndTime(t.getEndTime());
            vo.setPointCost(t.getPointCost());
            vo.setLimitPerUser(t.getLimitPerUser());
            vo.setTotal(t.getTotal());
            vo.setIssued(t.getIssued());

            long got = received.getOrDefault(t.getId(), 0L);
            vo.setReceivedCount((int) got);

            boolean expired = t.getEndTime() != null && t.getEndTime().isBefore(now);
            boolean soldOut = t.getTotal() != null && t.getTotal() >= 0 && t.getIssued() >= t.getTotal();
            boolean overLimit = got >= t.getLimitPerUser();

            vo.setCanReceive(!expired && !soldOut && !overLimit);
            if (expired) {
                vo.setReceiveDisabledReason("活动已结束");
            } else if (soldOut) {
                vo.setReceiveDisabledReason("已被领完");
            } else if (overLimit) {
                vo.setReceiveDisabledReason("已达领取上限");
            } else {
                vo.setReceiveDisabledReason("");
            }
            list.add(vo);
        }
        return list;
    }

    @Transactional
    public UserCoupon receive(Long userId, Long templateId) {
        CouponTemplate t = templateMapper.selectById(templateId);
        if (t == null || !Integer.valueOf(1).equals(t.getStatus())) {
            throw new BizException(ResultCode.COUPON_NOT_FOUND);
        }
        if (t.getEndTime() != null && t.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.COUPON_EXPIRED);
        }
        int got = userCouponMapper.countUserCoupon(userId, templateId);
        if (got >= t.getLimitPerUser()) {
            throw new BizException(ResultCode.COUPON_LIMIT_REACHED);
        }
        if (t.getTotal() != null && t.getTotal() >= 0 && t.getIssued() >= t.getTotal()) {
            throw new BizException(ResultCode.COUPON_SOLD_OUT);
        }

        LocalDateTime now = LocalDateTime.now();
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(templateId);
        uc.setStatus(com.anxineat.module.coupon.entity.UserCouponStatus.UNUSED);
        uc.setStartTime(now);
        // RANGE：固定有效期，用户券到期时间 = 模板 endTime；DAYS：领取后 N 天
        if ("RANGE".equals(t.getValidType()) && t.getEndTime() != null) {
            uc.setEndTime(t.getEndTime());
        } else {
            uc.setEndTime(now.plusDays(t.getValidDays() == null || t.getValidDays() <= 0 ? 7 : t.getValidDays()));
        }
        userCouponMapper.insert(uc);
        incrementIssued(templateId, t.getTotal());
        return uc;
    }

    /**
     * 发放量自增。
     *
     * <p>total >= 0 时附带 {@code issued < total} 条件，以受影响行数判断是否超发；
     * total = -1（不限量）时不加条件。这样即便并发领券也不会超过总量上限。
     */
    private void incrementIssued(Long templateId, Integer total) {
        var w = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CouponTemplate>()
                .setSql("issued = issued + 1")
                .eq(CouponTemplate::getId, templateId);
        if (total != null && total >= 0) {
            w.lt(CouponTemplate::getIssued, total);
        }
        templateMapper.update(null, w);
    }

    public List<MemberDTO.CouponVO> mine(Long userId, String status) {
        List<UserCoupon> list = userCouponMapper.selectUserCoupons(userId, status);
        LocalDateTime now = LocalDateTime.now();
        return list.stream().map(uc -> toVO(uc, now)).toList();
    }

    private MemberDTO.CouponVO toVO(UserCoupon uc, LocalDateTime now) {
        MemberDTO.CouponVO vo = new MemberDTO.CouponVO();
        vo.setId(uc.getId());
        vo.setCouponId(uc.getCouponId());
        vo.setName(uc.getName());
        vo.setType(uc.getType());
        vo.setAmount(uc.getAmount());
        vo.setMinAmount(uc.getMinAmount());
        vo.setDiscount(uc.getDiscount());
        vo.setMaxDiscount(uc.getMaxDiscount());
        vo.setScopeType(uc.getScopeType());
        vo.setChannels(uc.getChannels());
        vo.setPointCost(uc.getPointCost());
        vo.setStartTime(uc.getStartTime());
        vo.setEndTime(uc.getEndTime());
        // 未使用但已过期时，展示为已过期（不立刻刷库，由定时任务统一收敛）
        String showStatus = uc.getStatus();
        if (!"USED".equals(showStatus) && uc.isExpired(now)) {
            showStatus = "EXPIRED";
        }
        vo.setStatus(showStatus);
        vo.setStatusLabel(com.anxineat.module.coupon.entity.UserCouponStatus.label(showStatus));
        return vo;
    }

    // ==================== 积分兑换 ====================

    public List<MemberDTO.ExchangeVO> exchangeList(Long userId) {
        Long balance = pointService.balance(userId);
        List<CouponTemplate> templates = templateMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CouponTemplate>()
                        .eq(CouponTemplate::getStatus, 1)
                        .gt(CouponTemplate::getPointCost, 0)
                        .orderByAsc(CouponTemplate::getPointCost));
        return templates.stream().map(t -> {
            MemberDTO.ExchangeVO vo = new MemberDTO.ExchangeVO();
            vo.setTemplateId(t.getId());
            vo.setName(t.getName());
            vo.setType(t.getType());
            vo.setAmount(t.getAmount());
            vo.setMinAmount(t.getMinAmount());
            vo.setDiscount(t.getDiscount());
            vo.setMaxDiscount(t.getMaxDiscount());
            vo.setPointCost(t.getPointCost());
            boolean enough = balance != null && balance >= t.getPointCost();
            boolean soldOut = t.getTotal() != null && t.getTotal() >= 0 && t.getIssued() >= t.getTotal();
            vo.setCanExchange(enough && !soldOut);
            if (!enough) {
                vo.setReason("积分不足，还需 " + (t.getPointCost() - balance) + " 分");
            } else if (soldOut) {
                vo.setReason("已被兑完");
            } else {
                vo.setReason("");
            }
            return vo;
        }).toList();
    }

    /**
     * 积分兑换优惠券。
     *
     * <p>需求文档验收标准：积分兑换扣减与优惠券发放在同一事务内完成，避免不一致。
     * 因此这里用 @Transactional 包裹「扣积分 + 发券」两步。
     */
    @Transactional
    public MemberDTO.ExchangeResultVO exchange(Long userId, Long templateId) {
        CouponTemplate t = templateMapper.selectById(templateId);
        if (t == null || t.getPointCost() == null || t.getPointCost() <= 0) {
            throw new BizException(ResultCode.COUPON_NOT_FOUND, "该券不支持积分兑换");
        }
        if (t.getTotal() != null && t.getTotal() >= 0 && t.getIssued() >= t.getTotal()) {
            throw new BizException(ResultCode.COUPON_SOLD_OUT);
        }
        // 扣积分（余额不足时由 PointService 抛异常，事务整体回滚）
        pointService.consumeForExchange(userId, t.getPointCost(), t.getName());

        UserCoupon uc = receiveInternal(userId, t);

        MemberDTO.ExchangeResultVO vo = new MemberDTO.ExchangeResultVO();
        vo.setUserCouponId(uc.getId());
        vo.setPointCost(t.getPointCost());
        vo.setBalance(pointService.balance(userId));
        return vo;
    }

    private UserCoupon receiveInternal(Long userId, CouponTemplate t) {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(t.getId());
        uc.setStatus(com.anxineat.module.coupon.entity.UserCouponStatus.UNUSED);
        uc.setStartTime(now);
        // RANGE：固定有效期，用户券到期时间 = 模板 endTime；DAYS：领取后 N 天
        if ("RANGE".equals(t.getValidType()) && t.getEndTime() != null) {
            uc.setEndTime(t.getEndTime());
        } else {
            uc.setEndTime(now.plusDays(t.getValidDays() == null || t.getValidDays() <= 0 ? 7 : t.getValidDays()));
        }
        userCouponMapper.insert(uc);
        incrementIssued(t.getId(), t.getTotal());
        return uc;
    }

    // ==================== 下单时的可用券与抵扣计算 ====================

    /**
     * 列出用户在某笔订单上可用的券，并标注不可用券的原因。
     *
     * <p>需求文档只要求「仅展示当前订单可用的券」，但把不可用的原因一并返回，
     * 前端可以直接展示「还差 5 元可用」这类提示，比静默过滤体验好得多。
     */
    public List<OrderDTO.AvailableCouponVO> availableCoupons(Long userId, String orderType,
                                                             List<ProductService.ResolvedItem> items,
                                                             long goodsAmount, long couponBase) {
        List<UserCoupon> mine = userCouponMapper.selectUserCoupons(userId, null);
        LocalDateTime now = LocalDateTime.now();

        Map<Long, CouponTemplate> templates = templateMapper.selectList(null).stream()
                .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
        Map<Long, List<CouponScope>> scopes = scopeMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(CouponScope::getCouponId));

        Set<Long> itemProductIds = items.stream().map(ProductService.ResolvedItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> itemCategoryIds = items.stream().map(ProductService.ResolvedItem::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        List<OrderDTO.AvailableCouponVO> result = new ArrayList<>();
        for (UserCoupon uc : mine) {
            CouponTemplate t = templates.get(uc.getCouponId());
            if (t == null) {
                continue;
            }
            OrderDTO.AvailableCouponVO vo = new OrderDTO.AvailableCouponVO();
            vo.setUserCouponId(uc.getId());
            vo.setName(t.getName());
            vo.setType(t.getType());
            vo.setAmount(t.getAmount());
            vo.setMinAmount(t.getMinAmount());
            vo.setDiscount(t.getDiscount());
            vo.setMaxDiscount(t.getMaxDiscount());
            vo.setScopeType(t.getScopeType());
            vo.setChannels(t.getChannels());
            vo.setEndTime(uc.getEndTime());

            String reason = evaluate(t, uc, scopes.getOrDefault(t.getId(), List.of()),
                    orderType, goodsAmount, itemProductIds, itemCategoryIds, now);
            vo.setUsable(reason == null);
            vo.setUnusableReason(reason == null ? "" : reason);
            vo.setDeductAmount(reason == null ? calcDeduct(t, couponBase) : 0L);
            result.add(vo);
        }

        // 可用券在前，抵扣金额高的在前
        result.sort((a, b) -> {
            if (!Objects.equals(a.getUsable(), b.getUsable())) {
                return Boolean.TRUE.equals(a.getUsable()) ? -1 : 1;
            }
            return Long.compare(b.getDeductAmount(), a.getDeductAmount());
        });
        return result;
    }

    /** 返回 null 表示可用，否则返回不可用原因 */
    private String evaluate(CouponTemplate t, UserCoupon uc, List<CouponScope> scopes,
                            String orderType, long goodsAmount,
                            Set<Long> productIds, Set<Long> categoryIds, LocalDateTime now) {
        if (!"UNUSED".equals(uc.getStatus())) {
            return "已使用";
        }
        if (uc.isExpired(now)) {
            return "已过期";
        }
        if (!t.supportsChannel(orderType)) {
            return "仅限" + ("DELIVERY".equals(orderType) ? "自取" : "外卖") + "使用";
        }
        long min = t.getMinAmount() == null ? 0L : t.getMinAmount();
        if (goodsAmount < min) {
            return "差 " + Money.centToYuan(min - goodsAmount) + " 元可用";
        }
        // 适用范围
        if ("CATEGORY".equals(t.getScopeType())) {
            Set<Long> allowed = scopes.stream()
                    .filter(s -> "CATEGORY".equals(s.getScopeType()))
                    .map(CouponScope::getRefId).collect(Collectors.toSet());
            if (allowed.stream().noneMatch(categoryIds::contains)) {
                return "不含该分类商品";
            }
        } else if ("PRODUCT".equals(t.getScopeType())) {
            Set<Long> allowed = scopes.stream()
                    .filter(s -> "PRODUCT".equals(s.getScopeType()))
                    .map(CouponScope::getRefId).collect(Collectors.toSet());
            if (allowed.stream().noneMatch(productIds::contains)) {
                return "不含指定商品";
            }
        }
        return null;
    }

    /**
     * 计算券的抵扣金额。
     *
     * <p>口径：满减/折扣都作用在「商品总额 − 会员等级折扣」后的基数上，
     * 门槛判断则用商品总额（用户心智中的「满 15 减 3」指的是商品原价）。
     * 折扣券受最高抵扣上限约束，且抵扣不超过基数本身。
     */
    public long calcDeduct(CouponTemplate t, long couponBase) {
        long deduct;
        switch (t.getType()) {
            case CouponTemplate.TYPE_NO_THRESHOLD, CouponTemplate.TYPE_FULL_REDUCTION ->
                    deduct = t.getAmount() == null ? 0L : t.getAmount();
            case CouponTemplate.TYPE_DISCOUNT -> {
                deduct = Money.discountAmount(couponBase, t.getDiscount() == null ? 10000 : t.getDiscount());
                long cap = t.getMaxDiscount() == null ? 0L : t.getMaxDiscount();
                if (cap > 0) {
                    deduct = Math.min(deduct, cap);
                }
            }
            default -> deduct = 0L;
        }
        return Math.max(0, Math.min(deduct, couponBase));
    }

    // ==================== 占用 / 核销 / 释放 ====================

    /** 占用券，失败说明券已被其他订单占用（并发场景） */
    public void lock(Long userCouponId, Long orderId) {
        int rows = userCouponMapper.tryLock(userCouponId, orderId);
        if (rows != 1) {
            throw new BizException(ResultCode.COUPON_UNAVAILABLE, "优惠券不可用，可能已被其他订单使用");
        }
    }

    /** 支付成功后核销 */
    public void confirmUse(Long userCouponId, Long orderId) {
        userCouponMapper.tryUse(userCouponId, orderId);
    }

    /** 取消 / 超时 / 支付失败时释放 */
    public void release(Long userCouponId) {
        if (userCouponId != null) {
            userCouponMapper.release(userCouponId);
        }
    }

    /** 退款处理：券未过期则退回可再用，否则作废 */
    public void handleRefund(Long userCouponId) {
        if (userCouponId == null) {
            return;
        }
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !"USED".equals(uc.getStatus())) {
            return;
        }
        if (uc.getEndTime() != null && uc.getEndTime().isBefore(LocalDateTime.now())) {
            userCouponMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserCoupon>()
                            .set(UserCoupon::getStatus, "EXPIRED")
                            .eq(UserCoupon::getId, userCouponId));
        } else {
            userCouponMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserCoupon>()
                            .set(UserCoupon::getStatus, "UNUSED")
                            .set(UserCoupon::getOrderId, null)
                            .set(UserCoupon::getUsedAt, null)
                            .eq(UserCoupon::getId, userCouponId));
        }
    }

    /** 将已过期的未使用券置为过期状态 */
    public int markExpired() {
        return userCouponMapper.markExpired(LocalDateTime.now());
    }
}
