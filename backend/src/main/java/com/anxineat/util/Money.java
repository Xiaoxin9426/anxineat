package com.anxineat.util;

/**
 * 金额工具。
 *
 * <p>全系统金额统一以【分】为单位用 long 存储与计算，禁止使用 double/float。
 * 所有折扣与抵扣金额向下取整（分），规则见需求文档「金额计算口径」。
 */
public final class Money {

    private Money() {
    }

    /** 元 -> 分，入参为字符串避免 double 精度问题 */
    public static long yuanToCent(String yuan) {
        return Math.round(Double.parseDouble(yuan) * 100);
    }

    /** 分 -> 元（仅用于展示与日志，不参与计算） */
    public static String centToYuan(long cent) {
        return String.format("%.2f", cent / 100.0);
    }

    /**
     * 按万分比计算折扣金额，向下取整。
     *
     * @param baseCents 折扣基数（分）
     * @param discount  折扣万分比，10000 表示不打折，9000 表示 9 折
     * @return 优惠金额（分）
     */
    public static long discountAmount(long baseCents, int discount) {
        if (discount <= 0 || discount >= 10000) {
            return 0;
        }
        return baseCents * (10000L - discount) / 10000L;
    }

    /**
     * 按百分比取金额，向下取整。用于积分抵扣上限等场景。
     */
    public static long percentOf(long baseCents, int percent) {
        return baseCents * percent / 100L;
    }
}
