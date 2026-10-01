"""Unit tests for 问答质量管理 · 调优 support utilities and new System B endpoints.

Run: cd algorithm/analyze-streaming && python -m pytest tests/test_tuning_support.py -q
"""
import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from system_b.utils import tuning_support as ts  # noqa: E402
from system_b.utils import prompt_version_manager as pvm  # noqa: E402


# ---------------------------------------------------------------- tuning mode

class _Headers(dict):
    def get(self, k, default=None):
        return super().get(k, default)


def test_is_tuning_request_by_header_and_prefix():
    assert ts.is_tuning_request(_Headers({"X-Tuning-Task": "12"}), "s@c")
    assert ts.is_tuning_request(_Headers(), "tuning-12-3")
    assert not ts.is_tuning_request(_Headers(), "session@chat")
    assert not ts.is_tuning_request(None, "")


def test_normalize_prompt_overrides_aliases():
    out = ts.normalize_prompt_overrides({"main": "v1.0.6", "PROMPT_SUMMARY": "v2.0.3", "x": ""})
    assert out == {"prompt-main": "v1.0.6", "prompt-summary": "v2.0.3"}
    assert ts.normalize_prompt_overrides(None) == {}


def test_prompt_override_context_is_thread_local_and_restored():
    assert ts.get_prompt_override("prompt-main") is None
    with ts.PromptOverrideContext({"main": "v9.9.9"}):
        assert ts.get_prompt_override("prompt-main") == "v9.9.9"
        assert ts.current_prompt_overrides() == {"prompt-main": "v9.9.9"}
    assert ts.get_prompt_override("prompt-main") is None


def test_get_current_prompt_uses_override(monkeypatch):
    """prompt_version_manager.get_current_prompt must honour the override without caching."""
    monkeypatch.setattr(pvm.db, "get_prompt_group_by_name", lambda name: {"id": 1, "name": name})
    monkeypatch.setattr(pvm.db, "get_version_by_group_and_version",
                        lambda gid, v: {"file_path": "prompt-main/v1.0.6.txt"} if v == "v1.0.6" else None)
    monkeypatch.setattr(pvm, "_read_file_content", lambda fp: "DRAFT CONTENT")
    pvm._cache.clear()
    with ts.PromptOverrideContext({"prompt-main": "v1.0.6"}):
        content, meta = pvm.get_current_prompt("prompt-main")
    assert content == "DRAFT CONTENT"
    assert meta["version"] == "v1.0.6" and meta["override"] is True
    assert "prompt-main" not in pvm._cache  # override never pollutes the production cache

    with pytest.raises(ValueError):
        with ts.PromptOverrideContext({"prompt-main": "v0.0.0"}):
            pvm.get_current_prompt("prompt-main")


# ------------------------------------------------------- structured extraction

def test_extract_structured():
    steps = [
        {"step_id": "step_1", "step_type": "query", "params": {
            "expected_table": "view_line_loss_by_voltage",
            "expected_columns": [
                {"name": "ll_rate", "role": "measure"},
                {"name": "voltage_level", "role": "dimension"},
                {"name": "org_name", "filter": [{"operator": "=", "values": ["深圳"]}]},
            ]}},
        {"step_id": "step_2", "step_type": "summarize", "params": {}},
    ]
    log = [{"step_id": "step_1", "status": "success"}, {"step_id": "step_2", "status": "failed"}]
    out = ts.extract_structured(steps, log)
    assert out["used_tables"] == ["view_line_loss_by_voltage"]
    assert out["resolved_metrics"] == ["ll_rate"]
    assert out["resolved_dims"] == ["voltage_level", "org_name"]
    assert out["failed_steps"] == ["step_2"]
    assert out["step_count"] == 2


# ------------------------------------------------------------ lint and diff

MAIN_OK = "SYSTEM {current_date}\n=====USER_TEMPLATE=====\n{query} {database_meta_text} {functions_text} {business_logic_knowledge}"


def test_lint_prompt_main_ok_and_errors():
    ok = ts.lint_prompt("prompt-main", MAIN_OK)
    assert ok["ok"] and ok["errors"] == []
    assert "query" in ok["placeholders"]

    bad = ts.lint_prompt("prompt-main", "no separator {query}")
    assert not bad["ok"]
    assert any("分隔符" in e for e in bad["errors"])
    assert any("{database_meta_text}" in e for e in bad["errors"])

    empty = ts.lint_prompt("prompt-main", "   ")
    assert empty["errors"] == ["内容为空"]


def test_lint_bracket_and_unknown_placeholder_warnings():
    r = ts.lint_prompt("prompt-summary", "{original_question} {data_sections} {current_date} {foo_bar} ( [ }")
    assert r["ok"]  # warnings don't block
    assert any("foo_bar" in w for w in r["warnings"])
    assert any("未闭合" in w or "多余" in w for w in r["warnings"])
    # escaped braces are ignored by the bracket checker
    r2 = ts.lint_prompt("prompt-summary", "{original_question} {data_sections} {current_date} {{\"a\": 1}}")
    assert not any("多余" in w for w in r2["warnings"])


def test_diff_lines_counts_and_hunks():
    before = "a\nb\nc\nd"
    after = "a\nB\nc\nd\ne"
    d = ts.diff_lines(before, after)
    assert d["added"] == 2 and d["removed"] == 1
    types = [h["type"] for h in d["hunks"]]
    assert "del" in types and "add" in types and "ctx" in types
    assert "+B" in d["unified"] and "-b" in d["unified"]
    same = ts.diff_lines("x", "x")
    assert same["added"] == 0 and same["removed"] == 0


