package com.anxineat.module.marketing.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.module.coupon.entity.CouponScope;
import com.anxineat.module.coupon.entity.CouponTemplate;
import com.anxineat.module.coupon.entity.UserCoupon;
import com.anxineat.module.coupon.mapper.CouponScopeMapper;
import com.anxineat.module.coupon.mapper.CouponTemplateMapper;
import com.anxineat.module.coupon.service.CouponService;
import com.anxineat.module.marketing.entity.ActivityRecord;
import com.anxineat.module.marketing.entity.CommandActivity;
import com.anxineat.module.marketing.entity.FlashSale;
import com.anxineat.module.marketing.entity.MarketingActivity;
import com.anxineat.module.marketing.entity.ZeroExchangeActivity;
import com.anxineat.module.marketing.mapper.ActivityRecordMapper;
import com.anxineat.module.marketing.mapper.CommandActivityMapper;
import com.anxineat.module.marketing.mapper.FlashSaleMapper;
import com.anxineat.module.marketing.mapper.MarketingActivityMapper;
import com.anxineat.module.marketing.mapper.ZeroExchangeActivityMapper;
import com.anxineat.module.point.service.PointService;
import com.anxineat.module.product.entity.Product;
import com.anxineat.module.product.entity.ProductSku;
import com.anxineat.module.product.mapper.ProductMapper;
import com.anxineat.module.product.mapper.ProductSkuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销活动服务：秒杀 / 口令 / 0元兑换。
 *
 * <p>并发安全沿用项目既有模型：库存/计数走 CAS 条件更新，限领/幂等走
 * {@link ActivityRecord} 的 uk_biz_no 唯一约束。所有奖励（发券/发积分/免单券）
 * 均按 biz_no 幂等，保证「刷新/重试不会重复发放」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingService {

    private final MarketingActivityMapper activityMapper;
    private final FlashSaleMapper flashSaleMapper;
    private final CommandActivityMapper commandMapper;
    private final ZeroExchangeActivityMapper zeroMapper;
    private final ActivityRecordMapper recordMapper;
    private final CouponService couponService;
    private final PointService pointService;
    private final CouponTemplateMapper templateMapper;
    private final CouponScopeMapper scopeMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;

    // ==================== 用户端：发现 / 秒杀列表 ====================

    public List<ActivityDiscoverVO> discover(Long storeId, String type) {
        var w = new LambdaQueryWrapper<MarketingActivity>().eq(MarketingActivity::getStatus, "ONGOING");
        if (type != null && !type.isBlank()) {
            w.eq(MarketingActivity::getType, type);
        }
        if (storeId != null) {
            w.and(x -> x.eq(MarketingActivity::getStoreId, -1L).or().eq(MarketingActivity::getStoreId, storeId));
        }
        w.orderByDesc(MarketingActivity::getSort).orderByAsc(MarketingActivity::getId);
        return activityMapper.selectList(w).stream().map(a -> {
            ActivityDiscoverVO vo = new ActivityDiscoverVO();
            vo.setId(a.getId());
            vo.setType(a.getType());
            vo.setTitle(a.getTitle());
            vo.setBannerImage(a.getBannerImage());
            vo.setStartTime(a.getStartTime());
            vo.setEndTime(a.getEndTime());
            return vo;
        }).toList();
    }

    public List<FlashSaleVO> flashSaleList(Long storeId) {
        var aw = new LambdaQueryWrapper<MarketingActivity>()
                .eq(MarketingActivity::getStatus, "ONGOING")
                .eq(MarketingActivity::getType, "FLASH_SALE");
        if (storeId != null) {
            aw.and(x -> x.eq(MarketingActivity::getStoreId, -1L).or().eq(MarketingActivity::getStoreId, storeId));
        }
        List<MarketingActivity> acts = activityMapper.selectList(aw);
        List<FlashSaleVO> result = new ArrayList<>();
        for (MarketingActivity act : acts) {
            for (FlashSale fs : flashSaleMapper.selectByActivity(act.getId())) {
                ProductSku sku = skuMapper.selectById(fs.getSkuId());
                Product p = sku == null ? null : productMapper.selectById(sku.getProductId());
                FlashSaleVO vo = new FlashSaleVO();
                vo.setActivityId(act.getId());
                vo.setFlashSaleId(fs.getId());
                vo.setSkuId(fs.getSkuId());
                vo.setActivityPrice(fs.getActivityPrice());
                vo.setTotalStock(fs.getTotalStock());
                int sold = fs.getSoldStock() == null ? 0 : fs.getSoldStock();
                vo.setSoldStock(sold);
                vo.setRemaining(fs.getTotalStock() != null && fs.getTotalStock() == -1
                        ? -1 : Math.max(0, fs.getTotalStock() - sold));
                vo.setPerLimit(fs.getPerLimit());
                vo.setProductName(p == null ? "" : p.getName());
                vo.setProductImage(p == null ? "" : p.getImage());
                vo.setCupSize(sku == null ? "" : sku.getCupSize());
                result.add(vo);
            }
        }
        return result;
    }

    // ==================== 用户端：口令核销 ====================

    @Transactional
    public CommandRedeemVO redeemCommand(Long userId, String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            throw new BizException(ResultCode.COMMAND_INVALID);
        }
        String text = rawCommand.trim().toUpperCase();
        CommandActivity cmd = commandMapper.selectByText(text);
        if (cmd == null) {
            throw new BizException(ResultCode.COMMAND_INVALID);
        }
        MarketingActivity act = activityMapper.selectById(cmd.getActivityId());
        if (act == null || !"ONGOING".equals(act.getStatus())) {
            throw new BizException(ResultCode.COMMAND_INVALID);
        }

        String bizNo = "CMD:" + cmd.getActivityId() + ":" + cmd.getId() + ":" + userId;
        ActivityRecord existing = recordMapper.selectByBizNo(bizNo);
        if (existing != null) {
            return toCommandResult(cmd, existing.getRewardDetail());
        }

        if (cmd.getPerUserLimit() != null && cmd.getPerUserLimit() > 0) {
            int got = recordMapper.sumQuantity(cmd.getActivityId(), userId, "COMMAND");
            if (got >= cmd.getPerUserLimit()) {
                throw new BizException(ResultCode.COMMAND_CLAIMED);
            }
        }
        if (commandMapper.incrementClaimed(cmd.getId()) != 1) {
            throw new BizException(ResultCode.COMMAND_CLAIMED);
        }

        String rewardDetail = doCommandReward(userId, cmd);

        ActivityRecord rec = new ActivityRecord();
        rec.setActivityId(cmd.getActivityId());
        rec.setUserId(userId);
        rec.setType("COMMAND");
        rec.setQuantity(1);
        rec.setRewardDetail(rewardDetail);
        rec.setBizNo(bizNo);
        try {
            recordMapper.insert(rec);
        } catch (DuplicateKeyException ex) {
            log.warn("口令领取幂等命中：{}", bizNo);
        }
        return toCommandResult(cmd, rewardDetail);
    }

    private String doCommandReward(Long userId, CommandActivity cmd) {
        return switch (cmd.getRewardType()) {
            case "COUPON" -> {
                if (cmd.getCouponTemplateId() == null) {
                    throw new BizException(ResultCode.COUPON_NOT_FOUND, "口令未配置优惠券");
                }
                UserCoupon uc = couponService.receive(userId, cmd.getCouponTemplateId());
                yield "COUPON:" + uc.getId();
            }
            case "POINTS" -> {
                long pts = cmd.getPointAmount() == null ? 0 : cmd.getPointAmount();
                pointService.earnActivity(userId, pts,
                        "CMD:" + cmd.getActivityId() + ":" + cmd.getId() + ":" + userId + ":PTS", "口令奖励积分");
                yield "POINTS:" + pts;
            }
            case "PRODUCT_FREE" -> {
                if (cmd.getProductSkuId() == null) {
                    throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "口令未配置免单商品");
                }
                UserCoupon uc = issueFreeProductCoupon(userId, cmd.getProductSkuId(), "CMD_" + cmd.getId());
                yield "COUPON:" + uc.getId();
            }
            default -> throw new BizException(ResultCode.BIZ_ERROR, "未知奖励类型");
        };
    }

    private CommandRedeemVO toCommandResult(CommandActivity cmd, String rewardDetail) {
        CommandRedeemVO vo = new CommandRedeemVO();
        vo.setCommandId(cmd.getId());
        vo.setRewardType(cmd.getRewardType());
        if (rewardDetail != null) {
            if (rewardDetail.startsWith("COUPON:")) {
                vo.setUserCouponId(Long.parseLong(rewardDetail.substring(7)));
            } else if (rewardDetail.startsWith("POINTS:")) {
                vo.setPointAmount(Long.parseLong(rewardDetail.substring(7)));
            }
        }
        vo.setMessage("领取成功");
        return vo;
    }

    // ==================== 用户端：0元兑换 ====================

    @Transactional
    public ZeroRedeemVO redeemZeroExchange(Long userId, Long zeroId) {
        ZeroExchangeActivity z = zeroMapper.selectById(zeroId);
        if (z == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        MarketingActivity act = activityMapper.selectById(z.getActivityId());
        if (act == null || !"ONGOING".equals(act.getStatus())) {
            throw new BizException(ResultCode.ACTIVITY_NOT_ONGOING);
        }

        String bizNo = "ZERO:" + z.getActivityId() + ":" + z.getProductSkuId() + ":" + userId;
        ActivityRecord existing = recordMapper.selectByBizNo(bizNo);
        if (existing != null) {
            return toZeroResult(z, existing.getRewardDetail());
        }

        if (z.getPerUserLimit() != null && z.getPerUserLimit() > 0) {
            int got = recordMapper.sumQuantity(z.getActivityId(), userId, "ZERO_EXCHANGE");
            if (got >= z.getPerUserLimit()) {
                throw new BizException(ResultCode.ZERO_LIMIT_EXCEED);
            }
        }
        if (z.getPointCost() != null && z.getPointCost() > 0) {
            pointService.consumeForActivity(userId, z.getPointCost(),
                    "ZERO:" + z.getActivityId() + ":" + z.getProductSkuId() + ":" + userId + ":PTS",
                    "0元兑换消耗积分");
        }
        if (zeroMapper.incrementExchanged(z.getId()) != 1) {
            throw new BizException(ResultCode.ZERO_LIMIT_EXCEED);
        }

        if (z.getProductSkuId() == null) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "未配置免单商品");
        }
        UserCoupon uc = issueFreeProductCoupon(userId, z.getProductSkuId(), "ZERO_" + z.getId());
        String rewardDetail = "COUPON:" + uc.getId();

        ActivityRecord rec = new ActivityRecord();
        rec.setActivityId(z.getActivityId());
        rec.setUserId(userId);
        rec.setType("ZERO_EXCHANGE");
        rec.setQuantity(1);
        rec.setRewardDetail(rewardDetail);
        rec.setBizNo(bizNo);
        try {
            recordMapper.insert(rec);
        } catch (DuplicateKeyException ex) {
            log.warn("0元兑换幂等命中：{}", bizNo);
        }
        return toZeroResult(z, rewardDetail);
    }

    private ZeroRedeemVO toZeroResult(ZeroExchangeActivity z, String rewardDetail) {
        ZeroRedeemVO vo = new ZeroRedeemVO();
        vo.setProductSkuId(z.getProductSkuId());
        vo.setPointCost(z.getPointCost());
        if (rewardDetail != null && rewardDetail.startsWith("COUPON:")) {
            vo.setUserCouponId(Long.parseLong(rewardDetail.substring(7)));
        }
        vo.setMessage("兑换成功，免单券已发放");
        return vo;
    }

    /** 发放免单券：按 marker 复用同一张 NO_THRESHOLD 模板（scope=PRODUCT, ref=skuId） */
    private UserCoupon issueFreeProductCoupon(Long userId, Long skuId, String marker) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "免单商品不存在");
        }
        long amount = sku.getPrice() == null ? 0L : sku.getPrice();
        String name = "FREE_" + marker;
        CouponTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<CouponTemplate>().eq(CouponTemplate::getName, name));
        if (tpl == null) {
            tpl = new CouponTemplate();
            tpl.setName(name);
            tpl.setType(CouponTemplate.TYPE_NO_THRESHOLD);
            tpl.setAmount(amount);
            tpl.setMinAmount(0L);
            tpl.setScopeType("PRODUCT");
            tpl.setChannels("PICKUP,DELIVERY");
            tpl.setValidDays(7);
            tpl.setTotal(-1);
            tpl.setIssued(0);
            tpl.setLimitPerUser(1);
            tpl.setStackable(0);
            tpl.setStatus(1);
            templateMapper.insert(tpl);
            CouponScope scope = new CouponScope();
            scope.setCouponId(tpl.getId());
            scope.setScopeType("PRODUCT");
            scope.setRefId(skuId);
            scopeMapper.insert(scope);
        }
        return couponService.receive(userId, tpl.getId());
    }

    // ==================== 后台：活动 CRUD ====================

    @Transactional
    public Long upsertActivity(MarketingAdminRequest req) {
        if (req.getType() == null || req.getType().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "活动类型不能为空");
        }
        MarketingActivity act;
        boolean isNew = req.getId() == null;
        if (isNew) {
            act = new MarketingActivity();
            act.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "DRAFT" : req.getStatus());
        } else {
            act = activityMapper.selectById(req.getId());
            if (act == null) {
                throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
            }
            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                act.setStatus(req.getStatus());
            }
        }
        act.setType(req.getType());
        act.setTitle(req.getTitle());
        act.setStoreId(req.getStoreId() == null ? -1L : req.getStoreId());
        act.setStartTime(req.getStartTime());
        act.setEndTime(req.getEndTime());
        act.setBannerImage(req.getBannerImage());
        act.setSort(req.getSort() == null ? 0 : req.getSort());
        if (isNew) {
            activityMapper.insert(act);
        } else {
            activityMapper.updateById(act);
        }

        Long actId = act.getId();
        // 重建明细（编辑时先删后插，MVP 简化；重新配置会重置销量统计）
        flashSaleMapper.delete(new LambdaQueryWrapper<FlashSale>().eq(FlashSale::getActivityId, actId));
        commandMapper.delete(new LambdaQueryWrapper<CommandActivity>().eq(CommandActivity::getActivityId, actId));
        zeroMapper.delete(new LambdaQueryWrapper<ZeroExchangeActivity>().eq(ZeroExchangeActivity::getActivityId, actId));

        switch (req.getType()) {
            case "FLASH_SALE" -> {
                if (req.getSkuId() == null || req.getActivityPrice() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "秒杀需配置 sku 与活动价");
                }
                FlashSale fs = new FlashSale();
                fs.setActivityId(actId);
                fs.setStoreId(act.getStoreId());
                fs.setSkuId(req.getSkuId());
                fs.setActivityPrice(req.getActivityPrice());
                fs.setTotalStock(req.getTotalStock() == null ? -1 : req.getTotalStock());
                fs.setSoldStock(0);
                fs.setPerLimit(req.getPerLimit() == null ? 1 : req.getPerLimit());
                fs.setVersion(0);
                flashSaleMapper.insert(fs);
            }
            case "COMMAND" -> {
                if (req.getCommandText() == null || req.getCommandText().isBlank() || req.getRewardType() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "口令需配置口令串与奖励类型");
                }
                CommandActivity ca = new CommandActivity();
                ca.setActivityId(actId);
                ca.setStoreId(act.getStoreId());
                ca.setCommandText(req.getCommandText().trim().toUpperCase());
                ca.setRewardType(req.getRewardType());
                ca.setCouponTemplateId(req.getCouponTemplateId());
                ca.setPointAmount(req.getPointAmount());
                ca.setProductSkuId(req.getProductSkuId());
                ca.setTotalLimit(req.getTotalLimit() == null ? -1 : req.getTotalLimit());
                ca.setClaimedCount(0);
                ca.setPerUserLimit(req.getPerUserLimit() == null ? 1 : req.getPerUserLimit());
                commandMapper.insert(ca);
            }
            case "ZERO_EXCHANGE" -> {
                if (req.getProductSkuId() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "0元兑换需配置商品");
                }
                ZeroExchangeActivity ze = new ZeroExchangeActivity();
                ze.setActivityId(actId);
                ze.setStoreId(act.getStoreId());
                ze.setProductSkuId(req.getProductSkuId());
                ze.setPointCost(req.getPointCost() == null ? 0L : req.getPointCost());
                ze.setTotalLimit(req.getTotalLimit() == null ? -1 : req.getTotalLimit());
                ze.setExchangedCount(0);
                ze.setPerUserLimit(req.getPerUserLimit() == null ? 1 : req.getPerUserLimit());
                zeroMapper.insert(ze);
            }
            default -> throw new BizException(ResultCode.PARAM_ERROR, "未知活动类型");
        }
        return actId;
    }

    public List<ActivityAdminVO> listActivities(String status, String type) {
        var w = new LambdaQueryWrapper<MarketingActivity>();
        if (status != null && !status.isBlank()) {
            w.eq(MarketingActivity::getStatus, status);
        }
        if (type != null && !type.isBlank()) {
            w.eq(MarketingActivity::getType, type);
        }
        w.orderByDesc(MarketingActivity::getId);
        return activityMapper.selectList(w).stream().map(a -> {
            ActivityAdminVO vo = new ActivityAdminVO();
            vo.setId(a.getId());
            vo.setType(a.getType());
            vo.setTitle(a.getTitle());
            vo.setStoreId(a.getStoreId());
            vo.setStatus(a.getStatus());
            vo.setStartTime(a.getStartTime());
            vo.setEndTime(a.getEndTime());
            vo.setSort(a.getSort());
            vo.setBannerImage(a.getBannerImage());
            return vo;
        }).toList();
    }

    public void setStatus(Long id, String status) {
        MarketingActivity act = activityMapper.selectById(id);
        if (act == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (status == null || status.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "状态不能为空");
        }
        act.setStatus(status);
        activityMapper.updateById(act);
    }

    public ActivityDetailVO activityDetail(Long id) {
        MarketingActivity act = activityMapper.selectById(id);
        if (act == null) {
            throw new BizException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        ActivityDetailVO vo = new ActivityDetailVO();
        vo.setId(act.getId());
        vo.setType(act.getType());
        vo.setTitle(act.getTitle());
        vo.setStoreId(act.getStoreId());
        vo.setStatus(act.getStatus());
        vo.setStartTime(act.getStartTime());
        vo.setEndTime(act.getEndTime());
        vo.setBannerImage(act.getBannerImage());
        vo.setSort(act.getSort());
        switch (act.getType()) {
            case "FLASH_SALE" -> vo.setFlashSales(flashSaleMapper.selectByActivity(act.getId()));
            case "COMMAND" -> vo.setCommands(commandMapper.selectByActivity(act.getId()));
            case "ZERO_EXCHANGE" -> {
                List<ZeroExchangeActivity> zs = zeroMapper.selectByActivity(act.getId());
                vo.setZero(zs.isEmpty() ? null : zs.get(0));
            }
            default -> { /* no detail */ }
        }
        return vo;
    }

    // ==================== VO / 请求 ====================

    @Data
    public static class ActivityDiscoverVO {
        private Long id;
        private String type;
        private String title;
        private String bannerImage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    public static class FlashSaleVO {
        private Long activityId;
        private Long flashSaleId;
        private Long skuId;
        private Long activityPrice;
        private Integer totalStock;
        private Integer soldStock;
        /** -1 表示不限量 */
        private Integer remaining;
        private Integer perLimit;
        private String productName;
        private String productImage;
        private String cupSize;
    }

    @Data
    public static class CommandRedeemVO {
        private Long commandId;
        private String rewardType;
        private Long userCouponId;
        private Long pointAmount;
        private String message;
    }

    @Data
    public static class ZeroRedeemVO {
        private Long productSkuId;
        private Long pointCost;
        private Long userCouponId;
        private String message;
    }

    @Data
    public static class ActivityAdminVO {
        private Long id;
        private String type;
        private String title;
        private Long storeId;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer sort;
        private String bannerImage;
    }

    @Data
    public static class ActivityDetailVO {
        private Long id;
        private String type;
        private String title;
        private Long storeId;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String bannerImage;
        private Integer sort;
        private List<FlashSale> flashSales;
        private List<CommandActivity> commands;
        private ZeroExchangeActivity zero;
    }

    @Data
    public static class MarketingAdminRequest {
        private Long id;
        private String type;
        private String title;
        private Long storeId;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String bannerImage;
        private Integer sort;
        // ---- FLASH_SALE ----
        private Long skuId;
        private Long activityPrice;
        private Integer totalStock;
        private Integer perLimit;
        // ---- COMMAND ----
        private String commandText;
        private String rewardType;
        private Long couponTemplateId;
        private Long pointAmount;
        private Long productSkuId;
        private Integer totalLimit;
        private Integer perUserLimit;
        // ---- ZERO_EXCHANGE ----
        private Long pointCost; // 复用 productSkuId / pointCost / totalLimit / perUserLimit
    }
}
