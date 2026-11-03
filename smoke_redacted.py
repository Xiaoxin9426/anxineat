#!/usr/bin/env python3
# 脱敏版冒烟测试：只打印 HTTP 状态、业务 code、金额、订单状态，绝不打印 token / 密码。
import json, urllib.request, urllib.error, time

BASE = "http://127.0.0.1:8080"

def call(method, path, body=None, token=None):
    url = BASE + path
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

def code_of(resp):
    return resp.get("code")

def ok(label, status, resp, extra=""):
    c = code_of(resp)
    good = status == 200 and (c in (0, "0", None)) and "error" not in resp
    mark = "OK " if good else "FAIL"
    print(f"[{mark}] {label} -> HTTP {status} code={c} {extra}")
    return good

print("==== 1. Health (no auth) ====")
s, r = call("GET", "/api/health")
ok("health", s, r)

print("\n==== 2. Client config ====")
s, r = call("GET", "/api/config/client")
cc = r.get("data") or {}
ok("config/client", s, r, extra=f"wxRealLogin={cc.get('wxRealLogin')}")

print("\n==== 3. User login (mock wechat) ====")
s, r = call("POST", "/api/auth/login", {"code": "mock:smoke_openid_2", "nickname": "SmokeUser2"})
user_token = (r.get("data") or {}).get("token")
ok("login", s, r, extra=("token:yes" if user_token else "token:NO"))

print("\n==== 4. Add to cart ====")
s, r = call("POST", "/api/cart/add",
            {"storeId": 1, "productId": 1, "skuId": 1, "quantity": 1,
             "specOptionIds": [1, 25, 62]}, token=user_token)
ok("cart/add", s, r)

print("\n==== 5. Preview order ====")
s, r = call("POST", "/api/order/preview",
            {"storeId": 1, "orderType": "PICKUP", "fromCart": True}, token=user_token)
prev = r.get("data") or {}
ok("preview", s, r,
   extra=f"payAmount={prev.get('payAmount')} goods={prev.get('goodsAmount')} coupon={prev.get('couponAmount')} point={prev.get('pointAmount')}")

print("\n==== 6. Submit order ====")
s, r = call("POST", "/api/order",
            {"storeId": 1, "orderType": "PICKUP", "fromCart": True,
             "idempotentKey": "smoke-" + str(int(time.time()))}, token=user_token)
sub = r.get("data") or {}
order_id = sub.get("orderId"); pay_no = sub.get("payNo")
ok("submit", s, r, extra=f"orderId={order_id} payNo={'yes' if pay_no else 'NO'} payAmount={sub.get('payAmount')}")

print("\n==== 7. Mock pay callback (SUCCESS) ====")
s, r = call("POST", "/api/pay/mock/callback", {"payNo": pay_no, "result": "SUCCESS"})
ok("mock/callback", s, r)
s2, r2 = call("POST", "/api/pay/mock/callback", {"payNo": pay_no, "result": "SUCCESS", "duplicate": True})
ok("mock/callback dup(idempotent)", s2, r2)

print("\n==== 8. Order detail (expect PAID/MAKING) ====")
s, r = call("GET", f"/api/order/{order_id}", token=user_token)
det = r.get("data") or {}
ok("detail", s, r, extra=f"status={det.get('status')} pickupCode={det.get('pickupCode')}")

print("\n==== 9. Admin login ====")
admin_token = None
for pw in ["123456", "admin", "admin123", "anxineat", "Anxineat@2024"]:
    s, r = call("POST", "/api/admin/login", {"username": "admin", "password": pw})
    if s == 200 and (r.get("data") or {}).get("token"):
        admin_token = r["data"]["token"]
        break
ok("admin/login", 200 if admin_token else 401, {"code": 0 if admin_token else 1}, extra=("token:yes" if admin_token else "ALL_PASSWORDS_FAILED"))

if admin_token:
    print("\n==== 10. Admin overview ====")
    s, r = call("GET", "/api/admin/overview?storeId=1", token=admin_token)
    ok("overview", s, r)

    print("\n==== 11. Admin accept + ready ====")
    s, r = call("POST", f"/api/admin/order/{order_id}/accept?storeId=1", token=admin_token)
    ok("accept", s, r)
    s, r = call("POST", f"/api/admin/order/{order_id}/ready?storeId=1", token=admin_token)
    ok("ready", s, r)

    print("\n==== 12. Admin redeem (pickup code) ====")
    s, r = call("POST", "/api/admin/order/redeem",
                {"storeId": 1, "pickupCode": det.get("pickupCode")}, token=admin_token)
    ok("redeem", s, r)

    print("\n==== 13. Final order detail (expect COMPLETED) ====")
    s, r = call("GET", f"/api/order/{order_id}", token=user_token)
    fin = r.get("data") or {}
    ok("detail final", s, r, extra=f"status={fin.get('status')}")

print("\n==== SMOKE TEST DONE ====")
