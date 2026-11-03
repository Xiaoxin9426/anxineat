# 安心饮点餐小程序（Anxineat）

基于原生微信小程序 + Spring Boot 3 的「点餐 / 复刻」项目：覆盖到店自取与外卖下单、规格小料定制、会员积分、优惠券营销、商家后台，并内置模拟支付与真实微信 JSAPI 适配层。

> 品牌说明：本项目为独立品牌「安心饮 / Anxineat」，（详见 `docs/需求规格与技术实现方案.md` 附录 B）。

---

## 1. 工程结构

```text
Anxineat/
├─ backend/            后端（Spring Boot 3.2 + MyBatis-Plus 3.5.7 + MySQL 8）
│  ├─ pom.xml
│  ├─ mvn.sh           启动包装脚本（绕开 MAVEN_HOME 反斜杠路径问题）
│  └─ src/main/
│     ├─ java/com/anxineat/   controller / dto / module/* / common / config / job
│     └─ resources/
│        ├─ db/schema.sql, db/seed.sql
│        ├─ static/admin/index.html   商家后台单文件 Web 控制台（约 81KB）
│        └─ application.yml
├─ miniprogram/        原生微信小程序前端（pages/* components/* utils/*）
├─ docs/               API接口契约.md / 需求规格与技术实现方案.md（修订版）
├─ db/                 schema.sql / seed.sql（与 backend 内同源，便于直接导入）
└─ smoke_test.py       端到端联调脚本（Python 3，无第三方依赖）
```

## 2. 技术栈

|层|方案|
|---|---|
|小程序|原生微信小程序（WXML/WXSS/JS）|
|商家后台|单文件静态页 `backend/.../static/admin/index.html`（免构建）|
|后端|Spring Boot 3.2.x + MyBatis-Plus 3.5.7|
|数据库|MySQL 8.0（库 `anxineat`）|
|鉴权|JWT（jjwt 0.12.5），`Authorization: Bearer <token>`|
|并发安全|数据库 CAS（`version` 乐观锁）+ 唯一索引幂等（**无 Redis 依赖**）|
|支付|模拟支付 `MOCK` + 微信 JSAPI 适配层|
|异步|`@Scheduled` 定时任务（扫描超时订单，替代延迟队列）|

## 3. 环境准备

- JDK 17+，Maven 3.8+（本机 `mvn` 可用）；MySQL 8.0 运行中。
- 微信开发者工具（导入 `miniprogram/` 目录，填入测试 AppID 或「不校验合法域名」）。

### 3.1 建库与初始化

```sql
-- MySQL 客户端执行
CREATE DATABASE IF NOT EXISTS anxineat DEFAULT CHARSET utf8mb4;
-- 导入结构与种子数据
mysql -uroot -p123456 anxineat < db/schema.sql
mysql -uroot -p123456 anxineat < db/seed.sql
```

> 数据源配置见 `backend/src/main/resources/application.yml`：`root/123456`，库 `anxineat`，JDBC 已用 `characterEncoding=UTF-8`（Connector/J 8.x 拒绝 `utf8mb4`）。

### 3.2 启动后端

```bash
cd backend
./mvn.sh            # 等价于 SERVER_PORT=8080 mvn.cmd spring-boot:run
```

启动后访问：`http://localhost:8080/api/health` 应返回 `ok`。
商家后台：`http://localhost:8080/admin/index.html`。

> 说明：`mvn.sh` 内部以 `SERVER_PORT=8080` 显式指定端口（spring-boot:run fork 默认会用随机端口）。Windows 下该脚本调用 `mvn.cmd`，避免 `MAVEN_HOME` 反斜杠路径问题。

### 3.3 微信小程序

用微信开发者工具打开 `miniprogram/`，在「详情 → 本地设置」勾选「不校验合法域名」；将 `utils/request.js` 或 `config/` 中的后端基地址改为 `http://<本机IP>:8080/api`（真机调试需同网段）。

## 4. 默认账号与测试数据

|类型|账号|密码/说明|
|---|---|---|
|超管后台|`admin`|`admin123`（BCrypt）|
|店员|`clerk1` / `clerk2`|同密码哈希，分别绑定 store 1 / 2|
|测试用户|微信静默登录 `code=mock:openid-demo-0001`|或手机号 `13800000001` / 验证码 `1234`（dev 模式固定）|
|门店|store 1 = 安心饮·万达广场店|seed 内已关联全部商品与 SKU|
|商品|product 1 = 冰鲜柠檬水（400 分）|sku 中杯/大杯；CUP 中杯=1、SUGAR 五分糖=25、TEMP 正常冰=62|

