package com.anxineat.module.admin.mapper;

import com.anxineat.module.admin.entity.AdminUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("SELECT * FROM admin_user WHERE username = #{username} AND status = 1")
    AdminUser selectByUsername(String username);
}
