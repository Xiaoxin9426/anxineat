# 安心饮点餐小程序 —— 前后端接口契约

> 本文件是前端（原生微信小程序）与后端（Spring Boot 3）之间的**唯一权威契约**。
> 后端实现与前端调用均以此为准。任何接口变更必须先改本文件。
>
> 基础路径：`http://<host>:8080/api`
> 统一响应：`{ code, message, data, timestamp }`，`code === 0` 表示成功，非 0 为业务错误码。

## 通用约定

- **所有金额字段单位均为【分】（整数）**。前端展示时除以 100，禁止在前端做任何金额计算与四舍五入。
- 需要登录的接口在请求头携带 `Authorization: Bearer <token>`。
- 分页参数统一为 `page`（从 1 开始）与 `size`（默认 10），分页响应体为 `{ list, total, page, size, pages }`。
- 时间字段格式为 `yyyy-MM-dd HH:mm:ss`。

### 错误码

| code | 含义 | 前端处理建议 |
|---|---|---|
| 0 | 成功 | — |
| 1000 | 参数错误 | toast 提示 message |
| 1002 | 未登录 / token 失效 | 清空本地 token，跳转登录 |
| 1003 | 无权限 | toast |
| 1004 | 数据不存在 | toast |
| 1005 | 系统繁忙 | toast，可重试 |
| 2001 | 门店休息中 | 门店卡片置灰，禁止下单 |
| 2002 | 门店不支持该渠道 | 切换下单方式或换店 |
| 2005 | 商品已售罄 | 商品卡置灰，禁止加购 |
| 2009 | 库存不足 | 提示并刷新菜单 |
| 2008 | 规格未选全 | 详情页高亮未选规格组 |
| 2011 | 未达起送金额 | 结算页提示差额 |
| 2012 | 请先选择收货地址 | 跳转地址页 |
| 3002 | 订单状态不允许此操作 | 刷新订单详情 |
| 3003 | 订单已超时 | 提示重新下单 |
| 3004 | 金额已变化 | 刷新结算页 |
| 3008 / 3009 | 取餐码无效 / 已核销 | 后台核销页提示 |
| 4002 | 优惠券不可用 | 券列表置灰并说明原因 |
| 4007 | 积分不足 | 提示 |
| 4008 | 今日已签到 | 按钮置为「已签到」 |
| 4010 | 无权操作其他门店数据 | 后台提示 |

---

## 一、公开接口（免鉴权）

### `GET /api/health`
返回 `"ok"`，用于前端探测后端可用性与地址配置是否正确。

### `POST /api/auth/login`
微信小程序登录。支持两种模式：

```jsonc
// 模式 A：微信静默登录（推荐，成本最低）
{ "code": "mock:openid-demo-0001", "nickname": "小明", "avatar": "" }

// 模式 B：手机号验证码登录（dev 模式下验证码固定为 1234）
{ "phone": "13800000001", "verifyCode": "1234" }
```

响应：
```jsonc
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": 1, "nickname": "小明", "avatar": "", "phone": "13800000001",
    "levelName": "普通会员", "levelDiscount": 10000,
    "pointBalance": 500, "totalAmount": 0,
    "nextLevelName": "银卡会员", "nextLevelMinAmount": 20000
  }
}
```

> 说明：`code` 字段在真实环境下由 `wx.login()` 获取后交给后端做 code2session。
> 本地联调时后端未接微信服务器，约定 `code` 以 `mock:` 前缀开头时直接将其后缀作为 openid。

### `GET /api/store/nearby`
参数：`lat`、`lng`（可选，用于算距离）、`city`（可选）、`keyword`（可选）

响应（按距离升序，无坐标时按 sort）：
```jsonc
[{
  "id": 1, "name": "安心饮·万达广场店", "address": "中原区万达广场 1F-102",
  "lng": 113.6254, "lat": 34.7466, "city": "郑州市", "phone": "0371-10000001",
  "businessStatus": 1,          // 1 营业中 0 休息中（手动总开关）
  "supportPickup": 1, "supportDelivery": 1,
  "deliveryFee": 500,           // 分
  "minDeliveryAmount": 1500,    // 分，起送价
  "freeDeliveryThreshold": 3000,// 分，满此金额免配送费
  "pickupLeadMinutes": 10,
  "distanceKm": 1.2,
  "openNow": true,              // 结合 businessStatus 与营业时段实时计算
  "todayHours": "10:00-22:00",
  "closedReason": ""            // openNow=false 时的原因文案
}]
```

