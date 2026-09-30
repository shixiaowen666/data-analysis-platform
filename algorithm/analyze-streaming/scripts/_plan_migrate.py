# -*- coding: utf-8 -*-
"""迁移预演 v2：声明句式优先写 metrics description，列级仅在无 metrics 条目时兜底。

目标：无清单 + 修改后 metadata 的渲染结果 == 有清单 + 原 metadata 的渲染结果。
"""
import io
import json
import os
import sys
from collections import Counter

import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from system_b.utils.meta_governance import (
    merge_database_meta, parse_aggregation_decl, render_database_meta)

META_PATH = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt")
YAML_PATH = os.path.join(ROOT, "system_b", "config", "semantic_overrides.yaml")
MD_PATH = os.path.join(ROOT, "docs", "v1.0.2", "metadata修改清单.md")

DECL_TEXT = {"snapshot": "快照", "cumulative": "累计", "avg": "avg", "sum": "sum"}


def append_decl(desc, agg):
    base = str(desc or "").strip().rstrip("。，,；;")
    return f"{base}，聚合类型为【{DECL_TEXT[agg]}】" if base else f"聚合类型为【{DECL_TEXT[agg]}】"


def main():
    meta = json.load(open(META_PATH, encoding="utf-8"))
    cfg = yaml.safe_load(open(YAML_PATH, encoding="utf-8"))

    t_by_name = {str(t.get("table_name") or ""): t for t in meta.get("table_summaries") or []}
    m_by_code = {}
    for m in meta.get("available_metrics") or []:
        code = str(m.get("metric_code") or "")
        if code and code.lower() not in m_by_code:
            m_by_code[code.lower()] = m

    plan = []
    items = [dict(o, _kind="override") for o in cfg.get("overrides") or []]
    items += [dict(o, _kind="promote") for o in cfg.get("promote_to_measure") or []]
    for it in items:
        t_name, c_name, agg = it["table"], it["column"], it["agg_type"]
        rec = {"kind": it["_kind"], "table": t_name, "column": c_name,
               "agg": agg, "note": it.get("note", ""), "actions": []}
        t = t_by_name.get(t_name)
        if t is None:
            rec["status"] = "D-表不存在(清单死条目)"
            plan.append(rec)
            continue
        col = next((c for c in t.get("columns") or []
                    if str(c.get("column_name") or "").lower() == c_name.lower()), None)
        if col is None:
            rec["status"] = "B-列不存在(清单死条目)"
            plan.append(rec)
            continue
        m = m_by_code.get(c_name.lower())
        m_decl = parse_aggregation_decl(m.get("description")) if m else None
        c_decl = parse_aggregation_decl(col.get("description") or col.get("column_comment") or "")
        rec["metric_exists"] = bool(m)
        if c_decl == agg or m_decl == agg:
            rec["status"] = "E-已有同义声明(无需改)"
        elif c_decl is not None or m_decl is not None:
            rec["status"] = "F-声明冲突(清单现被压制)"
            rec["detail"] = f"metrics声明={m_decl} 列级声明={c_decl} 清单={agg}"
        else:
            rec["status"] = "A-可迁移"
            if m is not None:
                rec["actions"].append({
                    "field": f"available_metrics[].description (metric_code={m.get('metric_code')})",
                    "old": str(m.get("description") or ""),
                    "new": append_decl(m.get("description"), agg)})
            else:
                rec["actions"].append({
                    "field": "table_summaries[].columns[].description (无 metrics 条目，列级兜底)",
                    "old": str(col.get("description") or col.get("column_comment") or ""),
                    "new": append_decl(col.get("description") or col.get("column_comment"), agg)})
            if it["_kind"] == "promote" and not col.get("is_measure"):
                rec["actions"].append({
                    "field": "table_summaries[].columns[].is_measure",
                    "old": col.get("is_measure"), "new": 1})
        plan.append(rec)

    stat = Counter(r["status"].split("-")[0] for r in plan)
    print("== 诊断汇总（共 %d 条）==" % len(plan))
    for k, v in sorted(stat.items()):
        print(f"  {k}: {v}")

    n_m = sum(1 for r in plan if r["status"].startswith("A") and r["actions"]
              and r["actions"][0]["field"].startswith("available_metrics"))
    n_c = sum(1 for r in plan if r["status"].startswith("A") and r["actions"]
              and r["actions"][0]["field"].startswith("table_summaries"))
    n_p = sum(1 for r in plan for a in r["actions"] if a["field"].endswith("is_measure"))
    print(f"  A 类细分: 改 metrics description {n_m} 条 / 改列级 description {n_c} 条 / 改 is_measure {n_p} 条")

    # ---- 模拟：应用修改 → 无清单渲染，与现状（有清单）比对 ----
    sim = json.loads(json.dumps(meta, ensure_ascii=False))
    sim_t = {str(t.get("table_name") or ""): t for t in sim.get("table_summaries") or []}

    def sim_col(t_name, c_name):
        t = sim_t.get(t_name)
        return next((c for c in (t.get("columns") or []) if str(
            c.get("column_name") or "").lower() == c_name.lower()), None) if t else None

    n_applied = 0
    for r in plan:
        if not r["status"].startswith("A"):
            continue
        for a in r["actions"]:
            if a["field"].startswith("available_metrics[]"):
                for m in sim.get("available_metrics") or []:
                    if str(m.get("metric_code") or "").lower() == r["column"].lower():
                        m["description"] = a["new"]
                        n_applied += 1
            elif a["field"].startswith("table_summaries[].columns[].description"):
                col = sim_col(r["table"], r["column"])
                if col is not None:
                    if col.get("description") or col.get("column_comment"):
                        col["description"] = a["new"]
                    else:
                        col["description"] = a["new"]
                    n_applied += 1
            elif a["field"].endswith("is_measure"):
                col = sim_col(r["table"], r["column"])
                if col is not None:
                    col["is_measure"] = a["new"]
                    n_applied += 1
    print(f"  实际应用修改: {n_applied} 处")

    import system_b.utils.meta_governance as mg
    r_now = render_database_meta(merge_database_meta(meta))
    mg._overrides_cache = {"overrides": [], "promote_to_measure": []}
    r_sim = render_database_meta(merge_database_meta(sim))
    mg._overrides_cache = None
    print("\n== 模拟验证 ==")
    print(f"  渲染结果一致: {r_now == r_sim}  (now={len(r_now)} chars, sim={len(r_sim)} chars)")
    if r_now != r_sim:
        diffs = 0
        for i, (a, b) in enumerate(zip(r_now, r_sim)):
            if a != b:
                print(f"  差异@{i}:")
                print(f"    now: ...{r_now[max(0, i - 60):i + 100]}")
                print(f"    sim: ...{r_sim[max(0, i - 60):i + 100]}")
                diffs += 1
                if diffs >= 3:
                    break
        if len(r_now) != len(r_sim):
            print(f"  长度差: {len(r_sim) - len(r_now)}")

    # ---- B/D/F 死条目与冲突明细 ----
    print("\n== 需人工确认的条目 ==")
    for r in plan:
        if r["status"][0] in "BDF":
            print(f"  [{r['status']}] {r['table']}.{r['column']} agg={r['agg']} {r.get('detail', '')}")

    # ---- 写 MD ----
    lines = ["# metadata 回写修改清单（semantic_overrides 退役迁移）", "",
             f"> 对象：`docs/v1.0.2/database_meta_ddb2295c513a.txt` + `semantic_overrides.yaml`（90+2 条）",
             f"> 修改原则：声明句式「聚合类型为【xx】」优先写入 available_metrics[].description（第①级，优先级最高）；"
             f"metrics 无对应条目时写列级 description（第①.5级）。metric_name/metric_code 一律不动。", "",
             "| # | 类别 | 表 | 列 | agg | 修改动作 |", "|---|---|---|---|---|---|"]
    for i, r in enumerate(plan, 1):
        act = "；".join(f"`{a['field']}`：{a['old']!r} → {a['new']!r}" for a in r["actions"]) \
            or {"E": "无需修改（metadata 已写对）", "B": "不改（清单条目本身有误）",
                "D": "不改（清单条目本身有误）"}.get(r["status"][0], "")
        extra = f"<br>{r['detail']}" if r.get("detail") else ""
        note = f"<br>语义: {r['note']}" if r.get("note") else ""
        lines.append(f"| {i} | {r['status']} | {r['table']} | {r['column']} | {r['agg']} | {act}{extra}{note} |")
    lines += ["", "## 图例", "- A-可迁移：无任何声明，回写声明句式", "- B/D-清单死条目：清单的列/表在 metadata 中不存在（整理笔误），metadata 不动，清单侧删除",
              "- E-已有同义声明：metadata 已写对，清单本就冗余，两侧都不用动",
              "- F-声明冲突：metadata 已有声明但与清单不同，现治理以声明为准，需人工裁决谁对"]
    open(MD_PATH, "w", encoding="utf-8").write("\n".join(lines))
    print(f"\n明细已写: {os.path.relpath(MD_PATH, ROOT)}")


if __name__ == "__main__":
    main()
