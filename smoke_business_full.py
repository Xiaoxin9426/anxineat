#!/usr/bin/env python3
# 全模块业务回归（脱敏）：覆盖所有业务域，不打印 token/密码。
# 判定：HTTP 200 且业务 code 为 0/"0"/None 且无 error -> OK；其余 FAIL/WARN。
import json, urllib.request, urllib.error, time

BASE = "http://127.0.0.1:8080"

def call(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        q = "&".join(f"{k}={v}" for k, v in params.items())
        url += ("&" if "?" in url else "?") + q
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:200]}
    except Exception as e:
        return -1, {"error": str(e)}

results = []
def ok(label, status, resp, extra=""):
    c = resp.get("code")
    good = status == 200 and (c in (0, "0", None)) and "error" not in resp
    mark = "OK " if good else ("WARN" if status == 200 else "FAIL")
    print(f"[{mark}] {label} -> HTTP {status} code={c} {extra}")
    results.append((label, mark))
    return good, resp

print("########## 公开接口 ##########")
s, r = call("GET", "/api/health");                                  ok("health", s, r)
s, r = call("GET", "/api/config/client");                           ok("config/client", s, r, extra=f"wxRealLogin={(r.get('data') or {}).get('wxRealLogin')}")
s, r = call("GET", "/api/store/nearby");                            ok("store/nearby", s, r, extra=f"cnt={len(r.get('data') or [])}")
s, r = call("GET", "/api/store/1");                                 ok("store/1", s, r)
s, r = call("GET", "/api/store/1/menu");                            ok("store/1/menu", s, r)
s, r = call("GET", "/api/product/1", params={"storeId": 1});        ok("product/1", s, r)
s, r = call("GET", "/api/banner/list");                             ok("banner/list", s, r, extra=f"cnt={len(r.get('data') or [])}")

print("\n########## 用户登录 ##########")
s, r = call("POST", "/api/auth/login", {"code": "mock:smoke_full_1", "nickname": "FullSmoke"})
user_token = (r.get("data") or {}).get("token")
ok("auth/login", s, r, extra=("token:yes" if user_token else "token:NO"))

print("\n########## 购物车 ##########")
s, r = call("POST", "/api/cart/add", {"storeId": 1, "productId": 1, "skuId": 1, "quantity": 1, "specOptionIds": [1, 25, 62]}, token=user_token)
ok("cart/add", s, r)
s, r = call("GET", "/api/cart", params={"storeId": 1}, token=user_token)
cart_items = (r.get("data") or {}).get("items") or (r.get("data") or [])
ok("cart/list", s, r, extra=f"cnt={len(cart_items)}")
if cart_items:
    cid = cart_items[0].get("id") or cart_items[0].get("itemId")
    s, r = call("PUT", f"/api/cart/{cid}", {"quantity": 2}, token=user_token)
    ok("cart/update", s, r)
    s, r = call("DELETE", f"/api/cart/{cid}", token=user_token)
    ok("cart/delete", s, r)

print("\n########## 优惠券 ##########")
s, r = call("GET", "/api/coupon/center", token=user_token)
center = r.get("data") or []
tpl_id = None
if isinstance(center, list) and center:
    tpl_id = center[0].get("id") or center[0].get("templateId")
elif isinstance(center, dict):
    lst = center.get("list") or []
    if lst: tpl_id = lst[0].get("id") or lst[0].get("templateId")
ok("coupon/center", s, r, extra=f"cnt={len(center) if isinstance(center,list) else '?'}")
if tpl_id:
    s, r = call("POST", f"/api/coupon/receive/{tpl_id}", token=user_token)
    ok("coupon/receive", s, r)
else:
    print("[WARN] coupon/receive 跳过：券中心无可用模板（可能 seed 未含券模板）")
s, r = call("GET", "/api/coupon/mine", token=user_token)
ok("coupon/mine", s, r, extra=f"cnt={len(r.get('data') or [])}")
s, r = call("GET", "/api/coupon/exchange/list", token=user_token)
ok("coupon/exchange/list", s, r)

print("\n########## 积分 / 签到 / 资料 ##########")
s, r = call("GET", "/api/user/point/records", params={"page": 1, "size": 10}, token=user_token)
ok("user/point/records", s, r, extra=f"cnt={len((r.get('data') or {}).get('records') or (r.get('data') or []))}")
s, r = call("POST", "/api/user/signin", token=user_token)
ok("user/signin", s, r)
s, r = call("GET", "/api/user/profile", token=user_token)
prof = r.get("data") or {}
ok("user/profile", s, r, extra=f"points={prof.get('points')} level={prof.get('level')}")

