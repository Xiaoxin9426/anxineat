package com.anxineat.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品。
 *
 * <p>售罄判定归一：原文档同时存在 product.sold_out、product_sku.stock、
 * product_sku.status 三个状态源，容易出现「SKU 有库存但商品显示售罄」的不一致。
 * 本项目约定：
 * <pre>
 *   手动售罄   = product.sold_out = 1（运营主动下架）
 *   门店停售   = store_product.sold_out = 1 或 status = 0
 *   库存售罄   = 所有启用 SKU 的 stock = 0（stock = -1 表示不限量）
 *   实际可售   = 以上三者均为否
 * </pre>
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private String image;
    /** 基准价（分），等于最低 SKU 价 */
    private Long basePrice;
    private Integer calories;
    private Integer sales;
    /** 手动售罄开关 */
    private Integer soldOut;
    private Integer sort;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---------- 非数据库字段：菜单聚合时填充 ----------

    /** 在当前门店的最终起售价（含门店加价，分） */
    @TableField(exist = false)
    private Long storePrice;

    /** 在当前门店是否可售 */
    @TableField(exist = false)
    private Boolean available;

    /** 售罄原因，供前端差异化提示 */
    @TableField(exist = false)
    private String soldOutReason;

    @TableField(exist = false)
    private List<ProductSku> skus;
}
