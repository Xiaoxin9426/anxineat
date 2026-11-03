-- =============================================================================
-- 安心饮点餐小程序 —— MySQL 8.0 数据库结构
-- -----------------------------------------------------------------------------
-- 设计约定（对应需求文档「关键机制设计」）：
--   1. 所有金额字段单位为【分】，类型 BIGINT，禁止使用浮点。
--   2. 价格/名称等交易要素在下单时写入订单明细快照，商品后续改价不影响历史订单。
--   3. 所有写接口依赖数据库唯一索引做幂等，而非应用层判断。
--   4. 库存扣减与券核销使用条件更新（CAS）保证并发安全，不依赖 Redis。
--
-- 相对原需求文档修正的数据模型问题，见 docs/需求规格与技术实现方案.md「附录 C 修订记录」。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `anxineat`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `anxineat`;

-- -----------------------------------------------------------------------------
-- 会员等级
-- discount 为折扣率的万分比：10000 = 不打折，9000 = 9 折，8500 = 8.5 折。
-- 用万分比而非「折扣浮点数」，避免 0.1+0.2 类精度问题，并保证与券叠加时计算可复核。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `member_level`;
CREATE TABLE `member_level` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `code`         VARCHAR(32)  NOT NULL COMMENT '等级编码 NORMAL/SILVER/GOLD',
  `name`         VARCHAR(32)  NOT NULL COMMENT '等级名称 普通/银卡/金卡',
  `min_amount`   BIGINT       NOT NULL DEFAULT 0 COMMENT '升级门槛：累计实付金额（分）',
  `discount`     INT          NOT NULL DEFAULT 10000 COMMENT '折扣万分比 10000=不打折',
  `point_rate`   INT          NOT NULL DEFAULT 100 COMMENT '积分倍率百分比 100=1倍',
  `sort`         INT          NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级';

-- -----------------------------------------------------------------------------
-- 用户 / 会员
-- 修正点：原文档要求「同一手机号只能绑定一个会员账户」，但未定义微信 open_id 与手机号
-- 的绑定时序。实际存在「先微信静默登录（无手机号）→ 后补绑手机号」的路径，因此
-- open_id 与 phone 均可为空但各自唯一，通过 bind_phone 补齐。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `open_id`         VARCHAR(64)  DEFAULT NULL COMMENT '微信小程序 openid',
  `union_id`        VARCHAR(64)  DEFAULT NULL,
  `phone`           VARCHAR(20)  DEFAULT NULL COMMENT '手机号，绑定后唯一',
  `nickname`        VARCHAR(64)  DEFAULT NULL,
  `avatar`          VARCHAR(512) DEFAULT NULL,
  `member_level_id` BIGINT       NOT NULL DEFAULT 1 COMMENT '当前会员等级',
  `point_balance`   BIGINT       NOT NULL DEFAULT 0 COMMENT '当前可用积分',
  `point_total`     BIGINT       NOT NULL DEFAULT 0 COMMENT '累计获得积分（用于对账）',
  `total_amount`    BIGINT       NOT NULL DEFAULT 0 COMMENT '累计实付金额（分），等级升级依据',
  `last_signin_date` DATE        DEFAULT NULL COMMENT '最近签到日期（自然日，防重复签到）',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与会员主表';

-- -----------------------------------------------------------------------------
-- 后台账号
-- role: SUPER_ADMIN 超管（跨门店） / CLERK 店员（仅本门店）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `admin_user`;
CREATE TABLE `admin_user` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `username`   VARCHAR(64) NOT NULL,
  `password`   VARCHAR(128) NOT NULL COMMENT 'BCrypt 哈希',
  `real_name`  VARCHAR(64) DEFAULT NULL,
  `role`       VARCHAR(32) NOT NULL DEFAULT 'CLERK',
  `store_id`   BIGINT      DEFAULT NULL COMMENT '店员绑定的门店；超管为 NULL',
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理账号';

