# -*- coding: utf-8 -*-
"""
gen_database_meta.py —— 从生产元数据库（new_bi 5 张 olap_* 表）生成 database_meta.json
（只读，SELECT only；见 docs/database_meta/raw_meta数据源表说明.md）

用法：
  python scripts/gen_database_meta.py            # 试运行：写 database_meta_gen.json + 打印差异报告
  python scripts/gen_database_meta.py --write    # 核对后覆盖 data/database_meta.json（自动备份旧文件）

数据源映射：
  olap_table_pro(status=3)                  → table_summaries（tb_name/tb_cn_name/note）
  olap_table_field_mapping(status=1)        → columns（field_key/field_name/field_type_name/basic_type）
  olap_basic_pro(status=2)                  → metric_name/dimension_name（chinese_name）、aliases
  olap_basic_pro_indicator                  → metric unit / description（caliber_description）
  olap_basic_pro_dimension                  → possible_values（dimension_values JSON）
  ai_body_knowledge_info                    → business_context（业务知识）

字段角色判定：
  basic_type='维度'  → is_dimension=1
  basic_type='指标'  → is_measure=1
  '其他'/NULL        → 日期字段（timestamp / pf_status=1）按时间维度保留，其余不进指标维度池
  每张表的日期字段后自动挂 month/quarter/weekofyear/year 四个时间衍生维度

生成策略：完全以数据库为主——描述/单位/别名/维度值/业务知识全部来自库。
description 口径：指标/维度取 caliber_description，表取 note，库里没有就留空
（不回填中文名，描述统一由 AI 补全流程（docs/描述生成/方案.md）后续填充），
旧 database_meta.json 不参与生成（仅用于 diff 报告对比展示）。
"""
import argparse
import copy
import json
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

import pymysql

BASE_DIR = Path(__file__).resolve().parent.parent   # webapp/
sys.path.insert(0, str(BASE_DIR))
from app import config  # noqa: E402

DATABASE_META_PATH = BASE_DIR / "data" / "database_meta.json"
GEN_PATH = BASE_DIR / "data" / "database_meta_gen.json"
WITH_ID_PATH = BASE_DIR / "data" / "database_meta_with_id.json"

TIME_DERIVED = [
    ("month", "月", "varchar", "按月聚合（日期字段衍生）"),
    ("quarter", "季度", "varchar", "按季聚合（日期字段衍生）"),
    ("weekofyear", "年周号", "int", "按周聚合（日期字段衍生）"),
    ("year", "年", "varchar", "按年聚合（日期字段衍生）"),
]

# 视为日期/时间字段的数据类型
DATE_TYPES = {"timestamp", "datetime", "date", "time", "year"}

# caliber_description 中的无信息话术段（按"，"分段后整段丢弃）
CALIBER_NOISE_PREFIX = ("指标聚合类型为", "指标聚合类型", "聚合方式")


def clean_caliber(text):
    """去掉'指标聚合类型为【快照】'等模板话术段，保留有效口径文本"""
    if not text:
        return ""
    parts = [p.strip(" ，,；;") for p in re.split(r"[，,]", text)]
    kept = [p for p in parts if p and not p.startswith(CALIBER_NOISE_PREFIX)]
    return "，".join(kept)


def connect():
    return pymysql.connect(
        host=config.MYSQL_HOST, port=config.MYSQL_PORT,
        user=config.MYSQL_USER, password=config.MYSQL_PASSWORD,
        database=config.MYSQL_DB, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor, connect_timeout=10,
    )


def _in_clause(ids):
    return ", ".join(["%s"] * len(ids))


def fetch_tables(conn):
    """olap_table_pro：status=3 已上线的表"""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, tb_name, tb_cn_name, cn_name, note "
            "FROM olap_table_pro WHERE status = 3 ORDER BY id")
        return cur.fetchall()


def fetch_fields(conn, table_ids):
    """olap_table_field_mapping：字段映射（status=1）"""
    if not table_ids:
        return []
    with conn.cursor() as cur:
        cur.execute(
            f"SELECT table_id, field_key, field_name, field_type_name, basic_type, "
            f"basic_id, unit, pf_status, summary_key "
            f"FROM olap_table_field_mapping "
            f"WHERE table_id IN ({_in_clause(table_ids)}) AND status = 1 "
            f"ORDER BY table_id, id", tuple(table_ids))
        return cur.fetchall()


