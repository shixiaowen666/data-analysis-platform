"""
Progress Push (add-on, no modification to existing logic)
=========================================================
Bridges analysis progress events into an in-process bus and exposes
them to the browser via SSE, without touching the original Redis path.

- Wraps AnalysisProgressReporter._send: the original method is still
  called first with identical arguments; afterwards the event is
  published to the in-memory bus. Publishing failures are swallowed.
- Blueprint: GET /api/v1/progress/<request_id> (SSE), subject to the
  app's global JWT middleware like any other API route.
"""

import json
import logging
import threading
import time
import uuid
from collections import deque

from flask import Blueprint, Response, request, stream_with_context

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# In-memory event bus: request_id -> deque of events (with replay buffer)
# Also caches the final analyze response per request_id, so a page that
# reloaded mid-analysis can recover the result after the fact.
# ---------------------------------------------------------------------------

_MAX_BUFFER_PER_REQUEST = 2000
_REQUEST_TTL_SECONDS = 600
_CLEANUP_INTERVAL_SECONDS = 60


class _RequestBuffer:
    __slots__ = ("events", "subscribers", "last_seen", "result")

    def __init__(self):
        self.events = deque(maxlen=_MAX_BUFFER_PER_REQUEST)
        self.subscribers = 0
        self.last_seen = time.time()
        self.result = None


_lock = threading.Lock()
_buffers: dict = {}
_last_cleanup = 0.0


def _cleanup_locked():
    global _last_cleanup
    now = time.time()
    if now - _last_cleanup < _CLEANUP_INTERVAL_SECONDS:
        return
    _last_cleanup = now
    expired = [
        rid for rid, buf in _buffers.items()
        if buf.subscribers == 0 and now - buf.last_seen > _REQUEST_TTL_SECONDS
    ]
    for rid in expired:
        del _buffers[rid]


def _get_buffer(request_id: str) -> _RequestBuffer:
    with _lock:
        _cleanup_locked()
        buf = _buffers.get(request_id)
        if buf is None:
            buf = _RequestBuffer()
            _buffers[request_id] = buf
        buf.last_seen = time.time()
        return buf


def publish(request_id: str, event: dict) -> None:
    """Publish one event; never raises."""
    try:
        buf = _get_buffer(request_id)
        with _lock:
            buf.events.append(event)
            buf.last_seen = time.time()
    except Exception:
        pass


def store_result(request_id: str, result: dict) -> None:
    """Cache the final analyze response for this request_id; never raises."""
    try:
        buf = _get_buffer(request_id)
        with _lock:
            buf.result = result
            buf.last_seen = time.time()
    except Exception:
        pass


def get_result(request_id: str):
    """Return the cached final analyze response, or None."""
    try:
        buf = _get_buffer(request_id)
        with _lock:
            return buf.result
    except Exception:
        return None


def subscribe(request_id: str, fresh: bool = False):
    """Return an iterator over events for this request_id.

    With replay by default (fresh=False): every event buffered so far is
    yielded first, so a late subscriber sees the whole picture.
    With fresh=True only events published after subscription are yielded —
    used by the test page when it re-runs with a borrowed production
    request_id, to avoid replaying stale events from the previous run.

    The caller must consume the returned generator inside one request
    lifecycle; the returned iterator ends when a close/done event is seen
    or the client disconnects.
    """
    buf = _get_buffer(request_id)
    with _lock:
        buf.subscribers += 1
    try:
        with _lock:
            cursor = len(buf.events) if fresh else 0
        idle_deadline = time.time() + 300  # hard cap for a single SSE connection
        while time.time() < idle_deadline:
            with _lock:
                events = list(buf.events)
            while cursor < len(events):
                ev = events[cursor]
                cursor += 1
                yield ev
                if ev.get("event_type") == "done" and ev.get("step_type") == "close":
                    return
            # closed by publisher after final event? keep waiting until deadline
            time.sleep(0.2)
    finally:
        with _lock:
            buf.subscribers -= 1
            buf.last_seen = time.time()


# ---------------------------------------------------------------------------
# Sideways bridge: wrap AnalysisProgressReporter._send (original logic intact)
# ---------------------------------------------------------------------------

_BRIDGE_INSTALLED = False


def install_bridge() -> None:
    """Wrap AnalysisProgressReporter._send once. Idempotent."""
    global _BRIDGE_INSTALLED
    if _BRIDGE_INSTALLED:
        return

    from system_b.utils.step_result_notifier import AnalysisProgressReporter

    original_send = AnalysisProgressReporter._send

    def wrapped_send(self, message, step_type, event_type, step_id=""):
        original_send(self, message, step_type, event_type, step_id)
        try:
            publish(
                self.request_id,
                {
                    "request_id": self.request_id,
                    "timestamp": int(time.time()),
                    "message": message,
                    "step_type": step_type,
                    "event_type": event_type,
                    "step_id": step_id,
                },
            )
        except Exception:
            pass

    AnalysisProgressReporter._send = wrapped_send
    _BRIDGE_INSTALLED = True


# ---------------------------------------------------------------------------
# SSE Blueprint
# ---------------------------------------------------------------------------

progress_bp = Blueprint("progress_push", __name__)


def _sse_format(event: dict) -> str:
    return f"data: {json.dumps(event, ensure_ascii=False)}\n\n"


@progress_bp.route("/api/v1/progress/<request_id>", methods=["GET"])
def progress_stream(request_id: str):
    """SSE stream of progress events for one analysis request."""
    fresh = request.args.get("fresh", "") == "1"

    def generate():
        yield ": connected\n\n"
        try:
            for ev in subscribe(request_id, fresh=fresh):
                yield _sse_format(ev)
        except GeneratorExit:
            raise
        except Exception as e:
            logger.warning(f"[ProgressPush] stream error: {e}")

    resp = Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
    )
    resp.headers["Cache-Control"] = "no-cache"
    resp.headers["X-Accel-Buffering"] = "no"
    return resp


@progress_bp.route("/api/v1/analyze-result/<request_id>", methods=["GET"])
def analyze_result(request_id: str):
    """Return the cached final analyze response for a request_id.

    Used by the test page to recover after a mid-analysis reload: the result
    is only available while the in-memory buffer (TTL 10 min) is alive.
    """
    result = get_result(request_id)
    if result is None:
        return {"found": False}
    return {"found": True, "result": result}


install_bridge()
