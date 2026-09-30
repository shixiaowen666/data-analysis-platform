"""
Interface Communication Module - system_a_client.py
====================================================
This is the SOLE communication channel between System B and System A.
All HTTP interactions with System A are encapsulated here.

Design goals:
- Single file for all System A communication logic
- Easy to swap between real System A and mock System A
- Independent testing of the interface layer
"""

import logging
import time
from typing import Any, Optional

import requests

from system_b.config import Config
from system_b.models.standard_dataframe import (
    ColumnDef,
    Metadata,
    StandardDataFrame,
    infer_column_type_from_values,
)
from system_b.models.exceptions import (
    SystemAConnectionError,
    SystemATimeoutError,
    SystemAQueryError,
)

logger = logging.getLogger(__name__)


# ==============================================================================
# Role inference helpers
# ==============================================================================

# Keywords patterns for role inference (priority 4)
DIMENSION_KEYWORDS = [
    "name", "id", "code", "type", "category", "region", "province",
    "station", "department", "line", "month", "year", "date", "quarter",
    "subsidiary", "company", "area", "district", "system", "科室", "系统",
]
MEASURE_KEYWORDS = [
    "rate", "amount", "quantity", "cost", "price", "total", "avg", "sum",
    "count", "hours", "energy", "power", "generation", "capacity", "load",
    "ratio", "revenue", "expense", "income", "fee", "speed", "efficiency",
    "value", "volume", "percent", "number", "利用率", "费用", "收入",
]


def _infer_role_by_keyword(col_name: str) -> str:
    """Infer column role by keyword pattern matching (priority 4)."""
    lower_name = col_name.lower()
    for kw in DIMENSION_KEYWORDS:
        if kw in lower_name:
            return "dimension"
    for kw in MEASURE_KEYWORDS:
        if kw in lower_name:
            return "measure"
    return "measure"  # default


def _infer_role_by_data_type(data_type: str) -> str:
    """Infer column role by data type (priority 3)."""
    if data_type in ("float", "int"):
        return "measure"
    if data_type in ("date", "datetime"):
        return "dimension"
    if data_type == "string":
        return "dimension"
    return "measure"


def _build_expected_columns_map(expected_columns: list[dict]) -> dict:
    """Build a lookup map from expected_columns list."""
    m = {}
    for ec in expected_columns:
        m[ec["name"]] = ec
    return m


# ==============================================================================
# Data conversion: System A response -> StandardDataFrame
# ==============================================================================

def convert_system_a_response_to_sdf(
    response_data: dict,
    step_id: str,
    expected_columns: Optional[list[dict]] = None,
    description: str = "",
    time_range: Optional[dict] = None,
    database_meta: Optional[dict] = None,
) -> StandardDataFrame:
    """
    Convert System A query response data to StandardDataFrame.

    Role inference priority:
    1. expected_columns role annotation
    2. database_meta is_dimension/is_measure flags
    3. Data type inference
    4. Keyword pattern matching
    """
    columns_info = response_data.get("columns", [])
    rows = response_data.get("rows", [])
    ec_map = _build_expected_columns_map(expected_columns or [])

    # Build meta column map from database_meta if available
    db_col_map = {}
    if database_meta and "table_summaries" in database_meta:
        for table in database_meta["table_summaries"]:
            for col in table.get("columns", []):
                db_col_map[col["column_name"]] = col

    # Authoritative metric units from database_meta, used to correct units
    # hallucinated by the decomposition LLM in expected_columns
    metric_unit_map = {}
    if database_meta:
        for m in database_meta.get("available_metrics", []):
            code = m.get("metric_code")
            if code and m.get("unit"):
                metric_unit_map[code.casefold()] = m["unit"]

    sdf_columns = []
    for col_info in columns_info:
        col_name = col_info["name"]
        col_data_type = col_info.get("data_type", "string")

        # If data_type not specified, infer from actual data
        if col_data_type == "string" and rows:
            values = [r.get(col_name) for r in rows]
            col_data_type = infer_column_type_from_values(values)

        # Priority 1: expected_columns
        if col_name in ec_map:
            role = ec_map[col_name].get("role", "measure")
            unit = ec_map[col_name].get("unit")
        # Priority 2: database_meta
        elif col_name in db_col_map:
            db_col = db_col_map[col_name]
            if db_col.get("is_dimension"):
                role = "dimension"
            elif db_col.get("is_measure"):
                role = "measure"
            else:
                role = _infer_role_by_data_type(col_data_type)
            unit = None
        # Priority 3: data type
        else:
            role = _infer_role_by_data_type(col_data_type)
            unit = None

        # Priority 4: keyword pattern (fallback override for edge cases)
        if col_name not in ec_map and col_name not in db_col_map:
            kw_role = _infer_role_by_keyword(col_name)
            if kw_role != role:
                role = kw_role

        # Unit: database_meta metric registry is authoritative; expected_columns
        # (LLM output) may hallucinate units, so only use it as fallback
        if col_name in metric_unit_map:
            meta_unit = metric_unit_map[col_name]
            if unit and unit.strip() and unit.strip().casefold() != meta_unit.casefold():
                logger.warning(
                    "[UnitFix] %s.%s unit corrected: LLM said '%s', meta says '%s'",
                    step_id, col_name, unit, meta_unit,
                )
            unit = meta_unit
        elif not unit:
            unit = col_info.get("unit")

        # Filter out invalid units (pure digits likely represent DB column width, e.g. VARCHAR(50))
        if unit and isinstance(unit, str) and unit.strip().isdigit():
            unit = None

        sdf_columns.append(
            ColumnDef(
                name=col_name,
                role=role,
                data_type=col_data_type,
                unit=unit,
                description=col_info.get("description", "") or db_col_map.get(col_name, {}).get("description", ""),
                source_ref=None,
                is_computed=False,
                nullable=True,
            )
        )

    # Type conversion for rows
    converted_rows = []
    for row in rows:
        new_row = {}
        for col in sdf_columns:
            val = row.get(col.name)
            if val is not None:
                try:
                    if col.data_type == "float":
                        if isinstance(val, str):
                            val = val.replace(",", "")
                        val = float(val)
                    elif col.data_type == "int":
                        if isinstance(val, str):
                            val = val.replace(",", "")
                        val = int(float(val))
                    elif col.data_type == "boolean":
                        val = bool(val)
                except (ValueError, TypeError):
                    pass
            new_row[col.name] = val
        converted_rows.append(new_row)

    metadata = Metadata(
        source_step=step_id,
        source_operation=None,
        description=description,
        row_count=len(converted_rows),
        time_range=time_range or {},
        lineage=[step_id],
        is_scalar=False,
        tags={},
    )

    return StandardDataFrame(metadata=metadata, columns=sdf_columns, rows=converted_rows)


