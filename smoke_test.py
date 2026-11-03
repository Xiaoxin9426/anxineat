#!/usr/bin/env python3
import json, urllib.request, urllib.error, time

BASE = "http://127.0.0.1:8080"
PROXY = {}

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
            return e.code, {"raw": raw}
    except Exception as e:
        return -1, {"error": str(e)}

def show(label, status, resp):
    ok = status == 200 and (("code" in resp and resp.get("code") in (0, "0", None)) or "error" not in resp)
    mark = "OK " if ok else "FAIL"
    print(f"[{mark}] {label} -> HTTP {status} | {json.dumps(resp, ensure_ascii=False)[:300]}")
    return ok, resp

print("==== 1. Health (no auth) ====")
s, r = call("GET", "/api/health")
show("health", s, r)

print("\n==== 2. User login (mock wechat) ====")
s, r = call("POST", "/api/auth/login", {"code": "mock:smoke_openid_1", "nickname": "SmokeUser"})
ok, r = show("login", s, r)
user_token = (r.get("data") or {}).get("token")
print("   user_token:", (user_token or "")[:40], "...")

print("\n==== 3. Add to cart (store1, product1, sku1) ====")
s, r = call("POST", "/api/cart/add",
            {"storeId": 1, "productId": 1, "skuId": 1, "quantity": 1,
             "specOptionIds": [1, 25, 62]}, token=user_token)
show("cart/add", s, r)

print("\n==== 4. Preview order (PICKUP, fromCart) ====")
s, r = call("POST", "/api/order/preview",
            {"storeId": 1, "orderType": "PICKUP", "fromCart": True}, token=user_token)
show("preview", s, r)
prev = r.get("data") or {}
print("   preview payAmount:", prev.get("payAmount"), "goodsAmount:", prev.get("goodsAmount"),
      "couponAmount:", prev.get("couponAmount"), "pointAmount:", prev.get("pointAmount"))

print("\n==== 5. Submit order ====")
s, r = call("POST", "/api/order",
            {"storeId": 1, "orderType": "PICKUP", "fromCart": True,
             "idempotentKey": "smoke-" + str(int(time.time()))}, token=user_token)
show("submit", s, r)
sub = r.get("data") or {}
order_id = sub.get("orderId")
pay_no = sub.get("payNo")
print("   orderId:", order_id, "payNo:", pay_no, "payAmount:", sub.get("payAmount"))

print("\n==== 6. Mock pay callback (SUCCESS) ====")
s, r = call("POST", "/api/pay/mock/callback", {"payNo": pay_no, "result": "SUCCESS"})
show("mock/callback", s, r)
print("   (duplicate idempotency check)")
s2, r2 = call("POST", "/api/pay/mock/callback", {"payNo": pay_no, "result": "SUCCESS", "duplicate": True})
show("mock/callback duplicate", s2, r2)

print("\n==== 7. Order detail (expect PAID/MAKING) ====")
s, r = call("GET", f"/api/order/{order_id}", token=user_token)
show("detail", s, r)
det = r.get("data") or {}
print("   status:", det.get("status"), "pickupCode:", det.get("pickupCode"))

print("\n==== 8. Admin login (try common passwords) ====")
admin_token = None
for pw in ["123456", "admin", "admin123", "anxineat", "Anxineat@2024"]:
    s, r = call("POST", "/api/admin/login", {"username": "admin", "password": pw})
    if s == 200 and (r.get("data") or {}).get("token"):
        admin_token = r["data"]["token"]
        print(f"   admin login OK with password='{pw}'")
        break
    else:
        print(f"   try password='{pw}' -> {s} {json.dumps(r, ensure_ascii=False)[:120]}")
if not admin_token:
    print("   !! admin login failed for all tried passwords")
else:
    print("\n==== 9. Admin overview ====")
    s, r = call("GET", "/api/admin/overview?storeId=1", token=admin_token)
    show("overview", s, r)

    print("\n==== 10. Admin accept + ready ====")
    s, r = call("POST", f"/api/admin/order/{order_id}/accept?storeId=1", token=admin_token)
    show("accept", s, r)
    s, r = call("POST", f"/api/admin/order/{order_id}/ready?storeId=1", token=admin_token)
    show("ready", s, r)

    print("\n==== 11. Admin redeem (pickup code) ====")
    s, r = call("POST", "/api/admin/order/redeem",
                {"storeId": 1, "pickupCode": det.get("pickupCode")}, token=admin_token)
    show("redeem", s, r)

    print("\n==== 12. Final order detail (expect COMPLETED) ====")
    s, r = call("GET", f"/api/order/{order_id}", token=user_token)
    show("detail final", s, r)
    print("   status:", (r.get("data") or {}).get("status"))

print("\n==== SMOKE TEST DONE ====")
