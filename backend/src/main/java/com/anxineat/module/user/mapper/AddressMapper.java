package com.anxineat.module.user.mapper;

import com.anxineat.module.user.entity.Address;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AddressMapper extends BaseMapper<Address> {

    @Select("SELECT * FROM address WHERE user_id = #{userId} AND status = 1 ORDER BY is_default DESC, id DESC")
    List<Address> selectByUser(@Param("userId") Long userId);

    @Update("UPDATE address SET is_default = 0 WHERE user_id = #{userId}")
    int clearDefault(@Param("userId") Long userId);
}