### `GET /api/store/{id}`
单个门店详情，额外包含 `businessHours` 数组：
```jsonc
{ "...门店字段同上", "businessHours": [{ "dayOfWeek": 1, "openTime": "10:00:00", "closeTime": "22:00:00", "crossDay": 0 }] }
```

### `GET /api/store/{id}/menu`
门店菜单。已按售罄规则归一计算好 `available` 与 `soldOutReason`，前端**不需要**自行判断售罄。

```jsonc
{
  "storeId": 1,
  "categories": [{ "id": 1, "name": "招牌推荐", "icon": "star", "sort": 1 }],
  "products": [{
    "id": 1, "categoryId": 1, "name": "冰鲜柠檬水",
    "description": "鲜切柠檬片搭配茉莉绿茶，酸甜清爽",
    "image": "",                 // 为空时前端渲染占位图
    "basePrice": 400, "storePrice": 400,
    "calories": 120, "sales": 18230,
    "soldOut": false,            // 是否售罄
    "available": true,           // 综合可售判定
    "soldOutReason": "",         // 售罄时："已售罄" / "门店暂不售卖" / "已下架"
    "sort": 1,
    "skus": [{ "id": 1, "cupSize": "中杯", "price": 400, "stock": -1, "inStock": true }]
  }]
}
```

### `GET /api/product/{id}?storeId=1`
商品详情，含规格组、小料、SKU：
```jsonc
{
  "id": 1, "name": "冰鲜柠檬水", "description": "...", "image": "",
  "storePrice": 400, "calories": 120, "sales": 18230,
  "available": true, "soldOutReason": "",
  "specGroups": [{
    "id": 1, "code": "CUP", "name": "杯型", "isRequired": 1, "sort": 1,
    "options": [{ "id": 1, "label": "中杯", "extraPrice": 0, "isDefault": 1, "sort": 1 }]
  }],
  "toppings": [{ "id": 1, "name": "椰果", "price": 100, "maxCount": 2, "stock": -1 }],
  "skus": [{ "id": 1, "cupSize": "中杯", "price": 400, "stock": -1, "inStock": true }]
}
```

> **重要**：`specGroups` 中 `code === "CUP"` 的组为库存维度组，用户选中该项后，
> 前端必须把该选项的 `label` 与 `skus` 中的 `cupSize` 匹配，用匹配到的 `skuId` 加入购物车。

### `GET /api/banner/list`
```jsonc
[{ "id": 1, "title": "新客立减 3 元", "image": "", "linkType": "COUPON", "linkValue": "1", "sort": 1 }]
```
`linkType`：`NONE` / `PRODUCT` / `COUPON` / `POINT` / `PAGE`

### `GET /api/config/client`
```jsonc
{
  "payExpireMinutes": 15,
  "payChannel": "MOCK",
  "devMode": true,
  "pointRules": {
    "perYuan": 1, "signinAmount": 10,
    "centsPerPoint": 1, "minPoint": 100, "maxPercent": 50
  }
}
```

---

## 二、购物车（需登录）

### `GET /api/cart?storeId=1`
```jsonc
{
  "storeId": 1,
  "items": [{
    "id": 12, "productId": 1, "productName": "冰鲜柠檬水", "productImage": "",
    "skuId": 1, "skuName": "中杯", "quantity": 2,
    "basePrice": 400, "unitPrice": 500,      // unitPrice 含规格加价与小料
    "specSnapshot": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":1,\"label\":\"中杯\",\"extraPrice\":0}]",
    "toppingSnapshot": "[{\"toppingId\":1,\"name\":\"椰果\",\"price\":100,\"count\":1}]",
    "available": true, "unavailableReason": "", "maxQuantity": 99
  }],
  "totalQuantity": 2,
  "totalAmount": 1000
}
```
> `specSnapshot` / `toppingSnapshot` 是 **JSON 字符串**，前端需 `JSON.parse` 后展示。

