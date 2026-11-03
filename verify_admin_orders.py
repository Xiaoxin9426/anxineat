#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""脱敏验证：超管"全部门店"(storeId=null) 能否看到订单。
不打印 token / 密码。仅输出统计性结论。"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"

def post(path, body, token=None):
    data = json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())

def get(path, token):
    req = urllib.request.Request(BASE + path, method="GET")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())

def count_of(resp):
    """从 IPage 结构取 records 数量与门店集合。"""
    if not resp or resp.get("code") != 0:
        return None, None, resp
    data = resp.get("data") or {}
    records = data.get("records") or []
    store_ids = sorted({str(r.get("storeId")) for r in records if r.get("storeId") is not None})
    return len(records), store_ids, resp

print("== 1. 超管登录 ==")
st, login = post("/api/admin/login", {"username": "admin", "password": "admin123"})
if st != 200 or login.get("code") != 0:
    print("登录失败 code=%s status=%s" % (login.get("code"), st))
    raise SystemExit(1)
token = login["data"]["token"]
print("登录OK，拿到 token(已脱敏)")

print("== 2. 全部门店 (storeId 省略 = null) ==")
st, all_resp = get("/api/admin/orders", token)
n_all, stores_all, _ = count_of(all_resp)
print("  全部门店 -> 订单数=%s 涉及门店=%s code=%s" % (n_all, stores_all, all_resp.get("code")))

print("== 3. 指定门店 storeId=1 ==")
st, s1_resp = get("/api/admin/orders?storeId=1", token)
n1, stores1, _ = count_of(s1_resp)
print("  storeId=1 -> 订单数=%s 涉及门店=%s code=%s" % (n1, stores1, s1_resp.get("code")))

print("== 4. 看板 overview (storeId 省略) ==")
st, ov = get("/api/admin/overview", token)
if ov.get("code") == 0:
    d = ov.get("data") or {}
    print("  overview 今日订单=%s 营业额(分)=%s" % (d.get("todayOrderCount"), d.get("todayAmount")))
else:
    print("  overview code=%s" % ov.get("code"))

print("== 结论 ==")
if n_all is not None and n_all > 0:
    print("PASS: 全部门店返回 %d 条订单，fix 生效。" % n_all)
    if n1 is not None:
        # 全部门店应 >= 指定门店（除非只有门店1有单）
        if len(stores_all) >= 1 and (n_all >= n1):
            print("PASS: 全部门店覆盖门店集合 %s，包含(或等于)门店1的 %d 条。" % (stores_all, n1))
        else:
            print("WARN: 门店覆盖异常，需人工核对。")
else:
    print("FAIL: 全部门店仍返回 0 条，fix 未生效或数据库无订单。")
