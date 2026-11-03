package com.anxineat.module.order.entity;

import com.anxineat.module.order.enums.OrderStatus;
import com.anxineat.module.order.enums.OrderType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单主表。
 *
 * <p>相对原需求文档补齐的字段：
 * <ul>
 *   <li>各状态时间戳（paid_at / accepted_at / ready_at / delivering_at / completed_at / cancel_at）
 *       —— 原表只有 created_at，无法渲染验收项要求的「订单详情时间线」。</li>
 *   <li>point_used / level_discount_amount —— 原表只有 discount_amount 汇总，
 *       退款时无法按比例精确回退各类优惠。</li>
 *   <li>point_awarded —— 消费积分入账的幂等标记，防止回调重复触发重复发分。</li>
 *   <li>idempotent_key 唯一索引 —— 原文档要求「下单传 idempotent_key」但表上无约束，
 *       无唯一索引的幂等判断在并发下会失效。</li>
 * </ul>
 */
@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long storeId;

    @TableField("order_type")
    private String orderType;

    @TableField("status")
    private String status;

    /** 商品总额（分） */
    private Long totalAmount;
    /** 优惠总额 = 等级折扣 + 券 + 积分抵扣（分） */
    private Long discountAmount;
    private Long couponAmount;
    private Long pointAmount;
    /** 本次消耗积分 */
    private Long pointUsed;
    private Long levelDiscountAmount;
    private Long deliveryFee;
    /** 应收金额 = totalAmount - discountAmount + deliveryFee（分） */
    private Long payAmount;

    private Long userCouponId;
    private String pickupCode;
    private LocalDate pickupDate;
    /** 外卖地址快照 JSON */
    private String addressSnapshot;
    private LocalDateTime expectTime;
    private String remark;
    private String idempotentKey;
    private LocalDateTime expireTime;

    private LocalDateTime paidAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime deliveringAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelAt;
    private String cancelReason;
    private LocalDateTime refundedAt;
    private Integer pointAwarded;
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---------- 非数据库字段 ----------

    @TableField(exist = false)
    private List<OrderItem> items;

    @TableField(exist = false)
    private String storeName;

    @TableField(exist = false)
    private String statusLabel;

    public OrderType typeEnum() {
        return OrderType.of(orderType);
    }

    public OrderStatus statusEnum() {
        return OrderStatus.of(status);
    }

    public void setStatusEnum(OrderStatus s) {
        this.status = s.name();
    }
}
