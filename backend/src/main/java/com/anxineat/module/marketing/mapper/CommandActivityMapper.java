package com.anxineat.module.marketing.mapper;

import com.anxineat.module.marketing.entity.CommandActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommandActivityMapper extends BaseMapper<CommandActivity> {

    /** 口令公开可分享，仅凭口令串（大写）匹配，不限定活动 */
    @Select("SELECT * FROM command_activity WHERE command_text = #{commandText} LIMIT 1")
    CommandActivity selectByText(@Param("commandText") String commandText);

    @Select("SELECT * FROM command_activity WHERE activity_id = #{activityId}")
    java.util.List<CommandActivity> selectByActivity(@Param("activityId") Long activityId);

    /** 总量自增（CAS，total_limit=-1 不限），返回 0 表示已达上限 */
    @Update("""
            UPDATE command_activity
               SET claimed_count = claimed_count + 1, updated_at = NOW()
             WHERE id = #{id}
               AND (total_limit = -1 OR claimed_count < total_limit)
            """)
    int incrementClaimed(@Param("id") Long id);
}
