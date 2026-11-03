package com.anxineat.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动参与记录：每人限领 + 幂等（uk_biz_no）。
 *
 * <p>biz_no 规则：
 * COMMAND -> 'CMD:'  + activity_id + ':' + command_id    + ':' + user_id
 * ZERO    -> 'ZERO:' + activity_id + ':' + sku_id         + ':' + user_id
 * FLASH   -> 'FLASH:'+ order_no    + ':' + sku_id
 */
@Data
@TableName("activity_record")
public class ActivityRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    /** FLASH_SALE / COMMAND / ZERO_EXCHANGE */
    private String type;
    /** 秒杀记录购买件数，其余为 1 */
    private Integer quantity;
    /** 如 user_coupon_id / 积分数 / 免单 sku */
    private String rewardDetail;
    private String bizNo;
    private LocalDateTime createdAt;
}
