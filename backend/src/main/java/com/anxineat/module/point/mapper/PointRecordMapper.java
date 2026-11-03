package com.anxineat.module.point.mapper;

import com.anxineat.module.point.entity.PointRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PointRecordMapper extends BaseMapper<PointRecord> {

    @Select("SELECT * FROM point_record WHERE user_id = #{userId} ORDER BY created_at DESC, id DESC")
    IPage<PointRecord> selectUserRecords(Page<?> page, @Param("userId") Long userId);

    @Select("SELECT * FROM point_record WHERE biz_no = #{bizNo}")
    PointRecord selectByBizNo(String bizNo);

    @Select("SELECT COUNT(1) FROM point_record WHERE biz_no = #{bizNo}")
    int countByBizNo(@Param("bizNo") String bizNo);

    @Select("SELECT * FROM point_record WHERE user_id = #{userId} ORDER BY created_at DESC, id DESC LIMIT #{offset}, #{size}")
    List<PointRecord> selectByUser(@Param("userId") Long userId, @Param("size") int size, @Param("offset") int offset);

    @Select("SELECT COUNT(1) FROM point_record WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);
}