print("\n########## 地址 ##########")
addr = {"name": "测试地址", "phone": "13800000000", "province": "省", "city": "市", "district": "区", "detail": "某路1号", "isDefault": 1}
s, r = call("POST", "/api/address", addr, token=user_token)
addr_id = (r.get("data") or {}).get("id")
ok("address/add", s, r, extra=f"id={addr_id}")
s, r = call("GET", "/api/address/list", token=user_token)
ok("address/list", s, r, extra=f"cnt={len(r.get('data') or [])}")
if addr_id:
    s, r = call("POST", f"/api/address/{addr_id}/default", token=user_token)
    ok("address/default", s, r)
    s, r = call("DELETE", f"/api/address/{addr_id}", token=user_token)
    ok("address/delete", s, r)

print("\n########## 订单全生命周期 ##########")
s, r = call("POST", "/api/order/preview", {"storeId": 1, "orderType": "PICKUP", "fromCart": False, "items": [{"productId": 1, "skuId": 1, "quantity": 1, "specOptionIds": [1, 25, 62]}]}, token=user_token)
prev = r.get("data") or {}
ok("order/preview", s, r, extra=f"pay={prev.get('payAmount')}")
s, r = call("POST", "/api/order", {"storeId": 1, "orderType": "PICKUP", "fromCart": False, "items": [{"productId": 1, "skuId": 1, "quantity": 1, "specOptionIds": [1, 25, 62]}], "idempotentKey": "full-" + str(int(time.time()))}, token=user_token)
sub = r.get("data") or {}
oid = sub.get("orderId"); pay_no = sub.get("payNo")
ok("order/create", s, r, extra=f"orderId={oid} payNo={'yes' if pay_no else 'NO'}")
if oid:
    s, r = call("GET", "/api/order/list", params={"page": 1, "size": 10}, token=user_token)
    ok("order/list", s, r, extra=f"cnt={len((r.get('data') or {}).get('records') or (r.get('data') or []))}")
    s, r = call("GET", f"/api/order/{oid}", token=user_token)
    ok("order/detail", s, r, extra=f"status={(r.get('data') or {}).get('status')}")
    s, r = call("POST", f"/api/order/{oid}/pay/query", token=user_token)
    ok("order/pay/query", s, r)
    # 走支付 + 后台核销，验证订单域与后台域连通
    s, r = call("POST", "/api/pay/mock/callback", {"payNo": pay_no, "result": "SUCCESS"}, token=user_token)
    ok("pay/mock/callback", s, r)
    s, r = call("GET", f"/api/order/{oid}", token=user_token)
    ok("order/detail(PAID)", s, r, extra=f"status={(r.get('data') or {}).get('status')}")

print("\n########## 后台 ##########")
admin_token = None
for pw in ["123456", "admin", "admin123", "anxineat", "Anxineat@2024"]:
    s, r = call("POST", "/api/admin/login", {"username": "admin", "password": pw})
    if s == 200 and (r.get("data") or {}).get("token"):
        admin_token = r["data"]["token"]; break
ok("admin/login", 200 if admin_token else 401, {"code": 0 if admin_token else 1}, extra=("token:yes" if admin_token else "ALL_FAILED"))
if admin_token:
    s, r = call("GET", "/api/admin/overview", params={"storeId": 1}, token=admin_token)
    ok("admin/overview", s, r)
    s, r = call("GET", "/api/admin/orders", params={"storeId": 1, "page": 1, "size": 10}, token=admin_token)
    ok("admin/orders", s, r, extra=f"cnt={len((r.get('data') or {}).get('records') or (r.get('data') or []))}")
    if oid:
        s, r = call("POST", f"/api/admin/order/{oid}/accept", params={"storeId": 1}, token=admin_token)
        ok("admin/order/accept", s, r)
        s, r = call("POST", f"/api/admin/order/{oid}/ready", params={"storeId": 1}, token=admin_token)
        ok("admin/order/ready", s, r)
        det = (call("GET", f"/api/order/{oid}", token=user_token)[1].get("data") or {})
        s, r = call("POST", "/api/admin/order/redeem", {"storeId": 1, "pickupCode": det.get("pickupCode")}, token=admin_token)
        ok("admin/order/redeem", s, r)
        s, r = call("GET", f"/api/order/{oid}", token=user_token)
        ok("order/detail(COMPLETED)", s, r, extra=f"status={(r.get('data') or {}).get('status')}")

print("\n########## 汇总 ##########")
okk = sum(1 for _, m in results if m.strip() == "OK")
warn = sum(1 for _, m in results if m == "WARN")
fail = sum(1 for _, m in results if m == "FAIL")
print(f"总计 {len(results)} 项：OK={okk}  WARN={warn}  FAIL={fail}")
print("DONE")
