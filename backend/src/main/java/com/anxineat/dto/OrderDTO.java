package com.anxineat.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车、订单、支付相关的响应对象。金额单位一律为【分】。
 */
public final class OrderDTO {

    private OrderDTO() {
    }

    /** 购物车 */
    @Data
    public static class CartVO {
        private Long storeId;
        private List<CartItemVO> items;
        private Integer totalQuantity;
        private Long totalAmount;
        private String storeName;
    }

    @Data
    public static class CartItemVO {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage;
        private Long skuId;
        private String skuName;
        private Integer quantity;
        /** 商品基准价（SKU 价 + 门店加价），不含规格与小料加价 */
        private Long basePrice;
        /** 单份成交价，含规格加价与小料价 */
        private Long unitPrice;
        /** unitPrice * quantity */
        private Long totalPrice;
        /** JSON 字符串，前端需 JSON.parse */
        private String specSnapshot;
        /** JSON 字符串，前端需 JSON.parse */
        private String toppingSnapshot;
        private Boolean available;
        private String unavailableReason;
        private Integer maxQuantity;
    }

    /** 订单金额试算结果 */
    @Data
    public static class PreviewVO {
        private Long goodsAmount;
        private Long levelDiscountAmount;
        private Long couponAmount;
        private Long pointAmount;
        private Long pointUsed;
        private Long discountAmount;
        private Long deliveryFee;
        private Long payAmount;
        private List<PreviewItemVO> items;
        private List<AvailableCouponVO> availableCoupons;
        private Long bestCouponId;
        private Long pointAvailable;
        private Long pointUsable;
        private Long pointDeductAmount;
        /** 未达起送价时的差额，0 表示已满足 */
        private Long belowMinDeliveryAmount;
    }

    @Data
    public static class PreviewItemVO {
        private Long productId;
        private String productName;
        private String productImage;
        private Long skuId;
        private String skuName;
        private Integer quantity;
        private Long unitPrice;
        private Long totalPrice;
        /** 来源活动类型 FLASH_SALE，非活动为 null */
        private String activityType;
        private Long activityId;
        /** 规格文本，如「中杯 / 五分糖 / 少冰」 */
        private String specText;
        private String toppingText;
    }

    @Data
    public static class AvailableCouponVO {
        private Long userCouponId;
        private String name;
        private String type;
        private Long amount;
        private Long minAmount;
        private Integer discount;
        private Long maxDiscount;
        private String scopeType;
        private String channels;
        private LocalDateTime endTime;
        private Boolean usable;
        private String unusableReason;
        /** 若使用本券可抵扣的金额 */
        private Long deductAmount;
    }

    /** 提交订单的响应 */
    @Data
    public static class SubmitVO {
        private Long orderId;
        private String orderNo;
        private String payNo;
        private Long payAmount;
        private LocalDateTime expireTime;
        /** 自取订单的取餐码，外卖订单为 null */
        private String pickupCode;
    }

    /** 订单详情 */
    @Data
    public static class OrderDetailVO {
        private Long id;
        private String orderNo;
        private Long storeId;
        private String storeName;
        private String storeAddress;
        private String orderType;
        private String status;
        private String statusLabel;
        private Long totalAmount;
        private Long discountAmount;
        private Long couponAmount;
        private Long pointAmount;
        private Long pointUsed;
        private Long levelDiscountAmount;
        private Long deliveryFee;
        private Long payAmount;
        private String pickupCode;
        private String pickupDate;
        private String addressSnapshot;
        private LocalDateTime expectTime;
        private String remark;
        private LocalDateTime expireTime;
        private LocalDateTime paidAt;
        private LocalDateTime completedAt;
        private LocalDateTime cancelAt;
        private String cancelReason;
        private LocalDateTime createdAt;
        private List<OrderItemVO> items;
        private List<StatusLogVO> statusLog;
        private Boolean canCancel;
        private Boolean canPay;
    }

    @Data
    public static class OrderItemVO {
        private Long productId;
        private String productName;
        private String productImage;
        private Long skuId;
        private String skuSnapshot;
        private Integer quantity;
        private Long unitPrice;
        private Long totalPrice;
        /** 来源活动类型 FLASH_SALE，非活动为 null */
        private String activityType;
        private Long activityId;
        private String specSnapshot;
        private String toppingSnapshot;
        private String specText;
        private String toppingText;
    }

    @Data
    public static class StatusLogVO {
        private String fromStatus;
        private String toStatus;
        private String fromStatusLabel;
        private String toStatusLabel;
        private String operator;
        private String remark;
        private LocalDateTime createdAt;
    }

    /** 订单列表项 */
    @Data
    public static class OrderListVO {
        private Long id;
        private String orderNo;
        private Long storeId;
        private String storeName;
        private String orderType;
        private String status;
        private String statusLabel;
        private Long payAmount;
        private String pickupCode;
        private LocalDateTime createdAt;
        private LocalDateTime expireTime;
        private List<OrderItemVO> items;
        private Integer itemCount;
        private Boolean canCancel;
        private Boolean canPay;
    }

    /** 创建支付单的响应 */
    @Data
    public static class PayCreateVO {
        private String payNo;
        private Long orderId;
        private Long amount;
        private String channel;
        private LocalDateTime expireTime;
        /** true 表示当前是模拟支付渠道，前端应展示模拟支付界面 */
        private Boolean mock;
        private Object prepay;
    }

    /** 支付结果（回调处理 / 主动查询共用） */
    @Data
    public static class PayResultVO {
        /** 本次是否真正处理了支付成功逻辑 */
        private Boolean handled;
        /** 是否为重复回调（幂等拦截） */
        private Boolean duplicateHandled;
        private Long orderId;
        private String orderNo;
        private String orderStatus;
        private String orderStatusLabel;
        private String pickupCode;
        private String message;
    }

    /** 「再来一单」的响应 */
    @Data
    public static class ReorderVO {
        private Integer addedCount;
        private List<SkippedVO> skipped;
    }

    @Data
    public static class SkippedVO {
        private String productName;
        private String reason;
    }

    // ==================== 下单请求 ====================

    /** 金额试算请求 */
    @Data
    public static class PreviewRequest {
        private Long storeId;
        private String orderType;
        /** true 表示使用服务端购物车；false 时必传 items */
        private Boolean fromCart;
        private List<com.anxineat.module.product.service.ProductService.ItemRequest> items;
        /** 选用的用户券 id（可选） */
        private Long userCouponId;
        /** 是否使用积分抵现（可选） */
        private Boolean usePoint;
        /** DELIVERY 时必填 */
        private Long addressId;
    }

    /** 提交订单请求（在试算基础上增加备注/期望时间/幂等键） */
    @Data
    public static class SubmitRequest extends PreviewRequest {
        private String remark;
        private String expectTime;
        /** 客户端生成的唯一串，重复提交同一 key 返回首次创建的订单 */
        private String idempotentKey;
    }
}
