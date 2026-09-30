"""
User operation log manager.
Handles log file writing and database metadata operations.
"""

import atexit
import json
import logging
import queue
import re
import threading
import time
from concurrent.futures import Future
from datetime import datetime
from pathlib import Path
from typing import Optional

from system_b.config import Config
from system_b.utils import db

logger = logging.getLogger(__name__)


def _ensure_log_dir(user_dir: Path):
    """Create user log directory if not exists."""
    user_dir.mkdir(parents=True, exist_ok=True)


_STEP_TYPE_ZH = {
    "query": "查询",
    "compute": "计算",
    "summarize": "总结",
    "analyze": "分析",
}


def _next_seq(day_dir: Path) -> int:
    """Next question sequence number: max N among existing 问题N.log files + 1."""
    nums = [int(p.stem[2:]) for p in day_dir.glob("问题*.log") if p.stem[2:].isdigit()]
    return max(nums, default=0) + 1


def _format_step_output(step_id: str, step_outputs: dict, execution_log: list) -> str:
    """Render the 执行结果 section for one step: output JSON, or error from execution log."""
    sdf = step_outputs.get(step_id) if step_outputs else None
    if sdf is not None and hasattr(sdf, "to_dict"):
        return json.dumps(sdf.to_dict(), ensure_ascii=False, indent=2)
    for entry in execution_log or []:
        if entry.get("step_id") == step_id and entry.get("status") != "success":
            err = entry.get("error_message") or "no output produced"
            return f"执行失败：{err}"
    return "无输出"


def _write_operation_log_sync(
    user_id: str,
    username: str,
    query: str,
    system_prompt: str = "",
    user_prompt: str = "",
    steps: Optional[list] = None,
    step_outputs: Optional[dict] = None,
    execution_log: Optional[list] = None,
    result_future: Optional[Future] = None,
):
    """
    Write an operation log entry (4-module format, per 项目/日志格式.txt).
    - One file per question: logs/userlogs/{user_id}/{yyyy-MM-dd}/问题{seq}.log
    - Modules: 1 用户问题 / 2 执行提示词(全文) / 3 拆解过程及每一步执行结果 / 4 错误描述
    - Inserts one database row per question
    - Failure does not raise, only logs warning
    - On success, resolves result_future with the log's relative path
      (f"userlogs/{user_id}/{date}/问题{seq}.log"), or None on failure.
    """
    log_path: Optional[str] = None
    try:
        now = datetime.now()
        log_date = now.strftime("%Y-%m-%d")
        timestamp = now.strftime("%Y-%m-%d %H:%M:%S")

        logs_base = Path(Config.LOGS_DIR)
        user_dir = logs_base / "userlogs" / str(user_id)
        day_dir = user_dir / log_date
        _ensure_log_dir(day_dir)

        seq = _next_seq(day_dir)

        errors = [
            f"[{e.get('step_id')}] {e.get('error_message') or e.get('status')}"
            for e in (execution_log or [])
            if e.get("status") not in ("success", None)
        ]

        def _build_lines(seq: int) -> list:
            lines = [
                f"[{timestamp}] [{user_id}] [{username}]",
                f"问题{seq}：",
                f"1、用户问题：{query}",
                "",
                "2、执行提示词：",
                "System Prompt：",
                system_prompt or "（无）",
                "User Prompt：",
                user_prompt or "（无）",
                "",
                "3、拆解过程及每一步的执行结果",
            ]
            for step in steps or []:
                step_id = step.get("step_id", "")
                type_zh = _STEP_TYPE_ZH.get(step.get("step_type", ""), step.get("step_type", ""))
                lines.append(f"{step_id}-{type_zh}")
                lines.append("执行计划：")
                lines.append(json.dumps(step, ensure_ascii=False, indent=2))
                lines.append("执行结果：")
                lines.append(_format_step_output(step_id, step_outputs, execution_log))
            lines.append("")
            lines.append("4、错误描述：")
            lines.append("\n".join(errors) if errors else "")
            lines.append("")
            lines.append("")
            return lines

        # "x" 模式排他创建 + 重试，避免并发请求拿到相同 seq 互相覆盖文件
        while True:
            log_file = day_dir / f"问题{seq}.log"
            try:
                with open(log_file, "x", encoding="utf-8") as f:
                    f.write("\n".join(_build_lines(seq)))
                break
            except FileExistsError:
                seq += 1

        log_path = f"userlogs/{user_id}/{log_date}/问题{seq}.log"
        db_ok = db.insert_operation_log(user_id, username, log_date, log_path, question=query)

        if db_ok:
            logger.info(f"[LogManager] Log written: {log_file}")
        else:
            logger.warning(
                f"[LogManager] Log file written but DB insert FAILED: "
                f"user={user_id}, date={log_date}, path={log_path}"
            )
    except Exception as exc:
        logger.warning(f"[LogManager] Failed to write operation log (non-fatal): {exc}")
    finally:
        if result_future is not None:
            result_future.set_result(log_path)


# ==============================================================================
# Async log queue: write_operation_log never blocks the request thread.
# A single worker serializes "pick seq + exclusive file create + DB insert", which
# also removes the seq-number race of concurrent requests.
# ==============================================================================

_LOG_QUEUE: "queue.Queue[Optional[tuple]]" = queue.Queue()
_QUEUE_SENTINEL = None  # None = worker should exit after draining


