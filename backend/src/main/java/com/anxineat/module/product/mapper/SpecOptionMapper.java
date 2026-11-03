package com.anxineat.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.product.entity.SpecOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpecOptionMapper extends BaseMapper<SpecOption> {

    @Select("SELECT * FROM spec_option WHERE group_id = #{groupId} AND status = 1 ORDER BY sort ASC, id ASC")
    List<SpecOption> selectByGroup(Long groupId);

    @Select("SELECT * FROM spec_option WHERE group_id = #{groupId} AND id = #{optionId} AND status = 1")
    SpecOption selectOneInGroup(@org.apache.ibatis.annotations.Param("groupId") Long groupId,
                                @org.apache.ibatis.annotations.Param("optionId") Long optionId);
}
