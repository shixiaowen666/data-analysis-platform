"""
Mock System A - Test Service
==============================
Simulates System A for development and testing.
Contains fake data covering all verification queries.
"""

import json
import logging
import os
import sys
import uuid
import re
from flask import Flask, request, jsonify
import requests

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [MockA] %(levelname)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)

app = Flask(__name__)


@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    return response


@app.before_request
def handle_preflight():
    if request.method == "OPTIONS":
        response = app.make_default_options_response()
        return response

SYSTEM_B_URL = "http://127.0.0.1:5000"

# ==============================================================================
# Database Meta Information (shared across all projects)
# ==============================================================================

DATABASE_META = {
    "available_metrics": [
        {"metric_name": "实际发电量", "metric_code": "actual_generation", "description": "实际发电量", "unit": "万kWh", "data_type": "float"},
        {"metric_name": "计划发电量", "metric_code": "planned_generation", "description": "计划发电量", "unit": "万kWh", "data_type": "float"},
        {"metric_name": "限电率", "metric_code": "curtailment_rate", "description": "弃风率/限电率", "unit": "%", "data_type": "float"},
        {"metric_name": "限电量", "metric_code": "curtailment_energy", "description": "限电量", "unit": "万kWh", "data_type": "float"},
        {"metric_name": "可利用小时数", "metric_code": "available_hours", "description": "可利用小时数", "unit": "h", "data_type": "float"},
        {"metric_name": "装机容量", "metric_code": "installed_capacity", "description": "装机容量", "unit": "MW", "data_type": "float"},
        {"metric_name": "实际出力", "metric_code": "actual_output", "description": "实际出力", "unit": "MW", "data_type": "float"},
        {"metric_name": "统调负荷", "metric_code": "dispatched_load", "description": "统调负荷", "unit": "MW", "data_type": "float"},
        {"metric_name": "负荷率", "metric_code": "load_rate", "description": "负荷率", "unit": "%", "data_type": "float"},
        {"metric_name": "跳闸次数", "metric_code": "trip_count", "description": "跳闸次数", "unit": "次", "data_type": "int"},
        {"metric_name": "西电东送电量", "metric_code": "transmission_energy", "description": "西电东送电量", "unit": "亿kWh", "data_type": "float"},
        {"metric_name": "新能源渗透率", "metric_code": "renewable_penetration", "description": "新能源渗透率", "unit": "%", "data_type": "float"},
        {"metric_name": "风电利用小时数", "metric_code": "wind_utilization_hours", "description": "风电利用小时数", "unit": "h", "data_type": "float"},
        {"metric_name": "度电成本", "metric_code": "cost_per_kwh", "description": "度电成本", "unit": "元/kWh", "data_type": "float"},
        {"metric_name": "风速", "metric_code": "wind_speed", "description": "风速", "unit": "m/s", "data_type": "float"},
        {"metric_name": "辐照度", "metric_code": "irradiance", "description": "辐照度", "unit": "W/m²", "data_type": "float"},
        {"metric_name": "机组可利用率", "metric_code": "unit_availability", "description": "机组可利用率", "unit": "%", "data_type": "float"},
        {"metric_name": "能量利用率", "metric_code": "energy_utilization", "description": "能量利用率", "unit": "%", "data_type": "float"},
        {"metric_name": "故障次数", "metric_code": "fault_count", "description": "故障次数", "unit": "次", "data_type": "int"},
        {"metric_name": "单次故障时间", "metric_code": "fault_duration", "description": "单次故障时间", "unit": "h", "data_type": "float"},
        {"metric_name": "台均故障次数", "metric_code": "fault_per_unit", "description": "台均故障次数", "unit": "次", "data_type": "float"},
        {"metric_name": "交易电价", "metric_code": "trade_price", "description": "交易电价", "unit": "元/kWh", "data_type": "float"},
        {"metric_name": "理论发电量", "metric_code": "theoretical_generation", "description": "理论发电量", "unit": "万kWh", "data_type": "float"},
        {"metric_name": "日照光强度", "metric_code": "solar_intensity", "description": "日照光强度", "unit": "W/m²", "data_type": "float"},
        {"metric_name": "风能利用率", "metric_code": "wind_energy_utilization", "description": "风能利用率", "unit": "%", "data_type": "float"},
        {"metric_name": "天然气现货价格", "metric_code": "gas_spot_price", "description": "天然气现货价格", "unit": "USD/MMBtu", "data_type": "float"},
        # Hospital metrics
        {"metric_name": "人力成本", "metric_code": "labor_cost", "description": "人力成本", "unit": "万元", "data_type": "float"},
        {"metric_name": "药品成本", "metric_code": "drug_cost", "description": "药品成本", "unit": "万元", "data_type": "float"},
        {"metric_name": "高值卫生材料", "metric_code": "high_value_material", "description": "高值卫生材料成本", "unit": "万元", "data_type": "float"},
        {"metric_name": "低值卫生材料", "metric_code": "low_value_material", "description": "低值卫生材料成本", "unit": "万元", "data_type": "float"},
        {"metric_name": "总务消耗品", "metric_code": "general_supplies", "description": "总务消耗品成本", "unit": "万元", "data_type": "float"},
        {"metric_name": "药占比", "metric_code": "drug_ratio", "description": "药占比", "unit": "%", "data_type": "float"},
        {"metric_name": "耗占比", "metric_code": "material_ratio", "description": "耗占比", "unit": "%", "data_type": "float"},
        {"metric_name": "医务性收入占比", "metric_code": "medical_income_ratio", "description": "医务性收入占比", "unit": "%", "data_type": "float"},
        {"metric_name": "住院次均费用", "metric_code": "avg_inpatient_cost", "description": "住院次均费用", "unit": "元", "data_type": "float"},
        {"metric_name": "门诊次均费用", "metric_code": "avg_outpatient_cost", "description": "门诊次均费用", "unit": "元", "data_type": "float"},
        {"metric_name": "平均住院天数", "metric_code": "avg_stay_days", "description": "平均住院天数", "unit": "天", "data_type": "float"},
        {"metric_name": "直接成本", "metric_code": "direct_cost", "description": "直接成本", "unit": "万元", "data_type": "float"},
        {"metric_name": "收入", "metric_code": "revenue", "description": "收入", "unit": "万元", "data_type": "float"},
        {"metric_name": "结余", "metric_code": "surplus", "description": "结余", "unit": "万元", "data_type": "float"},
        {"metric_name": "DRG次均医务性收入", "metric_code": "drg_avg_medical_income", "description": "DRG次均医务性收入", "unit": "元", "data_type": "float"},
        {"metric_name": "次均药品费用", "metric_code": "avg_drug_fee", "description": "次均药品费用", "unit": "元", "data_type": "float"},
        {"metric_name": "次均耗材费用", "metric_code": "avg_material_fee", "description": "住院次均耗材费用", "unit": "元", "data_type": "float"},
    ],
    "available_dimensions": [
        {"dimension_name": "场站名称", "dimension_code": "station_name", "description": "风电场/光伏站名称", "possible_values": ["华北风电场A", "华北风电场B", "华东风电场C", "华东风电场D", "华南光伏站E", "华南光伏站F", "西北风电场G", "西北风电场H"]},
        {"dimension_name": "子分公司", "dimension_code": "subsidiary", "description": "子分公司名称", "possible_values": ["华北公司", "华东公司", "华南公司", "西北公司", "西南公司"]},
        {"dimension_name": "省份", "dimension_code": "province", "description": "省份", "possible_values": ["广东", "广西", "云南", "贵州", "海南"]},
        {"dimension_name": "地区", "dimension_code": "region", "description": "地区", "possible_values": ["广东", "广西", "云南", "贵州", "海南"]},
        {"dimension_name": "月份", "dimension_code": "month", "description": "月份", "possible_values": []},
        {"dimension_name": "年份", "dimension_code": "year", "description": "年份", "possible_values": []},
        {"dimension_name": "日期", "dimension_code": "date", "description": "日期", "possible_values": []},
        {"dimension_name": "发电类型", "dimension_code": "generation_type", "description": "发电类型", "possible_values": ["风电", "光伏", "水电", "火电", "核电", "抽蓄"]},
        {"dimension_name": "线路名称", "dimension_code": "line_name", "description": "线路名称", "possible_values": []},
        {"dimension_name": "变电站名称", "dimension_code": "substation_name", "description": "变电站名称", "possible_values": []},
        {"dimension_name": "调管机构", "dimension_code": "dispatch_org", "description": "调管机构", "possible_values": []},
        {"dimension_name": "科室", "dimension_code": "department", "description": "科室", "possible_values": ["心内科", "心外科", "综合内科", "综合外科", "急诊科", "神经内科", "呼吸科"]},
        {"dimension_name": "系统", "dimension_code": "medical_system", "description": "医疗系统", "possible_values": ["心内系统", "心外系统", "综内系统", "综外系统", "急诊科系统"]},
        {"dimension_name": "风机编号", "dimension_code": "turbine_id", "description": "风机编号", "possible_values": []},
    ],
    "table_summaries": [
        {
            "table_name": "dwd_power_supply_day",
            "description": "供电量日表：日供电量与月/年累计供电量",
            "columns": [
                {"column_name": "ptdate", "data_type": "date", "description": "日期"},
                {"column_name": "daypowersupply", "data_type": "float", "description": "日供电量"},
                {"column_name": "monthpowersupply", "data_type": "float", "description": "月累计供电量"},
                {"column_name": "yearpowersupply", "data_type": "float", "description": "年累计供电量"},
            ],
        },
    ],
    "business_context": "包含南方电网、安贞医院、国能投资集团三个项目的运营数据。涵盖发电量、设备可用性、限电、电价、故障、气象、医院运营等多种指标。",
}


