package com.anxineat.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("spec_option")
public class SpecOption {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String label;
    /** 加价（分） */
    private Long extraPrice;
    private Integer isDefault;
    private Integer sort;
    private Integer status;
}
