package com.anxineat.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品 SKU（杯型维度）。
 *
 * <p>库存与价格挂在 SKU 上。甜度/冰度/温度属于定制规格，不影响原料消耗，
 * 因此不作为 SKU 维度，避免 SKU 数量爆炸。
 */
@Data
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String cupSize;
    /** 售价（分） */
    private Long price;
    /** 可售库存，-1 表示不限量 */
    private Integer stock;
    private Integer version;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
