"""
Meta Governance Module
======================
元数据治理：从元数据声明合并出每列权威 agg_type，并渲染为提示词注入的 JSON。

agg_type 取值优先级（高→低）：
  ① available_metrics[].description 显式声明（"指标聚合类型为【快照】"等句式）
  ①.5 列级 description 同句式声明（真实元数据常见写法）
  ② 列级 agg_type 字段（time_format→snapshot，sum/avg 原值，空→sum 兜底）

  （semantic_overrides.yaml 覆盖清单与 promote 机制已于 2026-09-15 退役：
   92 条纠偏已全部回写元数据本体，维护点收敛为元数据描述本身。
   历史归档见 docs/v1.0.2/semantic_overrides.yaml.retired-20260915.yaml）

agg_type 值域：
  sum        事件流量，可安全求和（默认）
  snapshot   时点存量，跨行求和会虚增，取日期截面
  cumulative 期内累计，本身已是累加结果，取期末行，禁止再求和
  avg        比率，禁止求和，取当日值或加权
"""

import json
import logging
import re
from typing import Dict, Optional

logger = logging.getLogger(__name__)

# 声明句式：「指标聚合类型为【快照】」「聚合类型【快照】」「聚合类型：快照」「聚合类型为【avg】」
_DECL_PATTERN = re.compile(r"聚合类型[为是]?\s*[【\(:：]\s*([^\】\)：:]+?)\s*[\】)]?\s*[，,。;；\s]|聚合类型[为是]?\s*[【\(:：]\s*([^\】\)：:]+?)\s*[\】)]?$")

_AGG_NOTE = {
    "snapshot": "时点存量，禁止sum",
    "cumulative": "取期末行，禁止再sum",
    "avg": "比率，禁止sum",
}

# 列级 agg_type 原值中允许透传的值
_PASS_THROUGH = {"sum", "avg"}

# 无身份列兜底判定：data_type 为时间类型
_TIME_TYPE_PATTERN = re.compile(r"time|date", re.IGNORECASE)


def parse_aggregation_decl(description: str) -> Optional[str]:
    """从指标 description 提取声明句式，映射为标准 agg_type；无声明返回 None。"""
    if not description:
        return None
    m = _DECL_PATTERN.search(str(description))
    if not m:
        return None
    raw = (m.group(1) or m.group(2) or "").strip()
    if not raw:
        return None
    low = raw.lower()
    if "快照" in raw:
        return "snapshot"
    if "累计" in raw:
        return "cumulative"
    if low in ("avg", "average") or "平均" in raw:
        return "avg"
    if low == "sum":
        return "sum"
    logger.info("[MetaGov] 未识别的声明值 %r，忽略", raw)
    return None


def merge_database_meta(database_meta: dict) -> dict:
    """
    治理合并：以 table_summaries 列为主干，合并 metrics 声明与列级 agg_type。
    纯函数，不修改入参。
    """
    # metrics 索引：code -> (声明agg, cn, unit)
    metric_map: Dict[str, dict] = {}
    for m in database_meta.get("available_metrics", []) or []:
        code = str(m.get("metric_code") or "")
        if not code:
            continue
        metric_map.setdefault(code, {
            "cn": str(m.get("metric_name") or ""),
            "unit": str(m.get("unit") or ""),
            "decl": parse_aggregation_decl(m.get("description")),
        })

    # 维度字典索引：code_lower -> (枚举values, 名称name, 描述desc)
    # （available_dimensions 顶层字典，42 条中 35 个有非空枚举、8 个有独立业务描述）
    dim_map: Dict[str, dict] = {}
    for d in database_meta.get("available_dimensions", []) or []:
        code = str(d.get("dimension_code") or "").strip()
        if not code:
            continue
        dim_map.setdefault(code.lower(), {
            "values": [str(v) for v in (d.get("possible_values") or []) if str(v).strip()],
            "name": str(d.get("dimension_name") or ""),
            "desc": str(d.get("description") or ""),
        })

    matched_codes = set()
    governed_tables = []

    for t in database_meta.get("table_summaries", []) or []:
        table_name = str(t.get("table_name") or "")
        seen_cols = set()
        out_cols = []
        fallback_time = set()

        for c in t.get("columns", []) or []:
            col_name = str(c.get("column_name") or "")
            if not col_name:
                continue
            is_dim = bool(c.get("is_dimension"))
            is_measure = bool(c.get("is_measure"))
            if not is_dim and not is_measure:
                # 无身份列兜底：pt 前缀或时间类型的按时间列保留（如
                # industry_load.ptdate，is_dimension/is_measure 均未标）
                if _TIME_COL_PATTERN.match(col_name) or _TIME_TYPE_PATTERN.search(
                        str(c.get("data_type") or "")):
                    fallback_time.add(col_name)
                    logger.warning(
                        "[MetaGov] 表 %s 列 %s 无身份标志(is_dimension=0,is_measure=0)，"
                        "按时间列兜底保留", table_name, col_name)
                continue

            # 同名列去重：保留首个带类型定义的
            dup_key = col_name.lower()
            if dup_key in seen_cols:
                continue
            seen_cols.add(dup_key)

            desc = str(c.get("description") or c.get("column_comment") or "")
            mm = metric_map.get(col_name)
            if mm:
                matched_codes.add(col_name)
            # 列级声明（真实元数据常见"聚合类型【快照】"写在列描述里）
            col_decl = parse_aggregation_decl(desc)

            col = {
                "name": col_name,
                "cn": desc or (mm["cn"] if mm else ""),
                "data_type": str(c.get("data_type") or ""),
                "role": "measure" if is_measure else "dimension",
            }

            if col["role"] != "measure":
                dd = dim_map.get(col_name.lower())
                if dd:
                    if dd["values"]:
                        col["values"] = list(dd["values"])
                    if dd["desc"] and dd["desc"] != dd["name"] and dd["desc"] != col["cn"]:
                        col["dim_desc"] = dd["desc"]
                out_cols.append(col)
                continue

            # ---- measure 的 agg_type 两级合并 ----
            decl = mm["decl"] if mm else None
            col_agg = str(c.get("agg_type") or "").strip()

            if decl is not None:
                agg, src = decl, "decl"
            elif col_decl is not None:
                agg, src = col_decl, "col_decl"
            elif col_agg == "time_format":
                agg, src = "snapshot", "time_format"
            elif col_agg in _PASS_THROUGH:
                agg, src = col_agg, "column"
            else:
                agg, src = "sum", "default"

            col["agg_type"] = agg
            col["unit"] = mm["unit"] if mm else ""
            if agg in _AGG_NOTE:
                col["note"] = _AGG_NOTE[agg]
            col["_src"] = src
            # 规范指标名与列描述不一致时保留别名（如 desc「容量」alias「业扩容量」）
            if mm and mm["cn"] and mm["cn"] != col["cn"]:
                col["alias"] = mm["cn"]
            out_cols.append(col)

        entry = {
            "table": table_name,
            "desc": str(t.get("table_comment") or t.get("description") or t.get("desc") or ""),
            "columns": out_cols,
        }
        if fallback_time:
            entry["fallback_time"] = sorted(fallback_time)
        governed_tables.append(entry)

    # 孤立 metrics（无表列对应）：丢弃并记录，防止提示词膨胀
    orphan = sorted(set(metric_map) - matched_codes)
    if orphan:
        logger.info("[MetaGov] %d 个指标无表列对应，已丢弃: %s", len(orphan), orphan[:10])

    return {
        "tables": governed_tables,
        "business_context": str(database_meta.get("business_context") or ""),
    }


