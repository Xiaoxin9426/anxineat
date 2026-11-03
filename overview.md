# 本次交付概览（2026-09-03）

## 目标
基于「蜜雪冰城点餐小程序复刻」需求文档，复刻安心饮（Anxineat）点餐小程序，并**修正原文档不合理处**。

## 本会话交付物
1. **`docs/需求规格与技术实现方案.md`（修订版）** —— 原文档不合理处全部就地修正，文末 **附录 C 修订记录（C-1~C-15）** 逐条列出原表述/问题/修正/依据，并补全原文档空缺的「下单流程」「订单状态机」「总体架构」章节。
2. **`README.md`** —— 工程结构、技术栈、建库启动、默认账号、端到端联调、`API接口契约.md` 索引、设计决策（相对原文档的修正）、已知限制与已修复项。
3. **`docs/营销活动模块实现方案.md`（草案）** —— 在现有架构上设计秒杀/口令/0元兑换，复用既有券/积分/CAS 机制；含统一活动主表 + 明细表 DDL、接口契约、与下单链路集成点、§8 七条待产品确认项。待产品拍板后进入编码。

## 联调验证（已实跑）
- 后端 `mvn` 编译通过，Spring Boot 在 **8080** 常驻（`java -jar target/anxineat-backend.jar`）。
- `smoke_test.py` 端到端**全绿**：登录 → 加购 → 预览(¥400) → 下单 → 模拟支付（重复回调幂等 `duplicateHandled:true`）→ 后台核销 → 订单 `COMPLETED`。
- 商家后台单文件页 `http://localhost:8080/admin/index.html` 可访问。

## 本会话修复的真实 Bug
- **无限库存被错误扣减**：`ProductSkuMapper.deductStock` 原逻辑对 `stock=-1`（不限量）也会 `stock-1`，逐次把哨兵打成负数，最终误报「库存不足」。改为 `SET stock = IF(stock=-1, -1, stock-#{qty})`。clean build + 重启后连续两次 smoke 验证 sku1 稳定为 `-1`。
- **小程序「订单 / 积分」tab 静默白屏（分页字段不匹配）**：后端列表接口返回原生 MyBatis-Plus `Page`，JSON 字段为 `records`/`current`/`pages`；但 `pages/orders/orders.js` 与 `pages/points/points.js` 按 `r.list`/`r.page` 读取，导致两 tab 永远空列表。已改为读取 `records`/`current`。**切勿反向改后端为 `list`/`page`** —— 商家后台单文件控制台 `index.html` 已按 `records`/`current` 消费，改后端会破坏后台。de-facto 契约即 `records`/`current`/`pages`。

## 本轮新增（数据看板 + 契约核对）
- **Task #9 商家后台数据看板增强（已完成）**：`/admin/overview` 现返回近 7 日销售趋势 `trend7d`（MM-dd，缺失日补 0）与热销商品 TOP5 `topProducts`；后台控制台新增两张图表卡片。验证：`/admin/overview?storeId=1` 返回今日 4 单 / ¥20、TOP1「冰鲜柠檬水 5 杯 / ¥20」。
- **小程序 API 契约核对（36/36 一致）**：`miniprogram/utils/api.js` 全部 36 个调用与后端路由 1:1 对应；`request.js`、`app.json`(17 页 + 4 tabBar 图标) 健全；`orderDetail`/`cart`/`confirm` 关键字段与后端 DTO 一致。
- 回归 `smoke_test.py` 全绿，确认看板改动与小程序修复未破坏下单链路。

## 本轮新增：真实微信支付 JSAPI（代码完成，缺商户凭证）
`PaymentService` 原为 MOCK 占位，现已完整实现 V3 链路：

