package com.anxineat.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("coupon_scope")
public class CouponScope {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    /** CATEGORY / PRODUCT */
    private String scopeType;
    private Long refId;
}
