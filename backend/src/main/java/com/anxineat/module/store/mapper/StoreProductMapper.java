package com.anxineat.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.store.entity.StoreProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StoreProductMapper extends BaseMapper<StoreProduct> {

    @Select("SELECT * FROM store_product WHERE store_id = #{storeId} AND status = 1")
    List<StoreProduct> selectByStore(Long storeId);

    @Select("SELECT * FROM store_product WHERE store_id = #{storeId} AND product_id = #{productId} AND status = 1")
    StoreProduct selectOneInStore(Long storeId, Long productId);
}
