package com.anxineat.module.coupon.mapper;

import com.anxineat.module.coupon.entity.CouponScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CouponScopeMapper extends BaseMapper<CouponScope> {

    @Select("SELECT * FROM coupon_scope WHERE coupon_id = #{couponId} AND scope_type = #{scopeType}")
    List<CouponScope> selectByCouponAndType(@Param("couponId") Long couponId,
                                            @Param("scopeType") String scopeType);
}
