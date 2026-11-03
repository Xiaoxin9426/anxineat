package com.anxineat.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店。
 *
 * <p>相对原需求文档补充了 min_delivery_amount（起送价）与
 * free_delivery_threshold（满额免配送费）——原表只有配送费单价，
 * 无法支撑门店列表要求展示的「起送/配送信息」。
 */
@Data
@TableName("store")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private String city;
    private String phone;
    /** 1 营业中 0 休息中（手动总开关） */
    private Integer businessStatus;
    private Integer supportPickup;
    private Integer supportDelivery;
    /** 配送费（分） */
    private Long deliveryFee;
    /** 起送价（分），0 表示无门槛 */
    private Long minDeliveryAmount;
    /** 满额免配送费门槛（分），0 表示不减免 */
    private Long freeDeliveryThreshold;
    private BigDecimal deliveryRadiusKm;
    private Integer pickupLeadMinutes;
    private Integer sort;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 非数据库字段：与请求坐标的距离（公里） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Double distanceKm;
}