def build_governed_index(governed: dict) -> Dict[str, Dict[str, dict]]:
    """治理结果 -> {table_lower: {col_lower: col_dict}}，供校验器 O(1) 查询。"""
    index: Dict[str, Dict[str, dict]] = {}
    for t in governed.get("tables", []):
        cols = index.setdefault(str(t.get("table", "")).lower(), {})
        for c in t.get("columns", []):
            cols[str(c.get("name", "")).lower()] = c
    return index


def get_column_agg(index: dict, table: str, column: str) -> Optional[str]:
    """查询某表某列的治理后 agg_type；表/列不存在返回 None。"""
    col = index.get(str(table or "").lower(), {}).get(str(column or "").lower())
    if col is None:
        return None
    return col.get("agg_type")


_TIME_COL_PATTERN = re.compile(r"^pt", re.IGNORECASE)


def render_database_meta(governed: dict) -> str:
    """治理结果渲染为提示词注入文本：说明头 + 单行 JSON 数组。"""
    tables_out = []
    for t in governed.get("tables", []):
        entry = {"table": t["table"]}
        if t.get("desc"):
            entry["desc"] = t["desc"]
        time_cols = [c["name"] for c in t["columns"]
                     if c["role"] == "dimension" and _TIME_COL_PATTERN.match(c["name"])]
        for ft in t.get("fallback_time", []) or []:
            if ft not in time_cols:
                time_cols.append(ft)
        plain_dims = [c for c in t["columns"]
                      if c["role"] == "dimension" and c["name"] not in time_cols]
        cols_out = []
        for c in plain_dims:
            d = {"name": c["name"], "role": "dimension"}
            if c.get("cn"):
                d["cn"] = c["cn"]
            if c.get("dim_desc"):
                d["dim_desc"] = c["dim_desc"]
            cols_out.append(d)
        for c in t["columns"]:
            if c["role"] != "measure":
                continue
            d = {"name": c["name"], "role": "measure", "cn": c.get("cn", "")}
            d["agg_type"] = c.get("agg_type", "sum")
            if c.get("unit"):
                d["unit"] = c["unit"]
            if c.get("note"):
                d["note"] = c["note"]
            if c.get("alias"):
                d["alias"] = c["alias"]
            cols_out.append(d)
        if time_cols:
            entry["time_columns"] = time_cols
        entry["columns"] = cols_out
        tables_out.append(entry)

    # 维度枚举顶层全局字典：键=列名，值=全量枚举（只渲染一次，避免多表复用列重复几十遍）
    dim_values = {}
    for t in governed["tables"]:
        for c in t["columns"]:
            vals = c.get("values")
            if vals and c["name"] not in dim_values:
                dim_values[c["name"]] = vals
    payload = {"tables": tables_out}
    if dim_values:
        payload["dimension_values"] = dim_values
    bc = governed.get("business_context")
    if bc:
        payload["business_context"] = bc

    header = (
        "【可用数据库元信息（结构化 JSON）】\n"
        "字段说明：role=dimension维度/measure指标；agg_type 为该指标的聚合语义，"
        "sum=事件流量可安全求和；snapshot=时点存量，跨行求和会虚增，只能取日期截面；"
        "cumulative=期内累计值，直接取最后一行，禁止逐日重算或再求和；"
        "avg=比率，禁止求和。note 是该列的处理约束，必须遵守。"
        "time_columns 是该表的时间维度列（年/月/季/周/日）。\n"
        "dimension_values 是全库维度取值枚举字典（键为维度列名），query 步骤 filter 中的"
        "维度值必须从对应列的枚举中选取；维度列的 dim_desc 是业务说明（可能含别名映射，"
        "如【公变客户】别名为【低压客户】，用户说别名时按正名过滤）；alias 是指标的规范名称别名。\n"
        "未标注 agg_type 的维度列不参与聚合。以下为全部数据表：\n"
    )
    return header + json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
