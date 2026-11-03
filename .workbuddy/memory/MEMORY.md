# 安心饮(Anxineat) 项目长期约定

## 技术栈
- 前端：原生微信小程序；后端：Spring Boot 3 + MyBatis-Plus 3 + MySQL 8（Java 21）。
- 金额以「分」存 long；折扣用「万分比」(10000=不打折)。前端只展示不计算，服务端重算。
- 并发安全用 DB CAS + 唯一索引（乐观锁 `version`）+ 唯一 `biz_no`，不依赖 Redis。

## 关键约定（踩坑沉淀）
1. **微信登录双模（后端驱动自动切换）**：`/auth/login` 的 `code` 以 `mock:` 前缀 → 本地联调（后缀当 openid）；真实 `wx.login` code 在配置了 `WX_MINIAPP_APPID` / `WX_MINIAPP_SECRET`（application.yml `anxineat.wx.miniapp.*`）后走微信 `code2Session` 换 openid。
   - 前端 `doLogin()` **先读免鉴权的 `/api/config/client` 的 `wxRealLogin` 字段**：为 true 才调 `wx.login()`，为 false 则用 `mock:openid-demo-0001`。后端由 `UserService.isRealWxLoginEnabled()` 判断 appid+appsecret 是否非空。
   - **切勿写死只调 `wx.login()`**：未配凭证时 `wx.login` 仍会静默成功、`code2Session` 失败返回 1001，而 `request()` 把登录失败统一 toast 成「登录已过期，请重试」——表象是「无任何登录弹窗 + 登录已过期」，极难定位。
   - 登录失败时 `request()` 会透出后端真实 message（toast + `console.error`），便于排障。
2. **分页契约**：后端列表接口返回原生 MyBatis-Plus `Page`，JSON 字段为 `records` / `current` / `pages`。小程序必须读 `records`/`current`，**切勿反向把后端改成 `list`/`page`**。
   - **商家后台单文件控制台 `index.html` 也必须读 `records`/`current`**：原 `loadOrders` 误读 `data.list` / `data.page`（实际字段是 `records`/`current`），导致订单列表永远渲染空数组——这是「商家端看不到订单」的真正根因之一（另一根是 SQL 缺 null 守卫）。`index.html:1026` 已改为 `data.records`、`index.html:1039` 已改为 `data.current`。products/coupons 返回 `R<List>`（data 即数组），不受影响。
   - 注意：`request()` 助手（index.html:688）会丢弃空串参数，故「全部门店」(`state.storeId=''`) 实际不发 `storeId`，后端收到 `null`——`OrderMapper.selectStoreOrders` 等 6 个查询已加 `<where><if test="storeId != null">` 守卫，使 `null`=查全部门店。
3. **MyBatis-Plus null 两大坑**（已 3 次踩中，新写查询/更新必查）：
   - **`eq(field, null)` 生成 `WHERE field = NULL` 恒不成立**（不报错、静默 0 行）：任何「超管全部门店 storeId=null」路径必须先判空——null 时去掉条件或 `selectList(null)`；MyBatis XML 用 `<if test="storeId != null">` 守卫。已修复 3 处：OrderMapper 6 查询、setOnSale/setSoldOut、products() 的 store_product 查询。
   - **`updateById` 忽略 null 字段**（默认 field-strategy NOT_NULL）+ REST JSON 不序列化 null：传 null 无法把列清回 NULL；传空串 `''` 可清空（非 null 会更新）。控制台清空字段一律发空串。
4. **构建/运行**：改后端源码必须 `./mvn.sh clean package -DskipTests` 重建 `target/anxineat-backend.jar`；仅 `compile` 不生效。旧 jar 被运行进程占用会导致 `clean` 失败，需先停该后台任务再构建。
   - **启动必须显式带端口**：`SERVER_PORT=8080 java -jar target/anxineat-backend.jar`（用 `run_in_background`；不要 `&`，否则 JVM 随父 shell 退出被杀）。不带 `SERVER_PORT` 时会落到随机端口（曾撞到 58899 报 "Port already in use" 而启动失败），而小程序 `config/index.js` 固定连 `localhost:8080`，表现为「后端其实没起来、全部接口无响应」。
   - 排查端口：`netstat -ano | grep ":8080"` → `tasklist /FI "PID eq <pid>"`。Git Bash 下杀进程用 `MSYS_NO_PATHCONV=1 taskkill /F /PID <pid>`（`//F` 写法在本机报“无效参数”；**管道里 `xargs -I{} MSYS_NO_PATHCONV=1 ...` 不生效**，env 前缀会被当命令，先存 PID 变量）。
   - **AdminService 新增实体操作先补 import + @RequiredArgsConstructor 注入字段**：已两次因漏 import（DeleteMapping、SpecGroup/SpecOption/Topping 及 Mapper）编译失败，报「找不到符号」直接指到行号。
