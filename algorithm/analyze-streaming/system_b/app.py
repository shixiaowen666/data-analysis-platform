"""
System B Main API Server
=========================
Entry point for System B. Exposes the /api/v1/analyze endpoint,
log management endpoints, and prompt versioning endpoints.
"""

import io
import json
import logging
import os
import re
import sys
import threading
import uuid
from datetime import datetime
from typing import Optional
from pathlib import Path
from flask import Flask, request, jsonify, send_file, g

from system_b.config import Config
from system_b.decomposition.step_decomposer import decompose_query_stream
from system_b.models.exceptions import StepDecompositionError
from system_b.execution.dag_engine import DAGEngine
from system_b.utils import db
from system_b.utils import recall_client
from system_b.utils.log_manager import (
    write_operation_log, get_log_list, delete_log_by_user_date,
    get_users_with_logs, backfill_operation_logs,
)
from system_b.utils.prompt_version_manager import (
    list_groups, create_group, update_group, delete_group,
    list_versions, switch_version, get_current_version,
    get_version_content, update_version, delete_version,
    ensure_initial_versions,
)
from system_b.utils.step_result_notifier import AnalysisProgressReporter
from system_b.utils import batch_report

# Add-on: in-process progress bus + SSE endpoint (no existing logic touched)
from system_b.utils.progress_push import progress_bp, store_result
from system_b.communication.chatbi_client import ChatBiError, get_chatbi_client


_ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-9;]*m")


class HourlyFileHandler(logging.Handler):
    """Log handler that writes to {LOG_DIR}/run/{YYYY-MM-DD}/{HH}.log, rotating hourly."""

    def __init__(self, log_dir: str):
        super().__init__()
        self.log_dir = log_dir
        self._current_key: str = ""
        self._fp = None

    def _rotate_if_needed(self):
        now = datetime.now()
        date_str = now.strftime("%Y-%m-%d")
        hour_str = now.strftime("%H")
        key = f"{date_str}/{hour_str}"
        if key != self._current_key:
            if self._fp:
                self._fp.close()
            day_dir = os.path.join(self.log_dir, date_str)
            os.makedirs(day_dir, exist_ok=True)
            log_path = os.path.join(day_dir, f"{hour_str}.log")
            self._fp = open(log_path, "a", encoding="utf-8")
            self._current_key = key

    def emit(self, record):
        self._rotate_if_needed()
        msg = self.format(record)
        msg = _ANSI_ESCAPE_RE.sub("", msg) + "\n"
        self._fp.write(msg)
        self._fp.flush()

    def close(self):
        if self._fp:
            self._fp.close()
            self._fp = None
        super().close()


# Force UTF-8 encoding for stdout/stderr on Windows to prevent garbled
# Chinese characters when output is redirected to a file (e.g., start.bat).
# Skip under pytest: replacing sys.stdout/stderr here destroys the test
# runner's capture layer (unconfigure raises "I/O operation on closed file").
if "pytest" not in sys.modules:
    if hasattr(sys.stdout, "buffer"):
        sys.stdout = __import__("io").TextIOWrapper(
            sys.stdout.buffer, encoding="utf-8", errors="replace", line_buffering=True
        )
    if hasattr(sys.stderr, "buffer"):
        sys.stderr = __import__("io").TextIOWrapper(
            sys.stderr.buffer, encoding="utf-8", errors="replace", line_buffering=True
        )

_log_format = "%(asctime)s [%(name)s] %(levelname)s: %(message)s"
_handlers: list = [logging.StreamHandler(sys.stdout)]

if Config.LOG_DIR:
    try:
        _run_log_dir = os.path.join(Config.LOG_DIR, "run")
        os.makedirs(_run_log_dir, exist_ok=True)
        _handlers.append(HourlyFileHandler(_run_log_dir))
    except OSError as e:
        print(f"Warning: cannot create log dir '{Config.LOG_DIR}': {e}, file logging disabled.")

