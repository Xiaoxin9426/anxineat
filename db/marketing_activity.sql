-- 营销活动模块 DDL（秒杀 / 口令 / 0元兑换）
-- 对应 docs/营销活动模块实现方案.md §3，产品决策见 §8（2026-09-03 已确认）
--
-- 关键决策落地：
--   1. 秒杀用【独立库存池】flash_sale.sold_stock / total_stock，不触碰 product_sku.stock
--   3. 口令大小写不敏感（command_text 一律存大写），每人限领 1 次
--   4. 0元兑换支持积分门槛 point_cost（0 = 纯免费仅限量）
--   5. 免单以【发券】形式履约，不建 0 元订单
--   6. 超管统一配置，store_id = -1 表示全门店

CREATE TABLE IF NOT EXISTS `marketing_activity` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `type`         VARCHAR(16)  NOT NULL COMMENT 'FLASH_SALE / COMMAND / ZERO_EXCHANGE',
  `title`        VARCHAR(128) NOT NULL,
  `store_id`     BIGINT       NOT NULL DEFAULT -1 COMMENT '-1 表示全门店',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ONGOING/ENDED/CLOSED',
  `start_time`   DATETIME     DEFAULT NULL,
  `end_time`     DATETIME     DEFAULT NULL,
  `banner_image` VARCHAR(255) DEFAULT NULL,
  `sort`         INT          NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`type`, `status`),
  KEY `idx_store_status` (`store_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动主表';

-- 秒杀明细：独立库存池，CAS + version 扣减
CREATE TABLE IF NOT EXISTS `flash_sale` (
  `id`             BIGINT  NOT NULL AUTO_INCREMENT,
  `activity_id`    BIGINT  NOT NULL,
  `store_id`       BIGINT  NOT NULL,
  `sku_id`         BIGINT  NOT NULL,
  `activity_price` BIGINT  NOT NULL COMMENT '活动价（分）',
  `total_stock`    INT     NOT NULL DEFAULT -1 COMMENT '秒杀总量，-1 不限',
  `sold_stock`     INT     NOT NULL DEFAULT 0,
  `per_limit`      INT     NOT NULL DEFAULT 1 COMMENT '每人限购件数，0=不限',
  `version`        INT     NOT NULL DEFAULT 0 COMMENT '乐观锁，CAS 扣减',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`, `sku_id`),
  KEY `idx_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀明细（独立库存池）';

-- 口令明细：command_text 存大写，匹配前 trim + 转大写
CREATE TABLE IF NOT EXISTS `command_activity` (
  `id`                 BIGINT      NOT NULL AUTO_INCREMENT,
  `activity_id`        BIGINT      NOT NULL,
  `store_id`           BIGINT      NOT NULL,
  `command_text`       VARCHAR(64) NOT NULL COMMENT '口令串（大写存储），如 AXN666',
  `reward_type`        VARCHAR(16) NOT NULL COMMENT 'COUPON 发券 / POINTS 发积分 / PRODUCT_FREE 免单券',
  `coupon_template_id` BIGINT      DEFAULT NULL COMMENT 'reward_type=COUPON 时必填',
  `point_amount`       BIGINT      DEFAULT NULL COMMENT 'reward_type=POINTS 时发放积分',
  `product_sku_id`     BIGINT      DEFAULT NULL COMMENT 'reward_type=PRODUCT_FREE 时免单商品',
  `total_limit`        INT         NOT NULL DEFAULT -1 COMMENT '总领取次数，-1 不限',
  `claimed_count`      INT         NOT NULL DEFAULT 0,
  `per_user_limit`     INT         NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_command` (`activity_id`, `command_text`),
  KEY `idx_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='口令明细';

-- 0元兑换明细：point_cost=0 表示纯免费仅限量
CREATE TABLE IF NOT EXISTS `zero_exchange_activity` (
  `id`              BIGINT NOT NULL AUTO_INCREMENT,
  `activity_id`     BIGINT NOT NULL,
  `store_id`        BIGINT NOT NULL,
  `product_sku_id`  BIGINT NOT NULL,
  `point_cost`      BIGINT NOT NULL DEFAULT 0 COMMENT '兑换所需积分，0=纯免费',
  `total_limit`     INT    NOT NULL DEFAULT -1 COMMENT '总兑换次数，-1 不限',
  `exchanged_count` INT    NOT NULL DEFAULT 0,
  `per_user_limit`  INT    NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`, `product_sku_id`),
  KEY `idx_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='0元兑换明细';

-- 参与记录：每人限领 + 幂等（uk_biz_no）
-- biz_no 规则：COMMAND  -> 'CMD:'  + activity_id + ':' + command_id + ':' + user_id
--              ZERO     -> 'ZERO:' + activity_id + ':' + sku_id     + ':' + user_id
--              FLASH    -> 'FLASH:'+ order_no + ':' + sku_id（下单即记录，用于限购统计）
CREATE TABLE IF NOT EXISTS `activity_record` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `activity_id`   BIGINT      NOT NULL,
  `user_id`       BIGINT      NOT NULL,
  `type`          VARCHAR(16) NOT NULL COMMENT 'FLASH_SALE / COMMAND / ZERO_EXCHANGE',
  `quantity`      INT         NOT NULL DEFAULT 1 COMMENT '秒杀记录购买件数，其余为 1',
  `reward_detail` VARCHAR(255) DEFAULT NULL COMMENT '如 user_coupon_id / 积分数 / 免单 sku',
  `biz_no`        VARCHAR(64)  DEFAULT NULL COMMENT '幂等号，见文件头注释',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_no` (`biz_no`),
  KEY `idx_user_activity` (`user_id`, `activity_id`),
  KEY `idx_activity_type` (`activity_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动参与记录（限领 + 幂等）';

-- 下单链路扩展：标记优惠来源，便于对账与看板。
-- 用 information_schema 判定，保证可重复执行（Docker 初始化 / 重复迁移都不会因列已存在而中断）。
SET @exist_id := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'activity_id');
SET @sql := IF(@exist_id = 0,
    'ALTER TABLE `order_item` ADD COLUMN `activity_id` BIGINT DEFAULT NULL COMMENT ''来源活动''',
    'SELECT 1');
PREPARE _stmt FROM @sql; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

SET @exist_type := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'activity_type');
SET @sql := IF(@exist_type = 0,
    'ALTER TABLE `order_item` ADD COLUMN `activity_type` VARCHAR(16) DEFAULT NULL COMMENT ''FLASH_SALE/COMMAND/ZERO_EXCHANGE''',
    'SELECT 1');
PREPARE _stmt FROM @sql; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;
