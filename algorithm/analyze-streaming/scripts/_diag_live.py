# -*- coding: utf-8 -*-
"""临时：复现 analyze 请求，打印返回 JSON 前部，诊断批量失败原因"""
import base64
import hashlib
import hmac
import json
import time
import urllib.request


def b64url(d):
    return base64.urlsafe_b64encode(d).rstrip(b"=").decode()


def make_token():
    h = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    p = b64url(json.dumps({"user_id": "repro", "sub": "repro", "exp": int(time.time()) + 3600}).encode())
    s = b64url(hmac.new(b"chatbi-jwt-secret-key-for-production-2026", f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{s}"


body = json.dumps({
    "request_id": "live-check-002",
    "query": "深圳的变电站有多少座",
    "user_id": "repro",
    "return_details": False,
}).encode("utf-8")
req = urllib.request.Request(
    "http://127.0.0.1:5000/api/v1/analyze", data=body,
    headers={"Content-Type": "application/json", "Authorization": f"Bearer {make_token()}"},
)
try:
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read().decode("utf-8", "replace")
        print("HTTP", resp.status)
        print(raw[:1200])
except Exception as e:
    print("ERR", type(e).__name__, e)
