package com.anxineat.module.order.enums;

/**
 * 订单类型。
 */
public enum OrderType {
    /** 到店自取 */
    PICKUP,
    /** 外卖配送 */
    DELIVERY;

    public static OrderType of(String name) {
        try {
            return OrderType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new com.anxineat.common.BizException(
                    com.anxineat.common.ResultCode.PARAM_ERROR, "未知订单类型：" + name);
        }
    }
}
