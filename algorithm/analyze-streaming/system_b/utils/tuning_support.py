"""
Tuning support utilities (问答质量管理 · 自动调优)
==================================================
Pure-python helpers used by the analyze endpoint and the prompt editor API:

* ``is_tuning_request``     - detect draft-verification requests (``X-Tuning-Task`` header
                              or ``tuning-`` request_id prefix). In that mode recall
                              shrinking is skipped and the caller-supplied ``database_meta``
                              (already patched by bi-manager with the draft changes) is used.
* ``PromptOverrideContext`` - thread-local prompt version override (``prompt_overrides``)
                              consumed by ``prompt_version_manager.get_current_prompt``.
* ``extract_structured``    - derive ``used_tables / resolved_metrics / resolved_dims``
                              from decomposition steps for the verification judge.
* ``lint_prompt``           - placeholder / separator / bracket checks for the editor.
* ``diff_lines``            - line-level diff (``+N / -M``) for the editor.

Everything here is side-effect free and unit-tested in ``tests/test_tuning_support.py``.
"""

from __future__ import annotations

import difflib
import re
import threading
from typing import Iterable, Optional

TUNING_HEADER = "X-Tuning-Task"
TUNING_REQUEST_PREFIX = "tuning-"

USER_TEMPLATE_SEPARATOR = "=====USER_TEMPLATE====="

# Placeholders that MUST survive an edit, per prompt group.
REQUIRED_PLACEHOLDERS = {
    "prompt-main": ["{query}", "{database_meta_text}", "{functions_text}", "{current_date}"],
    "prompt-summary": ["{original_question}", "{data_sections}", "{current_date}"],
}

# Known placeholder names across both groups (mirrors prompt_template.KNOWN_FIELDS).
KNOWN_PLACEHOLDERS = {
    "current_date", "database_meta_text", "functions_text", "query", "business_logic_knowledge",
    "original_question", "glossary_section", "data_sections", "format_instruction", "emphasis_instruction",
}

_PLACEHOLDER_RE = re.compile(r"(?<!\{)\{([a-zA-Z_][a-zA-Z0-9_]*)\}(?!\})")


# -----------------------------------------------------------------------------
# tuning request detection
# -----------------------------------------------------------------------------

def is_tuning_request(headers, request_id: str) -> bool:
    """True when the request comes from the tuning verifier."""
    try:
        if headers is not None and headers.get(TUNING_HEADER):
            return True
    except Exception:  # pragma: no cover - defensive against odd header objects
        pass
    return bool(request_id) and str(request_id).startswith(TUNING_REQUEST_PREFIX)


# -----------------------------------------------------------------------------
# prompt override (thread local; the DAG engine spawns worker threads, so the
# override is captured per analyze() call and propagated explicitly).
# -----------------------------------------------------------------------------

_local = threading.local()


class PromptOverrideContext:
    """Context manager that sets prompt version overrides for the current thread.

    ``overrides`` maps group name -> version string, e.g. ``{"prompt-main": "v1.0.6"}``.
    ``main`` / ``summary`` shorthands are accepted and normalised.
    """

    def __init__(self, overrides: Optional[dict]):
        self.overrides = normalize_prompt_overrides(overrides)
        self._prev = None

    def __enter__(self):
        self._prev = getattr(_local, "overrides", None)
        _local.overrides = self.overrides
        return self.overrides

    def __exit__(self, exc_type, exc, tb):
        _local.overrides = self._prev
        return False


def normalize_prompt_overrides(overrides: Optional[dict]) -> dict:
    if not isinstance(overrides, dict):
        return {}
    alias = {"main": "prompt-main", "summary": "prompt-summary",
             "decompose": "prompt-main", "PROMPT_DECOMPOSE": "prompt-main", "PROMPT_SUMMARY": "prompt-summary"}
    out = {}
    for k, v in overrides.items():
        if not v:
            continue
        group = alias.get(str(k), str(k))
        out[group] = str(v)
    return out


def get_prompt_override(group_name: str) -> Optional[str]:
    overrides = getattr(_local, "overrides", None)
    if not overrides:
        return None
    return overrides.get(group_name)


def set_prompt_overrides(overrides: Optional[dict]) -> None:
    """Explicitly set overrides on the current thread (used by DAG worker threads)."""
    _local.overrides = normalize_prompt_overrides(overrides)


def current_prompt_overrides() -> dict:
    return dict(getattr(_local, "overrides", None) or {})


# -----------------------------------------------------------------------------
# structured extraction for the judge
# -----------------------------------------------------------------------------

def extract_structured(steps: Iterable[dict], execution_log: Optional[list] = None) -> dict:
    """Return ``{"used_tables": [...], "resolved_metrics": [...], "resolved_dims": [...],
    "failed_steps": [...], "step_count": n}`` from decomposition steps.

    A column is treated as a metric when its ``role``/``type`` says measure/metric,
    otherwise as a dimension. Unknown roles fall back to dimension when the column
    carries a filter and metric otherwise (mirrors system_a_client role inference).
    """
    tables, metrics, dims = [], [], []
    step_count = 0
    for step in steps or []:
        step_count += 1
        params = (step or {}).get("params") or {}
        table = params.get("expected_table")
        if table and table not in tables:
            tables.append(table)
        for col in params.get("expected_columns") or []:
            if not isinstance(col, dict):
                continue
            name = col.get("name") or col.get("column_name")
            if not name:
                continue
            role = str(col.get("role") or col.get("type") or "").lower()
            is_metric = role in ("measure", "metric", "index", "indicator")
            is_dim = role in ("dimension", "dim", "dimid")
            if not is_metric and not is_dim:
                is_dim = bool(col.get("filter"))
                is_metric = not is_dim
            target = metrics if is_metric else dims
            if name not in target:
                target.append(name)
    failed = [e.get("step_id") for e in (execution_log or []) if e.get("status") not in ("success", None)]
    return {
        "used_tables": tables,
        "resolved_metrics": metrics,
        "resolved_dims": dims,
        "failed_steps": failed,
        "step_count": step_count,
    }