- **下单**：`POST /v3/pay/transactions/jsapi`，用商户私钥（PKCS#8 PEM，缓存复用）对 `method\npath\ntimestamp\nnonce\nbody\n` 做 SHA256withRSA 签名生成 `Authorization` 头；拿到 `prepay_id` 后再对 `appId\ntimeStamp\nnonceStr\npackage\n` 二次签名得到 `paySign`，向前端返回 `wx.requestPayment` 全套参数。
- **回调**：新增 `POST /api/pay/wechat/notify`（已在 `WebConfig` 免鉴权，微信服务器无我方 token），用 api-v3-key 解密 AES-256-GCM 报文，取 `out_trade_no`（即 `pay_no`）与 `transaction_id`，复用与 MOCK 相同的幂等闸门 `tryMarkSuccess`。
- **前端**：`payment.js` 新增 `onWechatPay()` 拉起 `wx.requestPayment`，成功后 `pollUntilPaid()` 轮询 `payQuery`（最多 12 次 × 1.5s）等待回调落库；`payment.wxml` 按 `pay.mock` 用 `<block wx:if/wx:else>` 分流「真实支付」与「模拟支付」两套 UI。
- 验证：clean build 通过；通知端点空 body 返回业务错误 `1001`（证明已注册且免鉴权，非 404 / 未登录）。
- **尚缺企业商户凭证才能联调**：`WECHAT_APP_ID`、`WECHAT_MCH_ID`、`WECHAT_API_V3_KEY`、`WECHAT_MCH_SERIAL_NO`、`WECHAT_PRIVATE_KEY_PATH`、`WECHAT_NOTIFY_URL`，并将 `anxineat.pay.wechat.enabled` 置 `true`、`sys_config.pay.channel` 改为 `WECHAT_JSAPI`。

## 本轮修复：小程序「无登录弹窗 + 登录已过期，请重试」
上一轮为满足「经过真实 wx.login」把 `doLogin()` 改成只调 `wx.login()`，引入了回归：

- **根因（两个叠加）**：① 后端重启时落到随机端口而启动失败，小程序固定连 `localhost:8080`，实际后端不可用；② 更关键——`wx.login` 是**静默**接口、永不弹窗，拿到真实 code 后因后端未配置 appid/appsecret 返回 `1001`，而 `request()` 把登录失败统一 toast 成「登录已过期，请重试」，把真实原因吞掉了。
- **修复：改为后端驱动的双模自动切换**
  - 后端 `UserService.isRealWxLoginEnabled()`（appid 与 appsecret 均非空），由 `ConfigController` 在免鉴权的 `/api/config/client` 暴露 `wxRealLogin`。
  - 前端 `doLogin()` 先读该配置（免鉴权，不会触发登录递归，结果缓存）：`true` 才 `wx.login()`，`false` 则用 `mock:openid-demo-0001`。登录失败改为透出后端真实 message（toast + `console.error`）。
  - 效果：未配凭证时小程序**自动回落 mock 登录、立即可用**；一旦配好凭证重启后端，自动切换为真实 `wx.login`，无需改任何代码。
- 验证：8080（未配凭证）→ `wxRealLogin:false`，mock 登录正常签发 token；临时 8081 实例带假凭证 → `wxRealLogin:true`，确认切换逻辑生效。完整链路 `无 token → 1002 → 读配置 → 登录 → 带 token 重试 → 200` 通过，`smoke_test.py` 全绿。

## 关键修正点（文档侧，详见附录 C）
积分余额口径（`获得−使用−过期`）、order_item 补图片/规格/小料快照、营业状态「手动开关 ∧ 营业时间」、金额「分」+折扣「万分比」、product 去 store_id 改 store_product、购物车/库存/券去 Redis 改 DB CAS、用户券四态 + 乐观锁、券范围拆子表 + 渠道/叠加、`payment` 不存原始报文、登录绑定时序、品牌合规（安心饮）、技术选型对齐原生小程序 + 单文件后台。

