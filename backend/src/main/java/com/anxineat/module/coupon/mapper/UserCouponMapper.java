package com.anxineat.module.coupon.mapper;

import com.anxineat.module.coupon.entity.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 占用券（下单时调用，乐观锁 CAS）。
     *
     * <p>只有 status 仍为 UNUSED 的记录才能被置为 LOCKED。并发下单时
     * 仅一个请求能拿到 rows = 1，其余请求应提示「优惠券不可用」，
     * 从而满足验收标准「并发下单时一张券不会被重复核销」。
     */
    @Update("""
            UPDATE user_coupon
               SET status = 'LOCKED', order_id = #{orderId}, version = version + 1, updated_at = NOW()
             WHERE id = #{id} AND status = 'UNUSED'
            """)
    int tryLock(@Param("id") Long id, @Param("orderId") Long orderId);

    /** 支付成功后真正核销：仅占用态且归属本订单的券可被核销 */
    @Update("""
            UPDATE user_coupon
               SET status = 'USED', used_at = NOW(), version = version + 1, updated_at = NOW()
             WHERE id = #{id} AND status = 'LOCKED' AND order_id = #{orderId}
            """)
    int tryUse(@Param("id") Long id, @Param("orderId") Long orderId);

    /** 释放券：订单取消 / 支付失败 / 超时未支付 */
    @Update("""
            UPDATE user_coupon
               SET status = 'UNUSED', order_id = NULL, version = version + 1, updated_at = NOW()
             WHERE id = #{id} AND status = 'LOCKED'
            """)
    int release(@Param("id") Long id);

    /** 批量将已过期但未使用的券置为 EXPIRED */
    @Update("UPDATE user_coupon SET status = 'EXPIRED', updated_at = NOW() "
            + "WHERE status IN ('UNUSED','LOCKED') AND end_time < #{now}")
    int markExpired(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM user_coupon WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int countUserCoupon(@Param("userId") Long userId, @Param("couponId") Long couponId);

    @Select("""
            SELECT uc.*, ct.name, ct.type, ct.amount, ct.min_amount, ct.discount,
                   ct.max_discount, ct.scope_type, ct.channels
              FROM user_coupon uc
              JOIN coupon_template ct ON ct.id = uc.coupon_id
             WHERE uc.user_id = #{userId}
               AND (#{status} IS NULL OR uc.status = #{status})
             ORDER BY uc.end_time ASC, uc.id ASC
            """)
    List<UserCoupon> selectUserCoupons(@Param("userId") Long userId, @Param("status") String status);
}
