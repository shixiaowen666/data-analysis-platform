# -*- coding: utf-8 -*-
"""对比 fixture 与 新旧 metadata，确定 fixture 版本。"""
import hashlib
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\工作\公司\项目\问数\web-json\analyze-streaming-v2"


def h(p):
    return hashlib.md5(open(p, "rb").read()).hexdigest()[:12]


fx = ROOT + r"\tests\fixtures\database_meta_ddb2295c513a.json"
old = ROOT + r"\docs\v1.0.2\database_meta_ddb2295c513a.backup-20260915.txt"
new = ROOT + r"\docs\v1.0.2\database_meta_ddb2295c513a.txt"
print("fixture md5:", h(fx))
print("旧meta md5:", h(old))
print("新meta md5:", h(new))

fxd = json.load(open(fx, encoding="utf-8"))
nwd = json.load(open(new, encoding="utf-8"))
odd = json.load(open(old, encoding="utf-8"))
t = "view_power_management_consumption_overview"


def col(d, name):
    for tb in d.get("table_summaries") or []:
        if tb.get("table_name") == t:
            for c in tb.get("columns") or []:
                if c.get("column_name") == name:
                    return c
    return None


for label, d in (("fixture", fxd), ("旧", odd), ("新", nwd)):
    c = col(d, "dq_power_month")
    print(label, "dq_power_month:", "is_measure=", c.get("is_measure"),
          "desc=", (c.get("description") or "")[:40])

# fixture 顶层结构与真实文件一致性
print("顶层键一致:", sorted(fxd.keys()) == sorted(nwd.keys()))
print("fixture 表数:", len(fxd.get("table_summaries") or []))
