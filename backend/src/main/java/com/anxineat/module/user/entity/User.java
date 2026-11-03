package com.anxineat.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户与会员主表。
 *
 * <p>修正点：原文档要求「同一手机号只能绑定一个会员账户」，但未定义微信 open_id
 * 与手机号的绑定时序。真实路径是「先微信静默登录（此时无手机号）→ 后补绑手机号」，
 * 因此 open_id 与 phone 各自唯一、均可为空，通过 bind_phone 接口补齐。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String openId;
    private String unionId;
    private String phone;
    private String nickname;
    private String avatar;
    private Long memberLevelId;
    /** 当前可用积分 */
    private Long pointBalance;
    /** 累计获得积分（用于对账） */
    private Long pointTotal;
    /** 累计实付金额（分），会员等级升级依据 */
    private Long totalAmount;
    /** 最近签到日期，用于每日签到防重 */
    private LocalDate lastSigninDate;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---------- 非数据库字段 ----------
    @TableField(exist = false)
    private String levelName;
    @TableField(exist = false)
    private Integer levelDiscount;
    @TableField(exist = false)
    private Long nextLevelMinAmount;
    @TableField(exist = false)
    private String nextLevelName;
}
