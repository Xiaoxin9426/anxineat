package com.anxineat.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.store.entity.StoreBusinessHours;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StoreBusinessHoursMapper extends BaseMapper<StoreBusinessHours> {

    @Select("SELECT * FROM store_business_hours WHERE store_id = #{storeId} ORDER BY day_of_week, open_time")
    List<StoreBusinessHours> selectByStore(Long storeId);
}