## 运行速查
```bash
# 建库
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS anxineat DEFAULT CHARSET utf8mb4;"
mysql -uroot -p123456 anxineat < db/schema.sql
mysql -uroot -p123456 anxineat < db/seed.sql
# 启动
cd backend && SERVER_PORT=8080 java -jar target/anxineat-backend.jar
# 联调
python smoke_test.py
```
默认账号：超管 `admin/admin123`；门店 1 = 安心饮·万达广场店；商品 1 = 冰鲜柠檬水(400分)。

---

## 本轮（2026-09-03 续）：先保业务跑通，营销模块暂停

### 用户指令
「先不管营销模式，先保证所有业务能正常运行」——暂停营销开发，优先确保既有下单/支付/券/积分/登录等业务全部正常运行。

### 做了什么
1. **修复营销模块 4 处编译错误**（此前已写代码但未编译，导致整个 jar 构建失败；修复后业务 + 营销一并 `BUILD SUCCESS`）：
   - `module/marketing/mapper/ZeroExchangeActivityMapper.java` 补 `selectByActivity` 方法 + `import java.util.List;`（去重）。
   - `module/marketing/service/MarketingService.java` 的 `MarketingAdminRequest` 内部类补 `private Long pointCost;` 字段（`req.getPointCost()` 之前找不到符号）。
   - 同文件 `ze.setPointCost(...)` 中 `0` → `0L`（int 不能赋 Long）。
2. **重建并启动**：系统 `mvn` 损坏，用 `./mvn.sh clean package -DskipTests` 重建 `target/anxineat-backend.jar`；`SERVER_PORT=8080 java -jar ...`（run_in_background，当前 PID 39556）。
3. **端口治理**：8080 上存在未知旧实例（PID 3004，非本轮 `java -jar` 进程），`taskkill` 后确定性重启，消除「Port 8080 already in use」假故障。

### 验证（脱敏冒烟）
- `smoke_redacted.py`（核心下单链路）13/13 OK：health → config/client（`wxRealLogin=false`）→ 登录 → 加购 → 预下单(400分) → 提交 → 模拟支付回调 + 重复幂等 → 订单 PAID(取餐码009) → 后台登录(admin123) → 概览 → 接单 → 出餐 → 核销 → **COMPLETED**。
- `smoke_business_full.py`（**全模块回归，37 项**）全部正常：公开接口(health/config/store/nearby/store菜单/product/banner)、登录、购物车(增/查/改/删)、优惠券(center/receive/mine/exchange-list)、积分(records/signin/profile)、地址(增/查/默认/删)、订单全生命周期(预览/创建/列表/详情/支付查询/模拟支付/PAID)、后台(登录/概览/**`/api/admin/orders`列表=10条**/接单/出餐/核销/COMPLETED)。
  - 注：后台订单列表真实路径是 `/api/admin/orders`（GET，单文件控制台 index.html:1013 即调用此路径）；脚本初版误写为 `/api/admin/order/list` 导致 1005，纯测试路径错误、非后端 bug，已修正。
  - 重复运行同一 mock 身份时 `coupon/receive` 返 `4005`(已达领取上限)、`user/signin` 返 `4008`(今日已签到) —— 均为预期的重复拦截，证明限领/防重逻辑生效，非故障。

### 结论
现有业务链路确认正常运行。营销代码已编译进 jar 但暂停开发（独立接口、正常业务流不触达）；其 5 张表 DDL 与 `order_item` 两列 ALTER 是否已在 DB 落地未核实，启用营销前需先建表。用户此前创建的营销 worker（marketing-backend / marketing-frontend）因限流失败，本轮由主会话直接完成了所需编译修复，team 已无活动成员、将自动回收。

---

## 本轮续（2026-09-03 晚）：修复「商家端看不到订单」

### 用户反馈（最高优先）
小程序用户点完餐，商家后台订单列表看不到订单。