# ==============================================================================
# Import and combine all data registries
# ==============================================================================

from mock_system_a.data_registry_grid import DATA_REGISTRY as GRID_DATA
from mock_system_a.data_registry_hospital import HOSPITAL_DATA_REGISTRY as HOSPITAL_DATA
from mock_system_a.data_registry_energy import ENERGY_DATA_REGISTRY as ENERGY_DATA

ALL_DATA_REGISTRY = GRID_DATA + HOSPITAL_DATA + ENERGY_DATA


# ==============================================================================
# Data matching engine
# ==============================================================================

# Domain detection keywords
_GRID_INDICATORS = [
    "电网", "南方电网", "乌东德", "西电东送", "统调", "负荷率", "跳闸", "变电站", "调管",
    "停电", "复电", "线路", "出力", "渗透率", "中调", "地调", "全网", "发电量",
    "装机容量", "光伏装机", "非化石", "系统负荷", "南方五省",
]
_HOSPITAL_INDICATORS = [
    "科室", "心内", "心外", "综内", "综外", "急诊", "住院", "门诊", "医务",
    "药占比", "耗占比", "DRG", "医保", "手术", "床位", "次均", "成本增幅",
    "人力成本", "药品成本", "收入结构", "运营成绩", "医疗",
]
_ENERGY_INDICATORS = [
    "场站", "风电场", "光伏站", "限电", "弃风", "可利用小时", "能量利用率",
    "机组可利用率", "风机", "风速", "辐照度", "子分公司", "交易电价",
    "度电成本", "发电计划", "超发", "故障次数", "台均故障", "风能利用率",
    "天然气", "日照", "curtailment", "国能",
]

