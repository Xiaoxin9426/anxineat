package com.anxineat.util;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 单号生成。
 *
 * <p>订单号：yyyyMMddHHmmss + 6 位随机（保证可读且碰撞概率极低）。
 * 支付流水号：PAY + 时间戳 + 随机。
 */
public final class NoGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DIGITS = "0123456789";

    private NoGenerator() {
    }

    public static String orderNo() {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return ts + randomDigits(6);
    }

    public static String payNo() {
        return "PAY" + System.currentTimeMillis() + randomDigits(6);
    }

    /**
     * 取餐码：门店当日 3 位流水号（001-999）。
     *
     * <p>唯一域为「门店 + 日期 + 取餐码」，与门店实际喊号习惯一致；
     * 全局唯一会导致编码过长、不便口头播报。
     */
    public static String formatPickupCode(int seq) {
        return String.format("%03d", seq);
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }
}
