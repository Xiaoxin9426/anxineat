package com.anxineat.module.order.mapper;

import com.anxineat.module.order.entity.OrderStatusLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {

    @Select("SELECT * FROM order_status_log WHERE order_id = #{orderId} ORDER BY created_at ASC, id ASC")
    List<OrderStatusLog> selectByOrder(@Param("orderId") Long orderId);
}
