package com.anxineat.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 0元兑换明细：point_cost=0 表示纯免费仅限量；>0 表示需消耗积分。
 */
@Data
@TableName("zero_exchange_activity")
public class ZeroExchangeActivity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long storeId;
    private Long productSkuId;
    /** 兑换所需积分，0=纯免费 */
    private Long pointCost;
    /** 总兑换次数，-1 不限 */
    private Integer totalLimit;
    private Integer exchangedCount;
    /** 每人限兑次数，默认 1 */
    private Integer perUserLimit;
}
