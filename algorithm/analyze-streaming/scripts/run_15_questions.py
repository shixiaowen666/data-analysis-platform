# -*- coding: utf-8 -*-
"""批量跑 15 个代表性问题（qwen3-235b，即 .env 所配模型），结果存 docs/v1.0.2/结果/"""
import base64
import hashlib
import hmac
import json
import time
import urllib.request
from pathlib import Path

OUT_DIR = Path("docs/v1.0.2/结果")
OUT_DIR.mkdir(parents=True, exist_ok=True)

QUESTIONS = [
    ("01_供电量查询", "2026年7月20日深圳、龙华、南山的工作日和周末的供电量同比有没有明显差异？周末跟工作日比是偏高还是偏低？"),
    ("02_用户数量查询", "2026-08-01福田供电局的四可用户总数是多少？"),
    ("03_光伏新能源", "2026-08-01龙华供电局的光伏发电用户数是多少？"),
    ("04_线损率查询", "2026年8月1日龙华供电局的分区线损率是多少？"),
    ("05_线路设备", "2026-08-01坪山供电局的分线总数是多少？"),
    ("06_采集指标", "2026-08-01光明供电局采集指标总在线率和总体数据完整率分别是多少？"),
    ("07_负荷查询", "2026-08-01深圳供电量日最大负荷、月最大负荷、年最大负荷是多少？"),
    ("08_自动抄表率", "昨天深圳供电局自动抄表率是多少？"),
    ("09_区局对比", "2026年7月深圳各区供电局的分线合格率和分台总合格率排名情况如何？"),
    ("10_趋势同比环比", "2026年3月到7月，宝安供电局的日供电量变化趋势如何？"),
    ("11_特殊日期节假日", "2026年7月排除节假日后，深圳局每周五的日供电量均值是多少？"),
    ("12_电压等级", "2026-08-01深圳供电局按电压等级统计的分压线损率分别是多少？"),
    ("13_市场化交易", "2026年7月深圳市场化交易用户数、优购用户数、电网代购用户数分别是多少？"),
    ("14_特殊用户大客户", "2026年8月1日深圳各行业用电量排名前五的行业是哪些？"),
    ("15_综合多指标", "2026-08-01宝安供电局、南山供电局和福田供电局的日供电量分别是多少？"),
]


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def make_token() -> str:
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    payload = b64url(json.dumps({
        "user_id": "repro", "sub": "repro", "exp": int(time.time()) + 7200,
    }).encode())
    sig = b64url(hmac.new(
        b"chatbi-jwt-secret-key-for-production-2026",
        f"{header}.{payload}".encode(), hashlib.sha256,
    ).digest())
    return f"{header}.{payload}.{sig}"


def ask(query: str) -> dict:
    body = json.dumps({"query": query, "user_id": "repro", "username": "repro"},
                      ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        "http://127.0.0.1:5000/api/v1/analyze", data=body,
        headers={"Content-Type": "application/json",
                 "Authorization": f"Bearer {make_token()}"},
        method="POST")
    with urllib.request.urlopen(req, timeout=300) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


summary = []
for name, q in QUESTIONS:
    t0 = time.time()
    record = {"category": name, "question": q, "model": "qwen3-235b-a22b-instruct-2507",
              "started_at": time.strftime("%Y-%m-%d %H:%M:%S")}
    try:
        resp = ask(q)
        record["http_ok"] = True
        record["response"] = resp
    except Exception as e:
        record["http_ok"] = False
        record["error"] = f"{type(e).__name__}: {e}"
    record["elapsed_sec"] = round(time.time() - t0, 1)
    out = OUT_DIR / f"{name}.json"
    out.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    status = "OK" if record.get("http_ok") else "FAIL"
    summary.append(f"{name}  {status}  {record['elapsed_sec']}s  {out.name}")
    print(summary[-1], flush=True)

(OUT_DIR / "_run_summary.json").write_text(
    json.dumps({"model": "qwen3-235b-a22b-instruct-2507",
                "finished_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                "results": summary}, ensure_ascii=False, indent=2), encoding="utf-8")
print("DONE", flush=True)