-- -----------------------------------------------------------------------------
-- 门店
-- 修正点（相对原文档 store 表）：
--   + min_delivery_amount 起送价：原文档门店列表要求展示「起送信息」但表无此字段。
--   + free_delivery_threshold 满额免配送费：原文档只定义了配送费单价，未定义减免规则。
--   - business_hours 单字段拆为 store_business_hours 子表（见下）。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `store`;
CREATE TABLE `store` (
  `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
  `name`                    VARCHAR(128) NOT NULL,
  `address`                 VARCHAR(255) DEFAULT NULL,
  `lng`                     DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
  `lat`                     DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  `city`                    VARCHAR(64)  DEFAULT NULL,
  `phone`                   VARCHAR(32)  DEFAULT NULL,
  `business_status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1营业中 0休息中（手动总开关）',
  `support_pickup`          TINYINT      NOT NULL DEFAULT 1 COMMENT '支持到店自取',
  `support_delivery`        TINYINT      NOT NULL DEFAULT 1 COMMENT '支持外卖配送',
  `delivery_fee`            BIGINT       NOT NULL DEFAULT 0 COMMENT '配送费（分）',
  `min_delivery_amount`     BIGINT       NOT NULL DEFAULT 0 COMMENT '起送价（分），0=不起送门槛',
  `free_delivery_threshold` BIGINT       NOT NULL DEFAULT 0 COMMENT '满额免配送费门槛（分），0=不减免',
  `delivery_radius_km`      DECIMAL(6,2) NOT NULL DEFAULT 5.00 COMMENT '配送半径（公里）',
  `pickup_lead_minutes`     INT          NOT NULL DEFAULT 10 COMMENT '自取预计等待分钟数',
  `sort`                    INT          NOT NULL DEFAULT 0,
  `status`                  TINYINT      NOT NULL DEFAULT 1,
  `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店';

-- -----------------------------------------------------------------------------
-- 门店营业时段
-- 修正点：原文档用 store.business_hours 单字段表达营业时间，无法表达
-- 「多时段（如 10:00-14:00, 16:30-21:00）」「按星期区分」「跨零点（22:00-02:00）」。
-- cross_day=1 表示结束时间在次日。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `store_business_hours`;
CREATE TABLE `store_business_hours` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `store_id`   BIGINT   NOT NULL,
  `day_of_week` TINYINT NOT NULL COMMENT '0=周日 1=周一 ... 6=周六',
  `open_time`  TIME     NOT NULL,
  `close_time` TIME     NOT NULL,
  `cross_day`  TINYINT  NOT NULL DEFAULT 0 COMMENT '1=close_time 在次日',
  PRIMARY KEY (`id`),
  KEY `idx_store_day` (`store_id`, `day_of_week`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店营业时段（按星期）';

-- -----------------------------------------------------------------------------
-- 商品分类
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(64) NOT NULL,
  `icon`       VARCHAR(255) DEFAULT NULL,
  `sort`       INT         NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- -----------------------------------------------------------------------------
-- 商品
-- 修正点（关键）：原文档 product 表带 store_id 单列，却又在术语表中定义
-- 「商品可在若干门店售卖」——一对多关系用单列无法表达。已移除 product.store_id，
-- 改为 store_product 关联表，并支持门店维度改价与独立上下架。
--
-- 售罄归一：原文档同时存在 product.sold_out、product_sku.stock、product_sku.status
-- 三个状态源，易不一致。此处约定：
--   sold_out = 手动售罄开关（0/1），仅表示运营主动下架；
--   实际可售 = 手动开关关闭 && 该门店上架 && 至少一个 SKU 有库存。
--   product_sku.status 仅保留「SKU 停用」语义，不再承担售罄判定。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `category_id` BIGINT        NOT NULL,
  `name`        VARCHAR(128)  NOT NULL,
  `description` VARCHAR(512)  DEFAULT NULL,
  `image`       VARCHAR(512)  DEFAULT NULL,
  `base_price`  BIGINT        NOT NULL DEFAULT 0 COMMENT '基准价（分），取最低 SKU 价',
  `calories`    INT           DEFAULT NULL COMMENT '热量 kcal',
  `sales`       INT           NOT NULL DEFAULT 0 COMMENT '累计销量（展示用）',
  `sold_out`    TINYINT       NOT NULL DEFAULT 0 COMMENT '手动售罄开关 1=售罄',
  `sort`        INT           NOT NULL DEFAULT 0,
  `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品';

-- -----------------------------------------------------------------------------
-- 门店 - 商品可售关系
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `store_product`;
CREATE TABLE `store_product` (
  `id`        BIGINT NOT NULL AUTO_INCREMENT,
  `store_id`  BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `price_diff` BIGINT NOT NULL DEFAULT 0 COMMENT '门店加价（分），可为负',
  `sold_out`  TINYINT NOT NULL DEFAULT 0 COMMENT '该门店单独售罄',
  `status`    TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_product` (`store_id`, `product_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店可售商品';

-- -----------------------------------------------------------------------------
-- 商品 SKU（杯型）
-- 库存与价格载体。甜度/冰度/温度属于「定制规格」，不影响原料库存，因此不进 SKU。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT      NOT NULL,
  `cup_size`   VARCHAR(32) NOT NULL COMMENT '中杯/大杯',
  `price`      BIGINT      NOT NULL COMMENT '售价（分）',
  `stock`      INT         NOT NULL DEFAULT 0 COMMENT '可售库存，-1 表示不限量',
  `version`    INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_cup` (`product_id`, `cup_size`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU';

-- -----------------------------------------------------------------------------
-- 规格组与规格项（甜度 / 冰度 / 温度 / 杯型）
-- 修正点：cup_size 既是 SKU 维度又是规格组，会造成「选杯型改库存」与「选杯型只改定制」
-- 两套逻辑打架。此处约定：spec_group.code='CUP' 的规格组为「库存维度组」，
-- 其选项与 product_sku.cup_size 一一对应，选中即确定 SKU；其余规格组仅影响定制描述。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `spec_group`;
CREATE TABLE `spec_group` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `product_id`  BIGINT      NOT NULL,
  `code`        VARCHAR(32) NOT NULL COMMENT 'SUGAR/TEMP/ICE/CUP',
  `name`        VARCHAR(64) NOT NULL COMMENT '甜度/温度/冰度/杯型',
  `is_required` TINYINT     NOT NULL DEFAULT 1 COMMENT '1必选',
  `sort`        INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格组';

DROP TABLE IF EXISTS `spec_option`;
CREATE TABLE `spec_option` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `group_id`    BIGINT      NOT NULL,
  `label`       VARCHAR(64) NOT NULL COMMENT '五分糖/去冰/中杯',
  `extra_price` BIGINT      NOT NULL DEFAULT 0 COMMENT '加价（分）',
  `is_default`  TINYINT     NOT NULL DEFAULT 0,
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格项';

-- -----------------------------------------------------------------------------
-- 小料
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `topping`;
CREATE TABLE `topping` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT      NOT NULL,
  `name`       VARCHAR(64) NOT NULL,
  `price`      BIGINT      NOT NULL DEFAULT 0 COMMENT '单价（分）',
  `max_count`  INT         NOT NULL DEFAULT 1 COMMENT '单条目最多可加份数',
  `stock`      INT         NOT NULL DEFAULT -1 COMMENT '-1 不限量',
  `sort`       INT         NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小料';

-- -----------------------------------------------------------------------------
-- 购物车
-- 修正点：原文档将购物车放 Redis Hash。Redis 重启即丢，且本地环境无 Redis。
-- 改为落库（user_id + store_id 隔离），登录合并游客车时用唯一键做 upsert。
-- item_key 为「同一商品不同定制」的条目指纹，保证同规格合并、异规格分立。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `cart_item`;
CREATE TABLE `cart_item` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT   NOT NULL,
  `store_id`     BIGINT   NOT NULL,
  `product_id`   BIGINT   NOT NULL,
  `sku_id`       BIGINT   NOT NULL,
  `item_key`     VARCHAR(128) NOT NULL COMMENT 'product_sku_spec_topping 指纹',
  `quantity`     INT      NOT NULL DEFAULT 1,
  `spec_snapshot`  JSON   DEFAULT NULL COMMENT '规格选项快照 [{groupId,groupName,optionId,label,extraPrice}]',
  `topping_snapshot` JSON DEFAULT NULL COMMENT '小料快照 [{toppingId,name,price,count}]',
  `unit_price`   BIGINT   NOT NULL DEFAULT 0 COMMENT '单份价（SKU价+规格加价+小料价），分',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_line` (`user_id`, `store_id`, `item_key`),
  KEY `idx_user_store` (`user_id`, `store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车条目';

-- -----------------------------------------------------------------------------
-- 订单
-- 修正点（相对原文档 orders 表）：
--   + 补齐 paid_at / accepted_at / ready_at / delivering_at / completed_at / cancel_at，
--     原表只有 created_at，无法渲染「订单时间线」这一验收项。
--   + idempotent_key 唯一索引：原文档要求「下单传 idempotent_key」但表上无约束，
--     无唯一索引的幂等是不可靠的（并发下会双写）。
--   + pickup_code + pickup_date + store_id 联合唯一：原文档只说「生成唯一取餐码」，
--     未定义唯一域。全域唯一会导致编码过长，按「门店+当日」唯一更贴近门店实际喊号。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `order_no`          VARCHAR(32) NOT NULL COMMENT '订单号',
  `user_id`           BIGINT      NOT NULL,
  `store_id`          BIGINT      NOT NULL,
  `order_type`        VARCHAR(16) NOT NULL COMMENT 'PICKUP 自取 / DELIVERY 外卖',
  `status`            VARCHAR(24) NOT NULL COMMENT '见订单状态机',
  `total_amount`      BIGINT      NOT NULL DEFAULT 0 COMMENT '商品总额（分）',
  `discount_amount`   BIGINT      NOT NULL DEFAULT 0 COMMENT '优惠总额（含券+积分抵扣，分）',
  `coupon_amount`     BIGINT      NOT NULL DEFAULT 0 COMMENT '优惠券抵扣（分）',
  `point_amount`      BIGINT      NOT NULL DEFAULT 0 COMMENT '积分抵扣金额（分）',
  `point_used`        BIGINT      NOT NULL DEFAULT 0 COMMENT '本次消耗积分',
  `level_discount_amount` BIGINT  NOT NULL DEFAULT 0 COMMENT '会员等级折扣（分）',
  `delivery_fee`      BIGINT      NOT NULL DEFAULT 0 COMMENT '配送费（分）',
  `pay_amount`        BIGINT      NOT NULL DEFAULT 0 COMMENT '应收金额（分）',
  `user_coupon_id`    BIGINT      DEFAULT NULL COMMENT '本单使用的用户券',
  `pickup_code`       VARCHAR(16) DEFAULT NULL COMMENT '取餐码',
  `pickup_date`       DATE        DEFAULT NULL,
  `address_snapshot`  JSON        DEFAULT NULL COMMENT '外卖地址快照',
  `expect_time`       DATETIME    DEFAULT NULL COMMENT '预约到店/送达时间',
  `remark`            VARCHAR(255) DEFAULT NULL,
  `idempotent_key`    VARCHAR(64) DEFAULT NULL,
  `expire_time`       DATETIME    NOT NULL COMMENT '支付截止时间',
  `paid_at`           DATETIME    DEFAULT NULL,
  `accepted_at`       DATETIME    DEFAULT NULL,
  `ready_at`          DATETIME    DEFAULT NULL,
  `delivering_at`     DATETIME    DEFAULT NULL,
  `completed_at`      DATETIME    DEFAULT NULL,
  `cancel_at`         DATETIME    DEFAULT NULL,
  `cancel_reason`     VARCHAR(128) DEFAULT NULL,
  `refunded_at`       DATETIME    DEFAULT NULL,
  `point_awarded`     TINYINT     NOT NULL DEFAULT 0 COMMENT '消费积分是否已入账（幂等标记）',
  `version`           INT         NOT NULL DEFAULT 0,
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_idempotent` (`idempotent_key`),
  UNIQUE KEY `uk_pickup` (`store_id`, `pickup_date`, `pickup_code`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_store_status` (`store_id`, `status`),
  KEY `idx_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- -----------------------------------------------------------------------------
-- 订单明细
-- 修正点：原文档 order_item 无商品图片快照，订单详情页无法渲染商品图（只能回查商品表，
-- 商品下架后图片即丢失）。补 product_image。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `order_id`         BIGINT      NOT NULL,
  `product_id`       BIGINT      NOT NULL,
  `product_name`     VARCHAR(128) NOT NULL,
  `product_image`    VARCHAR(512) DEFAULT NULL,
  `sku_id`           BIGINT      NOT NULL,
  `sku_snapshot`     VARCHAR(64)  DEFAULT NULL COMMENT '杯型快照',
  `quantity`         INT         NOT NULL,
  `unit_price`       BIGINT      NOT NULL COMMENT '成交单份价（分）',
  `spec_snapshot`    JSON        DEFAULT NULL COMMENT '规格快照',
  `topping_snapshot` JSON        DEFAULT NULL COMMENT '小料快照',
  `spec_text`        VARCHAR(512) DEFAULT NULL COMMENT '规格可读文本（下单时快照，便于订单详情展示）',
  `topping_text`     VARCHAR(512) DEFAULT NULL COMMENT '小料可读文本（下单时快照）',
  `total_price`      BIGINT      NOT NULL COMMENT 'unit_price * quantity（分）',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- -----------------------------------------------------------------------------
-- 订单状态流转日志（订单详情时间线的数据源）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `order_status_log`;
CREATE TABLE `order_status_log` (
  `id`         BIGINT    NOT NULL AUTO_INCREMENT,
  `order_id`   BIGINT    NOT NULL,
  `from_status` VARCHAR(24) DEFAULT NULL,
  `to_status`  VARCHAR(24) NOT NULL,
  `operator`   VARCHAR(64) DEFAULT NULL COMMENT '操作人（店员账号或 SYSTEM/USER）',
  `remark`     VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志';

-- -----------------------------------------------------------------------------
-- 支付流水
-- 修正点：原文档 payment 表有 callback_body 字段，与「密钥不落库/不进日志」的安全要求
-- 存在张力。改为只保留回调中的非敏感要素（transaction_id、回调时间），不存原始报文。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `pay_no`         VARCHAR(64) NOT NULL COMMENT '本系统支付流水号（幂等键）',
  `order_id`       BIGINT      NOT NULL,
  `order_no`       VARCHAR(32) NOT NULL,
  `channel`        VARCHAR(24) NOT NULL COMMENT 'MOCK / WECHAT_JSAPI',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付订单号',
  `amount`         BIGINT      NOT NULL COMMENT '支付金额（分）',
  `status`         VARCHAR(16) NOT NULL DEFAULT 'PAYING' COMMENT 'PAYING/SUCCESS/FAILED/CLOSED',
  `prepay_json`    TEXT        DEFAULT NULL COMMENT '预支付参数（前端拉起支付用）',
  `fail_reason`    VARCHAR(255) DEFAULT NULL,
  `paid_at`        DATETIME    DEFAULT NULL,
  `callback_at`    DATETIME    DEFAULT NULL,
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  UNIQUE KEY `uk_transaction` (`channel`, `transaction_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水';

-- -----------------------------------------------------------------------------
-- 优惠券模板
-- 修正点：原文档 coupon 表用单个 scope 字段表达「全场/指定分类/指定商品」三种范围，
-- 无法表达「多个指定分类」。拆出 coupon_scope 子表。
-- 修正点：补 channels（自取/外卖适用渠道），原文档表结构遗漏了该字段。
-- 修正点：补 stackable 明确「券与会员等级折扣是否叠加」——原文档只定义了券与秒杀互斥，
-- 未定义与等级折扣的关系，会造成金额计算歧义。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `coupon_template`;
CREATE TABLE `coupon_template` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(128) NOT NULL,
  `type`          VARCHAR(16) NOT NULL COMMENT 'NO_THRESHOLD 无门槛 / FULL_REDUCTION 满减 / DISCOUNT 折扣',
  `amount`        BIGINT      NOT NULL DEFAULT 0 COMMENT '无门槛/满减的立减金额（分）',
  `min_amount`    BIGINT      NOT NULL DEFAULT 0 COMMENT '满减门槛（分）',
  `discount`      INT         NOT NULL DEFAULT 10000 COMMENT '折扣万分比，9000=9折',
  `max_discount`  BIGINT      NOT NULL DEFAULT 0 COMMENT '折扣券最高抵扣（分），0=不限',
  `scope_type`    VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL 全场 / CATEGORY 指定分类 / PRODUCT 指定商品',
  `channels`      VARCHAR(32) NOT NULL DEFAULT 'PICKUP,DELIVERY' COMMENT '适用渠道',
  `valid_type`    VARCHAR(16) NOT NULL DEFAULT 'DAYS' COMMENT 'DAYS 领取后N天有效 / RANGE 固定有效期',
  `valid_days`    INT         NOT NULL DEFAULT 7,
  `start_time`    DATETIME    DEFAULT NULL,
  `end_time`      DATETIME    DEFAULT NULL,
  `total`         INT         NOT NULL DEFAULT -1 COMMENT '发放总量，-1 不限',
  `issued`        INT         NOT NULL DEFAULT 0,
  `limit_per_user` INT        NOT NULL DEFAULT 1,
  `point_cost`     BIGINT     NOT NULL DEFAULT 0 COMMENT '积分兑换所需积分，0=不可用积分兑换',
  `stackable`     TINYINT     NOT NULL DEFAULT 0 COMMENT '1=可与会员等级折扣叠加（默认不叠加）',
  `status`        TINYINT     NOT NULL DEFAULT 1,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

DROP TABLE IF EXISTS `coupon_scope`;
CREATE TABLE `coupon_scope` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `coupon_id`  BIGINT   NOT NULL,
  `scope_type` VARCHAR(16) NOT NULL COMMENT 'CATEGORY / PRODUCT',
  `ref_id`     BIGINT   NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_scope` (`coupon_id`, `scope_type`, `ref_id`),
  KEY `idx_coupon` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券适用范围';

-- -----------------------------------------------------------------------------
-- 用户券
-- 修正点（关键）：原文档只有「未使用 / 已使用 / 已过期」三态，但正文同时要求
-- 「下单成功即核销占用」和「支付失败/取消则释放」。三态无法区分「被订单占用但尚未支付」
-- 与「真正在用」，导致超时取消时无法判断该不该退回。增加 LOCKED 占用态。
-- 状态：UNUSED 未使用 → LOCKED 已占用（订单待支付）→ USED 已核销
--       任意非 USED 态可被 EXPIRED 覆盖。
-- version 用于乐观锁，保证并发下单时同一张券只会被一张订单占用。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT   NOT NULL,
  `coupon_id`  BIGINT   NOT NULL,
  `order_id`   BIGINT   DEFAULT NULL COMMENT '占用/核销该券的订单',
  `status`     VARCHAR(16) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/LOCKED/USED/EXPIRED',
  `start_time` DATETIME NOT NULL,
  `end_time`   DATETIME NOT NULL,
  `used_at`    DATETIME DEFAULT NULL,
  `version`    INT      NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_order` (`order_id`),
  KEY `idx_expire` (`status`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

-- -----------------------------------------------------------------------------
-- 积分流水
-- 修正点（关键）：原文档验收标准写「积分余额 = 累计获得 − 累计使用」，同时又要求
-- 「积分默认年度滚动过期」。有过期机制时该等式不成立。修正为：
--   余额 = 累计获得 − 累计使用 − 累计过期
-- 并新增 EXPIRE 流水类型，使余额与流水始终可对账。
-- biz_no 唯一索引保证同一业务（同一订单的积分入账、同一日的签到）只记一次。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `point_record`;
CREATE TABLE `point_record` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT   NOT NULL,
  `type`          VARCHAR(24) NOT NULL COMMENT 'EARN_CONSUME/EARN_SIGNIN/USE_EXCHANGE/USE_DEDUCT/REFUND_ROLLBACK/EXPIRE/ADMIN_ADJUST',
  `amount`        BIGINT   NOT NULL COMMENT '正数为增加，负数为扣减',
  `balance_after` BIGINT   NOT NULL,
  `biz_no`        VARCHAR(64) DEFAULT NULL COMMENT '业务唯一号，防重复入账',
  `remark`        VARCHAR(255) DEFAULT NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_no` (`biz_no`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- -----------------------------------------------------------------------------
-- 收货地址
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `address`;
CREATE TABLE `address` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL,
  `name`       VARCHAR(64) NOT NULL,
  `phone`      VARCHAR(20) NOT NULL,
  `province`   VARCHAR(64) DEFAULT NULL,
  `city`       VARCHAR(64) DEFAULT NULL,
  `district`   VARCHAR(64) DEFAULT NULL,
  `detail`     VARCHAR(255) NOT NULL,
  `tag`        VARCHAR(16) DEFAULT NULL COMMENT '家/公司/学校',
  `is_default` TINYINT     NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址';

-- -----------------------------------------------------------------------------
-- 首页 Banner
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(128) DEFAULT NULL,
  `image`      VARCHAR(512) DEFAULT NULL,
  `link_type`  VARCHAR(32) DEFAULT NULL COMMENT 'NONE/PRODUCT/COUPON/POINT/PAGE',
  `link_value` VARCHAR(128) DEFAULT NULL,
  `sort`       INT         NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页Banner';

-- -----------------------------------------------------------------------------
-- 系统配置（积分规则、支付超时等可配置项的落库载体）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `config_key`  VARCHAR(64) NOT NULL,
  `config_value` VARCHAR(255) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

SET FOREIGN_KEY_CHECKS = 1;