def fetch_basics(conn, basic_ids):
    """olap_basic_pro：指标/维度主档（chinese_name/alias/english_name）"""
    ids = sorted({b for b in basic_ids if b})
    out = {}
    if not ids:
        return out
    with conn.cursor() as cur:
        for i in range(0, len(ids), 500):
            chunk = ids[i:i + 500]
            cur.execute(
                f"SELECT id, chinese_name, alias, english_name "
                f"FROM olap_basic_pro WHERE id IN ({_in_clause(chunk)})", tuple(chunk))
            for r in cur.fetchall():
                out[r["id"]] = r
    return out


def fetch_indicators(conn, basic_ids):
    """olap_basic_pro_indicator：指标明细（unit/口径描述），按 olap_basic_pro_id 索引"""
    ids = sorted({b for b in basic_ids if b})
    out = {}
    if not ids:
        return out
    with conn.cursor() as cur:
        for i in range(0, len(ids), 500):
            chunk = ids[i:i + 500]
            cur.execute(
                f"SELECT olap_basic_pro_id, unit, caliber_description "
                f"FROM olap_basic_pro_indicator "
                f"WHERE olap_basic_pro_id IN ({_in_clause(chunk)})", tuple(chunk))
            for r in cur.fetchall():
                out[r["olap_basic_pro_id"]] = r
    return out


def fetch_dimensions(conn, basic_ids):
    """olap_basic_pro_dimension：维度明细（dimension_values），按 olap_basic_pro_id 索引"""
    ids = sorted({b for b in basic_ids if b})
    out = {}
    if not ids:
        return out
    with conn.cursor() as cur:
        for i in range(0, len(ids), 500):
            chunk = ids[i:i + 500]
            cur.execute(
                f"SELECT olap_basic_pro_id, dimension_values, caliber_description "
                f"FROM olap_basic_pro_dimension "
                f"WHERE olap_basic_pro_id IN ({_in_clause(chunk)})", tuple(chunk))
            for r in cur.fetchall():
                out[r["olap_basic_pro_id"]] = r
    return out


def parse_dimension_values(raw):
    """dimension_values 为 JSON 数组字符串，解析失败返回 []"""
    if not raw:
        return []
    try:
        vals = json.loads(raw)
        if isinstance(vals, list):
            return [v.strip() for v in vals if isinstance(v, str) and v.strip()]
    except (ValueError, TypeError):
        pass
    return []


def fetch_business_context(conn):
    """ai_body_knowledge_info：业务知识 → business_context（对象数组）
    每条 {knowledgeAlias: [...], knowledgeElement: 文本}；element 为空的行跳过，
    alias 为空输出空列表，后续可在库中补充"""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT knowledge_element, knowledge_alias "
            "FROM ai_body_knowledge_info ORDER BY id")
        rows = cur.fetchall()
    out = []
    for r in rows:
        element = (r["knowledge_element"] or "").strip()
        if not element:
            continue
        raw_alias = (r["knowledge_alias"] or "").strip()
        aliases = []
        if raw_alias:
            try:
                parsed = json.loads(raw_alias)
                if isinstance(parsed, list):
                    aliases = [a.strip() for a in parsed if isinstance(a, str) and a.strip()]
            except (ValueError, TypeError):
                aliases = [a.strip() for a in re.split(r"[、,，;；]", raw_alias) if a.strip()]
        out.append({"knowledgeAlias": aliases, "knowledgeElement": element})
    return out


def split_aliases(alias_str, extra=()):
    """alias 列按逗号/顿号拆分 + 附加别名"""
    out = []
    for a in (alias_str or "").replace("，", ",").replace("、", ",").split(","):
        a = a.strip()
        if a:
            out.append(a)
    for e in extra:
        e = (e or "").strip()
        if e and e not in out:
            out.append(e)
    return out


