package com.anxineat.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 优惠券模板。
 *
 * <p>相对原文档补充：
 * <ul>
 *   <li>stackable —— 明确「券与会员等级折扣是否可叠加」。原文档只定义了券与秒杀互斥，
 *       未定义与等级折扣的关系，会造成金额计算歧义。</li>
 *   <li>适用范围由单字段 scope 拆到 coupon_scope 子表，以支持「多个指定分类/商品」。</li>
 * </ul>
 */
@Data
@TableName("coupon_template")
public class CouponTemplate {

    /** 无门槛券：立减固定金额 */
    public static final String TYPE_NO_THRESHOLD = "NO_THRESHOLD";
    /** 满减券：满 X 减 Y */
    public static final String TYPE_FULL_REDUCTION = "FULL_REDUCTION";
    /** 折扣券：按比例打折，受最高抵扣上限约束 */
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    /** 无门槛/满减的立减金额（分） */
    private Long amount;
    /** 满减门槛（分） */
    private Long minAmount;
    /** 折扣万分比，9000 = 9 折 */
    private Integer discount;
    /** 折扣券最高抵扣（分），0 表示不限 */
    private Long maxDiscount;
    /** ALL 全场 / CATEGORY 指定分类 / PRODUCT 指定商品 */
    private String scopeType;
    /** 适用渠道，逗号分隔：PICKUP,DELIVERY */
    private String channels;
    /** DAYS 领取后 N 天有效 / RANGE 固定有效期 */
    private String validType;
    private Integer validDays;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 发放总量，-1 不限 */
    private Integer total;
    private Integer issued;
    private Integer limitPerUser;
    /** 积分兑换所需积分，0 表示不可用积分兑换 */
    private Long pointCost;
    /** 1 表示可与会员等级折扣叠加 */
    private Integer stackable;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 非数据库字段：后台列表回显用，逗号分隔的适用范围 id（coupon_scope 子表聚合） */
    @TableField(exist = false)
    private String scopeIds;

    /**
     * 判断该券是否适用于指定下单渠道。
     *
     * @param orderType PICKUP / DELIVERY
     */
    public boolean supportsChannel(String orderType) {
        if (channels == null || orderType == null) {
            return false;
        }
        return Arrays.stream(channels.split(","))
                .map(String::trim)
                .anyMatch(c -> c.equalsIgnoreCase(orderType.trim()));
    }
}
