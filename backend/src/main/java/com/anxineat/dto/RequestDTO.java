package com.anxineat.dto;

import lombok.Data;

import java.util.List;

/**
 * 各写接口的入参对象（与 {@code docs/API接口契约.md} 对应）。
 * 统一放在此处，便于 Controller 与 Service 共享，避免重复定义。
 */
public final class RequestDTO {

    private RequestDTO() {
    }

    /** 登录：模式 A 微信静默（code 以 mock: 前缀） / 模式 B 手机号验证码 */
    @Data
    public static class LoginRequest {
        private String code;
        private String nickname;
        private String avatar;
        private String phone;
        private String verifyCode;
    }

    @Data
    public static class BindPhoneRequest {
        private String phone;
        private String verifyCode;
    }

    @Data
    public static class AddressRequest {
        private String name;
        private String phone;
        private String province;
        private String city;
        private String district;
        private String detail;
        private String tag;
        private Integer isDefault;
    }

    @Data
    public static class AdminLoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class StoreAdminRequest {
        private Integer businessStatus;
        private Integer supportPickup;
        private Integer supportDelivery;
        private Long deliveryFee;
        private Long minDeliveryAmount;
        private Long freeDeliveryThreshold;
    }

    /** 商品新建/更新（带 id 为更新） */
    @Data
    public static class ProductAdminRequest {
        private Long id;
        private String name;
        private Long categoryId;
        private Long basePrice;
        private String description;
        private String image;
        private Integer calories;
        private Integer sales;
        private Integer status;
        private Integer sort;
    }

    @Data
    public static class CouponAdminRequest {
        private Long id;
        private String name;
        private String type;
        private Long amount;
        private Long minAmount;
        private Integer discount;
        private Long maxDiscount;
        private String scopeType;
        private String channels;
        private String validType;
        private Integer validDays;
        /** 固定有效期起（yyyy-MM-dd HH:mm:ss 或 ISO，DAYS 类型可空） */
        private String startTime;
        private String endTime;
        /** 指定分类/商品 id，逗号分隔；scopeType=ALL 时忽略 */
        private String scopeIds;
        private Long pointCost;
        private Integer total;
        private Integer limitPerUser;
        private Integer status;
        private Integer stackable;
    }

    @Data
    public static class SkuStockRequest {
        private Integer stock;
    }

    @Data
    public static class ConfigRequest {
        private String value;
    }

    @Data
    public static class CouponStatusRequest {
        private Integer status;
    }

    @Data
    public static class StoreProductFlagRequest {
        private Long storeId;
        private Integer flag;
        /** 上架开关值（控制台 on-sale 接口实际下发字段） */
        private Integer onSale;
        /** 售罄开关值（控制台 sold-out 接口实际下发字段） */
        private Integer soldOut;
    }

    @Data
    public static class CategoryAdminRequest {
        private Long id;
        private String name;
        private String icon;
        private Integer sort;
        private Integer status;
    }

    @Data
    public static class CategoryStatusRequest {
        private Integer status;
    }

    /** 商品详情完整保存（基础 + SKU + 规格组/项 + 小料）。金额单位：分。 */
    @Data
    public static class ProductDetailAdminRequest {
        private Long id;
        private String name;
        private Long categoryId;
        private String description;
        private String image;
        private Integer calories;
        private Integer sort;
        private Integer status;
        private List<SkuItem> skus;
        private List<AdminSpecGroup> specGroups;
        private List<ToppingItem> toppings;
    }

    @Data
    public static class SkuItem {
        private Long id;
        private String cupSize;
        private Long price;      // 分
        private Integer stock;   // -1 不限量
        private Integer status;
    }

    @Data
    public static class AdminSpecGroup {
        private Long id;
        private String code;
        private String name;
        private Integer isRequired;
        private Integer sort;
        private List<AdminSpecOption> options;
    }

    @Data
    public static class AdminSpecOption {
        private Long id;
        private String label;
        private Long extraPrice; // 分
        private Integer isDefault;
        private Integer sort;
        private Integer status;
    }

    @Data
    public static class ToppingItem {
        private Long id;
        private String name;
        private Long price;    // 分
        private Integer maxCount;
        private Integer stock; // -1 不限量
        private Integer sort;
        private Integer status;
    }
}