def build():
    # 以数据库为主：所有内容仅来自库，不再从旧 json 兜底合并
    conn = connect()
    try:
        tables = fetch_tables(conn)
        table_ids = [t["id"] for t in tables]
        fields = fetch_fields(conn, table_ids)
        basic_ids = [f["basic_id"] for f in fields]
        basics = fetch_basics(conn, basic_ids)
        indicators = fetch_indicators(conn, basic_ids)
        dimensions = fetch_dimensions(conn, basic_ids)
        biz_ctx = fetch_business_context(conn)
    finally:
        conn.close()

    fields_by_table = {}
    for f in fields:
        fields_by_table.setdefault(f["table_id"], []).append(f)

    new_tables, new_metrics, new_dims = [], {}, {}
    seen_metric_codes, seen_dim_codes = set(), set()

    for t in tables:
        tname = t["tb_name"]
        cols = []
        for f in fields_by_table.get(t["id"], []):
            col_name = f["field_key"].strip()
            ftype = (f["field_type_name"] or "").strip().lower()
            is_date = (ftype in DATE_TYPES) or bool(f["pf_status"])

            # 日期字段后挂时间衍生维度（从库日期字段推导，属库结构而非人工口径）
            if is_date and f["basic_type"] not in ("维度", "指标"):
                for k, cn, dt, desc in TIME_DERIVED:
                    cols.append({"column_name": k, "data_type": dt,
                                 "is_dimension": True, "is_measure": False,
                                 "description": desc, "agg_type": ""})
                    if k not in seen_dim_codes:
                        seen_dim_codes.add(k)
                        new_dims[k] = {
                            "dimension_id": f"test_{k}",
                            "dimension_name": cn,
                            "dimension_code": k,
                            "description": desc,
                            "data_type": dt,
                            "possible_values": [],
                            "aliases": [k],
                        }

            if f["basic_type"] == "维度":
                role = "dimension"
            elif f["basic_type"] == "指标":
                role = "metric"
            elif is_date:
                role = "time_dimension"
            else:
                role = None  # 管理列/未登记列：保留在 columns 里但不进指标/维度池

            col = {
                "column_name": col_name,
                "data_type": f["field_type_name"] or "",
                "is_dimension": role in ("dimension", "time_dimension"),
                "is_measure": role == "metric",
                "description": f["field_name"] or "",
                "agg_type": f.get("summary_key") or "",
            }
            cols.append(col)

            basic = basics.get(f["basic_id"]) if f["basic_id"] else None
            if role == "dimension":
                if col_name not in seen_dim_codes:
                    seen_dim_codes.add(col_name)
                    db_desc, db_pv = "", []
                    if f["basic_id"]:
                        d_detail = dimensions.get(f["basic_id"], {})
                        db_desc = clean_caliber(d_detail.get("caliber_description"))
                        db_pv = parse_dimension_values(d_detail.get("dimension_values"))
                    aliases = split_aliases(basic.get("alias") if basic else None,
                                            extra=[basic.get("english_name")] if basic else [])
                    new_dims[col_name] = {
                        "dimension_id": f["basic_id"] or "",
                        "dimension_name": (basic.get("chinese_name") if basic else None) or col_name,
                        "dimension_code": col_name,
                        "description": db_desc,
                        "data_type": col["data_type"],
                        "possible_values": db_pv,
                        "aliases": aliases,
                    }
            elif role == "metric":
                if col_name not in seen_metric_codes:
                    seen_metric_codes.add(col_name)
                    db_unit, db_desc = "", ""
                    if f["basic_id"]:
                        i_detail = indicators.get(f["basic_id"], {})
                        db_unit = (i_detail.get("unit") or "").strip()
                        db_desc = clean_caliber(i_detail.get("caliber_description"))
                    if not db_unit:
                        db_unit = (f.get("unit") or "").strip()
                    new_metrics[col_name] = {
                        "metric_id": f["basic_id"] or "",
                        "metric_name": (basic.get("chinese_name") if basic else None) or col_name,
                        "metric_code": col_name,
                        "description": db_desc,
                        "unit": db_unit,
                        "data_type": col["data_type"],
                        "caliber_scope": "",
                        "aliases": split_aliases(
                            basic.get("alias") if basic else None,
                            extra=[basic.get("english_name")] if basic else []),
                    }

        new_tables.append({
            "table_id": t["id"],
            "table_name": tname,
            "display_name": t["tb_cn_name"] or "",
            "description": (t["note"] or "").strip(),
            "columns": cols,
        })

    gen = {
        "database_meta": {
            "available_metrics": list(new_metrics.values()),
            "available_dimensions": list(new_dims.values()),
            "table_summaries": new_tables,
            "business_context": biz_ctx,
        },
    }

    diff = _diff_report(old_json_summary(), gen["database_meta"])
    return gen, diff


def strip_ids(gen):
    """深拷贝并剥离 id 字段，供 database_meta.json（无 id）与带 id 文件分开落地"""
    out = copy.deepcopy(gen)
    dm = out["database_meta"]
    for m in dm["available_metrics"]:
        m.pop("metric_id", None)
    for d in dm["available_dimensions"]:
        d.pop("dimension_id", None)
    for t in dm["table_summaries"]:
        t.pop("table_id", None)
    return out


def old_json_summary():
    """读旧 database_meta.json 仅用于 diff 对比展示，不参与生成"""
    try:
        old = json.loads(DATABASE_META_PATH.read_text(encoding="utf-8"))
        return old.get("database_meta", {})
    except (OSError, ValueError):
        return {}