def _log_queue_worker():
    while True:
        item = _LOG_QUEUE.get()
        try:
            if item is _QUEUE_SENTINEL:
                return
            _write_operation_log_sync(*item)
        except Exception as exc:  # never kill the worker
            logger.warning(f"[LogManager] Log queue worker error (non-fatal): {exc}")
        finally:
            _LOG_QUEUE.task_done()


def _start_log_worker_once():
    if _log_queue_worker_started[0]:
        return
    with _log_worker_lock:
        if _log_queue_worker_started[0]:
            return
        t = threading.Thread(target=_log_queue_worker, name="log-queue-worker", daemon=True)
        t.start()
        _log_queue_worker_started[0] = True


_log_worker_lock = threading.Lock()
_log_queue_worker_started = [False]


def _drain_log_queue_on_exit():
    if not _log_queue_worker_started[0]:
        return  # worker never started: nothing to drain
    try:
        _LOG_QUEUE.put(_QUEUE_SENTINEL)
        deadline = time.time() + 10  # bound the wait; never block shutdown
        while time.time() < deadline:
            if _LOG_QUEUE.unfinished_tasks == 0:
                return
            time.sleep(0.1)
        logger.warning("[LogManager] Log queue not fully drained within 10s at exit (dropping remaining entries)")
    except Exception:
        pass


def write_operation_log(
    user_id: str,
    username: str,
    query: str,
    system_prompt: str = "",
    user_prompt: str = "",
    steps: Optional[list] = None,
    step_outputs: Optional[dict] = None,
    execution_log: Optional[list] = None,
) -> Future:
    """Enqueue an operation log write. Returns immediately; actual file write
    and DB insert happen on a background thread (non-fatal by design).
    The returned Future resolves to the log's relative path
    (e.g. "userlogs/page-test/2026-09-17/问题3.log"), or None if writing failed."""
    fut: Future = Future()
    try:
        _start_log_worker_once()
        _LOG_QUEUE.put(
            (
                user_id,
                username,
                query,
                system_prompt,
                user_prompt,
                steps,
                step_outputs,
                execution_log,
                fut,
            )
        )
    except Exception as exc:
        logger.warning(f"[LogManager] Failed to enqueue operation log (non-fatal): {exc}")
        if not fut.done():
            fut.set_result(None)
    return fut


atexit.register(_drain_log_queue_on_exit)


def get_log_list(
    user_id: str = "",
    username: str = "",
    log_date_start: str = "",
    log_date_end: str = "",
    question: str = "",
    page: int = 1,
    page_size: int = 20,
) -> dict:
    """Query log list from database."""
    return db.query_operation_logs(
        user_id=user_id,
        username=username,
        log_date_start=log_date_start,
        log_date_end=log_date_end,
        question=question,
        page=page,
        page_size=page_size,
    )


def delete_log_by_user_date(user_id: str, log_date: str = None) -> int:
    """删除数据库 user_operation_log 记录（磁盘文件保留）。
    log_date 为空时删除该用户全部日期记录。返回删除条数。
    """
    return db.delete_operation_log_by_user_date(user_id, log_date)


def get_users_with_logs() -> list:
    """Get distinct users who have operation logs."""
    return db.get_distinct_users_with_logs()


def _parse_log_file_meta(path: Path, user_id: str, log_date: str) -> Optional[tuple]:
    """Parse one 问题N.log file → (user_id, username, log_date, log_path, question, timestamp)
    or None if the file does not look like an operation log."""
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return None
    username = user_id
    question = ""
    timestamp = None
    for line in text.splitlines():
        if line.startswith("[") and "]" in line:
            parts = [p.strip() for p in re.split(r"\]\s*\[", line.strip("[] "))]
            if len(parts) >= 3:
                timestamp, user_id, username = parts[0], parts[1], parts[2]
                continue
        if not question and line.startswith("1、用户问题："):
            question = line[len("1、用户问题："):].strip()
        if timestamp and question:
            break
    if not timestamp:
        return None
    rel = f"userlogs/{user_id}/{log_date}/{path.name}"
    return (user_id, username, log_date, rel, question, timestamp)


def backfill_operation_logs() -> int:
    """Scan disk logs (userlogs/*/*/问题N.log) and insert DB rows for files
    whose log_path is not yet recorded (e.g. inserts failed while the table
    lacked the question column). Returns number of rows inserted. Non-fatal."""
    inserted = 0
    try:
        logs_base = Path(Config.LOGS_DIR) / "userlogs"
        if not logs_base.is_dir():
            return 0
        conn = None
        try:
            conn = db.get_connection()
            with conn.cursor() as cursor:
                cursor.execute("SELECT log_path FROM user_operation_log")
                existing = {r["log_path"] for r in cursor.fetchall()}
        finally:
            if conn:
                conn.close()

        for user_dir in sorted(logs_base.iterdir()):
            if not user_dir.is_dir():
                continue
            for day_dir in sorted(user_dir.iterdir()):
                if not day_dir.is_dir():
                    continue
                log_date = day_dir.name
                for f in sorted(day_dir.glob("问题*.log")):
                    meta = _parse_log_file_meta(f, user_dir.name, log_date)
                    if not meta:
                        continue
                    if meta[3] in existing:
                        continue
                    user, username, date, rel, question, ts = meta
                    if db.insert_operation_log(user, username, date, rel, question, created_at=ts):
                        inserted += 1
        if inserted:
            logger.info(f"[LogManager] Backfilled {inserted} operation log DB rows from disk")
    except Exception as exc:
        logger.warning(f"[LogManager] Backfill operation logs failed (non-fatal): {exc}")
    return inserted