> ⚠️ 联调便利项（**非生产配置**）：为便于端到端测试，seed 曾将 store 1 营业时间临时置为 00:00–23:59。交付前请改回正常营业时段（如 09:30–22:00），或直接以「营业中」手动开关（`business_status=1`）配合 `store_business_hours` 判定。

## 5. 端到端验证（smoke_test.py）

无第三方依赖，覆盖完整闭环：

```bash
cd "Anxineat"
python smoke_test.py        # Python 3.13，自动探测后端
```

脚本链路：`health → 登录 → 加购(带 specOptionIds:[1,25,62]) → 预览(¥800) → 下单 → 模拟支付(含 duplicate=true 幂等) → 详情 → 后台登录(admin123) → overview → accept/ready → redeem(取餐码核销) → 终态 COMPLETED`。

最近一次联调结果：**全绿**——订单自 `PENDING_PAYMENT` 经 `PAID→MAKING→READY→COMPLETED` 流转，重复支付回调幂等正确拦截（`duplicateHandled=true`）；后台 `overview` 订单数一致；取餐码核销成功。

## 6. 接口契约

前后端以 `docs/API接口契约.md` 为唯一权威。所有金额单位为**分（整数）**，折扣为**万分比**；响应统一 `{ code, message, data, timestamp }`，`code===0` 成功。核心接口：

- 公开：`/api/health`、`/api/store/nearby`、`/api/store/{id}/menu`、`/api/product/{id}`、`/api/auth/login`
- 购物车：`/api/cart`（GET/POST/PUT/DELETE）
- 订单：`/api/order/preview`、`/api/order`、`/api/order/{id}`、`/api/order/list`、`/api/order/cancel`
- 支付：`/api/pay/create`、`/api/pay/mock/callback`、`/api/pay/callback`
- 会员营销：`/api/user/point/records`、`/api/user/signin`、`/api/coupon/center`、`/api/coupon/mine`
- 后台：`/api/admin/*`（需 `type=ADMIN` 的 JWT）

## 7. 关键设计决策（相对原始需求文档的修正）

完整逐条记录见 `docs/需求规格与技术实现方案.md` **附录 C**。要点：

1. **积分余额口径**：`余额 = 累计获得 − 累计使用 − 累计过期`（原文档「=获得−使用」与「年度过期」矛盾）。
2. **金额/折扣精度**：一律「分」(BIGINT) + 「万分比」(INT)，禁用浮点。
3. **并发安全去 Redis**：购物车/库存/券改用数据库 CAS + 唯一索引；无 Redis 依赖。
4. **数据模型修正**：`product` 去 `store_id` 改 `store_product` 关联；`order_item` 补 `product_image/spec_text/topping_text`；`orders` 补时间线与 `idempotent_key`/`pickup` 唯一约束；`user_coupon` 增 `LOCKED` 态；`coupon` 拆 `coupon_scope` 子表 + `channels`/`stackable`；`payment` 不存原始回调报文。
5. **营业状态**：`business_status`（手动总开关）∧ `store_business_hours`（按星期多时段）联合判定。
6. **品牌合规**：全文改用「安心饮 / Anxineat」，规避商标与版权。

## 8. 当前进度与后续

- ✅ 阶段 0–2 主体：后端编译通过、DB 初始化、8080 常驻、端到端 smoke 全绿、小程序与商家后台落地。
- ⏳ 阶段 3（P2）：营销活动、会员等级专属折扣、复购推荐、数据看板。
- ⏳ 生产化：接入真实微信支付（JSAPI 适配层已预留）、短信验证码、对象存储、Docker 部署、Redis（如需横向扩展）。

---

## 9. 已知限制与已修复项

- **已修复（本会话）**：无限库存 SKU（`stock = -1`）此前会被订单错误扣减，逐次把哨兵打成负数并最终误报「库存不足」。已将 `ProductSkuMapper.deductStock` 改为 `SET stock = IF(stock = -1, -1, stock - #{qty})`，不限量库存永不递减；`restoreStock` 本就跳过 `-1`。已 clean build 重启并连续两次 smoke 验证 sku1 稳定为 `-1`。
- 本期支付为模拟（`MOCK`），真实微信支付需配置商户号与证书后启用 `/api/pay/callback` 验签。
- 图片字段占位/留空，未接入 OSS/COS。
- 外卖配送为状态模拟，未接第三方骑手。
- 联调账号与库密码为演示值，生产须替换。