def _diff_report(old_dm, new_dm):
    """结构化差异：类别 → {old_count, new_count, added, removed, extra}"""
    om = {m["metric_code"]: m for m in old_dm.get("available_metrics", [])}
    nm = {m["metric_code"]: m for m in new_dm["available_metrics"]}
    od = {d["dimension_code"]: d for d in old_dm.get("available_dimensions", [])}
    nd = {d["dimension_code"]: d for d in new_dm["available_dimensions"]}
    ot = {t["table_name"]: t for t in old_dm.get("table_summaries", [])}
    nt = {t["table_name"]: t for t in new_dm["table_summaries"]}

    def sect(og, ng, ok, nk):
        return {
            "old_count": len(og), "new_count": len(ng),
            "added": sorted(set(nk) - set(ok)),
            "removed": sorted(set(ok) - set(nk)),
        }

    diff = {
        "metrics": sect(om, nm, om, nm),
        "dimensions": sect(od, nd, od, nd),
        "tables": sect(ot, nt, ot, nt),
        "business_context": {
            "old_count": len(old_dm.get("business_context", [])),
            "new_count": len(new_dm.get("business_context", [])),
        },
        "columns_changed": [],
    }
    nmv = sum(1 for m in nm.values() if m.get("unit"))
    ndv = sum(1 for d in nd.values() if d.get("possible_values"))
    diff["metrics"]["with_unit"] = nmv
    diff["dimensions"]["with_possible_values"] = ndv
    for tn in sorted(nt):
        oc = {c["column_name"] for c in ot.get(tn, {}).get("columns", [])}
        nc = {c["column_name"] for c in nt[tn]["columns"]}
        if oc != nc:
            diff["columns_changed"].append({
                "table": tn,
                "old_count": len(oc), "new_count": len(nc),
                "added": sorted(nc - oc), "removed": sorted(oc - nc),
            })
    return diff


def format_diff(diff):
    """把结构化 diff 渲染成可读文本（命令行/日志用）"""
    m, d, t = diff["metrics"], diff["dimensions"], diff["tables"]
    lines = [
        f"指标: 旧 {m['old_count']} / 新 {m['new_count']}（有单位 {m['with_unit']}）；"
        f"移除 {m['removed'][:8]}；新增 {m['added'][:8]}",
        f"维度: 旧 {d['old_count']} / 新 {d['new_count']}（有维度值 {d['with_possible_values']}）；"
        f"移除 {d['removed'][:8]}；新增 {d['added'][:8]}",
        f"表:   旧 {t['old_count']} / 新 {t['new_count']}；新增 {t['added'][:8]}；移除 {t['removed'][:8]}",
        f"业务知识: 旧 {diff['business_context']['old_count']} / 新 {diff['business_context']['new_count']}",
    ]
    for c in diff["columns_changed"]:
        lines.append(f"  {c['table']}: 列 旧{c['old_count']}/新{c['new_count']} "
                     f"+{c['added'][:6]} -{c['removed'][:6]}")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="核对差异后覆盖 data/database_meta.json")
    args = ap.parse_args()

    if config.META_SOURCE_READONLY:
        # 只读自检：所有 cur.execute 的 SQL 首词必须是 SELECT
        src = Path(__file__).read_text(encoding="utf-8")
        sqls = re.findall(r'execute\(\s*f?"((?:SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\b[^"]*)', src, re.I)
        non_select = [s for s in sqls if not s.strip().lower().startswith("select")]
        assert not non_select, f"readonly guard: non-SELECT sql found: {non_select}"

    gen, diff = build()
    GEN_PATH.write_text(json.dumps(strip_ids(gen), ensure_ascii=False, indent=2), encoding="utf-8")
    WITH_ID_PATH.write_text(json.dumps(gen, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[trial] written {GEN_PATH.name}（无 id，供 database_meta.json）")
    print(f"[trial] written {WITH_ID_PATH.name}（含 id，供描述回写）")
    print("---- diff report ----")
    print(diff)

    if args.write:
        if DATABASE_META_PATH.exists():
            bak_dir = BASE_DIR / "data" / "backup"
            bak_dir.mkdir(exist_ok=True)
            ts = datetime.now().strftime("%Y%m%d_%H%M%S")
            bak = bak_dir / f"database_meta_{ts}.json"
            shutil.copy2(DATABASE_META_PATH, bak)
            print(f"[backup] {bak.relative_to(BASE_DIR)}")
        shutil.copy2(GEN_PATH, DATABASE_META_PATH)
        print(f"[write] database_meta.json updated ({DATABASE_META_PATH.stat().st_size} bytes)")
        print(f"[write] database_meta_with_id.json updated ({WITH_ID_PATH.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
