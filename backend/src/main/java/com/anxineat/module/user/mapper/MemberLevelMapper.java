package com.anxineat.module.user.mapper;

import com.anxineat.module.user.entity.MemberLevel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberLevelMapper extends BaseMapper<MemberLevel> {

    @Select("SELECT * FROM member_level ORDER BY min_amount ASC")
    List<MemberLevel> selectAllAsc();

    /**
     * 根据累计实付金额匹配应得的最高等级。
     */
    @Select("SELECT * FROM member_level WHERE min_amount <= #{amount} ORDER BY min_amount DESC LIMIT 1")
    MemberLevel matchLevel(long amount);
}
