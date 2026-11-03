package com.anxineat.dto;

import lombok.Data;

import java.util.List;

/**
 * 门店与商品目录相关的响应对象。
 *
 * <p>字段与 {@code docs/API接口契约.md} 严格对应。所有金额单位为【分】。
 */
public final class CatalogDTO {

    private CatalogDTO() {
    }

    /** 门店（含实时计算的营业状态） */
    @Data
    public static class StoreVO {
        private Long id;
        private String name;
        private String address;
        private java.math.BigDecimal lng;
        private java.math.BigDecimal lat;
        private String city;
        private String phone;
        private Integer businessStatus;
        private Integer supportPickup;
        private Integer supportDelivery;
        private Long deliveryFee;
        private Long minDeliveryAmount;
        private Long freeDeliveryThreshold;
        private Integer pickupLeadMinutes;
        private Double distanceKm;
        /** 结合手动开关与营业时段实时计算 */
        private Boolean openNow;
        private String todayHours;
        /** openNow = false 时的原因 */
        private String closedReason;
        private List<BusinessHoursVO> businessHours;
    }

    @Data
    public static class BusinessHoursVO {
        private Integer dayOfWeek;
        private String openTime;
        private String closeTime;
        private Integer crossDay;
    }

    /** 门店菜单 */
    @Data
    public static class MenuVO {
        private Long storeId;
        private List<CategoryVO> categories;
        private List<ProductVO> products;
    }

    @Data
    public static class CategoryVO {
        private Long id;
        private String name;
        private String icon;
        private Integer sort;
    }

    /** 商品（菜单列表项） */
    @Data
    public static class ProductVO {
        private Long id;
        private Long categoryId;
        private String name;
        private String description;
        private String image;
        private Long basePrice;
        /** 当前门店的最终起售价 */
        private Long storePrice;
        private Integer calories;
        private Integer sales;
        /** 是否售罄（综合判定结果） */
        private Boolean soldOut;
        private Boolean available;
        private String soldOutReason;
        private Integer sort;
        private List<SkuVO> skus;
    }

    @Data
    public static class SkuVO {
        private Long id;
        private String cupSize;
        /** 已叠加门店加价的最终售价 */
        private Long price;
        /** -1 表示不限量 */
        private Integer stock;
        private Boolean inStock;
    }

    /** 商品详情（在 ProductVO 基础上补充规格与小料） */
    @Data
    public static class ProductDetailVO {
        private Long id;
        private Long categoryId;
        private String name;
        private String description;
        private String image;
        private Long storePrice;
        private Integer calories;
        private Integer sales;
        private Boolean available;
        private String soldOutReason;
        private List<SpecGroupVO> specGroups;
        private List<ToppingVO> toppings;
        private List<SkuVO> skus;
    }

    @Data
    public static class SpecGroupVO {
        private Long id;
        /** SUGAR / TEMP / ICE / CUP。CUP 为库存维度组 */
        private String code;
        private String name;
        private Integer isRequired;
        private Integer sort;
        private List<SpecOptionVO> options;
    }

    @Data
    public static class SpecOptionVO {
        private Long id;
        private String label;
        private Long extraPrice;
        private Integer isDefault;
        private Integer sort;
    }

    @Data
    public static class ToppingVO {
        private Long id;
        private String name;
        private Long price;
        private Integer maxCount;
        private Integer stock;
    }
}
