package com.anxineat.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("topping")
public class Topping {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    /** 单价（分） */
    private Long price;
    /** 单个购物车条目最多可加份数 */
    private Integer maxCount;
    /** -1 不限量 */
    private Integer stock;
    private Integer sort;
    private Integer status;
}
