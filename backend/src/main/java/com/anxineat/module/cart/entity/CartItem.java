package com.anxineat.module.cart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目。
 *
 * <p>修正点：原文档将购物车存 Redis Hash。Redis 重启即丢，且目标环境不依赖 Redis 也能运行。
 * 改为落库，按 user_id + store_id 隔离，item_key 唯一键实现「同规格合并、异规格分立」。
 *
 * <p>item_key 由 productId + skuId + 规格选项 ID（升序）+ 小料 ID 与份数 计算，
 * 保证「同一商品的不同规格组合视为不同购物车条目」这一验收标准。
 */
@Data
@TableName("cart_item")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long storeId;
    private Long productId;
    private Long skuId;
    private String itemKey;
    private Integer quantity;
    /** 规格快照 JSON */
    private String specSnapshot;
    /** 小料快照 JSON */
    private String toppingSnapshot;
    /** 单份价（SKU 价 + 门店加价 + 规格加价 + 小料价），分 */
    private Long unitPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---------- 非数据库字段：购物车展示用 ----------
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String productImage;
    @TableField(exist = false)
    private String skuName;
    @TableField(exist = false)
    private Long basePrice;
    /** 该条目当前是否仍可售（售罄/下架/库存不足时为 false） */
    @TableField(exist = false)
    private Boolean available;
    @TableField(exist = false)
    private String unavailableReason;
    @TableField(exist = false)
    private Integer maxQuantity;
}
