package com.anxineat.module.order.enums;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 订单状态机。
 *
 * <p>原需求文档在「订单管理」「关键业务流程」中多次引用「本文件订单状态机定义」，
 * 但全文并未给出状态全集与合法迁移，导致验收标准「非法流转被拒绝」无法判定。
 * 此处补全该定义，并作为唯一权威来源：所有状态变更必须经 {@link #assertTransition} 校验。
 *
 * <pre>
 *   PENDING_PAYMENT ──支付成功──▶ PAID ──店员接单──▶ MAKING ──出餐──▶ READY
 *          │                      │                   │                │
 *          │                      │                   │                ├──(自取) 核销取餐码 ─▶ COMPLETED
 *          │                      │                   │                └──(外卖) 开始配送 ──▶ DELIVERING ─▶ COMPLETED
 *          │                      │                   │
 *          │                      └──退款─────────────┴────────────────────────────────▶ REFUNDED
 *          └──用户取消 / 15 分钟超时未支付 ─▶ CANCELLED
 * </pre>
 *
 * <p>终态：COMPLETED、CANCELLED、REFUNDED，终态不可再迁移。
 */
public enum OrderStatus {

    /** 待支付：订单已创建，资源（券、库存）已占用但未核销 */
    PENDING_PAYMENT("待支付", false),
    /** 已支付，等待店员接单 */
    PAID("已支付", false),
    /** 店员已接单，制作中 */
    MAKING("制作中", false),
    /** 已出餐：自取场景为「待取餐」，外卖场景为「待配送」 */
    READY("待取餐", false),
    /** 配送中（仅外卖订单） */
    DELIVERING("配送中", false),
    COMPLETED("已完成", true),
    CANCELLED("已取消", true),
    REFUNDED("已退款", true),
    ;

    private final String label;
    private final boolean terminal;

    OrderStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    static {
        PENDING_PAYMENT.next = EnumSet.of(PAID, CANCELLED);
        PAID.next = EnumSet.of(MAKING, CANCELLED, REFUNDED);
        MAKING.next = EnumSet.of(READY, REFUNDED);
        READY.next = EnumSet.of(COMPLETED, DELIVERING, REFUNDED);
        DELIVERING.next = EnumSet.of(COMPLETED, REFUNDED);
        COMPLETED.next = EnumSet.noneOf(OrderStatus.class);
        CANCELLED.next = EnumSet.noneOf(OrderStatus.class);
        REFUNDED.next = EnumSet.noneOf(OrderStatus.class);
    }

    private Set<OrderStatus> next;

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public Set<OrderStatus> getNext() {
        return next;
    }

    public boolean canTransitTo(OrderStatus target) {
        return next != null && next.contains(target);
    }

    /**
     * 校验状态流转是否合法，非法时抛出业务异常。
     */
    public void assertTransition(OrderStatus target) {
        if (!canTransitTo(target)) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    String.format("订单状态不允许从「%s」变更为「%s」", this.label, target.getLabel()));
        }
    }

    public static OrderStatus of(String name) {
        return Arrays.stream(values())
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.PARAM_ERROR, "未知订单状态：" + name));
    }
}