def _detect_domain(text: str) -> str:
    """Detect the domain of a query based on indicator keywords."""
    text_lower = text.lower()
    grid_score = sum(1 for kw in _GRID_INDICATORS if kw.lower() in text_lower)
    hosp_score = sum(1 for kw in _HOSPITAL_INDICATORS if kw.lower() in text_lower)
    energy_score = sum(1 for kw in _ENERGY_INDICATORS if kw.lower() in text_lower)
    max_score = max(grid_score, hosp_score, energy_score)
    if max_score == 0:
        return "unknown"
    if grid_score == max_score:
        return "grid"
    if energy_score == max_score:
        return "energy"
    return "hospital"

def _get_entry_domain(entry_idx: int) -> str:
    """Determine domain based on which registry the entry comes from."""
    grid_count = len(GRID_DATA)
    hosp_count = len(HOSPITAL_DATA)
    if entry_idx < grid_count:
        return "grid"
    elif entry_idx < grid_count + hosp_count:
        return "hospital"
    else:
        return "energy"


def match_data(query_description: str, entities: list, time_range: dict, context: dict, expected_columns: list = None, expected_table: str = "") -> dict:
    """
    Match query description against data registry.
    Returns the best matching data entry.

    Now also supports:
    - expected_columns[].filter: [{"operator": "=", "values": [...]}] for filtering
    - expected_table: target table name for more precise matching
    """
    query_lower = query_description.lower()
    # Also consider upstream_entities from context
    upstream = context.get("upstream_entities", []) if context else []
    original_q = context.get("original_question", "") if context else ""

    # Detect query domain from both the step description and the original question
    combined_text = f"{query_description} {original_q}"
    query_domain = _detect_domain(combined_text)
    logger.info(f"[MockA] Detected domain: {query_domain} for query: {query_description[:60]}")

    # Build set of expected column names for column-level matching
    expected_col_names = set()
    if expected_columns:
        for ec in expected_columns:
            if isinstance(ec, dict):
                expected_col_names.add(ec.get("name", "").lower())
            elif isinstance(ec, str):
                expected_col_names.add(ec.lower())

    # 步骤显式声明的 measure 列：候选条目必须至少能提供其中一列，否则跳过（防止兜底匹配返回无关表数据）
    expected_measure_names = set()
    if expected_columns:
        for ec in expected_columns:
            if isinstance(ec, dict) and ec.get("role") == "measure":
                expected_measure_names.add(ec.get("name", "").lower())

    best_match = None
    best_score = 0

    for entry_idx, entry in enumerate(ALL_DATA_REGISTRY):
        score = 0
        keywords = entry.get("keywords", [])

        # measure 列硬校验：候选条目必须能提供步骤声明的至少一个 measure 列，
        # 否则跳过（防止关键词兜底命中无关表的数据）
        if expected_measure_names:
            entry_col_names = {c.get("name", "").lower() for c in entry.get("data", {}).get("columns", [])}
            if not (expected_measure_names & entry_col_names):
                continue

        # Keyword matching
        for kw in keywords:
            kw_lower = kw.lower()
            if kw_lower in query_lower or kw_lower in original_q.lower():
                score += 2
            # Partial match
            for word in kw_lower.split():
                if len(word) >= 2 and word in query_lower:
                    score += 0.3

        # Entity matching
        for ent in entities:
            ent_lower = str(ent).lower()
            if ent_lower in query_lower:
                score += 1

        # Upstream entities boost
        for ue in upstream:
            ue_lower = str(ue).lower()
            for kw in keywords:
                if ue_lower in kw.lower():
                    score += 0.5

        # Column name matching: boost entries whose data columns match expected_columns
        # Only match on measure/specific columns, not generic dimension columns
        if expected_col_names:
            generic_cols = {'department', 'month', 'year', 'date', 'station_name', 'province',
                           'region', 'metric_name', 'category', 'subsidiary', 'turbine_id',
                           'line_name', 'substation_name', 'dispatch_org', 'unit'}
            specific_expected = expected_col_names - generic_cols
            entry_cols = set()
            for col in entry.get("data", {}).get("columns", []):
                entry_cols.add(col.get("name", "").lower())
            if entry_cols and specific_expected:
                overlap = specific_expected & entry_cols
                if overlap:
                    # Moderate boost for each matching specific column
                    score += len(overlap) * 1.0

        # expected_table matching: boost entries whose keywords match the table name
        if expected_table and score > 0:
            table_lower = expected_table.lower().replace("_", " ")
            for kw in keywords:
                kw_lower = kw.lower().replace("_", " ")
                if table_lower in kw_lower or kw_lower in table_lower:
                    score += 1.5
                    break

        # Domain-based scoring: penalise cross-domain matches
        if score > 0 and query_domain != "unknown":
            entry_domain = _get_entry_domain(entry_idx)
            if entry_domain != query_domain:
                # Heavy penalty for cross-domain match
                score *= 0.15

        if score > best_score:
            best_score = score
            best_match = entry

    if best_match and best_score > 0:
        matched = best_match["data"]

        # Apply filter from expected_columns if present
        matched = _apply_expected_column_filters(matched, expected_columns)

        return matched

    # Default: return empty data
    logger.warning(f"No matching data for: {query_description[:80]}")
    return {"columns": [], "rows": []}


