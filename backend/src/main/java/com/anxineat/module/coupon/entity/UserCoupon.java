package com.anxineat.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户券实例。
 *
 * <p>version 用于乐观锁：核销采用条件更新
 * {@code UPDATE user_coupon SET status='LOCKED' WHERE id=? AND status='UNUSED'}，
 * 以受影响行数判定是否抢占成功，保证并发下单时同一张券只被一张订单占用。
 */
@Data
@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    private Long orderId;
    /** UNUSED / LOCKED / USED / EXPIRED */
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime usedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---------- 非数据库字段：券模板信息（列表展示用） ----------
    @TableField(exist = false)
    private String name;
    @TableField(exist = false)
    private String type;
    @TableField(exist = false)
    private Long amount;
    @TableField(exist = false)
    private Long minAmount;
    @TableField(exist = false)
    private Integer discount;
    @TableField(exist = false)
    private Long maxDiscount;
    @TableField(exist = false)
    private String scopeType;
    @TableField(exist = false)
    private String channels;
    @TableField(exist = false)
    private Long pointCost;
    @TableField(exist = false)
    private String statusLabel;

    public boolean isExpired(LocalDateTime now) {
        return endTime != null && endTime.isBefore(now);
    }
}
