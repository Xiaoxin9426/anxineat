package com.anxineat.module.content.mapper;

import com.anxineat.module.content.entity.Banner;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BannerMapper extends BaseMapper<Banner> {

    @Select("SELECT * FROM banner WHERE status = 1 ORDER BY sort ASC, id ASC")
    List<Banner> selectActive();
}