# -----------------------------------------------------------------------------
# prompt editor: lint + diff
# -----------------------------------------------------------------------------

def _bracket_balance(text: str) -> list[str]:
    """Check (), [], {} pairing ignoring escaped braces ``{{ }}``."""
    issues = []
    pairs = {")": "(", "]": "[", "}": "{"}
    stack = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch in "{}" and i + 1 < n and text[i + 1] == ch:
            i += 2
            continue
        if ch in "([{":
            stack.append((ch, i))
        elif ch in ")]}":
            if not stack or stack[-1][0] != pairs[ch]:
                line = text.count("\n", 0, i) + 1
                issues.append(f"第 {line} 行：多余的 '{ch}'")
            else:
                stack.pop()
        i += 1
    for ch, pos in stack:
        line = text.count("\n", 0, pos) + 1
        issues.append(f"第 {line} 行：'{ch}' 未闭合")
    return issues[:20]


def lint_prompt(group_name: str, content: str) -> dict:
    """Return ``{"ok": bool, "errors": [...], "warnings": [...], "placeholders": [...]}``."""
    errors, warnings = [], []
    content = content or ""
    if not content.strip():
        return {"ok": False, "errors": ["内容为空"], "warnings": [], "placeholders": []}

    if group_name == "prompt-main" and USER_TEMPLATE_SEPARATOR not in content:
        errors.append(f"缺少分隔符 {USER_TEMPLATE_SEPARATOR}（SYSTEM_PROMPT 与 USER_TEMPLATE 之间）")

    found = _PLACEHOLDER_RE.findall(content)
    placeholders = sorted(set(found))
    for required in REQUIRED_PLACEHOLDERS.get(group_name, []):
        if required not in content:
            errors.append(f"必需占位符 {required} 缺失")
    for p in placeholders:
        if p not in KNOWN_PLACEHOLDERS:
            # Lower-case snake identifiers that look like placeholders but are unknown.
            if re.fullmatch(r"[a-z_]+", p):
                warnings.append(f"未知占位符 {{{p}}}，渲染时将原样保留")

    bracket_issues = _bracket_balance(content)
    if bracket_issues:
        warnings.extend(bracket_issues)

    if len(content) > 200_000:
        warnings.append("提示词超过 20 万字符，可能超出模型上下文")

    return {"ok": not errors, "errors": errors, "warnings": warnings, "placeholders": placeholders}


def diff_lines(before: str, after: str, context: int = 2) -> dict:
    """Line diff. Returns ``{"added": n, "removed": m, "hunks": [...], "unified": str}``.

    ``hunks`` is a flat list of ``{"type": "add|del|ctx", "line": text, "lineNo": {"a":.., "b":..}}``
    suitable for a side-by-side viewer.
    """
    a = (before or "").splitlines()
    b = (after or "").splitlines()
    sm = difflib.SequenceMatcher(a=a, b=b, autojunk=False)
    hunks = []
    added = removed = 0
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            # keep limited context around changes
            span = list(range(i1, i2))
            if len(span) > context * 2 and hunks:
                keep = span[:context] + [None] + span[-context:]
            elif len(span) > context:
                keep = [None] + span[-context:] if not hunks else span[:context] + [None]
            else:
                keep = span
            for idx in keep:
                if idx is None:
                    hunks.append({"type": "skip", "line": "…", "lineNo": {}})
                else:
                    hunks.append({"type": "ctx", "line": a[idx], "lineNo": {"a": idx + 1, "b": j1 + (idx - i1) + 1}})
            continue
        if tag in ("replace", "delete"):
            for idx in range(i1, i2):
                removed += 1
                hunks.append({"type": "del", "line": a[idx], "lineNo": {"a": idx + 1}})
        if tag in ("replace", "insert"):
            for idx in range(j1, j2):
                added += 1
                hunks.append({"type": "add", "line": b[idx], "lineNo": {"b": idx + 1}})
    unified = "\n".join(difflib.unified_diff(a, b, fromfile="before", tofile="after", lineterm="", n=context))
    return {
        "added": added,
        "removed": removed,
        "charsBefore": len(before or ""),
        "charsAfter": len(after or ""),
        "hunks": hunks,
        "unified": unified,
    }


def next_version(version: str) -> str:
    """``v1.0.5`` -> ``v1.0.6`` (keeps the ``v`` prefix if present)."""
    m = re.match(r"^(v?)(\d+)\.(\d+)\.(\d+)$", (version or "").strip())
    if not m:
        return "v1.0.0"
    prefix, major, minor, patch = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4))
    return f"{prefix}{major}.{minor}.{patch + 1}"
