# -*- coding: utf-8 -*-
"""应用迁移：把 63 处修改写入 docs/v1.0.2/database_meta_ddb2295c513a.txt。

修改内容（与 docs/v1.0.2/metadata改动清单.md 严格一致）：
  1. available_metrics[].description 追加声明句式 58 处
  2. table_summaries[].columns[].description 追加声明句式 3 处
  3. table_summaries[].columns[].is_measure 0→1 2 处

验证：
  A. 写回文件可正常 json 解析
  B. 新旧文件语义 diff 恰好 63 处（字段级精确核对）
  C. 新文件+空清单 渲染 == 旧文件+现清单 渲染（仅 3 处列描述 cn 后缀差异）
"""
import copy
import io
import json
import os
import sys

import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

import system_b.utils.meta_governance as mg
from system_b.utils.meta_governance import merge_database_meta, parse_aggregation_decl, render_database_meta

META_PATH = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt")
YAML_PATH = os.path.join(ROOT, "system_b", "config", "semantic_overrides.yaml")

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

    new = copy.deepcopy(meta)
    new_t = {str(t.get("table_name") or ""): t for t in new.get("table_summaries") or []}
    new_m_by_code = {}
    for m in new.get("available_metrics") or []:
        code = str(m.get("metric_code") or "")
        if code and code.lower() not in new_m_by_code:
            new_m_by_code[code.lower()] = m

    items = [dict(o, _kind="override") for o in cfg.get("overrides") or []]
    items += [dict(o, _kind="promote") for o in cfg.get("promote_to_measure") or []]

    n_metrics = n_col = n_measure = 0
    seen_col = set()
    promoted = set()
    for it in items:
        t_name, c_name, agg = it["table"], it["column"], it["agg_type"]
        t = t_by_name.get(t_name)
        if t is None:
            continue
        col = next((c for c in t.get("columns") or []
                    if str(c.get("column_name") or "").lower() == c_name.lower()), None)
        if col is None:
            continue
        m = m_by_code.get(c_name.lower())
        m_decl = parse_aggregation_decl(m.get("description")) if m else None
        c_decl = parse_aggregation_decl(col.get("description") or col.get("column_comment") or "")
        if c_decl == agg or m_decl == agg or c_decl is not None or m_decl is not None:
            continue  # E/F 类不动

        # promote 的 is_measure 先处理（同列可能同时有 override+promote）
        if it["_kind"] == "promote" and not col.get("is_measure") and (t_name, c_name) not in promoted:
            new_col = next(c for c in new_t[t_name].get("columns") or []
                           if str(c.get("column_name") or "").lower() == c_name.lower())
            new_col["is_measure"] = 1
            promoted.add((t_name, c_name))
            n_measure += 1

        if (t_name, c_name) in seen_col:
            continue
        seen_col.add((t_name, c_name))
        if m is not None:
            new_m_by_code[c_name.lower()]["description"] = append_decl(m.get("description"), agg)
            n_metrics += 1
        else:
            new_col = next(c for c in new_t[t_name].get("columns") or []
                           if str(c.get("column_name") or "").lower() == c_name.lower())
            old_desc = str(col.get("description") or col.get("column_comment") or "")
            new_col["description"] = append_decl(old_desc, agg)
            n_col += 1

    print(f"应用修改: metrics描述 {n_metrics} + 列描述 {n_col} + is_measure {n_measure} = {n_metrics + n_col + n_measure}")
    assert (n_metrics, n_col, n_measure) == (58, 3, 2), "与改动清单不一致，中止！"

    # ---- 写回（indent=2 与原格式一致）----
    text = json.dumps(new, ensure_ascii=False, indent=2)
    open(META_PATH, "w", encoding="utf-8", newline="\n").write(text)
    print(f"已写回: {os.path.relpath(META_PATH, ROOT)}  ({len(text)} chars)")

    # ---- 验证 A：写回文件可解析 ----
    reloaded = json.load(open(META_PATH, encoding="utf-8"))
    print("验证A 写回文件可解析: OK")

    # ---- 验证 B：新旧语义 diff 恰好 63 处 ----
    diffs = []

    def walk(a, b, path):
        if isinstance(a, dict) and isinstance(b, dict):
            for k in set(a) | set(b):
                walk(a.get(k), b.get(k), f"{path}.{k}")
        elif isinstance(a, list) and isinstance(b, list):
            if len(a) != len(b):
                diffs.append((path, "list长度", len(a), len(b)))
            for i, (x, y) in enumerate(zip(a, b)):
                walk(x, y, f"{path}[{i}]")
        elif a != b:
            diffs.append((path, a, b))

    walk(meta, reloaded, "$")
    kinds = {}
    for p, a, b in diffs:
        kinds[p.split(".")[-1].split("[")[0]] = kinds.get(p.split(".")[-1].split("[")[0], 0) + 1
    print(f"验证B 语义 diff: {len(diffs)} 处  按字段 {kinds}")
    assert len(diffs) == 63, f"diff 应为 63 处，实际 {len(diffs)}"

    # ---- 验证 C：渲染等价（新+空清单 vs 旧+现清单）----
    r_old = render_database_meta(merge_database_meta(meta))
    mg._overrides_cache = {"overrides": [], "promote_to_measure": []}
    try:
        r_new = render_database_meta(merge_database_meta(reloaded))
    finally:
        mg._overrides_cache = None
    same = r_old == r_new
    print(f"验证C 渲染等价: {same}  (旧={len(r_old)}, 新={len(r_new)}, 差={len(r_new)-len(r_old)} chars)")
    if not same:
        # 逐个差异打印，确认仅为 3 处 cn 后缀
        n = 0
        import difflib
        sm = difflib.SequenceMatcher(None, r_old, r_new)
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag != "equal":
                n += 1
                print(f"  差异{n}: 旧[{r_old[i1:i2][:60]}] -> 新[{r_new[j1:j2][:60]}]")
        assert n <= 3, f"差异块 {n} 超过预期 3 处"
    print("\n迁移完成，三层验证全部通过。")


if __name__ == "__main__":
    main()
