package com.anxineat.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销活动主表（秒杀 / 口令 / 0元兑换 三种类型共用生命周期、权限与发现）。
 */
@Data
@TableName("marketing_activity")
public class MarketingActivity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** FLASH_SALE / COMMAND / ZERO_EXCHANGE */
    private String type;
    private String title;
    /** -1 表示全门店 */
    private Long storeId;
    /** DRAFT / PUBLISHED / ONGOING / ENDED / CLOSED */
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String bannerImage;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
