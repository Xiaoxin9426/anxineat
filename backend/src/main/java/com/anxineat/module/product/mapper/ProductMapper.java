package com.anxineat.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.anxineat.module.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 Mapper。
 *
 * <p>说明：门店维度菜单查询刻意不写成 JOIN 大 SQL，而是拆成
 * 「store_product → product → product_sku」三次按 ID 批量查询后在 Java 层归并。
 * 原因：商品实体上有若干 {@code @TableField(exist = false)} 的计算字段
 * （门店价、可售标记、售罄原因），JOIN 结果集到实体的自动映射对这些字段不可靠；
 * 拆分后逻辑显式、可测试，且门店商品量级（百级）下性能无差异。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
