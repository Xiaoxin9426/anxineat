package com.anxineat.module.marketing.mapper;

import com.anxineat.module.marketing.entity.ActivityRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityRecordMapper extends BaseMapper<ActivityRecord> {

    @Select("SELECT * FROM activity_record WHERE biz_no = #{bizNo} LIMIT 1")
    ActivityRecord selectByBizNo(@Param("bizNo") String bizNo);

    /** 统计某用户在某活动下某类型的累计数量（用于秒杀/口令/0元兑换限购） */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
              FROM activity_record
             WHERE activity_id = #{activityId} AND user_id = #{userId} AND type = #{type}
            """)
    int sumQuantity(@Param("activityId") Long activityId,
                    @Param("userId") Long userId, @Param("type") String type);
}
