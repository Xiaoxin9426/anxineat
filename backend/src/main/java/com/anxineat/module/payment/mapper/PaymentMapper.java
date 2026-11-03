package com.anxineat.module.payment.mapper;

import com.anxineat.module.payment.entity.Payment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("SELECT * FROM payment WHERE pay_no = #{payNo}")
    Payment selectByPayNo(String payNo);

    @Select("SELECT * FROM payment WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    Payment selectLatestByOrder(Long orderId);

    /**
     * 支付成功：条件更新做幂等闸门。
     *
     * <p>只有 status 仍是 PAYING 的记录才可能被更新为 SUCCESS，
     * 受影响行数 = 1 的请求才是「第一个到达的回调」，
     * 后续重复回调在此处被拦下，从而实现「重复回调只生效一次」。
     */
    @Update("""
            UPDATE payment
               SET status = 'SUCCESS', transaction_id = #{transactionId},
                   paid_at = NOW(), callback_at = NOW(), updated_at = NOW()
             WHERE pay_no = #{payNo} AND status = 'PAYING'
            """)
    int tryMarkSuccess(@Param("payNo") String payNo, @Param("transactionId") String transactionId);

    @Update("UPDATE payment SET status = 'FAILED', fail_reason = #{reason}, callback_at = NOW(), updated_at = NOW() "
            + "WHERE pay_no = #{payNo} AND status = 'PAYING'")
    int tryMarkFailed(@Param("payNo") String payNo, @Param("reason") String reason);

    @Update("UPDATE payment SET status = 'CLOSED', updated_at = NOW() WHERE pay_no = #{payNo} AND status = 'PAYING'")
    int tryMarkClosed(String payNo);
}
