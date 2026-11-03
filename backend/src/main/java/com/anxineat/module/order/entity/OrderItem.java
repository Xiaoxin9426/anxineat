package com.anxineat.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单明细。
 *
 * <p>所有价格与名称均为下单时快照，商品后续改价/改名/下架不影响历史订单。
 * 相对原文档补充 product_image —— 原表缺失导致商品下架后订单详情页图片丢失。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImage;
    private Long skuId;
    private String skuSnapshot;
    private Integer quantity;
    /** 成交单份价（分），= SKU 价 + 门店加价 + 规格加价 + 小料价 */
    private Long unitPrice;
    /** 规格快照 JSON */
    private String specSnapshot;
    /** 小料快照 JSON */
    private String toppingSnapshot;
    /** 规格可读文本（下单时快照） */
    private String specText;
    /** 小料可读文本（下单时快照） */
    private String toppingText;
    /** unitPrice * quantity（分） */
    private Long totalPrice;
    /** 来源活动 id（秒杀/口令/0元兑换），非活动订单为 null */
    private Long activityId;
    /** FLASH_SALE / COMMAND / ZERO_EXCHANGE，非活动订单为 null */
    private String activityType;
}
