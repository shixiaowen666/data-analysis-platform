# -*- coding: utf-8 -*-
"""生成正式 metadata 改动清单 docs/v1.0.2/metadata改动清单.md。

数据全部取自真实文件：
  docs/v1.0.2/database_meta_ddb2295c513a.txt + system_b/config/semantic_overrides.yaml
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

from system_b.utils.meta_governance import parse_aggregation_decl

META_PATH = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt")
YAML_PATH = os.path.join(ROOT, "system_b", "config", "semantic_overrides.yaml")
OUT_PATH = os.path.join(ROOT, "docs", "v1.0.2", "metadata改动清单.md")

DECL_TEXT = {"snapshot": "快照", "cumulative": "累计", "avg": "avg", "sum": "sum"}
AGG_CN = {"snapshot": "snapshot（时点存量）", "cumulative": "cumulative（期内累计）",
          "avg": "avg（比率）", "sum": "sum（事件流量）"}


def append_decl(desc, agg):
    base = str(desc or "").strip().rstrip("。，,；;")
    return f"{base}，聚合类型为【{DECL_TEXT[agg]}】" if base else f"聚合类型为【{DECL_TEXT[agg]}】"


def esc(s):
    return str(s).replace("|", "\\|").replace("\n", " ") or "（空）"


def main():
    meta = json.load(open(META_PATH, encoding="utf-8"))
    cfg = yaml.safe_load(open(YAML_PATH, encoding="utf-8"))

    tables = meta.get("table_summaries") or []
    metrics = meta.get("available_metrics") or []
    dims = meta.get("available_dimensions") or []
    t_by_name = {str(t.get("table_name") or ""): t for t in tables}
    m_by_code = {}
    for m in metrics:
        code = str(m.get("metric_code") or "")
        if code and code.lower() not in m_by_code:
            m_by_code[code.lower()] = m

    items = [dict(o, _kind="override") for o in cfg.get("overrides") or []]
    items += [dict(o, _kind="promote") for o in cfg.get("promote_to_measure") or []]

    metrics_chg, col_chg, measure_chg = [], [], []
    e_items, b_items = [], []
    seen_col, seen_code = set(), {}
    for it in items:
        t_name, c_name, agg = it["table"], it["column"], it["agg_type"]
        t = t_by_name.get(t_name)
        if t is None:
            b_items.append({"table": t_name, "column": c_name, "agg": agg, "why": "表在 metadata 中不存在"})
            continue
        col = next((c for c in t.get("columns") or []
                    if str(c.get("column_name") or "").lower() == c_name.lower()), None)
        if col is None:
            b_items.append({"table": t_name, "column": c_name, "agg": agg, "why": "列在表结构中不存在（全库亦无此列名）"})
            continue
        m = m_by_code.get(c_name.lower())
        m_decl = parse_aggregation_decl(m.get("description")) if m else None
        c_decl = parse_aggregation_decl(col.get("description") or col.get("column_comment") or "")
        if c_decl == agg or m_decl == agg:
            e_items.append({"table": t_name, "column": c_name, "agg": agg,
                            "where": "metrics.description" if m_decl == agg else "列级 description"})
            continue
        if c_decl is not None or m_decl is not None:
            # 本次诊断 F=0，防御性兜底
            b_items.append({"table": t_name, "column": c_name, "agg": agg,
                            "why": f"已有声明与清单不一致(metrics={m_decl},列级={c_decl},清单={agg})，需人工裁决"})
            continue
        # promote 的 is_measure 检查必须先于 (table,column) 去重：
        # promote 与 overrides 对同列重复登记，去重不能吞掉标志位修改
        if it["_kind"] == "promote" and not col.get("is_measure"):
            measure_chg.append({"table": t_name, "column": c_name,
                                "cn": str(col.get("description") or ""),
                                "old": col.get("is_measure"), "new": 1})
        key = (t_name, c_name)
        if key in seen_col:
            continue
        seen_col.add(key)
        if m is not None:
            prev = seen_code.get(c_name.lower())
            if prev and prev != agg:
                print(f"[警告] 同一 metric_code 在清单中 agg 不一致: {c_name} {prev} vs {agg}")
            seen_code[c_name.lower()] = agg
            metrics_chg.append({
                "table": t_name, "code": m.get("metric_code"), "name": m.get("metric_name"),
                "agg": agg, "old": str(m.get("description") or ""),
                "new": append_decl(m.get("description"), agg)})
        else:
            old = str(col.get("description") or col.get("column_comment") or "")
            col_chg.append({"table": t_name, "column": c_name, "agg": agg,
                            "old": old, "new": append_decl(old, agg),
                            "note": it.get("note", "")})

    touched_tables = sorted({c["table"] for c in metrics_chg} | {c["table"] for c in col_chg}
                            | {c["table"] for c in measure_chg})
    total = len(metrics_chg) + len(col_chg) + len(measure_chg)

    L = []
    L.append("# metadata 改动清单（semantic_overrides 退役迁移）")
    L.append("")
    L.append("> **对象文件**：`docs/v1.0.2/database_meta_ddb2295c513a.txt`（65 表 / 297 指标列 / 42 维度）")
    L.append("> ")
    L.append("> **依据**：`system_b/config/semantic_overrides.yaml` 全部 92 条（90 overrides + 2 promote）逐条对照 metadata 诊断后的迁移方案")
    L.append("> ")
    L.append(f"> **改动总量**：{total} 处 —— metrics 描述追加 {len(metrics_chg)} 处 + 列级描述追加 {len(col_chg)} 处"
             f" + is_measure 标志位 {len(measure_chg)} 处（⭐ 需业务审核）")
    L.append("> ")
    L.append("> **追加句式统一为**：`，聚合类型为【快照】/【累计】/【avg】`（治理合并器第①级识别句式，值映射：快照→snapshot、累计→cumulative、avg→avg）")
    L.append("")
    L.append("## 〇、修改范围声明（先看这张表）")
    L.append("")
    L.append("| 范围 | 是否修改 | 说明 |")
    L.append("|---|---|---|")
    L.append(f"| available_metrics[].description（指标描述） | ✅ 改 {len(metrics_chg)} 处 | 仅在描述末尾追加声明句式 |")
    L.append(f"| table_summaries[].columns[].description（列描述） | ✅ 改 {len(col_chg)} 处 | 仅 3 个无 metrics 条目的列；中文名会带声明后缀（已确认接受） |")
    L.append(f"| table_summaries[].columns[].is_measure（指标标志位） | ✅ 改 {len(measure_chg)} 处 | 0→1；**⭐ 需业务审核后执行** |")
    L.append("| metric_name / metric_code | ❌ 一律不动 | |")
    L.append(f"| available_dimensions（{len(dims)} 个维度）及维度值 | ❌ 一律不动 | 本次改动不涉及任何维度 |")
    L.append("| table_summaries[].table_name 及其他表级字段 | ❌ 一律不动 | |")
    L.append("| unit / data_type / 字段顺序 / 其余一切字段 | ❌ 一律不动 | |")
    L.append(f"| 涉及表 | {len(touched_tables)} 张 / 共 65 张 | 明细见下 |")
    L.append("")
    L.append(f"涉及表清单：{'、'.join(touched_tables)}")
    L.append("")
    L.append("---")
    L.append("")
    L.append(f"## 一、available_metrics 描述追加（{len(metrics_chg)} 处）")
    L.append("")
    L.append("改法：`description` 末尾追加声明句式，其余字段不碰。")
    L.append("")
    L.append("| # | 所属表 | metric_code | metric_name | 目标聚合类型 | 原描述 | 修改后描述 |")
    L.append("|---|---|---|---|---|---|---|")
    for i, c in enumerate(metrics_chg, 1):
        L.append(f"| {i} | {esc(c['table'])} | {esc(c['code'])} | {esc(c['name'])} "
                 f"| {AGG_CN[c['agg']]} | {esc(c['old'])} | {esc(c['new'])} |")
    L.append("")
    L.append(f"## 二、列级描述追加（{len(col_chg)} 处，均为 available_metrics 中无对应条目的列）")
    L.append("")
    L.append("改法：`table_summaries[].columns[].description` 末尾追加声明句式。")
    L.append("副作用（已确认接受）：该列在提示词 JSON 中的中文名（cn 字段）会带上声明后缀。")
    L.append("")
    L.append("| # | 表 | 列名 | 目标聚合类型 | 原描述 | 修改后描述 |")
    L.append("|---|---|---|---|---|---|")
    for i, c in enumerate(col_chg, 1):
        L.append(f"| {i} | {esc(c['table'])} | {esc(c['column'])} | {AGG_CN[c['agg']]} "
                 f"| {esc(c['old'])} | {esc(c['new'])} |")
    L.append("")
    L.append(f"## 三、is_measure 标志位（{len(measure_chg)} 处，⭐ 需业务审核）")
    L.append("")
    L.append("### 审核背景")
    L.append("")
    L.append("这两列在源 metadata 中被标为 `is_measure=0`（不承认为指标）。0915 事故「月累计供电量拆解死循环」的直接成因之一：")
    L.append("模型要找月累计类指标，metadata 里没有可用列，被迫猜测不存在的列名 `monthpowersupply`，三次重拆全部失败。")
    L.append("把这两列恢复指标身份（is_measure=1）后，模型可直接使用真列并按「取期末行」规则出数。")
    L.append("")
    L.append("**请业务确认：这两列（月累计用电量 / 年累计用电量）确实是对外可查询的指标，且口径为「期末时点值，不可逐日重算求和」。**")
    L.append("")
    L.append("| # | 表 | 列名 | 列描述 | 字段 | 原值 | 新值 |")
    L.append("|---|---|---|---|---|---|---|")
    for i, c in enumerate(measure_chg, 1):
        L.append(f"| {i} | {esc(c['table'])} | {esc(c['column'])} | {esc(c['cn'])} "
                 f"| is_measure | {c['old']} | {c['new']} |")
    L.append("")
    L.append(f"## 四、附录 A：无需修改的 {len(e_items)} 条（metadata 已写对，清单本就冗余）")
    L.append("")
    L.append("| # | 表 | 列 | 清单 agg | 声明已存在于 |")
    L.append("|---|---|---|---|---|")
    for i, c in enumerate(e_items, 1):
        L.append(f"| {i} | {esc(c['table'])} | {esc(c['column'])} | {AGG_CN[c['agg']]} | {c['where']} |")
    L.append("")
    L.append(f"## 五、附录 B：清单死条目 {len(b_items)} 条（metadata 不动，清单侧删除）")
    L.append("")
    L.append("以下 4 条是 0915 人工整理清单时的笔误（列名全库不存在），纠偏从未生效过，迁移时直接从 semantic_overrides.yaml 删除。")
    L.append("")
    L.append("| # | 表 | 列 | 清单 agg | 死因 |")
    L.append("|---|---|---|---|---|")
    for i, c in enumerate(b_items, 1):
        L.append(f"| {i} | {esc(c['table'])} | {esc(c['column'])} | {c['agg']} | {esc(c['why'])} |")
    L.append("")
    L.append("## 六、metadata 修改完成后的收尾动作（我方执行）")
    L.append("")
    L.append("1. `semantic_overrides.yaml` 清空（overrides 与 promote_to_measure 置空列表），代码零改动；")
    L.append("2. 验证：无清单渲染结果与现清单模式渲染结果 diff（仅 3 处列描述 cn 后缀差异，约 26 字符）；")
    L.append("3. 重跑治理链路 4 个测试文件 51 个用例；")
    L.append("4. 此后指标语义变更 = 业务改 metadata 描述，服务下次拉取自动生效，无需改任何代码或配置文件。")
    L.append("")

    open(OUT_PATH, "w", encoding="utf-8").write("\n".join(L))
    print(f"metrics 描述追加: {len(metrics_chg)}")
    print(f"列级描述追加: {len(col_chg)}")
    print(f"is_measure: {len(measure_chg)}")
    print(f"E 无需改: {len(e_items)}  B 死条目: {len(b_items)}")
    print(f"合计修改: {total} 处，涉及 {len(touched_tables)} 张表")
    print(f"已生成: {os.path.relpath(OUT_PATH, ROOT)}")


if __name__ == "__main__":
    main()
