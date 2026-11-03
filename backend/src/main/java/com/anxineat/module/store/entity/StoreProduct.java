package com.anxineat.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店 - 商品可售关系。
 *
 * <p>修正原文档把 store_id 直接放在 product 表上的设计：术语表定义
 * 「商品可在若干门店售卖」，一对多关系无法用单列表达。
 */
@Data
@TableName("store_product")
public class StoreProduct {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Long productId;
    /** 门店加价（分），可为负 */
    private Long priceDiff;
    /** 该门店单独售罄 */
    private Integer soldOut;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
