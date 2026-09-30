# -*- coding: utf-8 -*-
"""核查：available_metrics 中重复 code 的条目结构 + 清单中涉及这些 code 的条目。"""
import io
import json
import os
import sys

import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

meta = json.load(open(os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt"), encoding="utf-8"))
cfg = yaml.safe_load(open(os.path.join(ROOT, "system_b", "config", "semantic_overrides.yaml"), encoding="utf-8"))

print("== available_metrics 中 code 重复的条目 ==")
by_code = {}
for m in meta.get("available_metrics") or []:
    by_code.setdefault(str(m.get("metric_code") or "").lower(), []).append(m)
for code, ms in by_code.items():
    if len(ms) > 1:
        print(f"  code={ms[0].get('metric_code')}  共{len(ms)}条:")
        for m in ms:
            print(f"    name={m.get('metric_name')!r} desc={str(m.get('description'))[:60]!r} 全字段键={sorted(m.keys())}")

print("\n== 清单中涉及 kwh_num/user_num 的条目 ==")
for o in (cfg.get("overrides") or []):
    if o["column"].lower() in ("kwh_num", "user_num"):
        print(f"  {o['table']}.{o['column']}  agg={o['agg_type']}  note={o.get('note','')}")

print("\n== 这些列在各表中的列级 agg_type 现状 ==")
for t in meta.get("table_summaries") or []:
    tn = str(t.get("table_name") or "")
    for c in t.get("columns") or []:
        cn = str(c.get("column_name") or "")
        if cn.lower() in ("kwh_num", "user_num"):
            print(f"  {tn}.{cn}  列级agg_type={c.get('agg_type')!r} desc={str(c.get('description') or '')[:40]!r}")
