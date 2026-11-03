package com.anxineat.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会员、积分、优惠券、地址、后台相关的响应对象。金额单位为【分】。
 */
public final class MemberDTO {

    private MemberDTO() {
    }

    @Data
    public static class UserVO {
        private Long id;
        private String nickname;
        private String avatar;
        private String phone;
        private Long memberLevelId;
        private String levelName;
        /** 折扣万分比，10000 = 不打折 */
        private Integer levelDiscount;
        private Integer pointRate;
        private Long pointBalance;
        private Long totalAmount;
        private String nextLevelName;
        private Long nextLevelMinAmount;
        /** 距离下一等级还差多少（分） */
        private Long toNextLevelAmount;
    }

    @Data
    public static class LoginVO {
        private String token;
        private UserVO user;
    }

    @Data
    public static class PointRecordVO {
        private Long id;
        private String type;
        private String typeLabel;
        private Long amount;
        private Long balanceAfter;
        private String remark;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SigninVO {
        private Long awarded;
        private Long balance;
        private LocalDate date;
    }

    /** 领券中心的一项 */
    @Data
    public static class CouponCenterVO {
        private Long templateId;
        private String name;
        private String type;
        private Long amount;
        private Long minAmount;
        private Integer discount;
        private Long maxDiscount;
        private String scopeType;
        private String channels;
        private Integer validDays;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long pointCost;
        private Integer limitPerUser;
        private Integer receivedCount;
        private Integer total;
        private Integer issued;
        private Boolean canReceive;
        private String receiveDisabledReason;
    }

    /** 我的优惠券 */
    @Data
    public static class CouponVO {
        private Long id;
        private Long couponId;
        private String name;
        private String type;
        private Long amount;
        private Long minAmount;
        private Integer discount;
        private Long maxDiscount;
        private String scopeType;
        private String channels;
        private String status;
        private String statusLabel;
        private Long pointCost;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /** 积分兑换列表项 */
    @Data
    public static class ExchangeVO {
        private Long templateId;
        private String name;
        private String type;
        private Long amount;
        private Long minAmount;
        private Integer discount;
        private Long maxDiscount;
        private Long pointCost;
        private Boolean canExchange;
        private String reason;
    }

    @Data
    public static class ExchangeResultVO {
        private Long userCouponId;
        private Long pointCost;
        private Long balance;
    }

    @Data
    public static class AddressVO {
        private Long id;
        private String name;
        private String phone;
        private String province;
        private String city;
        private String district;
        private String detail;
        private String tag;
        private Integer isDefault;
    }

    // ---------- 后台 ----------

    @Data
    public static class AdminLoginVO {
        private String token;
        private AdminVO admin;
    }

    @Data
    public static class AdminVO {
        private Long id;
        private String username;
        private String realName;
        private String role;
        private Long storeId;
        private String storeName;
    }

    @Data
    public static class OverviewVO {
        private TodayVO today;
        private Map<String, Long> statusCounts;
        /** 近 7 日销售趋势（含今日，按 MM-dd 升序，缺失日补 0） */
        private List<TrendDayVO> trend7d;
        /** 热销商品 TOP（按已完成订单明细销量聚合） */
        private List<TopProductVO> topProducts;
    }

    @Data
    public static class TodayVO {
        private Long orderCount;
        private Long salesAmount;
        private Long completedAmount;
        private Long avgAmount;
    }

    @Data
    public static class TrendDayVO {
        private String date;
        private Long orderCount;
        private Long salesAmount;
    }

    @Data
    public static class TopProductVO {
        private Long productId;
        private String productName;
        private Long qty;
        private Long amount;
    }

    @Data
    public static class AdminProductVO {
        private Long id;
        private String name;
        private Long categoryId;
        private String categoryName;
        private Long basePrice;
        private Integer sales;
        private Integer sort;
        private Integer status;
        /** 商品级手动售罄（全局生效，优先于门店级） */
        private Boolean manualSoldOut;
        /** 本店是否上架 */
        private Boolean onSale;
        /** 本店是否售罄 */
        private Boolean storeSoldOut;
        private Long storePriceDiff;
        private List<AdminSkuVO> skus;
    }

    @Data
    public static class AdminSkuVO {
        private Long id;
        private String cupSize;
        private Long price;
        private Integer stock;
        /** -1 表示不限量 */
        private Boolean unlimited;
        private Integer status;
    }

    /** 商品详情（后台编辑用）：基础信息 + SKU + 规格组/项 + 小料。金额单位：分。 */
    @Data
    public static class AdminProductDetailVO {
        private Long id;
        private String name;
        private Long categoryId;
        private String description;
        private String image;
        private Long basePrice;
        private Integer calories;
        private Integer sort;
        private Integer status;
        private List<AdminSkuDetailVO> skus;
        private List<AdminSpecGroupDetailVO> specGroups;
        private List<AdminToppingDetailVO> toppings;
    }

    @Data
    public static class AdminSkuDetailVO {
        private Long id;
        private String cupSize;
        private Long price;
        private Integer stock;
        private Integer status;
    }

    @Data
    public static class AdminSpecGroupDetailVO {
        private Long id;
        private String code;
        private String name;
        private Integer isRequired;
        private Integer sort;
        private List<AdminSpecOptionDetailVO> options;
    }

    @Data
    public static class AdminSpecOptionDetailVO {
        private Long id;
        private String label;
        private Long extraPrice;
        private Integer isDefault;
        private Integer sort;
        private Integer status;
    }

    @Data
    public static class AdminToppingDetailVO {
        private Long id;
        private String name;
        private Long price;
        private Integer maxCount;
        private Integer stock;
        private Integer sort;
        private Integer status;
    }
}
