package com.anxineat.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * 规格组。
 *
 * <p>约定：code = CUP 的组为「库存维度组」，其选项 label 与 product_sku.cup_size
 * 一一对应，选中即确定 SKU；其余组（SUGAR/TEMP/ICE）仅影响定制描述，不影响价格与库存。
 */
@Data
@TableName("spec_group")
public class SpecGroup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String code;
    private String name;
    private Integer isRequired;
    private Integer sort;

    @TableField(exist = false)
    private List<SpecOption> options;
}