### 根因（两个叠加）
1. **控制台字段读错（主因，所有门店都看不到）**：`AdminController.orders` 返回 MyBatis-Plus `IPage`，JSON 字段是 `records`/`current`/`pages`；但 `static/admin/index.html` 的 `loadOrders` 误读 `data.list`（:1026）与 `data.page`（:1039）。API 无 `list`/`page` 字段 → `data.list` 恒 `undefined` → 永远渲染空列表。早期记忆误记「控制台已按 records/current 消费」，实为错误（已改正）。
2. **SQL 缺 null 守卫（超管「全部门店」专属漏单）**：`OrderMapper` 的 `selectStoreOrders` 等 6 个查询原 `WHERE store_id = #{storeId}`。「全部门店」选项 `state.storeId=''`，`request()` 助手丢弃空串 → 不发 `storeId` → 后端收到 `null` → `WHERE store_id = null` 恒不成立 → 0 行。店员因 `effectiveStoreId` 强制绑定 storeId 不受影响。

### 修复
- `index.html:1026` `data.list` → `data.records`；`:1039` `data.page` → `data.current`（对齐 de-facto 契约，不改后端）。
- `OrderMapper.java` 6 个 `@Select`（`selectStoreOrders`/`selectTodayStat`/`selectStatusCounts`/`select7DayTrend`/`selectTopProducts`/`selectPaidOrderCount`）统一改为 `<where><if test="storeId != null"> AND store_id = #{storeId} </if>…</where>`。
- products/coupons 走 `R<List>`（data 即数组），不受影响。

### 验证
- `./mvn.sh clean package -DskipTests` → BUILD SUCCESS（两修复均进 jar）。
- 重启 8080（单起干净实例 PID 39496）。
- 脱敏 `verify_admin_orders.py`：超管登录后 `GET /api/admin/orders`（不发 storeId，即「全部门店」）返回 `records` 10 条、code=0。
- `curl /admin/index.html` 确认线上文件含 `data.records`、`const list = data.list` 已消失 → 两修复均已落地线上。
- 结论：商家端订单列表现已能正常展示全部门店订单。

### 注：端口治理小插曲
重启时显式 `java -jar` 因「Port 8080 already in use」失败一次——环境看门狗在杀旧实例后抢先以 jar 重新拉起（该实例已含修复，curl 验证通过）。最终杀净 8080 后单起干净实例确定性验证通过。

---

## 本轮续（2026-09-03 晚 2）：修复「在售状态无法更改」

### 用户反馈
商家端商品上架/下架、售罄开关点了没反应，无法更改状态。

### 根因（两个叠加）
1. **字段名错位（主因，全角色中招）**：控制台发 `onSale`/`soldOut`，但 `StoreProductFlagRequest` 只有 `flag`，`AdminController` 读 `req.getFlag()`（恒为 null → 兜底 `1`）。后端一律 `setStatus(1)`/`setSoldOut(1)`，永远只能开、关不掉。
2. **超管「全部门店」storeId=null**：`setOnSale/setSoldOut` 把 null 直接塞 `WHERE store_id = null` → 0 行、静默失效（店员因绑定门店非空不受影响）。

### 修复
- `RequestDTO.StoreProductFlagRequest` 增加 `onSale`/`soldOut` 字段（保留 `flag` 兼容）。
- `AdminController.onSale` 优先读 `getOnSale()`（兜底 `flag`→`1`）；`onSoldOut` 同理读 `getSoldOut()`。
- `AdminService.setOnSale/setSoldOut` 改用 `effectiveStoreId(requestedStoreId)`：店员强制本店；超管传 null 时不加 `store_id` 条件、对该商品所有门店生效。

### 验证
- `./mvn.sh clean package -DskipTests` → BUILD SUCCESS；重启 8080。
- 脱敏脚本：store 1「冰鲜柠檬水」发 `onSale=0` → 列表 `onSale=False`，再发 `onSale=1` → `True`，往返正确（RESULT: PASS）。前端 `index.html` 本就正确，无需改动。

