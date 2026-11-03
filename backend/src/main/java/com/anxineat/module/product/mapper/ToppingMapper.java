package com.anxineat.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.product.entity.Topping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ToppingMapper extends BaseMapper<Topping> {

    @Select("SELECT * FROM topping WHERE product_id = #{productId} AND status = 1 ORDER BY sort ASC, id ASC")
    List<Topping> selectByProduct(Long productId);
}
