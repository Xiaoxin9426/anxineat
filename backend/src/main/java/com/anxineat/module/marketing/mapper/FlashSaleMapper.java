package com.anxineat.module.marketing.mapper;

import com.anxineat.module.marketing.entity.FlashSale;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FlashSaleMapper extends BaseMapper<FlashSale> {

    @Select("SELECT * FROM flash_sale WHERE activity_id = #{activityId} AND sku_id = #{skuId} LIMIT 1")
    FlashSale selectByActivityAndSku(@Param("activityId") Long activityId, @Param("skuId") Long skuId);

    @Select("SELECT * FROM flash_sale WHERE id = #{id} LIMIT 1")
    FlashSale selectById(@Param("id") Long id);

    @Select("SELECT * FROM flash_sale WHERE activity_id = #{activityId}")
    List<FlashSale> selectByActivity(@Param("activityId") Long activityId);

    /**
     * 独立库存池 CAS 扣减（秒杀售罄不影响 product_sku 正价库存）。
     *
     * @return 受影响行数，1 表示扣减成功；0 表示版本冲突或已售罄
     */
    @Update("""
            UPDATE flash_sale
               SET sold_stock = sold_stock + #{qty}, version = version + 1, updated_at = NOW()
             WHERE id = #{id} AND version = #{oldVersion}
               AND (total_stock = -1 OR sold_stock + #{qty} <= total_stock)
            """)
    int deductSoldStock(@Param("id") Long id, @Param("oldVersion") int oldVersion, @Param("qty") int qty);

    /** 取消 / 超时订单回补秒杀库存（仅 sold_stock>0 才减，避免负数） */
    @Update("""
            UPDATE flash_sale
               SET sold_stock = GREATEST(0, sold_stock - #{qty}), version = version + 1, updated_at = NOW()
             WHERE activity_id = #{activityId} AND sku_id = #{skuId}
               AND sold_stock > 0
            """)
    int restoreSoldStockByActivitySku(@Param("activityId") Long activityId,
                                      @Param("skuId") Long skuId, @Param("qty") int qty);
}
