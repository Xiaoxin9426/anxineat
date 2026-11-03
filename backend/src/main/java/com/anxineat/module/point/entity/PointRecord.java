package com.anxineat.module.point.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水。
 *
 * <p>修正点：原需求文档验收标准写「积分余额 = 累计获得 − 累计使用」，
 * 同时又要求「积分默认年度滚动过期」。存在过期机制时该等式不成立，
 * 余额与流水将无法对账。修正后的口径：
 * <pre>
 *   余额 = 累计获得 − 累计使用 − 累计过期
 * </pre>
 * 并新增 EXPIRE 流水类型，使对账始终成立。
 *
 * <p>biz_no 唯一索引保证同一业务（同一订单的积分入账、同一用户的同一日签到）
 * 只记一次，是防重复入账的最后一道防线。
 */
@Data
@TableName("point_record")
public class PointRecord {

    /** 消费获得 */
    public static final String EARN_CONSUME = "EARN_CONSUME";
    /** 签到获得 */
    public static final String EARN_SIGNIN = "EARN_SIGNIN";
    /** 活动奖励获得（口令/秒杀等营销活动） */
    public static final String EARN_ACTIVITY = "EARN_ACTIVITY";
    /** 兑换优惠券消耗 */
    public static final String USE_EXCHANGE = "USE_EXCHANGE";
    /** 下单抵现消耗 */
    public static final String USE_DEDUCT = "USE_DEDUCT";
    /** 退款按比例扣回 */
    public static final String REFUND_ROLLBACK = "REFUND_ROLLBACK";
    /** 积分过期扣减 */
    public static final String EXPIRE = "EXPIRE";
    /** 后台人工调整 */
    public static final String ADMIN_ADJUST = "ADMIN_ADJUST";
    /** 下单抵现后取消订单，退回积分 */
    public static final String ROLLBACK_DEDUCT = "ROLLBACK_DEDUCT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    /** 正数为增加，负数为扣减 */
    private Long amount;
    /** 变动后余额 */
    private Long balanceAfter;
    /** 业务唯一号，防重复入账 */
    private String bizNo;
    private String remark;
    private LocalDateTime createdAt;

    public static String label(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case EARN_CONSUME -> "消费赠送";
            case EARN_SIGNIN -> "每日签到";
            case USE_EXCHANGE -> "兑换优惠券";
            case USE_DEDUCT -> "下单抵现";
            case REFUND_ROLLBACK -> "退款扣回";
            case EXPIRE -> "积分过期";
            case ADMIN_ADJUST -> "系统调整";
            case ROLLBACK_DEDUCT -> "取消退回";
            default -> type;
        };
    }
}
