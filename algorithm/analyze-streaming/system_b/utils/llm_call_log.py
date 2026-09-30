"""
LLM call logger.
Writes structured JSON Lines log entries and hash-based variable archives.
Archive files are write-once: first occurrence of a hash is persisted,
subsequent calls with the same hash skip the write.

Log file: {LLM_LOG_DIR}/{YYYY-MM-DD}.log  (JSON Lines)
Archive:   {LLM_LOG_DIR}/archive/{prefix}_{hash}.txt
"""

import json
import hashlib
import logging
import os
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional

from system_b.config import Config

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Independent logger for LLM call records (does not propagate to root)
# ---------------------------------------------------------------------------
_llm_call_logger: Optional[logging.Logger] = None
_archive_dir: Optional[Path] = None


def _get_llm_call_logger() -> logging.Logger:
    global _llm_call_logger
    if _llm_call_logger is not None:
        return _llm_call_logger

    _llm_call_logger = logging.getLogger("llm_call")
    _llm_call_logger.propagate = False
    _llm_call_logger.setLevel(logging.INFO)

    log_dir = Path(Config.LLM_LOG_DIR)
    log_dir.mkdir(parents=True, exist_ok=True)
    handler = logging.FileHandler(
        log_dir / f"{datetime.now().strftime('%Y-%m-%d')}.log",
        encoding="utf-8",
    )
    handler.setFormatter(logging.Formatter("%(message)s"))
    _llm_call_logger.addHandler(handler)
    return _llm_call_logger


def _get_archive_dir() -> Path:
    global _archive_dir
    if _archive_dir is None:
        _archive_dir = Path(Config.LLM_LOG_DIR) / "archive"
        _archive_dir.mkdir(parents=True, exist_ok=True)
    return _archive_dir


# ---------------------------------------------------------------------------
# Hash utilities
# ---------------------------------------------------------------------------

def hash_content(data) -> str:
    """Compute a 12-char hex hash from a dict or string."""
    if isinstance(data, dict):
        raw = json.dumps(data, ensure_ascii=False, sort_keys=True)
    else:
        raw = str(data)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:12]


# ---------------------------------------------------------------------------
# Archive (write-once per hash)
# ---------------------------------------------------------------------------

def archive_variable(prefix: str, hash_val: str, content: str) -> None:
    """Write content to archive/{prefix}_{hash}.txt if it doesn't already exist."""
    if not hash_val or not content:
        return
    archive_file = _get_archive_dir() / f"{prefix}_{hash_val}.txt"
    if not archive_file.exists():
        try:
            archive_file.write_text(content, encoding="utf-8")
        except Exception:
            pass  # silent failure — archive is non-critical


# ---------------------------------------------------------------------------
# LLM call entry
# ---------------------------------------------------------------------------

def write_llm_call(
    request_id: str,
    source: str,
    model: str,
    sys_prompt_ref: dict,
    usr_template_ref: dict,
    usr_vars: dict,
    resp: dict,
    error: Optional[str] = None,
    call_id: str = "",
) -> None:
    """
    Write a single LLM call record as JSON Lines.

    Args:
        request_id: The API request ID this call belongs to
        source: "decompose" | "step_analysis" | "summary" | "test"
        model: The model name used
        sys_prompt_ref: {"group": "prompt-main", "version": "v1.0.0"}
        usr_template_ref: {"group": "prompt-main", "version": "v1.0.0"}
        usr_vars: {"query": "...", "db_meta_hash": "...", "func_hash": "...", "biz_hash": "..."}
        resp: {"chars": 3120, "ms": 8421, "finish": "stop"}
        error: Error message if call failed, None otherwise
        call_id: Short unique ID for this call (auto-generated if empty)
    """
    try:
        entry = {
            "ts": datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
            "request_id": request_id,
            "call_id": call_id or f"llm-{uuid.uuid4().hex[:6]}",
            "source": source,
            "model": model,
            "sys_prompt_ref": sys_prompt_ref,
            "usr_template_ref": usr_template_ref,
            "usr_vars": usr_vars,
            "resp": resp,
            "error": error,
        }
        _get_llm_call_logger().info(json.dumps(entry, ensure_ascii=False))
    except Exception:
        pass  # silent failure — logging must not break the call