# -*- coding: utf-8 -*-
"""补充核查：B 类死条目是否为表名笔误；列级兜底 5 条明细；渲染 diff 精确区域。"""
import io
import json
import os
import sys

import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

meta = json.load(open(os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt"), encoding="utf-8"))
cfg = yaml.safe_load(open(os.path.join(ROOT, "system_b", "config", "semantic_overrides.yaml"), encoding="utf-8"))

# 列名 -> [表名] 全局索引（小写）
col_owner = {}
for t in meta.get("table_summaries") or []:
    tn = str(t.get("table_name") or "")
    for c in t.get("columns") or []:
        col_owner.setdefault(str(c.get("column_name") or "").lower(), []).append(tn)

dead = [("view_mk_mc_archive_scale_stat", "statistic0100"),
        ("view_mk_mc_file_sum_stat", "statistic0400"),
        ("view_mk_mc_file_sum_stat", "statistic0403"),
        ("view_mk_mc_file_sum_stat", "statistic0405")]
print("== B 类死条目追踪 ==")
for tn, cn in dead:
    owners = col_owner.get(cn.lower(), [])
    print(f"  {tn}.{cn}")
    print(f"    '{cn}' 实际存在于: {owners or '全库不存在'}")

# 列级兜底 5 条：A 类中 metrics 无条目的
m_codes = {str(m.get("metric_code") or "").lower() for m in meta.get("available_metrics") or []}
t_by_name = {str(t.get("table_name") or ""): t for t in meta.get("table_summaries") or []}
print("\n== 列级 description 兜底的 A 类条目（会污染提示词 cn 字段）==")
for o in (cfg.get("overrides") or []) + [dict(x, _p=1) for x in (cfg.get("promote_to_measure") or [])]:
    tn, cn = o["table"], o["column"]
    t = t_by_name.get(tn)
    if not t:
        continue
    col = next((c for c in t.get("columns") or [] if str(c.get("column_name") or "").lower() == cn.lower()), None)
    if col is None:
        continue
    if cn.lower() not in m_codes:
        print(f"  {tn}.{cn}  列描述现状: {str(col.get('description') or col.get('column_comment') or '')!r}")
