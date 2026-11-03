package com.anxineat.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 门店营业时段。
 *
 * <p>修正原文档 store.business_hours 单字段设计：单字段无法表达
 * 多时段、按星期区分、跨零点（如 22:00-02:00）三种常见情形。
 */
@Data
@TableName("store_business_hours")
public class StoreBusinessHours {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    /** 0=周日 1=周一 ... 6=周六 */
    private Integer dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    /** 1 表示 closeTime 在次日 */
    private Integer crossDay;
}