---

## 本轮续（2026-09-03 晚 3）：商家后台控制台全量契约审计

用户要求「继续推进」，在两处 bug 修复后，对商家端单文件控制台 `index.html` 做了一次**全量契约对齐审计**（排查同类「前端读 A / 后端回 B」字段错配），结果如下。

### 审计结论：除已修两处外，无其余字段错配
- **今日概览**：`OverviewVO.today.{orderCount,salesAmount,completedAmount,avgAmount}`、`statusCounts`、`trend7d[].{date,salesAmount,orderCount}`、`topProducts[].{productName,qty,amount}` —— 控制台读取字段全部匹配 ✓。
- **订单生命周期**（接单/出餐/配送/完成/退款/核销）：走 `assertStore(orderId,...)`，用**订单自身** storeId 做权限校验，超管「全部门店」(null) 不受影响 ✓（与订单列表的 null 守卫是不同路径，已分别处理）。
- **SKU 库存保存、券模板启停**：字段匹配 ✓。
- **商品上下架 / 售罄**：已修 ✓。

### 非阻塞小问题（未报障，暂不修）
- `saveCoupon` 发出的 `validType` / `startTime` / `endTime` / `scopeIds` 不在 `CouponAdminRequest` 中，被 Jackson 忽略 → 券的「有效期类型(RANGE)/起止时间/适用商品范围」不会落库，仅 `validDays` 生效。属功能缺口、非崩溃，后续按需补 DTO 字段或前端改用后端支持字段。

---

## 本轮续（2026-09-03 晚 4）：新增「分类管理」功能

### 用户指令
「增加分类管理」——在商家后台增加商品分类的增删改查与启停管理能力。

### 模型与现状
- `category` 表为**全局分类**（无 `store_id`），seed 5 条：招牌推荐 / 鲜果茶 / 奶茶 / 冰淇淋 / 咖啡。
- 实体 `Category` 与 `CategoryMapper` 此前已存在，但前后端都**没有任何分类管理接口**；小程序菜单 `ProductService.getMenu` 已按 `status=1`+`sort` 读取分类。

### 改动
- **后端**
  - `RequestDTO` 新增 `CategoryAdminRequest`(id/name/icon/sort/status) 与 `CategoryStatusRequest`(status)。
  - `AdminService` 注入 `CategoryMapper`，新增 `categories()`(sort+id 升序)、`categoryUpsert()`(name 非空校验，insert 或 updateById)、`categorySetStatus()`(存在校验)、`categoryDelete()`(存在校验 + 删前查 `product` 表该分类下商品数，`>0` 则抛 `该分类下还有 N 个商品，无法删除`)。
  - `AdminController` 新增 `GET /api/admin/categories`、`POST /api/admin/category`、`POST /api/admin/category/{id}/status`、`DELETE /api/admin/category/{id}`。
- **控制台 UI**（`static/admin/index.html`）：MENU/VIEWS/state 接入「分类管理」；新增 `loadCategories`/`renderCategories`/`openCategoryModal`/`saveCategory`/`toggleCategory`/`deleteCategory` 及事件分发。表格列：名称/图标/排序/状态/操作（编辑、启停、删除）；删除带 `confirm` 二次确认。

### 构建
- 首编因漏 `import org.springframework.web.bind.annotation.DeleteMapping;`（`@DeleteMapping` 端点）编译失败 → 补 import 后 `./mvn.sh clean package -DskipTests` **BUILD SUCCESS**。重启 8080（当前 PID 16388）。

### 验证
- 分类 CRUD 生命周期（`_cat_check.py`）：5 分类 → 创建「测试分类X」→ 停用 → 改名「测试分类Y」→ 删除 → 回到原 5 分类，**PASS**。
- 删除守卫（`_cat_guard.py`）：DELETE 含 2 商品的分类 1 返回 `code=1001, msg="该分类下还有 2 个商品，无法删除"`，**PASS**。
- 控制台 UI 已暴露「分类管理」菜单（grep 命中 ≥ 5 处）。
- 本次续接复核：PID 16388 实例 `GET /api/admin/categories` 返回 `code=0, count=5`（招牌推荐/鲜果茶/奶茶/冰淇淋/咖啡），端点与界面均在线。