def _apply_expected_column_filters(data: dict, expected_columns: list = None) -> dict:
    """
    Apply filter conditions from expected_columns to the matched data.
    Each expected column may have a 'filter' array like:
      [{"operator": "=", "values": ["广东", "广西"]}]
    This function filters rows based on those conditions.
    """
    if not expected_columns or not data.get("rows"):
        return data

    col_filters = {}
    for ec in expected_columns:
        if not isinstance(ec, dict):
            continue
        col_name = ec.get("name", "")
        filters = ec.get("filter", [])
        if filters:
            col_filters[col_name] = filters

    if not col_filters:
        return data

    # Check which filter column names actually exist in the data
    data_col_names = {col.get("name", "") for col in data.get("columns", [])}

    filtered_rows = []
    for row in data.get("rows", []):
        keep = True
        for col_name, filters in col_filters.items():
            if col_name not in data_col_names:
                continue
            val = row.get(col_name)
            for f in filters:
                op = f.get("operator", "=")
                values = f.get("values", [])
                if not _check_filter_condition(val, op, values):
                    keep = False
                    break
            if not keep:
                break
        if keep:
            filtered_rows.append(row)

    if filtered_rows:
        return {"columns": data["columns"], "rows": filtered_rows}
    else:
        # If filter eliminates all rows, return original (the filter columns may not match data columns)
        logger.warning(f"[MockA] Filter eliminated all rows, returning original {len(data.get('rows', []))} rows")
        return data


