package com.anxineat.module.system.mapper;

import com.anxineat.module.system.entity.SysConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    @Select("SELECT * FROM sys_config")
    List<SysConfig> selectAll();

    @Select("SELECT * FROM sys_config WHERE config_key = #{key}")
    SysConfig selectByKey(String key);
}