### 结论
分类管理已在商家后台「分类管理」菜单完整上线，支持新建/编辑/启停/删除（删除前校验商品数防误删），全局生效，小程序菜单按 `status=1` 自动展示。营销模块仍暂停。

---

## 本轮续（2026-09-03 晚 5）：新增「商品详情编辑」功能

### 用户指令
「增加详细的商品设置，更改商品的详细信息，例如甜度 杯型 冰度 图片 库存之类的」。

### 设计要点
- 数据模型本就支持全部维度：`product_sku`（杯型+价格+库存，-1=不限量）、`spec_group`/`spec_option`（SUGAR=甜度、TEMP=冰度/温度，含加价/默认项）、`topping`（小料）、`product.image/description`。缺的是后台读写接口与编辑 UI，本次补齐。
- **CUP 规格组由 SKU 派生**：保存时后端自动同步「杯型」选项与激活 SKU（label=cupSize、加价=差价、最低价为默认），保证下单校验（`resolveItems` 要求 CUP 选项 label 与 cup_size 一致）不被破坏；前端只编辑 SKU 行，不手编 CUP。
- 移除项一律**置 status=0 软删除**（保留历史订单快照引用），不物理删除。

### 改动
- **后端**：`RequestDTO` 新增 `ProductDetailAdminRequest`（含 SkuItem/AdminSpecGroup/AdminSpecOption/ToppingItem）；`MemberDTO` 新增 `AdminProductDetailVO` 嵌套 VO；`AdminService` 新增 `getProductDetail` / `saveProductDetail`（事务；`basePrice` 自动重算为最低激活 SKU 价）与 `syncCupSpecGroup`；`AdminController` 新增 `GET /api/admin/product/{id}/detail`、`POST /api/admin/product/detail`。
- **控制台**：商品列表每行新增「编辑详情」按钮；编辑弹窗分区展示——基础信息（名称/分类/状态/图片URL/卡路里/排序/描述）、杯型 SKU 行（增删、售价元输入、库存留空=不限量）、规格组（甜度/冰度等：选项增删、加价、默认项）、小料（增删、单价、最多份数、库存）；金额按元输入、提交自动转分。

### 验证
- BUILD SUCCESS（中途补了 SpecGroup/SpecOption/Topping 的 import 与 Mapper 注入后通过）；重启 8080。
- 脱敏往返脚本（商品 1「冰鲜柠檬水」）：改图片/描述/SKU 库存(-1→5)/糖度选项改名/小料改价/新增小料 → 回读 7 项全 PASS（含 basePrice 重算）；原样回存 → 还原检查通过；测试残留已清理，DB 核对回 seed。
- 用户端回归：`GET /api/store/1/menu` 正常（12 商品 / 5 分类 / 商品1 价格无误）。

### 已知边界
- MyBatis-Plus `updateById` 忽略 null 字段 + REST 不序列化 null：API 传 null 无法把字段清回 NULL；控制台发空串 `''` 可正常清空，实际使用无碍。

---

## 本轮续（2026-09-03 晚 6）：修复优惠券「固定有效期 / 适用范围」不落库

### 背景
此前控制台全量契约审计发现的非阻塞遗留：`saveCoupon` 发出的 `validType/startTime/endTime/scopeIds` 不在 `CouponAdminRequest` 中被忽略——券的「固定时间段有效期(RANGE)」与「指定分类/商品范围」保存后不生效，只有「领取后 N 天」可用。属既有业务功能缺口，本轮补齐。

