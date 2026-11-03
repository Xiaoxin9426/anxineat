package com.anxineat.module.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付流水。
 *
 * <p>修正点：原文档的 callback_body 字段会把完整回调报文落库，与
 * 「密钥等敏感信息不落库、不进日志」的安全要求存在冲突且无实际查询价值。
 * 改为只保留回调中的非敏感要素（transaction_id、回调时间）。
 */
@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 本系统支付流水号，支付回调幂等的判定键 */
    private String payNo;
    private Long orderId;
    private String orderNo;
    /** MOCK / WECHAT_JSAPI */
    private String channel;
    private String transactionId;
    /** 支付金额（分） */
    private Long amount;
    /** PAYING / SUCCESS / FAILED / CLOSED */
    private String status;
    /** 预支付参数（前端拉起支付用），JSON 字符串 */
    private String prepayJson;
    private String failReason;
    private LocalDateTime paidAt;
    private LocalDateTime callbackAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