### `POST /api/cart/add`
```jsonc
{ "storeId": 1, "productId": 1, "skuId": 1, "quantity": 1,
  "specOptionIds": [1, 2, 3],
  "toppings": [{ "toppingId": 1, "count": 1 }] }
```
响应：更新后的购物车（同 `GET /api/cart`）。
同规格（同 itemKey）自动累加数量，异规格分立为不同条目。

### `PUT /api/cart/{itemId}`
`{ "quantity": 3 }`，`quantity <= 0` 表示删除。

### `DELETE /api/cart/{itemId}`
### `DELETE /api/cart/clear?storeId=1`

---

## 三、下单与支付（需登录）

### `POST /api/order/preview` —— 金额试算
```jsonc
{
  "storeId": 1,
  "orderType": "PICKUP",      // PICKUP | DELIVERY
  "fromCart": true,           // true 表示使用服务端购物车；false 时必传 items
  "items": [                  // fromCart=false 时必填
    { "productId": 1, "skuId": 1, "quantity": 2,
      "specOptionIds": [1,2,3],
      "toppings": [{ "toppingId": 1, "count": 1 }] }
  ],
  "userCouponId": 5,          // 可选
  "usePoint": true,           // 可选
  "addressId": 3              // DELIVERY 时必填
}
```

响应：
```jsonc
{
  "goodsAmount": 2000,          // 商品总额（分）
  "levelDiscountAmount": 0,     // 会员等级折扣
  "couponAmount": 300,          // 优惠券抵扣
  "pointAmount": 100,           // 积分抵现金额
  "pointUsed": 100,             // 消耗积分
  "discountAmount": 400,        // 优惠合计
  "deliveryFee": 0,             // 配送费
  "payAmount": 1600,            // 应收 = goodsAmount - discountAmount + deliveryFee
  "items": [{
    "productId": 1, "productName": "冰鲜柠檬水", "productImage": "",
    "skuId": 1, "skuName": "中杯", "quantity": 2,
    "unitPrice": 500, "totalPrice": 1000,
    "specText": "中杯 / 五分糖 / 少冰",
    "toppingText": "椰果 x1"
  }],
  "availableCoupons": [{
    "userCouponId": 5, "name": "满 15 减 3", "type": "FULL_REDUCTION",
    "amount": 300, "minAmount": 1500, "discount": 10000, "maxDiscount": 0,
    "endTime": "2026-09-16 22:00:00",
    "usable": true, "unusableReason": "", "deductAmount": 300
  }],
  "bestCouponId": 5,
  "pointAvailable": 500,        // 用户当前积分
  "pointUsable": 100,           // 本单最多可用积分（已考虑上限与余额）
  "pointDeductAmount": 100      // 使用 pointUsable 时可抵扣金额
}
```

### `POST /api/order` —— 提交订单
请求在 preview 基础上增加：
```jsonc
{ "...preview 全部字段", "remark": "不要糖", "expectTime": "2026-09-02 23:00:00",
  "idempotentKey": "客户端生成的唯一串" }
```
响应：
```jsonc
{ "orderId": 88, "orderNo": "20260902223300123456", "payNo": "PAY...",
  "payAmount": 1600, "expireTime": "2026-09-02 22:48:00", "pickupCode": "012" }
```
> `idempotentKey` 由客户端生成（建议 UUID），重复提交同一 key 会返回首次创建的订单，绝不会产生第二笔。

### `POST /api/order/{id}/cancel`
仅 `PENDING_PAYMENT` 可取消。释放券占用与库存。

### `GET /api/order/list?status=ONGOING&page=1&size=10`
`status` 支持分组名或具体状态（可逗号分隔多个具体状态）：
- `UNPAID` → PENDING_PAYMENT
- `ONGOING` → PAID, MAKING, READY, DELIVERING
- `DONE` → COMPLETED, CANCELLED, REFUNDED
- 也可直接传 `PAID`、`COMPLETED` 等

响应为分页对象，`data.list` 中每项为订单摘要（含 `items`）。

