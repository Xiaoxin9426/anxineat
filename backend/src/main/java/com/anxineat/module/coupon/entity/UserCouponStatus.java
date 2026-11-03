package com.anxineat.module.coupon.entity;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 用户券状态。
 *
 * <p>修正点（关键）：原需求文档只定义了「未使用 / 已使用 / 已过期」三态，
 * 但正文同时要求「下单成功即核销占用」与「支付失败、取消则释放」。
 * 三态无法区分「已被订单占用但未支付」和「真正在用」，导致超时取消时
 * 无法判断该券是否应当退回。因此增加 LOCKED（占用态）：
 *
 * <pre>
 *   UNUSED ──下单占用──▶ LOCKED ──支付成功──▶ USED
 *      ▲                   │
 *      └──取消/超时/支付失败──┘
 *   任意非 USED 态 ──到期──▶ EXPIRED
 * </pre>
 */
public final class UserCouponStatus {

    private UserCouponStatus() {
    }

    public static final String UNUSED = "UNUSED";
    public static final String LOCKED = "LOCKED";
    public static final String USED = "USED";
    public static final String EXPIRED = "EXPIRED";

    public static void checkValid(String s) {
        if (!Arrays.asList(UNUSED, LOCKED, USED, EXPIRED).contains(s)) {
            throw new BizException(ResultCode.PARAM_ERROR, "非法的用户券状态：" + s);
        }
    }

    public static String label(String s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case UNUSED -> "未使用";
            case LOCKED -> "占用中";
            case USED -> "已使用";
            case EXPIRED -> "已过期";
            default -> s;
        };
    }
}
