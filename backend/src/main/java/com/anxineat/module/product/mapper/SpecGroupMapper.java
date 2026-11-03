package com.anxineat.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.product.entity.SpecGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpecGroupMapper extends BaseMapper<SpecGroup> {

    @Select("SELECT * FROM spec_group WHERE product_id = #{productId} ORDER BY sort ASC, id ASC")
    List<SpecGroup> selectByProduct(Long productId);
}