### `GET /api/order/{id}`
```jsonc
{
  "id": 88, "orderNo": "...", "storeId": 1, "storeName": "安心饮·万达广场店",
  "orderType": "PICKUP", "status": "READY", "statusLabel": "待取餐",
  "totalAmount": 2000, "discountAmount": 400, "couponAmount": 300,
  "pointAmount": 100, "pointUsed": 100, "levelDiscountAmount": 0,
  "deliveryFee": 0, "payAmount": 1600,
  "pickupCode": "012", "pickupDate": "2026-09-02",
  "addressSnapshot": null, "expectTime": null, "remark": "",
  "expireTime": "...", "paidAt": "...", "createdAt": "...",
  "items": [{ "...同 preview.items", "specSnapshot": "...", "toppingSnapshot": "..." }],
  "statusLog": [
    { "fromStatus": null, "toStatus": "PENDING_PAYMENT", "operator": "USER", "createdAt": "..." },
    { "fromStatus": "PENDING_PAYMENT", "toStatus": "PAID", "operator": "SYSTEM", "createdAt": "..." }
  ],
  "canCancel": false, "canPay": false
}
```

### `POST /api/order/{id}/reorder` —— 再来一单
按历史订单重建购物车。已售罄商品跳过并返回原因。
```jsonc
{ "addedCount": 3, "skipped": [{ "productName": "脆筒冰淇淋", "reason": "商品已售罄" }] }
```

### `POST /api/pay/create`
`{ "orderId": 88 }` →
```jsonc
{ "payNo": "PAY...", "orderId": 88, "amount": 1600, "channel": "MOCK",
  "expireTime": "...", "mock": true, "prepay": { "note": "模拟支付，无需真实拉起微信支付" } }
```

### `POST /api/pay/mock/callback` —— 模拟支付回调（仅 MOCK 渠道）
```jsonc
{ "payNo": "PAY...", "result": "SUCCESS", "duplicate": false }
```
- `result`: `SUCCESS` | `FAIL`
- `duplicate: true` 时后端会**连续处理两次回调**，用于验证幂等（订单状态只应变更一次）
响应：
```jsonc
{ "handled": true, "duplicateHandled": false, "orderId": 88, "orderStatus": "PAID",
  "pickupCode": "012", "message": "支付成功" }
```

### `POST /api/order/{id}/pay/query` —— 主动查支付结果（兜底）
用于「用户已扣款但回调丢失」的场景。返回同 mock/callback 结构。

---

## 四、会员、积分、券、地址（需登录）

### `GET /api/user/profile`
同登录响应的 `user` 字段，外加 `memberLevel` 明细与 `nextLevel` 进度。

### `POST /api/user/bind-phone`
`{ "phone": "...", "verifyCode": "1234" }` → 返回更新后的 user

### `GET /api/user/point/records?page=1&size=20`
```jsonc
{ "list": [{ "id": 1, "type": "EARN_CONSUME", "typeLabel": "消费赠送",
             "amount": 100, "balanceAfter": 600, "remark": "订单 ...", "createdAt": "..." }],
  "total": 10, "page": 1, "size": 20, "pages": 1 }
```

### `POST /api/user/signin`
```jsonc
{ "awarded": 10, "balance": 510, "date": "2026-09-02" }
```
重复签到返回 `code=4008`。

### `GET /api/coupon/center` —— 领券中心
```jsonc
[{ "templateId": 1, "name": "新客无门槛 3 元券", "type": "NO_THRESHOLD",
   "amount": 300, "minAmount": 0, "discount": 10000, "maxDiscount": 0,
   "scopeType": "ALL", "channels": "PICKUP,DELIVERY",
   "validDays": 7, "pointCost": 300,
   "receivedCount": 1, "limitPerUser": 1,
   "canReceive": false, "receiveDisabledReason": "已达领取上限" }]
```

### `POST /api/coupon/receive/{templateId}`
```jsonc
{ "userCouponId": 77, "endTime": "2026-09-09 22:00:00" }
```

### `GET /api/coupon/mine?status=UNUSED`
`status`：`UNUSED` / `USED` / `EXPIRED` / 空（全部，含 LOCKED）
```jsonc
[{ "id": 77, "couponId": 1, "name": "新客无门槛 3 元券", "type": "NO_THRESHOLD",
   "amount": 300, "minAmount": 0, "discount": 10000, "maxDiscount": 0,
   "scopeType": "ALL", "channels": "PICKUP,DELIVERY",
   "status": "UNUSED", "statusLabel": "未使用",
   "startTime": "...", "endTime": "..." }]
```

