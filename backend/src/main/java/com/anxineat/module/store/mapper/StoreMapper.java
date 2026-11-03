package com.anxineat.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.store.entity.Store;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StoreMapper extends BaseMapper<Store> {

    @Select("""
            SELECT s.* FROM store s
            WHERE s.status = 1
              AND (#{city} IS NULL OR s.city = #{city})
            ORDER BY s.sort ASC, s.id ASC
            """)
    List<Store> selectAllAvailable(String city);

    @Select("""
            SELECT s.* FROM store s
            WHERE s.status = 1 AND s.name LIKE CONCAT('%', #{keyword}, '%')
            ORDER BY s.sort ASC, s.id ASC
            """)
    List<Store> searchByKeyword(String keyword);
}
