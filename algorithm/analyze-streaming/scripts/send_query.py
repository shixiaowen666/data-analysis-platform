# -*- coding: utf-8 -*-
"""发起深圳月累计供电量查询（带自签 JWT），结果写入文件避免终端吞输出（临时脚本）"""
import base64
import hashlib
import hmac
import json
import time
import urllib.request

OUT = "logs/query_result.json"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def make_token() -> str:
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    payload = b64url(json.dumps({
        "user_id": "repro",
        "sub": "repro",
        "exp": int(time.time()) + 3600,
    }).encode())
    sig = b64url(hmac.new(
        "chatbi-jwt-secret-key-for-production-2026".encode(),
        f"{header}.{payload}".encode(),
        hashlib.sha256,
    ).digest())
    return f"{header}.{payload}.{sig}"


result = {}
try:
    query = "2026年7月深圳月累计供电量是多少"
    payload_bytes = json.dumps(
        {"query": query, "user_id": "repro", "username": "repro"},
        ensure_ascii=False,
    ).encode("utf-8")
    req = urllib.request.Request(
        "http://127.0.0.1:5000/api/v1/analyze",
        data=payload_bytes,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {make_token()}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        body = resp.read().decode("utf-8", errors="replace")
        result["http_status"] = resp.status
        try:
            result["response"] = json.loads(body)
        except Exception:
            result["raw_body"] = body[:3000]
except Exception as e:
    result["error"] = f"{type(e).__name__}: {e}"

with open(OUT, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)
