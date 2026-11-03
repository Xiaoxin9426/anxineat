package com.anxineat.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Select("SELECT * FROM product_sku WHERE product_id = #{productId} AND status = 1 ORDER BY price ASC")
    List<ProductSku> selectByProduct(Long productId);

    /**
     * 库存预占（条件更新，CAS）。
     *
     * <p>stock = -1 表示不限量，直接放行不参与扣减。
     * 以受影响行数判定是否扣减成功：并发下只有一个请求能拿到 rows = 1，
     * 从而保证不超卖。这是比「先查后改」唯一可靠的方式。
     *
     * @return 受影响行数，1 表示扣减成功
     */
    @Update("""
            UPDATE product_sku
               SET stock = IF(stock = -1, -1, stock - #{qty}), version = version + 1, updated_at = NOW()
             WHERE id = #{id}
               AND status = 1
               AND (stock = -1 OR stock >= #{qty})
            """)
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 库存回补（取消订单、超时取消、缺货回滚时使用） */
    @Update("""
            UPDATE product_sku
               SET stock = stock + #{qty}, version = version + 1, updated_at = NOW()
             WHERE id = #{id}
               AND status = 1
               AND stock <> -1
            """)
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);

    /** 后台直接设置库存 */
    @Update("UPDATE product_sku SET stock = #{stock}, updated_at = NOW() WHERE id = #{id}")
    int setStock(@Param("id") Long id, @Param("stock") int stock);
}
