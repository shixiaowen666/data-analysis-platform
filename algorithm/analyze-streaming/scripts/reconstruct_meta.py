# -*- coding: utf-8 -*-
"""将 database_meta 归档文本反向解析回 JSON，并用格式化函数 round-trip 校验。"""
import ast
import io
import json
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

SRC = (
    r"D:\工作\公司\项目\问数\web-json\analyze-streaming-v2"
    r"\docs\bug记录\不同表里指标拆分到多个问题里0911\database_meta_c2964c551a64.txt"
)
DST = (
    r"D:\工作\公司\项目\问数\web-json\analyze-streaming-v2"
    r"\docs\bug记录\不同表里指标拆分到多个问题里0911\database_meta_c2964c551a64.json"
)

ENTRY_RE = re.compile(r"^-\s?(?P<name>.+?)\((?P<code>[^()]*)\):\s?(?P<rest>.*)$")
SECTION_MAP = {"可用数据指标：": "metrics", "可用数据维度：": "dims", "可用数据表：": "tables"}
anomalies = []


def split_unit(rest):
    m = re.search(r"，单位：(.+)$", rest)
    if m:
        return rest[: m.start()], m.group(1)
    return rest, ""


def split_values(rest):
    m = re.search(r"，可选值：(\[.*\])$", rest)
    if m:
        try:
            return rest[: m.start()], ast.literal_eval(m.group(1))
        except Exception:
            anomalies.append("可选值解析失败: " + m.group(1)[:80])
            return rest[: m.start()], []
    return rest, []


lines = open(SRC, encoding="utf-8").read().splitlines()

metrics, dims, tables = [], [], []
section = None
cur = None  # {'kind': 'metric'|'dim', 'rest': ..., 'out': {...}}
table = None
mode = None  # 'dim' | 'measure'
last_col = None


def finalize():
    global cur
    if cur is None:
        return
    rest = cur["rest"]
    if cur["kind"] == "dim":
        rest, vals = split_values(rest)
        cur["out"]["possible_values"] = vals
    desc, unit = split_unit(rest)
    cur["out"]["description"] = desc
    if cur["kind"] == "metric":
        cur["out"]["unit"] = unit
    (metrics if cur["kind"] == "metric" else dims).append(cur["out"])
    cur = None


for line in lines:
    if line in SECTION_MAP:
        finalize()
        section = SECTION_MAP[line]
        mode, last_col = None, None
        continue

    if section in ("metrics", "dims"):
        if line == "":
            finalize()
            continue
        m = ENTRY_RE.match(line)
        if m:
            finalize()
            if section == "metrics":
                out = {
                    "metric_name": m.group("name"),
                    "metric_code": m.group("code"),
                    "description": "",
                    "unit": "",
                    "data_type": "",
                    "caliber_scope": "",
                    "aliases": [],
                }
                cur = {"kind": "metric", "rest": m.group("rest"), "out": out}
            else:
                out = {
                    "dimension_name": m.group("name"),
                    "dimension_code": m.group("code"),
                    "description": "",
                    "data_type": "",
                    "possible_values": [],
                    "aliases": [],
                }
                cur = {"kind": "dim", "rest": m.group("rest"), "out": out}
        elif cur is not None:
            cur["rest"] += "\n" + line  # 描述内含换行的续行
        elif line:
            anomalies.append(f"[{section}] 未识别行: {line[:80]}")
        continue

    if section == "tables":
        if line.startswith("表："):
            table = {
                "table_name": line[len("表："):],
                "display_name": "",
                "description": "",
                "columns": [],
            }
            tables.append(table)
            mode, last_col = None, None
        elif line.startswith("说明：") and table is not None:
            table["description"] = line[len("说明："):]
        elif line == "维度列：":
            mode = "dim"
        elif line == "指标列：":
            mode = "measure"
        elif line == "":
            mode, last_col = None, None
        elif line.startswith("- ") and mode and table is not None:
            m = ENTRY_RE.match(line)
            if m:
                last_col = {
                    "column_name": m.group("name"),
                    "data_type": m.group("code"),
                    "is_dimension": mode == "dim",
                    "is_measure": mode == "measure",
                    "description": m.group("rest"),
                }
                table["columns"].append(last_col)
            else:
                anomalies.append(f"[tables] 列行解析失败: {line[:80]}")
        elif table is not None and last_col is not None and line:
            last_col["description"] += "\n" + line
        elif table is not None and line:
            anomalies.append(f"[tables] 未识别行: {line[:80]}")

finalize()

meta = {
    "available_metrics": metrics,
    "available_dimensions": dims,
    "table_summaries": tables,
    "business_context": "",
}

# ---------------- round-trip 校验：重新格式化必须与归档原文一致 ----------------

def fmt(meta):
    out = []
    if meta.get("business_context"):
        out += [meta["business_context"], ""]
    ms = meta.get("available_metrics", [])
    if ms:
        out.append("可用数据指标：")
        for m in ms:
            u = f"，单位：{m['unit']}" if m.get("unit") else ""
            out.append(f"- {m['metric_name']}({m['metric_code']}): {m.get('description', '')}{u}")
        out.append("")
    ds = meta.get("available_dimensions", [])
    if ds:
        out.append("可用数据维度：")
        for d in ds:
            v = f"，可选值：{d['possible_values']}" if d.get("possible_values") else ""
            out.append(f"- {d['dimension_name']}({d['dimension_code']}): {d.get('description', '')}{v}")
        out.append("")
    ts = meta.get("table_summaries", [])
    if ts:
        out.append("可用数据表：")
        for t in ts:
            out.append(f"表：{t.get('table_name', '')}")
            if t.get("description"):
                out.append(f"说明：{t['description']}")
            cols = t.get("columns", [])
            dc = [c for c in cols if c.get("is_dimension")]
            mc = [c for c in cols if c.get("is_measure")]
            if dc:
                out.append("维度列：")
                for c in dc:
                    out.append(f"- {c['column_name']}({c['data_type']}): {c.get('description', '')}")
            if mc:
                out.append("指标列：")
                for c in mc:
                    out.append(f"- {c['column_name']}({c['data_type']}): {c.get('description', '')}")
            out.append("")
    return "\n".join(out)


original = open(SRC, encoding="utf-8").read()
rebuilt = fmt(meta)
match = original == rebuilt

n_cols = sum(len(t["columns"]) for t in tables)
n_dim_cols = sum(1 for t in tables for c in t["columns"] if c["is_dimension"])
n_meas_cols = sum(1 for t in tables for c in t["columns"] if c["is_measure"])
print(f"metrics={len(metrics)} dims={len(dims)} tables={len(tables)} columns={n_cols} (维度列 {n_dim_cols} / 指标列 {n_meas_cols})")
print(f"anomalies={len(anomalies)}")
for a in anomalies[:20]:
    print("  !", a)
print("round-trip 一致:", match)
if not match:
    import difflib
    diff = list(difflib.unified_diff(original.splitlines(), rebuilt.splitlines(), lineterm=""))
    changes = [l for l in diff if (l.startswith("+") or l.startswith("-")) and not l.startswith(("+++", "---"))]
    print("差异行数:", len(changes))
    for l in changes[:30]:
        print(" ", l[:160])

with open(DST, "w", encoding="utf-8") as f:
    json.dump({"database_meta": meta}, f, ensure_ascii=False, indent=2)
print("已写出:", DST)