def test_next_version():
    assert ts.next_version("v1.0.5") == "v1.0.6"
    assert ts.next_version("2.3.9") == "2.3.10"
    assert ts.next_version("garbage") == "v1.0.0"


# ----------------------------------------------------------- flask endpoints

@pytest.fixture(scope="module")
def client():
    os.environ.setdefault("LLM_API_KEY", "test")
    from system_b import app as app_module
    app_module.app.config["TESTING"] = True
    return app_module.app.test_client()


def _auth(monkeypatch):
    from system_b import auth
    monkeypatch.setattr(auth, "get_user", lambda h: ("1", "tester"))


def test_prompts_lint_endpoint(client, monkeypatch):
    _auth(monkeypatch)
    from system_b import app as app_module
    monkeypatch.setattr(app_module.db, "get_prompt_group_by_name", lambda n: {"id": 1, "name": n})
    r = client.post("/api/v1/prompts/lint", json={"group_name": "prompt-main", "content": MAIN_OK},
                    headers={"Authorization": "Bearer x"})
    body = r.get_json()
    assert body["code"] == 200 and body["data"]["ok"] is True


def test_prompts_version_diff_endpoint_with_contents(client, monkeypatch):
    _auth(monkeypatch)
    r = client.post("/api/v1/prompts/version/diff", json={"from_content": "a\nb", "to_content": "a\nc"},
                    headers={"Authorization": "Bearer x"})
    body = r.get_json()
    assert body["code"] == 200
    assert body["data"]["added"] == 1 and body["data"]["removed"] == 1


def test_prompts_version_create_validations_and_success(client, monkeypatch):
    _auth(monkeypatch)
    from system_b import app as app_module
    group = {"id": 1, "name": "prompt-main"}
    monkeypatch.setattr(app_module.db, "get_prompt_group_by_name", lambda n: group)
    monkeypatch.setattr(app_module.db, "get_prompt_group_by_id", lambda i: group)
    monkeypatch.setattr(app_module, "get_version_content",
                        lambda group_id, version: {"content": MAIN_OK, "version": version} if version == "v1.0.5" else None)
    monkeypatch.setattr(app_module, "get_current_version", lambda gid: {"version": "v1.0.5", "content": MAIN_OK})
    monkeypatch.setattr(app_module.db, "get_latest_version_number", lambda gid: "v1.0.5")
    existing = {"v1.0.5"}
    monkeypatch.setattr(app_module.db, "get_version_by_group_and_version",
                        lambda gid, v: {"version": v} if v in existing else None)
    created = {}

    def fake_update(group_id, content, version, description):
        created.update(group_id=group_id, version=version, description=description)
        return {"version_id": 42, "version": version, "file_path": f"prompt-main/{version}.txt", "is_active": False}
    monkeypatch.setattr(app_module, "update_version", fake_update)
    H = {"Authorization": "Bearer x"}

    # missing description
    r = client.post("/api/v1/prompts/version/create", json={"group_name": "prompt-main", "content": MAIN_OK}, headers=H)
    assert r.get_json()["code"] == 400
    # identical to baseline -> empty diff rejected
    r = client.post("/api/v1/prompts/version/create",
                    json={"group_name": "prompt-main", "content": MAIN_OK, "description": "d", "base_version": "v1.0.5"}, headers=H)
    assert "diff 为空" in r.get_json()["message"]
    # lint failure -> 422
    r = client.post("/api/v1/prompts/version/create",
                    json={"group_name": "prompt-main", "content": "broken", "description": "d"}, headers=H)
    assert r.get_json()["code"] == 422
    # success: auto version v1.0.6, not active
    r = client.post("/api/v1/prompts/version/create",
                    json={"group_name": "prompt-main", "content": MAIN_OK + "\n规则十四之二 累计类指标处理", "description": "fix"}, headers=H)
    body = r.get_json()
    assert body["code"] == 200
    assert body["data"]["version"] == "v1.0.6" and body["data"]["is_active"] is False
    assert body["data"]["diff_added"] == 1 and body["data"]["base_version"] == "v1.0.5"
    assert created["description"] == "fix"


def test_logs_by_request_endpoint(client, monkeypatch, tmp_path):
    _auth(monkeypatch)
    from system_b import app as app_module
    monkeypatch.setattr(app_module.Config, "LOGS_DIR", str(tmp_path))
    (tmp_path / "userlogs").mkdir()
    (tmp_path / "userlogs" / "q.log").write_text("LOG BODY", encoding="utf-8")
    monkeypatch.setattr(app_module.db, "get_operation_log_by_request_id",
                        lambda rid: {"log_path": "userlogs/q.log", "question": "q", "user_id": "1",
                                     "username": "u", "created_at": "2026-10-01"} if rid == "s@c" else None)
    H = {"Authorization": "Bearer x"}
    r = client.post("/api/v1/logs/by-request", json={"request_id": "s@c"}, headers=H)
    body = r.get_json()
    assert body["code"] == 200 and body["data"]["content"] == "LOG BODY"
    r = client.post("/api/v1/logs/by-request", json={"request_id": "nope"}, headers=H)
    assert r.get_json()["code"] == 404
