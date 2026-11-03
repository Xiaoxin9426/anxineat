package com.anxineat.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 口令明细：command_text 一律存大写，匹配前对输入 trim + 转大写。
 */
@Data
@TableName("command_activity")
public class CommandActivity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long storeId;
    /** 口令串（大写存储），如 AXN666 */
    private String commandText;
    /** COUPON 发券 / POINTS 发积分 / PRODUCT_FREE 免单券 */
    private String rewardType;
    /** reward_type=COUPON 时必填 */
    private Long couponTemplateId;
    /** reward_type=POINTS 时发放积分 */
    private Long pointAmount;
    /** reward_type=PRODUCT_FREE 时免单商品 */
    private Long productSkuId;
    /** 总领取次数，-1 不限 */
    private Integer totalLimit;
    private Integer claimedCount;
    /** 每人限领次数，默认 1 */
    private Integer perUserLimit;
}
