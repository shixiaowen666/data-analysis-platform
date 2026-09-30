"""
Access log middleware.

Records pruned request/response for management endpoints (prompts, user logs)
into {LOG_DIR}/access/YYYY-MM-DD.log, separate from run logs.

Rules (agreed):
- Success (HTTP 200 and body code == 200): only key fields.
  * request: whitelist fields per route; long strings (>100 chars, e.g. prompt
    content) summarized as {"len": N, "head": "first 100 chars"}.
  * response: code/message + scalar fields; "list" replaced by item count;
    long strings summarized.
- Failure: full request and response bodies (masked, truncated to 2000 chars).
- Sensitive keys (password/api_key/token/secret) always masked.
"""

import json
import logging
import os
import time
from datetime import datetime

from flask import g, request

from system_b.config import Config

logger = logging.getLogger("system_b.access")

# path -> list of request fields to keep; None = keep all fields; [] = log no body
_REQUEST_FIELDS = {
    "/api/v1/prompts/groups": ["keyword"],
    "/api/v1/prompts/group/create": ["name", "label", "description"],
    "/api/v1/prompts/group/update": ["id", "label", "description"],
    "/api/v1/prompts/group/delete": ["id"],
    "/api/v1/prompts/update": ["group_id", "version", "description"],
    "/api/v1/prompts/list": ["group_id", "keyword", "is_active", "page", "page_size"],
    "/api/v1/prompts/switch": ["group_id", "version"],
    "/api/v1/prompts/current": ["group_id"],
    "/api/v1/prompts/version/content": ["group_id", "version"],
    "/api/v1/prompts/delete": ["group_id", "version"],
    "/api/v1/logs/list": None,
    "/api/v1/logs/preview": None,
    "/api/v1/logs/download": None,
    "/api/v1/logs/users": [],
}

_SENSITIVE_KEYS = {"password", "api_key", "apikey", "secret", "token"}
_MAX_DUMP = 2000
_SUMMARY_HEAD = 100
_SUMMARY_MIN = 100  # summarize strings longer than this


class _DailyAccessHandler(logging.Handler):
    """Write to {LOG_DIR}/access/YYYY-MM-DD.log, rotating daily."""

    def __init__(self, log_dir: str):
        super().__init__()
        self.log_dir = log_dir
        self._current_date = ""
        self._fp = None

    def _rotate_if_needed(self):
        date_str = datetime.now().strftime("%Y-%m-%d")
        if date_str != self._current_date:
            if self._fp:
                self._fp.close()
            os.makedirs(self.log_dir, exist_ok=True)
            self._fp = open(os.path.join(self.log_dir, f"{date_str}.log"), "a", encoding="utf-8")
            self._current_date = date_str

    def emit(self, record):
        self._rotate_if_needed()
        self._fp.write(self.format(record) + "\n")
        self._fp.flush()

    def close(self):
        if self._fp:
            self._fp.close()
            self._fp = None
        super().close()


def _mask(value):
    """Recursively mask sensitive keys."""
    if isinstance(value, dict):
        return {
            k: ("****" if str(k).lower() in _SENSITIVE_KEYS else _mask(v))
            for k, v in value.items()
        }
    if isinstance(value, list):
        return [_mask(v) for v in value]
    return value


def _summarize(s: str):
    return {"len": len(s), "head": s[:_SUMMARY_HEAD]}


def _prune_strings(obj):
    """Summarize long strings inside a pruned structure."""
    if isinstance(obj, dict):
        return {k: _prune_strings(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_prune_strings(v) for v in obj]
    if isinstance(obj, str) and len(obj) > _SUMMARY_MIN:
        return _summarize(obj)
    return obj


def _dump(obj) -> str:
    try:
        s = json.dumps(_mask(obj), ensure_ascii=False, default=str)
    except (TypeError, ValueError):
        s = str(obj)
    return s if len(s) <= _MAX_DUMP else s[:_MAX_DUMP] + f"...<truncated, total {len(s)} chars>"


def _pruned_request_body(path: str, body):
    fields = _REQUEST_FIELDS.get(path)
    if fields is None:
        return _prune_strings(body)
    if not isinstance(body, dict):
        return body
    return _prune_strings({k: v for k, v in body.items() if k in fields})


def _pruned_response_body(body):
    out = {"code": body.get("code"), "message": body.get("message")}
    data = body.get("data")
    if isinstance(data, dict):
        pruned = {}
        for k, v in data.items():
            if isinstance(v, list):
                pruned[k] = f"<{len(v)} items>"
            elif isinstance(v, str) and len(v) > _SUMMARY_MIN:
                pruned[k] = _summarize(v)
            elif isinstance(v, (dict, list)):
                pruned[k] = _summarize(json.dumps(v, ensure_ascii=False, default=str))
            else:
                pruned[k] = v
        out["data"] = pruned
    elif isinstance(data, str):
        out["data"] = _summarize(data) if len(data) > _SUMMARY_MIN else data
    elif data is not None:
        out["data"] = data
    return out


def register_access_log(app):
    """Attach before/after request hooks that write the access log."""
    if not any(isinstance(h, _DailyAccessHandler) for h in logger.handlers):
        access_dir = os.path.join(Config.LOG_DIR, "access")
        handler = _DailyAccessHandler(access_dir)
        handler.setFormatter(logging.Formatter("%(asctime)s [ACCESS] %(message)s", datefmt="%Y-%m-%d %H:%M:%S"))
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
        logger.propagate = False  # keep access log out of run logs

    @app.before_request
    def _access_before():
        if request.path in _REQUEST_FIELDS:
            g.access_start = time.time()

    @app.after_request
    def _access_after(response):
        if not hasattr(g, "access_start"):
            return response
        duration_ms = int((time.time() - g.access_start) * 1000)

        req_body = request.get_json(silent=True)
        resp_body = None
        if response.mimetype == "application/json":
            try:
                resp_body = json.loads(response.get_data(as_text=True))
            except ValueError:
                resp_body = None

        ok = response.status_code == 200 and not (
            isinstance(resp_body, dict) and resp_body.get("code") not in (None, 200)
        )

        if resp_body is None:
            # file download (send_file) or non-JSON response
            resp_desc = f"<{response.mimetype} {response.status_code}>"
        elif ok:
            resp_desc = _dump(_pruned_response_body(resp_body))
        else:
            resp_desc = _dump(resp_body)

        if req_body is None:
            req_desc = "<no json body>"
        elif ok:
            req_desc = _dump(_pruned_request_body(request.path, req_body))
        else:
            req_desc = _dump(req_body)

        logger.info(
            "%s %s %s %dms\n  req : %s\n  resp: %s",
            request.remote_addr, request.method, request.path,
            duration_ms, req_desc, resp_desc,
        )
        return response