logging.basicConfig(
    level=getattr(logging, Config.LOG_LEVEL, logging.INFO),
    format=_log_format,
    handlers=_handlers,
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

from system_b.utils.access_log import register_access_log
register_access_log(app)

from system_b.auth import register_auth
register_auth(app)

app.register_blueprint(progress_bp)

# =============================================================================
# Static files for testing (HTML pages served from /test_*)
# =============================================================================
STATICS_DIR = Path(__file__).parent / "statics"


@app.route("/test_logs", methods=["GET"])
def test_logs_page():
    """Serve the static test page for logs management."""
    page_path = STATICS_DIR / "test_logs.html"
    return send_file(str(page_path), mimetype="text/html")


@app.route("/test_prompts", methods=["GET"])
def test_prompts_page():
    """Serve the static test page for prompts management."""
    page_path = STATICS_DIR / "test_prompts.html"
    return send_file(str(page_path), mimetype="text/html")


@app.route("/test_analyze_stream", methods=["GET"])
def test_analyze_stream_page():
    """Serve the static test page for analyze endpoint with streaming response."""
    page_path = STATICS_DIR / "test_analyze_stream.html"
    return send_file(str(page_path), mimetype="text/html")


@app.route("/test_all", methods=["GET"])
def test_all_page():
    """Serve the test hub page aggregating all test pages via iframes."""
    page_path = STATICS_DIR / "test_all.html"
    return send_file(str(page_path), mimetype="text/html")


@app.route("/test_meta", methods=["GET"])
def test_meta_page():
    """Serve the static test page for database metadata inspection."""
    page_path = STATICS_DIR / "test_meta.html"
    return send_file(str(page_path), mimetype="text/html")


@app.route("/test_model_config", methods=["GET"])
def test_model_config_page():
    """Serve the static test page for model configuration management."""
    page_path = STATICS_DIR / "test_model_config.html"
    return send_file(str(page_path), mimetype="text/html")


@app.route("/test_db", methods=["GET"])
def test_db_page():
    """Serve the static test page for database administration."""
    page_path = STATICS_DIR / "test_db.html"
    return send_file(str(page_path), mimetype="text/html")


# ==============================================================================
# Startup initialization
# ==============================================================================

def _init_tables():
    """Ensure all required tables exist on startup."""
    try:
        db.ensure_user_operation_log_table()
        db.ensure_batch_record_table()
        db.ensure_prompt_group_table()
        db.ensure_prompt_version_table()
        ensure_initial_versions()
        backfill_operation_logs()
        logger.info("[Startup] Database tables ensured")
    except Exception as exc:
        logger.warning(
            f"[Startup] Failed to ensure tables (non-fatal): {exc}\n"
            f"  MySQL: {Config.MYSQL_HOST}:{Config.MYSQL_PORT}/{Config.MYSQL_DATABASE}"
        )


# ==============================================================================
# Database administration (test page /test_db)
# ==============================================================================

@app.route("/api/v1/db/info", methods=["GET"])
def db_info():
    """Connection info (password masked) and existence status of managed tables.
    If the database itself is missing, still returns 200 with database_exists=False."""
    info = {
        "host": Config.MYSQL_HOST,
        "port": Config.MYSQL_PORT,
        "user": Config.MYSQL_USER,
        "database": Config.MYSQL_DATABASE,
        "password": "******",
    }
    try:
        existing = set(db.list_tables())
        info["database_exists"] = True
        info["tables"] = [{"name": t, "exists": t in existing} for t in db.MANAGED_TABLES]
    except Exception as exc:
        message = str(exc)
        if "(1049" in message or "Unknown database" in message:
            info["database_exists"] = False
            info["tables"] = [{"name": t, "exists": False} for t in db.MANAGED_TABLES]
        else:
            return _fail(f"数据库连接失败: {exc}", 500)
    return _ok(data=info)


@app.route("/api/v1/db/ensure-tables", methods=["POST"])
def db_ensure_tables():
    """Create the database if missing, then tables + seed data, mirroring
    startup _init_tables. Returns per-step result so the page can show
    what was created/fixed."""
    steps = []

    def _run(name, func):
        try:
            func()
            steps.append({"step": name, "status": "success", "error": None})
        except Exception as exc:
            steps.append({"step": name, "status": "failed", "error": str(exc)[:500]})

    _run(f"数据库 {Config.MYSQL_DATABASE}", db.ensure_database)
    _run("user_operation_log", db.ensure_user_operation_log_table)
    _run("batch_record", db.ensure_batch_record_table)
    _run("prompt_group (含种子数据)", db.ensure_prompt_group_table)
    _run("prompt_version", db.ensure_prompt_version_table)
    _run("提示词初始版本", ensure_initial_versions)
    _run("日志回填", backfill_operation_logs)

    failed = [s for s in steps if s["status"] == "failed"]
    try:
        existing = set(db.list_tables())
        for t in db.MANAGED_TABLES:
            steps.append({"step": f"检查表 {t}", "status": "success" if t in existing else "failed",
                          "error": None if t in existing else "表仍不存在"})
    except Exception as exc:
        return _fail(f"建表后校验失败: {exc}", 500)

    ok = not failed
    return _ok(data={"success": ok, "steps": steps},
               message="全部就绪" if ok else "部分步骤失败，详见 steps")


@app.route("/api/v1/db/table", methods=["GET"])
def db_table():
    """Schema or paged rows of a managed table. Query: name, mode=schema|rows,
    page, page_size, keyword."""
    name = (request.args.get("name") or "").strip()
    if name not in db.MANAGED_TABLES:
        return _fail(f"参数错误：表 {name} 不在允许列表中")
    mode = (request.args.get("mode") or "rows").strip()
    try:
        if mode == "schema":
            return _ok(data={"table": name, "schema": db.describe_table(name)})
        page = int(request.args.get("page") or 1)
        page_size = int(request.args.get("page_size") or 20)
        keyword = (request.args.get("keyword") or "").strip()[:100]
        return _ok(data=db.query_table_rows(name, page=page, page_size=page_size,
                                            keyword=keyword))
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] db/table error: {exc}", exc_info=True)
        return _fail("查询失败", 500)


@app.route("/api/v1/db/sql", methods=["POST"])
def db_sql():
    """Execute a SQL script (paste or uploaded file), statement by statement.
    Body: JSON {sql} or multipart file field 'file'. Limited to 2MB."""
    try:
        script = ""
        if request.files:
            f = request.files.get("file") or next(iter(request.files.values()))
            raw = f.read()
            if len(raw) > 2 * 1024 * 1024:
                return _fail("脚本文件超过 2MB 限制")
            script = raw.decode("utf-8", errors="replace")
            filename = f.filename or "upload.sql"
        else:
            data = request.get_json(silent=True) or {}
            script = str(data.get("sql") or "")
            filename = "inline.sql"
            if len(script.encode("utf-8")) > 2 * 1024 * 1024:
                return _fail("脚本内容超过 2MB 限制")
        if not script.strip():
            return _fail("脚本内容为空")
        logger.info(f"[SystemB] db/sql executing {filename} "
                    f"({len(script.encode('utf-8'))} bytes) by test page")
        result = db.execute_sql_script(script)
        return _ok(data=result, message=f"执行完成：成功 {result['summary']['success']} 条，"
                                        f"失败 {result['summary']['failed']} 条")
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] db/sql error: {exc}", exc_info=True)
        return _fail(f"执行失败: {exc}", 500)


# ==============================================================================
# Analyze endpoint
# ==============================================================================

