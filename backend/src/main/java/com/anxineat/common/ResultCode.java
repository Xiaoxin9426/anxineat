package com.anxineat.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码分级。
 *
 * <p>0 成功；1xxx 通用；2xxx 门店与商品；3xxx 订单与支付；4xxx 营销资产（券/积分）。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "ok"),

    // ---- 通用 1xxx ----
    PARAM_ERROR(1000, "参数错误"),
    BIZ_ERROR(1001, "业务异常"),
    UNAUTHORIZED(1002, "请先登录"),
    FORBIDDEN(1003, "无权限访问"),
    NOT_FOUND(1004, "数据不存在"),
    SYSTEM_ERROR(1005, "系统繁忙，请稍后再试"),
    FREQUENT(1006, "操作过于频繁，请稍后再试"),

    // ---- 门店与商品 2xxx ----
    STORE_CLOSED(2001, "该门店休息中，暂不可下单"),
    STORE_CHANNEL_DISABLED(2002, "该门店暂不支持此下单方式"),
    STORE_OUT_OF_RANGE(2003, "超出该门店配送范围"),
    PRODUCT_OFF_SHELF(2004, "商品已下架"),
    PRODUCT_SOLD_OUT(2005, "商品已售罄"),
    PRODUCT_NOT_IN_STORE(2006, "该门店不售卖此商品"),
    SKU_NOT_FOUND(2007, "商品规格不存在"),
    SPEC_REQUIRED(2008, "请选择完整的商品规格"),
    STOCK_NOT_ENOUGH(2009, "商品库存不足"),
    CART_EMPTY(2010, "购物车是空的"),
    DELIVERY_BELOW_MIN(2011, "未达到起送金额"),
    ADDRESS_REQUIRED(2012, "请选择收货地址"),

    // ---- 订单与支付 3xxx ----
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_ILLEGAL(3002, "订单状态不允许此操作"),
    ORDER_EXPIRED(3003, "订单已超时，请重新下单"),
    AMOUNT_MISMATCH(3004, "订单金额已变化，请刷新后重试"),
    PAY_AMOUNT_MISMATCH(3005, "支付金额与订单金额不一致"),
    PAY_FAILED(3006, "支付失败"),
    PAY_CHANNEL_ERROR(3007, "支付渠道异常"),
    PICKUP_CODE_INVALID(3008, "取餐码无效"),
    PICKUP_CODE_USED(3009, "该取餐码已核销"),

    // ---- 券与积分 4xxx ----
    COUPON_NOT_FOUND(4001, "优惠券不存在"),
    COUPON_UNAVAILABLE(4002, "优惠券不可用"),
    COUPON_ALREADY_USED(4003, "优惠券已被使用"),
    COUPON_EXPIRED(4004, "优惠券已过期"),
    COUPON_LIMIT_REACHED(4005, "已达领取上限"),
    COUPON_SOLD_OUT(4006, "优惠券已被领完"),
    POINT_NOT_ENOUGH(4007, "积分不足"),
    SIGNIN_ALREADY(4008, "今日已签到"),
    ADMIN_AUTH_FAILED(4009, "账号或密码错误"),
    ADMIN_FORBIDDEN(4010, "无权操作其他门店数据"),

    // ---- 营销活动 41xx（秒杀 / 口令 / 0元兑换）----
    ACTIVITY_NOT_FOUND(4101, "活动不存在"),
    ACTIVITY_NOT_ONGOING(4102, "活动未开始或已结束"),
    FLASH_SOLD_OUT(4103, "秒杀已售罄"),
    COMMAND_INVALID(4104, "口令无效"),
    COMMAND_CLAIMED(4105, "该口令已领取"),
    ZERO_LIMIT_EXCEED(4106, "兑换次数已用完"),
    FLASH_PER_LIMIT(4107, "超过秒杀限购数量"),
    ;

    private final int code;
    private final String message;
}
