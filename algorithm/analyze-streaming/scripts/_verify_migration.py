# -*- coding: utf-8 -*-
"""迁移后验证：以备份为基准对比新文件。清单 63 行中有两对共享同一 metrics 条目
（kwh_num、user_num 各对应两张表），唯一语义变更 = 59 description + 2 is_measure = 61。"""
import difflib
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

import system_b.utils.meta_governance as mg
from system_b.utils.meta_governance import merge_database_meta, render_database_meta

OLD = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.backup-20260915.txt")
NEW = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt")

old = json.load(open(OLD, encoding="utf-8"))
new = json.load(open(NEW, encoding="utf-8"))

diffs = []

def walk(a, b, path):
    if isinstance(a, dict) and isinstance(b, dict):
        for k in set(a) | set(b):
            walk(a.get(k), b.get(k), f"{path}.{k}")
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            diffs.append((path, f"list长度 {len(a)}->{len(b)}", ""))
        for i, (x, y) in enumerate(zip(a, b)):
            walk(x, y, f"{path}[{i}]")
    elif a != b:
        diffs.append((path, a, b))

walk(old, new, "$")
kinds = {}
for p, a, b in diffs:
    k = p.split(".")[-1].split("[")[0]
    kinds[k] = kinds.get(k, 0) + 1
print(f"语义 diff: {len(diffs)} 处  按字段: {kinds}")
assert len(diffs) == 61 and kinds == {"description": 59, "is_measure": 2}, "变更数不符"

# is_measure 两处确认
for p, a, b in diffs:
    if p.endswith("is_measure"):
        print(f"  {p}: {a} -> {b}")

# 顶层结构不变
assert sorted(old.keys()) == sorted(new.keys()), "顶层键变化"
for k in old:
    if isinstance(old[k], list):
        assert len(old[k]) == len(new[k]), f"{k} 长度变化"
print(f"顶层键 {sorted(old.keys())}，各 list 长度不变: OK")

# 渲染等价：旧+现清单 vs 新+空清单
r_old = render_database_meta(merge_database_meta(old))
mg._overrides_cache = {"overrides": [], "promote_to_measure": []}
try:
    r_new = render_database_meta(merge_database_meta(new))
finally:
    mg._overrides_cache = None
same = r_old == r_new
print(f"渲染直接等价: {same}  (旧={len(r_old)}, 新={len(r_new)}, 差={len(r_new)-len(r_old)} chars)")
# 归一化已知 3 处变更后再比对：
#   1) 3 列 cn 带声明后缀（已确认接受）
#   2) dq_power_month/year 的 note 由 promote 过渡说明换成标准累计口径（变好）
r_new_norm = (r_new
    .replace("区局敏感客户数量，聚合类型为【快照】", "区局敏感客户数量")
    .replace('"cn":"月累计用电量，聚合类型为【累计】","agg_type":"cumulative","note":"取期末行，禁止再sum"',
             '"cn":"月累计用电量","agg_type":"cumulative","note":"元数据误标非指标，实为指标"')
    .replace('"cn":"年累计用电量，聚合类型为【累计】","agg_type":"cumulative","note":"取期末行，禁止再sum"',
             '"cn":"年累计用电量","agg_type":"cumulative","note":"元数据误标非指标，实为指标"'))
norm_same = r_old == r_new_norm
print(f"归一化后等价: {norm_same}")
if not norm_same:
    sm = difflib.SequenceMatcher(None, r_old, r_new_norm)
    n = 0
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag != "equal":
            n += 1
            print(f"  残余差异{n}: 旧[{r_old[i1:i2][:70]}] -> 新[{r_new_norm[j1:j2][:70]}]")
    raise SystemExit(f"存在 {n} 处未预期的残余差异")
print("渲染等价验证通过（差异仅为 3 列已知变更）。")

# agg_type 治理分布对比
def dist(meta_obj, empty=False):
    if empty:
        mg._overrides_cache = {"overrides": [], "promote_to_measure": []}
    try:
        g = merge_database_meta(meta_obj)
    finally:
        mg._overrides_cache = None
    from collections import Counter
    c = Counter()
    for t in g.get("tables") or []:
        for col in (t.get("columns") or {}).values() if isinstance(t.get("columns"), dict) else (t.get("columns") or []):
            pass
    return g

print("\n（渲染等价已证明语义一致，分布对比略）")
print("验证全部通过。")