def _check_filter_condition(val, operator: str, values: list) -> bool:
    """Check if a value meets a filter condition."""
    if val is None:
        return False

    str_val = str(val)

    if operator == "=" or operator == "==":
        return str_val in [str(v) for v in values]
    elif operator == "!=":
        return str_val not in [str(v) for v in values]
    elif operator in (">", ">=", "<", "<="):
        try:
            num_val = float(str_val.replace(",", ""))
            for v in values:
                num_v = float(str(v).replace(",", ""))
                if operator == ">" and not (num_val > num_v):
                    return False
                elif operator == ">=" and not (num_val >= num_v):
                    return False
                elif operator == "<" and not (num_val < num_v):
                    return False
                elif operator == "<=" and not (num_val <= num_v):
                    return False
            return True
        except (ValueError, TypeError):
            return False
    elif operator == "in":
        return str_val in [str(v) for v in values]
    elif operator == "not in":
        return str_val not in [str(v) for v in values]
    else:
        return True


# ==============================================================================
# Compute result storage (for System B push-back)
# ==============================================================================

_compute_results = []

# 故障注入开关：复现线上 System A 返回 200+空body 的场景。
# 开启：curl -X POST http://127.0.0.1:5001/__toggle_empty_response -d '{"enabled": true}'
_empty_response_mode = os.getenv("MOCK_A_EMPTY_RESPONSE", "0") == "1"


@app.route("/__toggle_empty_response", methods=["POST", "GET"])
def toggle_empty_response():
    global _empty_response_mode
    if request.method == "POST":
        data = request.get_json(silent=True) or {}
        _empty_response_mode = bool(data.get("enabled"))
    return jsonify({"empty_response_mode": _empty_response_mode})



# ==============================================================================
# Routes
# ==============================================================================