5. **默认账号**：超管 `admin/admin123`；store 1 = 安心饮·万达广场店；商品 1 = 冰鲜柠檬水(400分)，sku1 中杯（库存 `-1`=不限量哨兵）。user 表列是 `open_id`（非 `openid`）。
6. **无限库存哨兵**：`product_sku.stock=-1` 表示不限量；`deductStock` 用 `IF(stock=-1,-1,stock-#{qty})` 保护，勿直接 `stock-1`。
7. **本地联调便利**：手机号登录验证码固定 1234（dev-mode，无短信网关）；支付为 MOCK；营业时间曾临时置 24h 便于凌晨测试，交付前应改回正常时段或靠 `business_status`。
8. **商品域结构约定**（2026-09-03 定型）：`category` 全局无 store_id；CUP 规格组由 SKU 派生（`AdminService.syncCupSpecGroup`：label=cupSize、extraPrice=差价、最低价为默认），**切勿让前端手编 CUP**（`resolveItems` 强校验 CUP 选项 label 与 cup_size 一致）；移除 SKU/规格项/小料一律软删 status=0（保订单快照引用）；新建商品自动挂所有启用门店；`basePrice` 保存时重算=最低激活 SKU 价。
9. **验证脚本红线**：中途抛错会留下「已修改未还原」状态——重跑前先 SQL 还原 seed；脚本不得打印 token/密码（触发敏感信息拦截）。
10. **wx.showModal 的 confirmText/cancelText 硬限 4 个字符**（官方文档约束）：超长时接口**直接 fail、弹窗不显示**——若 Promise 只挂 success 无 fail 兜底，将永远 pending，整条流程挂死且无任何提示（2026-09-03「切换不了门店」根因：store.js confirmText『切换并清空』5 字超限）。规则：所有 showModal 必须显式 fail 兜底 + confirmText ≤4 字（『确认切换』/『去购物车』/『查看订单』均合规）；排查小程序「点了没反应」先 grep showModal 的按钮文案长度。小程序当前门店以 `utils/store.js`（storage）为单一事实源，`setCurrent/clear` 已同步 `globalData.currentStore`，新页面读门店一律走 `storeUtil.getCurrent()`。
11. **真机调试 = 换网络环境**：`config/index.js` 已实现平台自动判定（`wx.getDeviceInfo().platform==='devtools'` → localhost；真机 → `lan`）。真机连不上后端 = ① LAN_HOST 不是电脑当前 IP（DHCP 会变，ipconfig 查后同步改 config）② 手机与电脑不同 WiFi ③ 域名校验未关（urlCheck=false 已配）。**页面加载失败禁止静默 catch**——store 页已落「错误态 + 重试 + 显示实际 baseUrl」模式，新列表页照抄。后端监听 0.0.0.0、java 防火墙规则已放行。

## 待产品确认（营销活动模块）
- `docs/营销活动模块实现方案.md` 已出草案；§8 列 7 条开放问题（秒杀库存独立性/限购/口令一次性/0元兑换门槛与免单履约形态/叠加互斥/权限/审核流）待拍板后才编码（Task #10）。

## 部署/生产化（未做）
- 短信验证码、对象存储、Docker 部署均待做，需用户提供凭证/环境。
- **真实微信支付 JSAPI：代码已实现（2026-09-03）**。`PaymentService` 已接 V3 `/v3/pay/transactions/jsapi`（商户私钥 SHA256withRSA 签名 Authorization 头）+ 对 `wx.requestPayment` 参数做 `paySign` 二次签名；回调 `POST /api/pay/wechat/notify`（已在 `WebConfig` 免鉴权）用 api-v3-key 解密 AES-256-GCM 报文后走同一幂等闸门 `tryMarkSuccess`。
  尚缺**企业商户凭证**才能联调：`WECHAT_APP_ID` / `WECHAT_MCH_ID` / `WECHAT_API_V3_KEY` / `WECHAT_MCH_SERIAL_NO` / `WECHAT_PRIVATE_KEY_PATH` / `WECHAT_NOTIFY_URL`，并把 `anxineat.pay.wechat.enabled` 置 true、`sys_config.pay.channel` 改为 `WECHAT_JSAPI`。
  前端 `payment.js/wxml` 已按 `pay.mock` 分流：真实渠道拉 `wx.requestPayment` 并轮询 `payQuery`，MOCK 渠道保留原三个模拟按钮。
