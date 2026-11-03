package com.anxineat.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 秒杀明细：独立库存池，不触碰 product_sku.stock。
 *
 * <p>扣减走 CAS：{@code WHERE id=? AND version=? AND (total_stock=-1 OR sold_stock+qty<=total_stock)}，
 * 受影响行数=0 表示售罄或版本冲突，调用方据此抛 FLASH_SOLD_OUT。
 */
@Data
@TableName("flash_sale")
public class FlashSale {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long storeId;
    private Long skuId;
    /** 活动价（分） */
    private Long activityPrice;
    /** 秒杀总量，-1 不限 */
    private Integer totalStock;
    private Integer soldStock;
    /** 每人限购件数，0=不限 */
    private Integer perLimit;
    /** 乐观锁，CAS 扣减 */
    private Integer version;
}
