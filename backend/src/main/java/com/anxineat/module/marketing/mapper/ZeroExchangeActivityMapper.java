package com.anxineat.module.marketing.mapper;

import com.anxineat.module.marketing.entity.ZeroExchangeActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ZeroExchangeActivityMapper extends BaseMapper<ZeroExchangeActivity> {

    @Select("SELECT * FROM zero_exchange_activity WHERE id = #{id} LIMIT 1")
    ZeroExchangeActivity selectById(@Param("id") Long id);

    @Select("SELECT * FROM zero_exchange_activity WHERE activity_id = #{activityId}")
    List<ZeroExchangeActivity> selectByActivity(@Param("activityId") Long activityId);

    /** 总量自增（CAS，total_limit=-1 不限），返回 0 表示已达上限 */
    @Update("""
            UPDATE zero_exchange_activity
               SET exchanged_count = exchanged_count + 1, updated_at = NOW()
             WHERE id = #{id}
               AND (total_limit = -1 OR exchanged_count < total_limit)
            """)
    int incrementExchanged(@Param("id") Long id);
}
