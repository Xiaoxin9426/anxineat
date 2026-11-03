package com.anxineat.module.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    @Select("SELECT * FROM cart_item WHERE user_id = #{userId} AND store_id = #{storeId} ORDER BY created_at ASC, id ASC")
    List<CartItem> selectByUserAndStore(@Param("userId") Long userId, @Param("storeId") Long storeId);

    @Select("SELECT * FROM cart_item WHERE user_id = #{userId} AND store_id = #{storeId} AND item_key = #{itemKey}")
    CartItem selectByKey(@Param("userId") Long userId,
                         @Param("storeId") Long storeId,
                         @Param("itemKey") String itemKey);

    /**
     * 加购：已有同规格条目则累加数量，否则插入。
     * 依赖 uk_cart_line(user_id, store_id, item_key) 唯一索引，
     * 并发加购不会拆成两条记录。
     */
    @Update("""
            INSERT INTO cart_item
              (user_id, store_id, product_id, sku_id, item_key, quantity,
               spec_snapshot, topping_snapshot, unit_price)
            VALUES
              (#{userId}, #{storeId}, #{productId}, #{skuId}, #{itemKey}, #{quantity},
               #{specSnapshot}, #{toppingSnapshot}, #{unitPrice})
            ON DUPLICATE KEY UPDATE
              quantity = quantity + #{quantity},
              unit_price = #{unitPrice},
              updated_at = NOW()
            """)
    int upsert(CartItem item);

    @Update("DELETE FROM cart_item WHERE user_id = #{userId} AND store_id = #{storeId}")
    int clearByStore(@Param("userId") Long userId, @Param("storeId") Long storeId);

    @Update("DELETE FROM cart_item WHERE user_id = #{userId}")
    int clearAll(Long userId);

    /**
     * 下单成功后清理购物车中已提交的条目。
     * 「再来一单」场景下前端传空表示清空整店购物车。
     */
    @Update("DELETE FROM cart_item WHERE user_id = #{userId} AND store_id = #{storeId} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>")
    int deleteByIds(@Param("userId") Long userId,
                    @Param("storeId") Long storeId,
                    @Param("ids") List<Long> ids);
}