### `GET /api/coupon/exchange/list` —— 积分可兑换券
`[{ "templateId": 1, "name": "...", "pointCost": 300, "type": "...", "amount": 300, "canExchange": true, "reason": "" }]`

### `POST /api/point/exchange`
`{ "templateId": 1 }` → `{ "userCouponId": 78, "pointCost": 300, "balance": 200 }`

### 地址
- `GET /api/address/list`
- `POST /api/address` `{ name, phone, province, city, district, detail, tag, isDefault }`
- `PUT /api/address/{id}` 同上
- `DELETE /api/address/{id}`
- `POST /api/address/{id}/default`

地址对象：`{ id, name, phone, province, city, district, detail, tag, isDefault }`

---

## 五、商家后台（需管理员 token）

登录：`POST /api/admin/login` `{ username, password }` → `{ token, admin: { id, username, realName, role, storeId } }`

> `role`: `SUPER_ADMIN`（可跨店，`storeId` 为 null）/ `CLERK`（仅本店）。
> 店员调用任何带 `storeId` 的接口时，后端强制覆盖为其绑定门店，越权返回 `code=4010`。

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/admin/overview?storeId=` | GET | 今日概览：`{ today: { orderCount, salesAmount, completedAmount, avgAmount }, statusCounts: { PENDING_PAYMENT: 2, PAID: 1, ... } }` |
| `/api/admin/orders?storeId=&status=&keyword=&page=&size=` | GET | 订单分页（keyword 匹配订单号或取餐码） |
| `/api/admin/order/{id}/accept` | POST | 接单 → MAKING |
| `/api/admin/order/{id}/ready` | POST | 出餐 → READY |
| `/api/admin/order/{id}/delivering` | POST | 开始配送 → DELIVERING（仅外卖） |
| `/api/admin/order/{id}/complete` | POST | 完成（自取出餐/外卖送达）→ COMPLETED |
| `/api/admin/order/{id}/refund` | POST | 退款 `{ reason }` → REFUNDED，回补库存与券 |
| `/api/admin/order/redeem` | POST | 核销取餐码 `{ pickupCode }` |
| `/api/admin/stores` | GET | 门店列表（超管全部，店员仅本店） |
| `/api/admin/store/{id}` | PUT | 修改营业状态、渠道开关、配送费、起送价 |
| `/api/admin/products?storeId=&categoryId=&keyword=` | GET | 商品列表，含 `onSale`（本店是否上架）、`storeSoldOut`、SKU 库存 |
| `/api/admin/product` | POST | 新建/更新商品（带 id 为更新） |
| `/api/admin/product/{id}/on-sale` | POST | `{ storeId, onSale }` 本店上下架 |
| `/api/admin/product/{id}/sold-out` | POST | `{ storeId, soldOut }` 本店售罄开关 |
| `/api/admin/sku/{id}/stock` | POST | `{ stock }` 设置库存（-1 为不限量） |
| `/api/admin/coupons` | GET | 券模板列表 |
| `/api/admin/coupon` | POST | 新建/更新券模板 |
| `/api/admin/coupon/{id}/status` | POST | `{ status }` 停用/启用 |
| `/api/admin/configs` | GET | 系统配置列表 |
| `/api/admin/config/{key}` | PUT | `{ value }` 修改配置 |

---

## 六、订单状态机（前端渲染依据）

| 状态 | 中文 | 用户端展示 | 可流转到 |
|---|---|---|---|
| `PENDING_PAYMENT` | 待支付 | 显示「去支付」倒计时 | PAID、CANCELLED |
| `PAID` | 已支付 | 「已支付，等待门店接单」 | MAKING、CANCELLED、REFUNDED |
| `MAKING` | 制作中 | 「门店制作中」 | READY、REFUNDED |
| `READY` | 待取餐 | 自取显示取餐码；外卖显示「待配送」 | COMPLETED、DELIVERING、REFUNDED |
| `DELIVERING` | 配送中 | 「骑手配送中」 | COMPLETED、REFUNDED |
| `COMPLETED` | 已完成 | 显示「再来一单」 | 终态 |
| `CANCELLED` | 已取消 | 显示「再来一单」 | 终态 |
| `REFUNDED` | 已退款 | 显示退款说明 | 终态 |

「我的订单」页签分组：`UNPAID` / `ONGOING` / `DONE`，映射见 `GET /api/order/list`。