### 根因（两层）
1. DTO 缺字段 + 保存逻辑未写这些列、未同步 `coupon_scope` 子表。
2. **领券侧同样没实现 RANGE**：`receive()/receiveInternal()` 只按 `validDays` 计算用户券到期时间，RANGE 券会被错误按「领取后 7 天」生效。

### 修复
- `CouponAdminRequest` 补 `validType/startTime/endTime/scopeIds`（时间以 String 接收再解析，兼容控制台的 `"yyyy-MM-dd HH:mm:ss"` 与 ISO 格式）。
- `AdminService.couponUpsert`：RANGE 强校验起止齐全且止>起（否则 `1001`）；`coupon_scope` 子表按 scopeType+scopeIds 全删重插；列表接口聚合子表回显 `scopeIds`。
- `CouponService.receive` 与 `receiveInternal`（积分兑换路径）：RANGE 券的用户券到期时间 = 模板 `endTime`。
- 控制台前端无需改动（表单本就发送全字段，此前只是后端丢弃）。

### 验证
- BUILD SUCCESS；重启 8080。
- 创建 RANGE + 指定分类(1,2) 测试券 → 后台列表回读 validType/起止时间/scopeIds 全 PASS → 用户领券后到期时间 = 模板 endTime PASS → RANGE 缺结束时间被拒（code=1001）PASS。
- 回归：券中心 6 模板 / 我的券 7 张（seed 口径），DAYS 券链路无回归；测试数据已清理。

---

## 本轮续（2026-09-03 晚 7）：商品「新建」能力 + 修复超管「全部门店」商品视图

### 新建商品（补齐商品管理闭环）
- 后端 `saveProductDetail` 支持无 id 新建：名称必填校验；先落库商品再挂 SKU/规格/小料；**自动上架到所有启用门店**（priceDiff=0）；`sales` 从 0 起算。
- 控制台：商品管理新增「+ 新建商品」按钮（复用详情弹窗空白模板）；编辑弹窗支持「+ 添加规格组」（新组可填编码如 SUGAR/TEMP，已有组编码只读防误改）与整组移除。
- 端到端验证：新建含 2 SKU + 糖度/温度规格组 + 小料的商品 → 详情/CUP 组自动同步（label=cupSize、差价、默认项）/basePrice 重算/菜单立即可见全部 PASS；空名被拒（1001）；测试数据已清理。

### 修复存量 bug：超管「全部门店」商品列表全显示已下架
- 根因：`products()` 对 store_product 无条件 `eq(storeId)`，null 时生成 `WHERE store_id = NULL` 恒不成立（与订单列表、在售开关同类的第 3 处 null 守卫缺失）。
- 修复：null 时查全部门店关系并聚合（任一店上架→onSale；全店售罄→storeSoldOut）；单店语义不变。
- 验证：全部门店视图商品正常显示上架，单店视图回归一致，菜单接口无回归。

---

## 本轮续（2026-09-03 晚 8）：商品级「全局售罄」开关

### 背景
菜单售罄判定为三源归一（商品级手动售罄 / 门店级售罄 / SKU 库存），但控制台此前只有门店级开关与库存编辑——**商品级 `product.sold_out` 无界面入口**，seed 中全局售罄的商品（脆筒冰淇淋）商家无法恢复可售。

### 改动
- 后端：`AdminProductVO` 增加 `manualSoldOut` 回显；新增 `POST /api/admin/product/{id}/manual-sold-out`（更新 product.sold_out）。
- 控制台：商品表格「售罄」列拆分为「全局售罄」「门店售罄」两个独立开关，语义清晰互不覆盖。

### 验证
- 列表回显正确（seed 商品9 全局售罄、商品1 正常）；商品1 开全局售罄 → 用户端菜单立即售罄 → 关闭 → 恢复可售，往返 PASS；控制台 UI 已上线。

---

## 本轮续（2026-09-03 晚 9）：全模块业务回归（当日收尾）