@app.route("/api/v1/analyze", methods=["POST"])
def analyze():
    """
    Main entry point for System B.
    Receives query and database meta, performs analysis, auto-logs operation.
    """
    data = None
    recall_info = None
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing request body"}), 200

        request_id = data.get("request_id", f"test-{uuid.uuid4().hex[:12]}")
        # 生产链路 request_id 即 "sessionId@chatId"，拆出供 chat/clear 使用；
        # 无 @（测试页默认 id）时回退显式字段，都没有则跳过 clear
        if "@" in request_id:
            chat_session_id, chat_id = request_id.split("@", 1)
            _remember_prod_request_id(request_id)
        else:
            chat_session_id = data.get("chatSessionId", "")
            chat_id = data.get("chatId", "")
        query = data.get("query", "")
        database_meta = data.get("database_meta", {})
        # recall 元数据缩圈：配置生效时用缩圈 meta 替换请求体全量；
        # 失败/超时/空结果降级走原全量（recall_client 内部已记录降级日志）
        recalled = None
        if query:
            recalled, recall_info = recall_client.fetch_database_meta_with_info(query)
            if recalled is not None:
                database_meta = recalled
                logger.info(
                    "[SystemB] 使用 recall 缩圈 database_meta 替换请求体全量"
                    "（metrics=%d, dims=%d, tables=%d）",
                    len(recalled.get("available_metrics") or []),
                    len(recalled.get("available_dimensions") or []),
                    len(recalled.get("table_summaries") or []),
                )
        _seed_database_meta_if_empty(database_meta)
        user_id = g.current_user_id
        username = g.current_username

        stream_name = data.get("stream_name", "")
        progress_reporter = AnalysisProgressReporter(
            request_id=request_id,
            stream_name=stream_name,
        )
        if not query:
            return jsonify({"error": "Missing 'query' field"}), 200
        progress_reporter.intent_started()
        progress_reporter.original_query_received(request_id, query)

        logger.info(f"[SystemB] Received analyze request: {request_id}, query: {query[:80]}...")

        # Streaming decomposition + incremental execution
        step_gen = decompose_query_stream(query, database_meta)
        engine = DAGEngine(
            request_id=request_id,
            original_question=query,
            database_meta=database_meta,
            stream_name=stream_name,
        )
        result = engine.execute_streaming(
            step_gen,
            chat_session_id=chat_session_id,
            chat_id=chat_id,
            auth_header=request.headers.get("Authorization", ""),
            tenant_id=request.headers.get("tenantid", ""),
            skip_chat_clear=bool(data.get("skip_chat_clear", False)),
        )
        decomposition = result["decomposition"]

        response = {
            "request_id": request_id,
            "status": _determine_status(result["execution_log"]),
            "answer": result["answer"],
            "data": result["data"],
            "execution_log": result["execution_log"],
            "model_outputs": decomposition.get("model_outputs", []),
            "recall": recall_info,
        }
        if recall_info and recall_info.get("used") and recalled is not None:
            response["recall"] = {**recall_info, "database_meta": recalled}

        # Auto-write operation log (non-fatal)
        log_path = ""
        if user_id and username:
            try:
                log_fut = write_operation_log(
                    user_id=user_id,
                    username=username,
                    query=query,
                    system_prompt=decomposition.get("_system_prompt", ""),
                    user_prompt=decomposition.get("_user_prompt", ""),
                    steps=decomposition.get("steps", []),
                    step_outputs=engine.context.get_all_outputs(),
                    execution_log=result["execution_log"],
                )
                log_path = _log_path_of(log_fut)
            except Exception as log_err:
                logger.warning(f"[SystemB] Failed to write operation log: {log_err}")

        logger.info(f"[SystemB]======== Analysis complete: {response}")
        if log_path:
            response["log_path"] = log_path
        # Cache final response in the in-memory progress bus so a page that
        # reloaded mid-analysis can recover the result (non-fatal).
        try:
            store_result(request_id, response)
        except Exception as result_err:
            logger.warning(f"[SystemB] Failed to cache analyze result: {result_err}")
        return jsonify(response)

    except StepDecompositionError as e:
        logger.error(f"[SystemB] Decomposition failed: {e}", exc_info=True)
        log_path = ""
        # Write operation log with the prompts from the last failed attempt
        if user_id and username:
            try:
                log_fut = write_operation_log(
                    user_id=user_id,
                    username=username,
                    query=query,
                    system_prompt=e.system_prompt,
                    user_prompt=e.user_prompt,
                    steps=[],
                    step_outputs={},
                    execution_log=[{"step_id": "decompose", "status": "failed", "error": str(e)}],
                )
                log_path = _log_path_of(log_fut)
            except Exception as log_err:
                logger.warning(f"[SystemB] Failed to write operation log: {log_err}")
        _resp = {
            "request_id": data.get("request_id", "") if data else "",
            "status": "failure",
            "answer": f"Plan generation failed: {str(e)}",
            "data": None,
            "execution_log": [],
            "recall": recall_info,
            **({"log_path": log_path} if log_path else {}),
        }
        try:
            store_result(_resp["request_id"], _resp)
        except Exception as result_err:
            logger.warning(f"[SystemB] Failed to cache analyze result: {result_err}")
        return jsonify(_resp), 500

    except Exception as e:
        logger.error(f"[SystemB] Unhandled error: {e}", exc_info=True)
        log_path = ""
        # Try to write operation log even for unexpected errors
        if user_id and username:
            try:
                log_fut = write_operation_log(
                    user_id=user_id,
                    username=username,
                    query=query,
                    system_prompt="",
                    user_prompt="",
                    steps=[],
                    step_outputs={},
                    execution_log=[{"step_id": "system", "status": "failed", "error": str(e)}],
                )
                log_path = _log_path_of(log_fut)
            except Exception as log_err:
                logger.warning(f"[SystemB] Failed to write operation log: {log_err}")
        _resp = {
            "request_id": data.get("request_id", "") if data else "",
            "status": "failure",
            "answer": f"System error: {str(e)}",
            "data": None,
            "execution_log": [],
            "recall": recall_info,
            **({"log_path": log_path} if log_path else {}),
        }
        try:
            store_result(_resp["request_id"], _resp)
        except Exception as result_err:
            logger.warning(f"[SystemB] Failed to cache analyze result: {result_err}")
        return jsonify(_resp), 500

    finally:
        try:
            progress_reporter.query_close()
        except NameError:
            pass
        except Exception as close_err:
            logger.error(f"[SystemB] query_close error: {close_err}", exc_info=True)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "service": "system_b"})


META_FILE = Path(__file__).parent / "data" / "database_meta.json"
_META_SEED_LOCK = threading.Lock()
_META_KEYS = ("available_metrics", "available_dimensions", "table_summaries", "business_context")

# 测试页用：缓存生产最近一次发来的 request_id（sessionId@chatId），每次生产请求覆盖刷新。
# 只放内存不落盘：生产 id 指向 System A 真实会话，token/会话有时效，播种式"只写第一次"必然过期。
_LATEST_PROD_REQUEST_ID = {"value": "", "updated_at": ""}
_PROD_REQUEST_ID_LOCK = threading.Lock()