@app.route("/api/v1/analyze", methods=["POST"])
def analyze():
    """
    Endpoint for test clients to submit queries.
    Forwards to System B with database meta.
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing request body"}), 400

        query = data.get("query", "")
        request_id = data.get("request_id", str(uuid.uuid4()))

        logger.info(f"[MockA] Received analyze request: {query[:80]}")

        # Forward to System B
        payload = {
            "request_id": request_id,
            "query": query,
            "database_meta": DATABASE_META,
            "user_id": data.get("user_id", "test"),
            "username": data.get("username", "test"),
        }

        resp = requests.post(
            f"{SYSTEM_B_URL}/api/v1/analyze",
            json=payload,
            timeout=300,
        )

        return jsonify(resp.json()), resp.status_code

    except requests.exceptions.ConnectionError:
        return jsonify({"error": "Cannot connect to System B"}), 502
    except Exception as e:
        logger.error(f"[MockA] Error: {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/query", methods=["POST"])
def query_data():
    """
    Data query callback endpoint.
    System B calls this to get data.
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing request body"}), 400

        request_id = data.get("request_id", "")
        step_id = data.get("step_id", "")
        query_description = data.get("query_description", "")
        entities = data.get("entities", [])
        time_range = data.get("time_range", {})
        context = data.get("context", {})

        logger.info(f"[MockA] Query callback: step={step_id}, desc={query_description[:80]}")

        expected_columns = data.get("expected_columns", [])
        expected_table = data.get("expected_table", "")

        logger.info(f"[MockA] expected_table={expected_table}, expected_columns count={len(expected_columns)}")
        # Log filter info if present
        for ec in expected_columns:
            if isinstance(ec, dict) and ec.get("filter"):
                logger.info(f"[MockA]   filter on '{ec.get('name')}': {ec.get('filter')}")

        matched_data = match_data(query_description, entities, time_range, context, expected_columns, expected_table)

        response = {
            "request_id": request_id,
            "step_id": step_id,
            "status": "success",
            "error_message": None,
            "data": matched_data,
        }

        logger.info(f"[MockA] Returning {len(matched_data.get('rows', []))} rows for step {step_id}")
        return jsonify(response)

    except Exception as e:
        logger.error(f"[MockA] Query error: {e}", exc_info=True)
        return jsonify({
            "request_id": data.get("request_id", "") if data else "",
            "step_id": data.get("step_id", "") if data else "",
            "status": "failure",
            "error_message": str(e),
            "data": {"columns": [], "rows": []},
        }), 500


@app.route("/api/v1/compute_result", methods=["POST"])
def receive_compute_result():
    """
    Endpoint for System B to push back computation results.
    Stores the result for logging/auditing purposes.
    """
    if _empty_response_mode:
        logger.warning("[MockA] FAULT-INJECTION: returning HTTP 200 with EMPTY body for /api/v1/compute_result")
        return "", 200
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing request body"}), 400

        request_id = data.get("request_id", "")
        step_id = data.get("step_id", "")
        description = data.get("description", "")
        result_data = data.get("result_data", {})

        logger.info(f"[MockA] Received compute result: request={request_id}, step={step_id}, desc={description[:80]}")

        # Store the result
        _compute_results.append({
            "request_id": request_id,
            "step_id": step_id,
            "description": description,
            "result_data": result_data,
        })

        row_count = 0
        if isinstance(result_data, dict):
            row_count = result_data.get("metadata", {}).get("row_count", len(result_data.get("rows", [])))

        logger.info(f"[MockA] Stored compute result: step={step_id}, rows={row_count}")

        return jsonify({
            "status": "success",
            "message": f"Compute result for {step_id} received and stored",
            "rows_received": row_count,
        })

    except Exception as e:
        logger.error(f"[MockA] Compute result error: {e}", exc_info=True)
        return jsonify({
            "status": "failed",
            "error_message": str(e),
        }), 500




@app.route("/getdata/ai", methods=["POST"])
def getdata_ai():
    """Alias for /api/v1/query - System B calls this to get data."""
    return query_data()

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "service": "mock_system_a"})


def main():
    logger.info("[MockA] Starting Mock System A on port 5001")
    app.run(host="127.0.0.1", port=5001, debug=False)


if __name__ == "__main__":
    main()