对今日 8 项改动（订单可见修复/在售开关/分类管理/商品详情编辑/券 RANGE 修复/商品新建/全部门店视图修复/全局售罄开关）之后的系统做全量回归：

- `smoke_business_full.py` 37 项：**35 OK + 2 WARN + 0 FAIL**（2 个 WARN 为重复 mock 身份的预期拦截：券限领 4005、今日已签到 4008，防重逻辑生效的证明）。
- 覆盖：公开接口、登录、购物车增删改查、优惠券（中心/领取/我的/积分兑换）、积分（记录/签到/概览）、地址、订单全生命周期（预览→下单→模拟支付→PAID→接单→出餐→核销→COMPLETED）、商家后台（登录/概览/订单列表/生命周期操作）。
- 结论：**既有业务链路全部正常，今日改动零回归**。
- 同步把当日踩坑沉淀为项目长期约定（MyBatis-Plus null 两大坑、AdminService import 红线、商品域结构约定等）。

---

## 本轮续（2026-09-03 晚 9:30）：修复小程序端「切换不了门店」

### 根因（铁证级）
`miniprogram/utils/store.js` 的 `switchTo()` 弹确认框时 `confirmText: '切换并清空'` 为 **5 个字，超出微信官方「confirmText 最多 4 个字符」硬限制** → `wx.showModal` 直接 fail、弹窗根本不显示；而等待确认的 Promise 只挂 `success`、无 `fail` 兜底 → **Promise 永久 pending**，切换流程整体挂死。表象：在门店页点其他门店毫无反应、无任何提示（点当前门店走免弹窗分支故正常）。

### 修复（纯小程序端，无需重建后端）
- `utils/store.js`：confirmText 改为 4 字「确认切换」；showModal 补 `fail` 兜底（弹窗失败降级为直接切换）+ 15s 防挂死超时；`setCurrent/clear` 同步 `globalData.currentStore`，消除 storage 与内存双源漂移的潜在 bug。
- `pages/store/store.js`：`onPick` 门店查找改字符串稳健比较防 dataset 类型陷阱；整体 try/catch，任何异常必 toast，杜绝再次「静默无反应」。

### 验证
- 后端接口实测：`/store/nearby`（3 家店 openNow=true）、`/store/{1,2,3}/menu` 全部正常——排除后端因素。
- Node 模拟微信运行时 harness（严格执行官方 4 字符 fail 规则）双向验证：旧代码复现「永久挂起」；新代码跨店切换/取消/同店 3 场景全 PASS，storage 与 globalData 同步一致。
- 教训沉淀：MEMORY.md 新增第 10 条——所有 showModal 必须 fail 兜底 + 按钮文案 ≤4 字。

---

## 本轮续（2026-09-03 晚 11:30）：修复真机演示「附近暂无门店」

### 根因
`config/index.js` 写死 `CURRENT_ENV='dev'`（baseUrl=`http://localhost:8080/api`）。**真机上 `localhost` 指向手机自身**，所有请求失败；门店页静默 catch 后只显示「附近暂无门店」。开发者工具正常（localhost 正确指向电脑），真机必挂。已排除：防火墙（java 规则已放行）、域名校验（urlCheck=false）、后端监听（0.0.0.0:8080，经 192.168.31.134 实测可达）。

### 修复（纯小程序端）
- `config/index.js`：平台自动判定环境——开发者工具走 localhost，真机自动走局域网 IP（`http://192.168.31.134:8080/api`，IP 变动需同步改）。
- `pages/store/store.js` + `wxml`/`wxss`：加载失败不再静默——错误态显示实际请求地址与三条排查指引 + 重试按钮。

### 验证
Node 模拟三平台环境判定全 PASS（devtools→localhost / android→LAN IP / 低版本回退→LAN IP）；语法检查通过。真机演示三要素：同一 WiFi + LAN_HOST 为电脑当前 IP + 勾选不校验合法域名。