# ==============================================================================
# HTTP Client for System A
# ==============================================================================

class SystemAClient:
    """
    HTTP client for communicating with System A.
    Encapsulates all HTTP details including timeout, retry, and logging.
    """

    def __init__(self, base_url: Optional[str] = None, timeout: Optional[int] = None, max_retries: Optional[int] = None):
        self.base_url = (base_url or Config.SYSTEM_A_BASE_URL).rstrip("/")
        self.timeout = timeout or Config.SYSTEM_A_TIMEOUT
        self.max_retries = max_retries if max_retries is not None else Config.SYSTEM_A_MAX_RETRIES
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def send_query_callback(
        self,
        request_id: str,
        step_id: str,
        query_description: str,
        time_range: dict,
        entities: list,
        expected_columns: list[dict],
        expected_table: Optional[str] = None,
        context: Optional[dict] = None,
    ) -> dict:
        """
        Send a data query callback to System A.
        POST {base_url}/api/v1/query

        Returns the raw response data dict from System A.
        Raises SystemAConnectionError, SystemATimeoutError, or SystemAQueryError.
        """
        url = f"{self.base_url}/api/v1/query"
        # 新api
        url = f"{self.base_url}/getdata/ai"
        payload = {
            "request_id": request_id,
            "step_id": step_id,
            "query_description": query_description,
            "time_range": time_range,
            "entities": entities,
            "expected_columns": expected_columns,
            "expected_table": expected_table or "",
            "context": context or {},
        }

        logger.info(f"[SystemAClient] Sending query callback: step={step_id}, desc={query_description[:80]}...")
        logger.debug(f"[SystemAClient] Full payload: {payload}")

        last_exception = None
        for attempt in range(1, self.max_retries + 1):
            try:
                resp = self.session.post(url, json=payload, timeout=self.timeout)
                logger.info(f"[SystemAClient] Response status: {resp.status_code} (attempt {attempt})")

                if resp.status_code != 200:
                    error_msg = f"System A returned HTTP {resp.status_code}: {resp.text[:500]}"
                    logger.error(f"[SystemAClient] {error_msg}")
                    raise SystemAQueryError(error_msg)

                result = resp.json()

                if result.get("status") == "failure":
                    error_msg = result.get("error_message", "Unknown error from System A")
                    logger.error(f"[SystemAClient] Query failed: {error_msg}")
                    raise SystemAQueryError(error_msg)

                logger.info(f"[SystemAClient] Query success: step={step_id}, rows={len(result.get('data', {}).get('rows', []))}")
                return result

            except requests.exceptions.Timeout:
                last_exception = SystemATimeoutError(f"System A request timeout after {self.timeout}s (attempt {attempt})")
                logger.warning(f"[SystemAClient] Timeout on attempt {attempt}")
            except requests.exceptions.ConnectionError as e:
                last_exception = SystemAConnectionError(f"Cannot connect to System A: {e}")
                logger.warning(f"[SystemAClient] Connection error on attempt {attempt}: {e}")
            except (SystemAQueryError, SystemATimeoutError, SystemAConnectionError):
                raise
            except Exception as e:
                last_exception = SystemAConnectionError(f"Unexpected error: {e}")
                logger.warning(f"[SystemAClient] Unexpected error on attempt {attempt}: {e}")

            if attempt < self.max_retries:
                time.sleep(1)

        raise last_exception

    def send_compute_result(
        self,
        request_id: str,
        step_id: str,
        step_type: str,
        description: str,
        result_data: dict,
        finished: bool = False
    ) -> dict:
        """
        Send computation result to System A for synchronization.
        POST {base_url}/api/v1/compute_result

        Returns the raw response dict from System A.
        """
        url = f"{self.base_url}/api/v1/compute_result"
        payload = {
            "request_id": request_id,
            "step_id": step_id,
            "step_type": step_type,
            "description": description,
            "result_data": result_data,
            "finished":finished
        }

        logger.info(f"[SystemAClient] Sending compute result: step={step_id}, desc={description[:80]}...")

        try:
            resp = self.session.post(url, json=payload, timeout=self.timeout)
            if resp.status_code != 200:
                logger.warning(f"[SystemAClient] Compute result sync returned HTTP {resp.status_code}")
                return {"status": "failed", "error_message": f"HTTP {resp.status_code}"}
            if not resp.text.strip():
                # 线上 System A 对该接口存在返回 200+空body 的情况（真实环境已确认），
                # 直接 resp.json() 会抛 JSONDecodeError: Expecting value
                logger.warning("[SystemAClient] Compute result sync: HTTP 200 but empty response body")
                return {"status": "failed", "error_message": "System A returned 200 with empty response body"}
            result = resp.json()
            logger.info(f"[SystemAClient] Compute result sync success: step={step_id}")
            return result
        except Exception as e:
            logger.warning(f"[SystemAClient] Compute result sync failed: {e}")
            return {"status": "failed", "error_message": str(e)}

    def clear_chat_history(
        self,
        chat_session_id: str,
        chat_id: str,
        auth_header: Optional[str] = None,
        tenant_id: Optional[str] = None,
    ) -> dict:
        """
        Clear all step records under one chat on System A.

        POST {base_url}/api/v1/chat-server/history/chat/clear

        Never raises: any failure is logged as warning and returned as
        {"status": "failed", "error_message": ...}. The caller treats this as
        non-fatal — clear is idempotent, leftovers can be cleared on retry.
        """
        url = f"{self.base_url}/api/v1/chat-server/history/chat/clear"
        payload = {
            "chatSessionId": chat_session_id,
            "chatId": chat_id,
        }

        headers = {}
        if auth_header:
            headers["Authorization"] = (
                auth_header if auth_header.startswith("Bearer ") else f"Bearer {auth_header}"
            )
        # 老客户端 token 可能不含 tenant_id claim，文档要求此时客户端透传
        if tenant_id:
            headers["tenantid"] = str(tenant_id)

        logger.info(f"[SystemAClient] Clearing chat history: chatId={chat_id}")

        try:
            resp = self.session.post(
                url, json=payload, timeout=Config.CHAT_CLEAR_TIMEOUT, headers=headers
            )
            if resp.status_code != 200:
                msg = f"clear chat history returned HTTP {resp.status_code}: {resp.text[:300]}"
                logger.warning(f"[SystemAClient] {msg}")
                return {"status": "failed", "error_message": msg}
            result = resp.json()
            logger.info(f"[SystemAClient] Clear chat history done: {result}")
            return result
        except Exception as e:
            logger.warning(f"[SystemAClient] Clear chat history failed: {e}")
            return {"status": "failed", "error_message": str(e)}

    def query_data(
        self,
        request_id: str,
        step_id: str,
        query_description: str,
        time_range: dict,
        entities: list,
        expected_columns: list[dict],
        expected_table: Optional[str] = None,
        context: Optional[dict] = None,
        description: str = "",
        database_meta: Optional[dict] = None,
    ) -> StandardDataFrame:
        """
        High-level method: send query to System A and convert response to StandardDataFrame.
        """
        result = self.send_query_callback(
            request_id=request_id,
            step_id=step_id,
            query_description=query_description,
            time_range=time_range,
            entities=entities,
            expected_columns=expected_columns,
            expected_table=expected_table,
            context=context,
        )

        response_data = result.get("data", {"columns": [], "rows": []})
        sdf = convert_system_a_response_to_sdf(
            response_data=response_data,
            step_id=step_id,
            expected_columns=expected_columns,
            description=description,
            time_range=time_range,
            database_meta=database_meta,
        )
        return sdf
