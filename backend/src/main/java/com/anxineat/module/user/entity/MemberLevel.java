package com.anxineat.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member_level")
public class MemberLevel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** 升级门槛：累计实付金额（分） */
    private Long minAmount;
    /** 折扣万分比，10000 = 不打折 */
    private Integer discount;
    /** 积分倍率百分比，100 = 1 倍 */
    private Integer pointRate;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