def _remember_prod_request_id(request_id: str) -> None:
    with _PROD_REQUEST_ID_LOCK:
        _LATEST_PROD_REQUEST_ID["value"] = request_id
        _LATEST_PROD_REQUEST_ID["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _get_latest_prod_request_id() -> dict:
    with _PROD_REQUEST_ID_LOCK:
        return dict(_LATEST_PROD_REQUEST_ID)


def _seed_database_meta_if_empty(meta) -> None:
    """Seed data/database_meta.json from production metadata when the file is empty.

    Non-fatal by design: seeding only affects local test pages, never the
    production analysis flow. Caller passes the database_meta received from
    /api/v1/analyze; the file is written only if it is missing/corrupt/blank.
    """
    if not isinstance(meta, dict) or not meta.get("available_metrics"):
        logger.warning("[SystemB] database_meta not seeded: invalid or empty payload")
        return
    with _META_SEED_LOCK:
        try:
            current = None
            if META_FILE.exists():
                try:
                    current = json.loads(META_FILE.read_text(encoding="utf-8"))
                except (OSError, ValueError) as exc:
                    logger.warning(f"[SystemB] Existing meta file unreadable, will overwrite: {exc}")
            is_empty = (
                current is None
                or not isinstance(current, dict)
                or not current
                or all(not current.get(k) for k in _META_KEYS)
            )
            if not is_empty:
                return
            META_FILE.parent.mkdir(parents=True, exist_ok=True)
            tmp = META_FILE.with_suffix(".json.tmp")
            tmp.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
            os.replace(tmp, META_FILE)
            n_metrics = len(meta.get("available_metrics") or [])
            n_dims = len(meta.get("available_dimensions") or [])
            n_tables = len(meta.get("table_summaries") or [])
            logger.info(
                f"[SystemB] database_meta.json seeded from production request "
                f"(metrics={n_metrics}, dimensions={n_dims}, tables={n_tables})"
            )
        except OSError as exc:
            logger.warning(f"[SystemB] Failed to seed database_meta.json: {exc}")


@app.route("/api/v1/database-meta", methods=["GET"])
def get_database_meta():
    """Return database_meta content from data/database_meta.json for test pages."""
    try:
        with open(META_FILE, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as exc:
        return jsonify({"code": 500, "message": f"读取元数据失败: {exc}", "data": None}), 500
    return jsonify({
        "code": 1,
        "message": "操作成功",
        "data": data,
    })


@app.route("/api/v1/latest-request-id", methods=["GET"])
def get_latest_request_id():
    """Return the latest production request_id for the test page.

    测试页勾选"使用生产 request_id"时读取。未收到过生产请求时 data 为 null。
    updated_at 是收到该生产请求的时间，供测试页显示"收到新的数据"。
    """
    info = _get_latest_prod_request_id()
    request_id = info["value"]
    return jsonify({
        "code": 1,
        "message": "操作成功",
        "data": {"request_id": request_id, "updated_at": info["updated_at"]} if request_id else None,
    })


@app.route("/api/v1/prod-agents", methods=["GET"])
def list_prod_agents():
    """测试页"平台会话"模式：返回问数平台智能体列表（name + code/aicode）。"""
    try:
        client = get_chatbi_client()
        agents = client.list_agents()
    except ChatBiError as exc:
        logger.warning(f"[SystemB] prod-agents failed: {exc}")
        return jsonify({"code": 500, "message": str(exc), "data": None}), 200
    except Exception as exc:
        logger.error(f"[SystemB] prod-agents error: {exc}", exc_info=True)
        return jsonify({"code": 500, "message": f"获取智能体列表失败: {exc}", "data": None}), 200
    return jsonify({"code": 1, "message": "操作成功", "data": {"agents": agents}})


@app.route("/api/v1/prod-session", methods=["POST"])
def create_prod_session():
    """测试页"平台会话"模式：创建平台真实会话，返回 sessionId@chatId 格式 request_id。

    请求体: {agent_code, question}（均必填，question 用测试页当前输入的问题，
    平台会对该问题真实执行 AI 分析，属预期消耗）。
    """
    payload = request.get_json(silent=True) or {}
    agent_code = (payload.get("agent_code") or "").strip()
    question = (payload.get("question") or "").strip()
    if not agent_code or not question:
        return jsonify({"code": 400, "message": "agent_code 和 question 不能为空", "data": None}), 200

    try:
        client = get_chatbi_client()
        session = client.create_session(agent_code, question)
    except ChatBiError as exc:
        logger.warning(f"[SystemB] prod-session failed: {exc}")
        return jsonify({"code": 500, "message": str(exc), "data": None}), 200
    except Exception as exc:
        logger.error(f"[SystemB] prod-session error: {exc}", exc_info=True)
        return jsonify({"code": 500, "message": f"创建平台会话失败: {exc}", "data": None}), 200

    session["created_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    return jsonify({"code": 1, "message": "操作成功", "data": session})


@app.route("/api/v1/database-meta", methods=["DELETE"])
def clear_database_meta():
    """Delete data/database_meta.json so the next production request reseeds it (test pages only)."""
    try:
        META_FILE.unlink(missing_ok=True)
    except OSError as exc:
        return jsonify({"code": 500, "message": f"清理元数据失败: {exc}", "data": None}), 500
    return jsonify({"code": 1, "message": "已清理，等待下次生产请求重新播种", "data": None})


@app.route("/api/v1/database-meta", methods=["PUT"])
def update_database_meta():
    """Overwrite data/database_meta.json with the request body (test pages only)."""
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify({"code": 400, "message": "请求体必须是 JSON 对象", "data": None}), 400
    try:
        with open(META_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except OSError as exc:
        return jsonify({"code": 500, "message": f"写入元数据失败: {exc}", "data": None}), 500
    return jsonify({"code": 1, "message": "保存成功", "data": None})


# ==============================================================================
# Recall config endpoints (test page, overrides config.py)
# ==============================================================================

@app.route("/api/v1/recall-config", methods=["GET"])
def get_recall_config():
    """返回当前生效的 recall 配置（页面配置优先，其次 config.py/.env）。"""
    cfg = recall_client.get_effective_config()
    return jsonify({"code": 1, "message": "操作成功", "data": cfg})


@app.route("/api/v1/recall-config", methods=["PUT"])
def update_recall_config():
    """保存页面 recall 配置，同时覆盖生产行为（优先级高于 config.py/.env）。"""
    payload = request.get_json(silent=True) or {}
    try:
        cfg = recall_client.save_page_config(payload)
    except (ValueError, OSError) as exc:
        return jsonify({"code": 500, "message": f"保存 recall 配置失败: {exc}", "data": None}), 200
    return jsonify({"code": 1, "message": "保存成功（已覆盖生产配置）", "data": cfg})


@app.route("/api/v1/recall-config", methods=["DELETE"])
def clear_recall_config():
    """清除页面配置，回退到 config.py/.env。"""
    try:
        cfg = recall_client.clear_page_config()
    except OSError as exc:
        return jsonify({"code": 500, "message": f"清除 recall 配置失败: {exc}", "data": None}), 200
    return jsonify({"code": 1, "message": "已清除页面配置，回退 config.py", "data": cfg})


@app.route("/api/v1/recall/preview", methods=["POST"])
def preview_recall():
    """测试页实时预览：输入 query 调 /api/recall/prod，返回缩圈 database_meta。"""
    payload = request.get_json(silent=True) or {}
    query = (payload.get("query") or "").strip()
    if not query:
        return jsonify({"code": 400, "message": "query 不能为空", "data": None}), 200
    result = recall_client.preview_recall(query)
    if not result["ok"]:
        return jsonify({"code": 500, "message": result["message"], "data": None}), 200
    return jsonify({"code": 1, "message": "操作成功", "data": {
        "database_meta": result["database_meta"],
        "stats": result["stats"],
        "elapsed": result["elapsed"],
        "source": result["source"],
    }})


# ==============================================================================
# Old prompt editor endpoints (kept for backward compatibility, not used)
# ==============================================================================

@app.route("/webapp/prompts/get", methods=["GET"])
def get_prompts():
    """Return current prompts for prompt editor pages."""
    prompt_record = db.get_prompt_record(prompt_id=1)
    return jsonify({
        "code": 1,
        "message": "操作成功",
        "data": {
            "id": prompt_record["id"] if prompt_record else None,
            "system_prompt": prompt_record["system_prompt"] if prompt_record else "",
            "user_prompt": prompt_record["user_prompt"] if prompt_record else "",
            "using_db_values": bool(prompt_record),
        }
    })


@app.route("/webapp/prompts/save", methods=["POST"])
def save_prompts():
    """Save prompts from prompt editor pages."""
    data = request.get_json()
    if not data:
        return jsonify({"code": 0, "message": "Missing request body", "data": None}), 400

    prompt_id = data.get("id")
    system_prompt = data.get("system_prompt")
    user_prompt = data.get("user_prompt")

    if prompt_id == "":
        prompt_id = None

    if prompt_id is not None and not isinstance(prompt_id, int):
        return jsonify({
            "code": 0,
            "message": "'id' must be an integer, null, or empty string",
            "data": None,
        }), 400

    if not isinstance(system_prompt, str) or not isinstance(user_prompt, str):
        return jsonify({
            "code": 0,
            "message": "'system_prompt' and 'user_prompt' must both be strings",
            "data": None,
        }), 400

    try:
        saved_id = db.save_prompts(
            prompt_id=prompt_id,
            system_prompt=system_prompt,
            user_prompt=user_prompt,
        )
    except Exception as exc:
        logger.error(f"[SystemB] Save prompts failed: {exc}", exc_info=True)
        return jsonify({
            "code": 0,
            "message": str(exc),
            "data": None,
        }), 500

    return jsonify({
        "code": 1,
        "message": "操作成功",
        "data": {
            "id": saved_id,
        },
    })


# ==============================================================================
# Log management endpoints
# ==============================================================================

@app.route("/api/v1/logs/list", methods=["POST"])
def logs_list():
    """Query operation log list with filters and pagination."""
    try:
        data = request.get_json() or {}
        user_id = data.get("user_id", "")
        username = data.get("username", "")
        log_date_start = data.get("log_date_start", "")
        log_date_end = data.get("log_date_end", "")
        question = data.get("question", "")
        try:
            page = int(data.get("page", 1))
        except (TypeError, ValueError):
            page = 1
        try:
            page_size = int(data.get("page_size", 20))
        except (TypeError, ValueError):
            page_size = 20
        page = max(1, page)
        page_size = min(max(1, page_size), 100)

        result = get_log_list(
            user_id=user_id,
            username=username,
            log_date_start=log_date_start,
            log_date_end=log_date_end,
            question=question,
            page=page,
            page_size=page_size,
        )
        return _ok(data=result)
    except Exception as exc:
        logger.error(f"[SystemB] Logs list error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


def _log_separator(rel_path: str, content: str) -> str:
    """Build a section header from log file content (first line [ts] [uid] [name],
    and the 「1、用户问题：」 line). Falls back to the file path on parse failure."""
    user_id = username = question = ""
    for line in content.splitlines():
        if not user_id and line.startswith("[") and "]" in line:
            parts = [p.strip() for p in re.split(r"\]\s*\[", line.strip("[] "))]
            if len(parts) >= 3:
                user_id, username = parts[1], parts[2]
                continue
        if not question and line.startswith("1、用户问题："):
            question = line[len("1、用户问题："):].strip()
        if user_id and question:
            break
    header = "=" * 60
    title = f"用户：{user_id}（{username}）  问题：{question}" if user_id else rel_path
    return f"{header}\n{title}\n{header}\n"


@app.route("/api/v1/logs/download", methods=["POST"])
def logs_download():
    """Download selected log files by relative path(s), merged into one text file."""
    try:
        data = request.get_json() or {}
        paths = data.get("paths")
        if isinstance(paths, str):
            paths = [paths]
        if not isinstance(paths, list):
            return _fail("参数错误：paths 为必填项")
        paths = [p.strip() for p in paths if isinstance(p, str) and p.strip()]
        if not paths:
            return _fail("参数错误：paths 不能为空")
        if len(paths) > 100:
            return _fail("参数错误：单次最多下载 100 条")

        logs_root = Path(Config.LOGS_DIR).resolve()
        parts = []
        for rel in paths:
            file_path = (logs_root / rel).resolve()
            if not file_path.is_relative_to(logs_root):
                return _fail("参数错误：非法路径")
            if not file_path.exists():
                logger.error(f"[SystemB] Download file missing: {file_path}")
                continue
            content = file_path.read_text(encoding="utf-8")
            parts.append(_log_separator(rel, content) + content)

        if not parts:
            return _fail("日志文件不存在", 404)

        download_name = f"operation_logs_{datetime.now():%Y%m%d%H%M%S}.txt"
        return send_file(
            io.BytesIO("\n\n".join(parts).encode("utf-8")),
            as_attachment=True,
            download_name=download_name,
            mimetype="text/plain",
        )
    except Exception as exc:
        logger.error(f"[SystemB] Logs download error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/logs/preview", methods=["POST"])
def logs_preview():
    """Preview one question's log file content by relative path (no DB access)."""
    try:
        data = request.get_json() or {}
        log_path = data.get("log_path")
        if not isinstance(log_path, str) or not log_path.strip():
            return _fail("参数错误：log_path 为必填项")
        log_path = log_path.strip()

        logs_root = Path(Config.LOGS_DIR).resolve()
        file_path = (logs_root / log_path).resolve()
        if not file_path.is_relative_to(logs_root):
            return _fail("参数错误：非法路径")
        if not file_path.exists():
            return _fail("日志文件不存在", 404)

        content = file_path.read_text(encoding="utf-8")
        return _ok(data={"log_path": log_path, "content": content})
    except Exception as exc:
        logger.error(f"[SystemB] Logs preview error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/logs/users", methods=["POST"])
def logs_users():
    """Get distinct users who have operation logs."""
    try:
        users = get_users_with_logs()
        return _ok(data={"list": users})
    except Exception as exc:
        logger.error(f"[SystemB] Logs users error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/logs/delete", methods=["POST"])
def logs_delete():
    """Delete a user's operation log DB records.
    log_date optional: when omitted, delete ALL dates for the user.
    Only deletes DB records; disk log files are preserved.
    """
    try:
        data = request.get_json() or {}
        user_id = data.get("user_id")

        if not user_id or not isinstance(user_id, str):
            return _fail("user_id 为必填项且必须是字符串")

        deleted = delete_log_by_user_date(user_id=user_id, log_date=data.get("log_date"))

        if not deleted:
            return _fail("未找到日志记录", 404)

        return _ok()
    except Exception as exc:
        logger.error(f"[SystemB] logs/delete error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


# ==============================================================================
# Prompt versioning endpoints
# ==============================================================================

def _ok(data=None, message="操作成功"):
    return jsonify({"code": 200, "message": message, "data": data})


def _fail(message, code=400):
    http_status = 500 if code == 500 else 200
    return jsonify({"code": code, "message": message, "data": None}), http_status


# ---- Group management ----

@app.route("/api/v1/prompts/groups", methods=["POST"])
def prompts_groups():
    """List all prompt groups."""
    try:
        data = request.get_json() or {}
        keyword = data.get("keyword", "")
        groups = list_groups(keyword=keyword)
        return _ok({"list": groups})
    except Exception as exc:
        logger.error(f"[SystemB] prompts/groups error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/group/create", methods=["POST"])
def prompts_group_create():
    """Create a new prompt group."""
    try:
        data = request.get_json() or {}
        name = data.get("name", "")
        label = data.get("label", "")
        description = data.get("description", "")

        if not name or not label:
            return _fail("name 和 label 为必填项")

        if not re.match(r"^[a-zA-Z][a-zA-Z0-9_-]*$", name):
            return _fail("name 只能包含英文、数字、下划线和连字符，且以字母开头")

        result = create_group(name=name, label=label, description=description)
        return _ok(result)
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/group/create error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/group/update", methods=["POST"])
def prompts_group_update():
    """Update a prompt group."""
    try:
        data = request.get_json() or {}
        group_id = data.get("id")
        label = data.get("label")
        description = data.get("description")

        if not group_id or not isinstance(group_id, int):
            return _fail("id 为必填项且必须是整数")

        update_group(group_id=group_id, label=label, description=description)
        return _ok()
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/group/update error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/group/delete", methods=["POST"])
def prompts_group_delete():
    """Delete a prompt group (DB only, files preserved)."""
    try:
        data = request.get_json() or {}
        group_id = data.get("id")

        if not group_id or not isinstance(group_id, int):
            return _fail("id 为必填项且必须是整数")

        delete_group(group_id=group_id)
        return _ok()
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/group/delete error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


# ---- Version management ----

@app.route("/api/v1/prompts/update", methods=["POST"])
def prompts_update():
    """
    Create/update a prompt version.
    - If version is provided: use it directly; auto-increments if already exists.
    - If version is omitted: start from v1.0.0, auto-increment if needed.
    """
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")
        content = data.get("content", "")
        version = data.get("version", "")
        description = data.get("description", "")

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")
        if not content:
            return _fail("content 为必填项")

        if version and not re.match(r"^v?\d+\.\d+\.\d+$", version):
            return _fail("version 格式错误，应为 vX.Y.Z")

        result = update_version(
            group_id=group_id,
            content=content,
            version=version or None,
            description=description,
        )
        return _ok(result)
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/update error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/list", methods=["POST"])
def prompts_list():
    """List prompt versions for a group."""
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")
        keyword = data.get("keyword", "")
        is_active = data.get("is_active")
        page = int(data.get("page", 1))
        page_size = int(data.get("page_size", 20))

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")

        if is_active is not None and not isinstance(is_active, bool):
            return _fail("is_active 必须是布尔值")

        result = list_versions(
            group_id=group_id,
            keyword=keyword,
            is_active=is_active,
            page=page,
            page_size=page_size,
        )
        return _ok(result)
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/list error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/switch", methods=["POST"])
def prompts_switch():
    """Switch the active version of a group."""
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")
        version = data.get("version", "")

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")
        if not version:
            return _fail("version 为必填项")

        result = switch_version(group_id=group_id, version=version)
        return _ok(result)
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/switch error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/current", methods=["POST"])
def prompts_current():
    """Get the current active prompt content."""
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")

        result = get_current_version(group_id=group_id)
        if not result:
            return _fail("该分组没有生效版本", 404)
        return _ok(result)
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/current error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/version/content", methods=["POST"])
def prompts_version_content():
    """Get a specific version's content."""
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")
        version = data.get("version", "")

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")
        if not version:
            return _fail("version 为必填项")

        result = get_version_content(group_id=group_id, version=version)
        if not result:
            return _fail("版本不存在", 404)
        return _ok(result)
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/version/content error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/version/exists", methods=["POST"])
def prompts_version_exists():
    """Check if a version exists in a group."""
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")
        version = data.get("version", "")

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")
        if not version:
            return _fail("version 为必填项")

        existing = db.get_version_by_group_and_version(group_id, version)
        return _ok({"exists": existing is not None})
    except Exception as exc:
        logger.error(f"[SystemB] prompts/version/exists error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


@app.route("/api/v1/prompts/delete", methods=["POST"])
def prompts_delete():
    """Delete a prompt version (active version cannot be deleted)."""
    try:
        data = request.get_json() or {}
        group_id = data.get("group_id")
        version = data.get("version", "")

        if not group_id or not isinstance(group_id, int):
            return _fail("group_id 为必填项且必须是整数")
        if not version:
            return _fail("version 为必填项")

        delete_version(group_id=group_id, version=version)
        return _ok()
    except ValueError as exc:
        return _fail(str(exc))
    except Exception as exc:
        logger.error(f"[SystemB] prompts/delete error: {exc}", exc_info=True)
        return _fail(str(exc), 500)


# ==============================================================================
# Helper functions
# ==============================================================================

# ==============================================================================
# Batch test report endpoints
# ==============================================================================

def _validate_report_params(data: dict) -> tuple:
    """Common validation for batch report endpoints. Returns (error, user_id, log_date, log_paths, items)."""
    user_id = (data.get("user_id") or "").strip()
    log_date = (data.get("log_date") or "").strip()
    log_paths = data.get("log_paths") or []
    items = data.get("items") or []

    if items:
        if not isinstance(items, list) or not all(isinstance(it, dict) for it in items):
            return "参数错误：items 应为对象数组", "", "", [], []
        cleaned = []
        for it in items:
            try:
                qnum = int(it.get("qnum"))
            except (TypeError, ValueError):
                qnum = 0
            lp = str(it.get("log_path") or "").strip().replace("\\", "/")
            if lp:
                norm = os.path.normpath(lp)
                if norm.startswith("..") or os.path.isabs(norm) or not norm.startswith("userlogs"):
                    return "参数错误：items 含非法 log_path", "", "", [], []
            cleaned.append({
                "qnum": qnum,
                "log_path": lp,
                "question": str(it.get("question") or "").strip()[:1024],
            })
        return "", user_id, log_date, [], cleaned

    if log_paths:
        if not isinstance(log_paths, list) or not all(isinstance(p, str) for p in log_paths):
            return "参数错误：log_paths 应为字符串数组", "", "", [], []
        cleaned = [p.strip().replace("\\", "/") for p in log_paths if p.strip()]
        if not cleaned:
            return "参数错误：log_paths 不能为空数组", "", "", [], []
        for p in cleaned:
            norm = os.path.normpath(p)
            if norm.startswith("..") or os.path.isabs(norm) or not norm.startswith("userlogs"):
                return "参数错误：log_paths 含非法路径", "", "", [], []
        return "", user_id, log_date, cleaned, []

    if not user_id or not log_date:
        return "参数错误：user_id 和 log_date 为必填项", "", "", [], []
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", log_date):
        return "参数错误：log_date 格式应为 YYYY-MM-DD", "", "", [], []
    if not re.match(r"^[\w\-]+$", user_id):
        return "参数错误：user_id 含非法字符", "", "", [], []
    return "", user_id, log_date, [], []


@app.route("/api/v1/batch/report", methods=["POST"])
def batch_report_api():
    """Analyze operation logs of a user/date (or explicit log_paths), return judgment report JSON."""
    try:
        data = request.get_json() or {}
        err, user_id, log_date, log_paths, items = _validate_report_params(data)
        if err:
            return _fail(err)

        if items:
            report = batch_report.analyze_log_items(Config.LOGS_DIR, items, user_id=user_id, log_date=log_date)
        elif log_paths:
            report = batch_report.analyze_log_paths(Config.LOGS_DIR, log_paths, user_id=user_id, log_date=log_date)
        else:
            report = batch_report.analyze_logs(Config.LOGS_DIR, user_id, log_date)
        if report.get("error"):
            return _fail(report["error"], 404)
        return _ok(data=report)
    except Exception as exc:
        logger.error(f"[SystemB] batch/report error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/batch/report/excel", methods=["POST"])
def batch_report_excel_api():
    """Export the same report as an Excel file download."""
    try:
        data = request.get_json() or {}
        err, user_id, log_date, log_paths, items = _validate_report_params(data)
        if err:
            return _fail(err)

        if items:
            report = batch_report.analyze_log_items(Config.LOGS_DIR, items, user_id=user_id, log_date=log_date)
        elif log_paths:
            report = batch_report.analyze_log_paths(Config.LOGS_DIR, log_paths, user_id=user_id, log_date=log_date)
        else:
            report = batch_report.analyze_logs(Config.LOGS_DIR, user_id, log_date)
        if report.get("error"):
            return _fail(report["error"], 404)

        batch_dir = Path(Config.LOGS_DIR) / "batch_reports"
        batch_dir.mkdir(parents=True, exist_ok=True)
        output_path = batch_dir / f"batch_report_{user_id}_{log_date}.xlsx"
        batch_report.export_excel(report, str(output_path))

        download_name = f"batch_report_{user_id}_{log_date}.xlsx"
        return send_file(
            str(output_path),
            as_attachment=True,
            download_name=download_name,
            mimetype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
    except Exception as exc:
        logger.error(f"[SystemB] batch/report/excel error: {exc}", exc_info=True)
        return _fail(str(exc) if "openpyxl" in str(exc) else "服务器内部错误", 500)


# ==============================================================================
# Batch test history endpoints
# ==============================================================================

@app.route("/api/v1/batch/history", methods=["POST"])
def batch_history_upsert():
    """Create or sync a batch record. Body: {batch_id?, user_id?, items[], duration_ms?}.
    Without batch_id a new one is generated and returned."""
    try:
        data = request.get_json() or {}
        user_id = str(data.get("user_id") or "page-test").strip()[:64]
        if not re.match(r"^[\w\-]+$", user_id):
            return _fail("参数错误：user_id 含非法字符")
        items = data.get("items")
        if not isinstance(items, list):
            return _fail("参数错误：items 应为数组")
        cleaned = []
        for it in items:
            if not isinstance(it, dict):
                continue
            cleaned.append({
                "q": str(it.get("q") or "")[:1024],
                "status": str(it.get("status") or "pending")[:16],
                "ms": int(it.get("ms") or 0),
                "error": str(it.get("error") or "")[:512],
                "log_path": str(it.get("log_path") or "")[:512],
            })
        batch_id = str(data.get("batch_id") or "").strip()[:48]
        created_at = ""
        if batch_id:
            existing = db.get_batch_record(batch_id)
            if not existing or existing.get("user_id") != user_id:
                batch_id = ""
            else:
                created_at = existing.get("created_at") or ""
        if not batch_id:
            batch_id = f"{user_id}_{datetime.now().strftime('%Y%m%d_%H%M%S')}_" \
                       f"{uuid.uuid4().hex[:6]}"
        duration_ms = data.get("duration_ms")
        try:
            duration_ms = int(duration_ms or 0)
        except (TypeError, ValueError):
            duration_ms = 0
        if not db.upsert_batch_record(batch_id, user_id, cleaned, duration_ms, created_at):
            return _fail("保存批次失败", 500)
        return _ok(data={"batch_id": batch_id})
    except Exception as exc:
        logger.error(f"[SystemB] batch/history upsert error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/batch/history", methods=["GET"])
def batch_history_list():
    """Paged batch list (no items). Query: user_id, page, page_size."""
    try:
        user_id = (request.args.get("user_id") or "").strip()
        if user_id and not re.match(r"^[\w\-]+$", user_id):
            return _fail("参数错误：user_id 含非法字符")
        try:
            page = max(1, int(request.args.get("page") or 1))
        except ValueError:
            page = 1
        try:
            page_size = min(100, max(1, int(request.args.get("page_size") or 10)))
        except ValueError:
            page_size = 10
        result = db.query_batch_records(user_id=user_id, page=page, page_size=page_size)
        return _ok(data=result)
    except Exception as exc:
        logger.error(f"[SystemB] batch/history list error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/batch/history/<batch_id>", methods=["GET"])
def batch_history_detail(batch_id):
    """Batch detail including items."""
    try:
        row = db.get_batch_record(batch_id)
        if not row:
            return _fail("批次不存在", 404)
        return _ok(data=row)
    except Exception as exc:
        logger.error(f"[SystemB] batch/history detail error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/batch/history/<batch_id>", methods=["DELETE"])
def batch_history_delete(batch_id):
    """Delete a batch record. Query with_logs=true also deletes its log files
    and user_operation_log rows."""
    try:
        with_logs = (request.args.get("with_logs") or "").lower() in ("1", "true", "yes")
        deleted, items = db.delete_batch_record(batch_id)
        if not deleted:
            return _fail("批次不存在", 404)

        removed_logs = 0
        removed_db_rows = 0
        if with_logs:
            log_paths, user_dates = [], set()
            for it in items:
                lp = str(it.get("log_path") or "").strip().replace("\\", "/")
                if not lp.startswith("userlogs"):
                    continue
                norm = os.path.normpath(lp)
                if norm.startswith("..") or os.path.isabs(norm):
                    continue
                log_paths.append(lp)
                parts = lp.split("/")
                if len(parts) >= 3:
                    user_dates.add((parts[1], parts[2]))
            for lp in log_paths:
                try:
                    p = Path(Config.LOGS_DIR) / lp
                    if p.is_file():
                        p.unlink()
                        removed_logs += 1
                except OSError as exc:
                    logger.warning(f"[SystemB] Failed to delete log file {lp}: {exc}")
            for uid, ldate in user_dates:
                try:
                    n = db.delete_operation_log_by_log_path_prefix(uid, ldate, log_paths)
                    removed_db_rows += n
                except Exception as exc:
                    logger.warning(f"[SystemB] Failed to delete db log rows {uid}/{ldate}: {exc}")

        return _ok(data={"deleted": True, "removed_logs": removed_logs, "removed_db_rows": removed_db_rows})
    except Exception as exc:
        logger.error(f"[SystemB] batch/history delete error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


def _determine_status(execution_log: list) -> str:
    """Determine overall status from execution log."""
    statuses = [entry["status"] for entry in execution_log]
    if all(s == "success" for s in statuses):
        return "success"
    elif any(s == "success" for s in statuses):
        return "partial_failure"
    return "failure"


def _log_path_of(fut, timeout: float = 0.5) -> str:
    """Bounded wait for the operation-log write to finish; returns relative
    log path or "" (log write is non-fatal, so is the wait)."""
    try:
        return fut.result(timeout=timeout) or ""
    except Exception:
        return ""


# ==================== 单个测试历史（JSONL 文件存储，不进数据库） ====================

_SINGLE_HISTORY_FILE = os.path.join("logs", "single_history.jsonl")
_SINGLE_HISTORY_MAX = 200


def _clip_str(val, limit: int) -> str:
    return str(val if val is not None else "")[:limit]


@app.route("/api/v1/single/history", methods=["POST"])
def single_history_add():
    """记录一条单次测试结果到 logs/single_history.jsonl（一行一条 JSON，含回放数据）。"""
    try:
        body = request.get_json(silent=True) or {}
        # 回放数据体积保护：避免单条记录过大拖垮 JSONL
        events = body.get("progress_events")
        if isinstance(events, list):
            events = events[:2000]
            for ev in events:
                if isinstance(ev, dict) and ev.get("message"):
                    ev["message"] = str(ev["message"])[:2000]
        else:
            events = []
        outputs = body.get("model_outputs")
        if isinstance(outputs, list):
            for o in outputs:
                if isinstance(o, dict) and o.get("raw_output"):
                    o["raw_output"] = str(o["raw_output"])[:50000]
        else:
            outputs = []
        record = {
            "id": uuid.uuid4().hex[:12],
            "ts": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "query": str(body.get("query", ""))[:500],
            "status": "ok" if body.get("status") == "ok" else "err",
            "ms": int(body.get("ms") or 0),
            "error": str(body.get("error", ""))[:300],
            "log_path": str(body.get("log_path", ""))[:300],
            "request_id": str(body.get("request_id", ""))[:100],
            "answer": _clip_str(body.get("answer"), 5000),
            "execution_log": body.get("execution_log") if isinstance(body.get("execution_log"), list) else [],
            "model_outputs": outputs,
            "progress_events": events,
        }
        os.makedirs(os.path.dirname(_SINGLE_HISTORY_FILE), exist_ok=True)
        with open(_SINGLE_HISTORY_FILE, "a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
        # 裁剪到最近 N 条，防止文件无限膨胀
        try:
            with open(_SINGLE_HISTORY_FILE, "r", encoding="utf-8") as f:
                lines = f.readlines()
            if len(lines) > _SINGLE_HISTORY_MAX:
                with open(_SINGLE_HISTORY_FILE, "w", encoding="utf-8") as f:
                    f.writelines(lines[-_SINGLE_HISTORY_MAX:])
        except OSError:
            pass
        return _ok(data={"saved": True})
    except Exception as exc:
        logger.error(f"[SystemB] single/history add error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/single/history", methods=["GET"])
def single_history_list():
    """返回最近 N 条单次测试记录（新在前）。"""
    try:
        limit = max(1, min(int(request.args.get("limit", 50)), 200))
        records = []
        if os.path.exists(_SINGLE_HISTORY_FILE):
            with open(_SINGLE_HISTORY_FILE, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    records.append(line)
        recent = records[-limit:][::-1]
        return _ok(data={"items": [json.loads(r) for r in recent], "total": len(records)})
    except Exception as exc:
        logger.error(f"[SystemB] single/history list error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)


@app.route("/api/v1/single/history", methods=["DELETE"])
def single_history_delete():
    """按 id（优先）或 ts+query+status+ms 删除一条单次测试记录。"""
    try:
        body = request.get_json(silent=True) or {}
        rid = str(body.get("id", "")).strip()
        q = str(body.get("query", "")).strip()
        ts = str(body.get("ts", "")).strip()
        status = str(body.get("status", "")).strip()
        ms = body.get("ms")
        if not os.path.exists(_SINGLE_HISTORY_FILE):
            return _ok(data={"deleted": 0})
        with open(_SINGLE_HISTORY_FILE, "r", encoding="utf-8") as f:
            lines = f.readlines()
        keep = []
        deleted = 0
        for line in lines:
            line_s = line.strip()
            if not line_s:
                continue
            try:
                rec = json.loads(line_s)
            except ValueError:
                keep.append(line)
                continue
            match = bool(rid and str(rec.get("id", "")) == rid)
            if not match and not rid:
                match = (
                    str(rec.get("query", "")).strip() == q
                    and str(rec.get("ts", "")).strip() == ts
                    and str(rec.get("status", "")).strip() == status
                    and (ms is None or rec.get("ms") == int(ms))
                )
            if match:
                deleted += 1
            else:
                keep.append(line)
        if deleted:
            with open(_SINGLE_HISTORY_FILE, "w", encoding="utf-8") as f:
                f.writelines(keep)
        return _ok(data={"deleted": deleted})
    except Exception as exc:
        logger.error(f"[SystemB] single/history delete error: {exc}", exc_info=True)
        return _fail("服务器内部错误", 500)





def main():
    _init_tables()
    host = Config.SYSTEM_B_HOST
    port = Config.SYSTEM_B_PORT
    logger.info(f"[SystemB] Starting on {host}:{port}")
    app.run(host=host, port=port, debug=False)


if __name__ == "__main__":
    main()