package com.anxineat.module.order.mapper;

import com.anxineat.module.order.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM orders WHERE idempotent_key = #{key}")
    Order selectByIdempotentKey(String key);

    /**
     * 订单状态流转（条件更新，CAS）。
     *
     * <p>以受影响行数判定是否抢占成功：并发下只有一个请求能让 rows = 1，
     * 这是支付回调幂等、店员并发接单不冲突的根基。
     * 状态迁移的合法性由 {@link com.anxineat.module.order.enums.OrderStatus#assertTransition}
     * 在调用前校验，本 SQL 只负责保证原子性。
     */
    @Update("UPDATE orders SET status = #{to}, updated_at = NOW(), version = version + 1 "
            + "WHERE id = #{id} AND status = #{from}")
    int tryTransit(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    @Select("""
            <script>
            SELECT * FROM orders
             WHERE user_id = #{userId}
               <if test="status != null and status != ''">
                 AND status IN
                 <foreach collection='status' item='s' open='(' separator=',' close=')'>#{s}</foreach>
               </if>
             ORDER BY created_at DESC
            </script>
            """)
    IPage<Order> selectUserOrders(Page<?> page,
                                  @Param("userId") Long userId,
                                  @Param("status") List<String> status);

    @Select("""
            <script>
            SELECT * FROM orders
             <where>
               <if test="storeId != null"> AND store_id = #{storeId} </if>
               <if test="status != null and status != ''">
                 AND status IN
                 <foreach collection='status' item='s' open='(' separator=',' close=')'>#{s}</foreach>
               </if>
               <if test="keyword != null and keyword != ''">
                 AND (order_no LIKE CONCAT('%', #{keyword}, '%') OR pickup_code = #{keyword})
               </if>
             </where>
             ORDER BY created_at DESC
            </script>
            """)
    IPage<Order> selectStoreOrders(Page<?> page,
                                   @Param("storeId") Long storeId,
                                   @Param("status") List<String> status,
                                   @Param("keyword") String keyword);

    /** 查询超时未支付的订单，供定时任务批量取消 */
    @Select("SELECT * FROM orders WHERE status = 'PENDING_PAYMENT' AND expire_time < #{now} ORDER BY id ASC LIMIT #{limit}")
    List<Order> selectExpiredPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 后台概览：全量订单统计（不排除已取消） */
    @Select("""
            <script>
            SELECT COUNT(*) AS order_count,
                   COALESCE(SUM(pay_amount), 0) AS sales_amount,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN pay_amount ELSE 0 END), 0) AS completed_amount
              FROM orders
             <where>
               <if test="storeId != null"> AND store_id = #{storeId} </if>
               AND created_at >= #{from}
             </where>
            </script>
            """)
    TodayStat selectTodayStat(@Param("storeId") Long storeId, @Param("from") LocalDateTime from);

    /** 各状态订单数（用于后台概览状态分布） */
    @Select("""
            <script>
            SELECT status AS status, COUNT(*) AS c
              FROM orders
             <where>
               <if test="storeId != null"> AND store_id = #{storeId} </if>
               AND created_at >= #{from}
             </where>
             GROUP BY status
            </script>
            """)
    List<StatusCount> selectStatusCounts(@Param("storeId") Long storeId, @Param("from") LocalDateTime from);

    /** 近 7 日销售趋势（按自然日聚合；缺失日期由 AdminService 补全为 0） */
    @Select("""
            <script>
            SELECT DATE(created_at) AS date, COUNT(*) AS orderCount, COALESCE(SUM(pay_amount), 0) AS salesAmount
              FROM orders
             <where>
               <if test="storeId != null"> AND store_id = #{storeId} </if>
               AND created_at >= #{from}
             </where>
             GROUP BY DATE(created_at)
             ORDER BY date
            </script>
            """)
    List<TrendDay> select7DayTrend(@Param("storeId") Long storeId, @Param("from") LocalDateTime from);

    /** 热销商品 TOP N（按已完成订单的明细销量聚合） */
    @Select("""
            <script>
            SELECT oi.product_id AS productId, oi.product_name AS productName,
                   SUM(oi.quantity) AS qty, COALESCE(SUM(oi.total_price), 0) AS amount
              FROM orders o
              JOIN order_item oi ON oi.order_id = o.id
             <where>
               <if test="storeId != null"> AND o.store_id = #{storeId} </if>
               AND o.status = 'COMPLETED'
             </where>
             GROUP BY oi.product_id, oi.product_name
             ORDER BY qty DESC
             LIMIT #{limit}
            </script>
            """)
    List<TopProduct> selectTopProducts(@Param("storeId") Long storeId, @Param("limit") int limit);

    @Select("""
            SELECT * FROM orders
             WHERE store_id = #{storeId} AND pickup_code = #{pickupCode} AND pickup_date = #{date}
             ORDER BY id DESC LIMIT 1
            """)
    Order selectByStoreAndPickup(@Param("storeId") Long storeId,
                                  @Param("pickupCode") String pickupCode,
                                  @Param("date") java.time.LocalDate date);

    /** 取当前门店当日已发出的最大取餐码序号，用于生成下一取餐码 */
    @Select("""
            SELECT COALESCE(MAX(CAST(pickup_code AS UNSIGNED)), 0)
              FROM orders WHERE store_id = #{storeId} AND pickup_date = #{date}
            """)
    long selectMaxPickupCode(@Param("storeId") Long storeId, @Param("date") java.time.LocalDate date);

    /** 状态时间戳写入（状态本身由 tryTransit 设置，这里只落时间戳） */
    @Update("UPDATE orders SET paid_at = #{now}, updated_at = NOW() WHERE id = #{id}")
    int setPaid(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE orders SET accepted_at = #{now}, updated_at = NOW() WHERE id = #{id}")
    int setAccepted(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE orders SET ready_at = #{now}, updated_at = NOW() WHERE id = #{id}")
    int setReady(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE orders SET delivering_at = #{now}, updated_at = NOW() WHERE id = #{id}")
    int setDelivering(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE orders SET completed_at = #{now}, updated_at = NOW() WHERE id = #{id}")
    int setCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE orders SET cancel_at = #{now}, cancel_reason = #{reason}, updated_at = NOW() WHERE id = #{id}")
    int setCancelled(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Update("UPDATE orders SET refunded_at = #{now}, updated_at = NOW() WHERE id = #{id}")
    int setRefunded(@Param("id") Long id, @Param("now") LocalDateTime now);

    class StatusCount {
        public String status;
        public Long c;
    }

    /** 需求文档「今日订单数」以已支付及之后的订单为准，取消单不计入 */
    @Select("""
            <script>
            SELECT COUNT(*) AS paid_count
              FROM orders
             <where>
               <if test="storeId != null"> AND store_id = #{storeId} </if>
               AND created_at >= #{from}
               AND status IN ('PAID','MAKING','READY','DELIVERING','COMPLETED')
             </where>
            </script>
            """)
    Long selectPaidOrderCount(@Param("storeId") Long storeId, @Param("from") LocalDateTime from);

    class TodayStat {
        public Long orderCount;
        public Long salesAmount;
        public Long completedAmount;
    }

    class TrendDay {
        public LocalDate date;
        public Long orderCount;
        public Long salesAmount;
    }

    class TopProduct {
        public Long productId;
        public String productName;
        public Long qty;
        public Long amount;
    }
}
