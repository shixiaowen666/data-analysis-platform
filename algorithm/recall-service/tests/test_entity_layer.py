# -*- coding: utf-8 -*-
"""
实体层（增量向量化 / Agent 范围召回）离线测试。

运行方式（无需任何外部 API / MySQL）：
    cd webapp && python -m pytest tests/test_entity_layer.py -q

原理：
  - EMBEDDING_PROVIDER=fake → 确定性 n-gram 哈希向量，不出网
  - DB_URL=sqlite:///<tmp> → 每次测试会话使用独立的临时库
  - 数据源为仓库内 data/database_meta.json
"""
import json
import os
import sys
import tempfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

_TMP = tempfile.mkdtemp(prefix="recall_test_")
os.environ.setdefault("EMBEDDING_PROVIDER", "fake")
os.environ.setdefault("EMBEDDING_DIM", "256")
os.environ.setdefault("DB_URL", f"sqlite:///{_TMP}/recall.db")
os.environ.setdefault("LOG_DIR", f"{_TMP}/logs")
os.environ.setdefault("LLM_API_KEY", "sk-dummy")
os.environ.setdefault("ADMIN_API_KEY", "test-key")
os.environ.setdefault("AUTO_IMPORT_ON_EMPTY", "0")
os.environ.setdefault("META_MIRROR_ENABLED", "0")

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402
from app.entity.sync_service import get_sync_service, _prefix_match  # noqa: E402
from app.entity.store import get_entity_store  # noqa: E402
from app.entity.scope import get_scope_resolver, AgentNotFound  # noqa: E402

ADMIN = {"X-API-Key": "test-key"}


def _dm() -> dict:
    return json.loads((ROOT / "data" / "database_meta.json").read_text(encoding="utf-8"))["database_meta"]


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:
        # 导入基线数据（fake provider，秒级）
        r = c.post("/api/entities/import?replace=true", json=_dm(), headers=ADMIN)
        assert r.status_code == 200, r.text
        assert r.json()["encoded"] > 0
        yield c


# ----------------------------------------------------------------------
# 基础工具
# ----------------------------------------------------------------------
def test_prefix_match_does_not_overreach():
    assert _prefix_match("metric:ll_rate", "metric:ll_rate")
    assert _prefix_match("metric:ll_rate#a1b2c3d4", "metric:ll_rate")
    assert not _prefix_match("metric:ll_rate_total", "metric:ll_rate")
    assert _prefix_match("value:org_name:宝安", "value:org_name:")
    assert not _prefix_match("value:org_name_x:宝安", "value:org_name:")


# ----------------------------------------------------------------------
# 鉴权
# ----------------------------------------------------------------------
def test_admin_key_required_on_write(client):
    r = client.post("/api/entities/resync", json={})
    assert r.status_code == 401
    r = client.put("/api/agents/x", json={"scope": {"tables": ["view_gdl"]}})
    assert r.status_code == 401
    # 读接口不需要
    assert client.get("/api/entities/status").status_code == 200
    assert client.get("/api/agents").status_code == 200


# ----------------------------------------------------------------------
# 增量向量化
# ----------------------------------------------------------------------
def test_idempotent_repush_encodes_nothing(client):
    dm = _dm()
    m = dm["available_metrics"][0]
    r = client.post("/api/entities/upsert", json={"metrics": [m]}, headers=ADMIN)
    assert r.status_code == 200, r.text
    assert r.json()["encoded"] == 0


def test_metric_description_change_only_reencodes_itself(client):
    store = get_entity_store()
    dm = _dm()
    m = dict(dm["available_metrics"][0])
    code = m["metric_code"]
    before = store.entity_counts()
    m["description"] = (m.get("description") or "") + " 单元测试修改描述"
    r = client.post("/api/entities/upsert", json={"metrics": [m]}, headers=ADMIN)
    body = r.json()
    # 描述变化只影响该指标（含变体），不影响表 / 维度
    variants = [k for k in get_sync_service().catalog.by_key if k == f"metric:{code}" or k.startswith(f"metric:{code}#")]
    assert body["encoded"] == len(variants)
    assert body["removed"] == 0
    assert store.entity_counts() == before
    # 再推一次 → 0
    assert client.post("/api/entities/upsert", json={"metrics": [m]}, headers=ADMIN).json()["encoded"] == 0


def test_metric_name_change_propagates_to_parent_table(client):
    dm = _dm()
    m = dict(dm["available_metrics"][0])
    m["metric_name"] = m["metric_name"] + "X"
    r = client.post("/api/entities/upsert", json={"metrics": [m]}, headers=ADMIN).json()
    cat = get_sync_service().catalog
    variants = [k for k in cat.by_key if k == f"metric:{m['metric_code']}" or k.startswith(f"metric:{m['metric_code']}#")]
    parent_tables = {t for k in variants for t in cat.by_key[k]["legal_tables"]}
    assert parent_tables
    # 传播：父表被重建并做 hash 校验（built 计入），但表文本使用列描述、未变化 → 不重编码
    assert r["built"] >= len(variants) + len(parent_tables)
    assert r["encoded"] == len(variants)
    assert r["unchanged"] >= len(parent_tables)
    # 回滚名字
    client.post("/api/entities/upsert", json={"metrics": [dm["available_metrics"][0]]}, headers=ADMIN)


def test_dim_value_add_and_delete(client):
    dm = _dm()
    d = next(x for x in dm["available_dimensions"] if x.get("possible_values"))
    code = d["dimension_code"]
    key = f"value:{code}:单元测试值"
    r = client.post("/api/entities/upsert",
                    json={"dim_values": [{"dimension_code": code, "values": ["单元测试值"], "mode": "upsert"}]},
                    headers=ADMIN).json()
    # 新值 1 条 + 维度本体（其文本包含值列表）
    assert r["encoded"] >= 1
    assert client.get(f"/api/entities/{key}").status_code == 200
    r = client.post("/api/entities/upsert",
                    json={"dim_values": [{"dimension_code": code, "values": ["单元测试值"], "mode": "delete"}]},
                    headers=ADMIN).json()
    assert r["removed"] == 1
    assert client.get(f"/api/entities/{key}").status_code == 404


def test_new_table_then_delete(client):
    tbl = {
        "table_name": "ut_tmp_table", "display_name": "单元测试临时表", "description": "仅测试用",
        "columns": [
            {"column_name": "daypowersupply", "data_type": "text", "is_dimension": False, "is_measure": True,
             "description": "日供电量", "agg_type": "sum"},
            {"column_name": "org_name", "data_type": "varchar", "is_dimension": True, "is_measure": False,
             "description": "供电局名称", "agg_type": ""},
        ],
    }
    r = client.post("/api/entities/upsert", json={"tables": [tbl]}, headers=ADMIN).json()
    assert r["encoded"] >= 1
    cat = get_sync_service().catalog
    assert "ut_tmp_table" in cat.table_members
    # 指标 legal_tables 已包含新表
    ent = client.get("/api/entities/metric:daypowersupply").json()
    assert "ut_tmp_table" in ent["legal_tables"]

    r = client.delete("/api/entities/table/ut_tmp_table", headers=ADMIN).json()
    assert r["removed"] >= 1
    assert "ut_tmp_table" not in get_sync_service().catalog.table_members
    ent = client.get("/api/entities/metric:daypowersupply").json()
    assert "ut_tmp_table" not in ent["legal_tables"]


def test_table_rebuild_keeps_members(client):
    """维度值变更 → 传播重建维度/表实体时，Catalog.table_members 不得丢失成员（真实冒烟发现的回归）"""
    cat = get_sync_service().catalog
    before = {et: set(v) for et, v in cat.table_members["view_gdl"].items()}
    assert before["metric"], "view_gdl 应有指标成员"
    dm = _dm()
    tbl = next(t for t in dm["table_summaries"] if t["table_name"] == "view_gdl")
    tbl = dict(tbl, description=(tbl.get("description") or "") + " 回归测试")
    r = client.post("/api/entities/upsert", json={"tables": [tbl]}, headers=ADMIN).json()
    assert r["encoded"] >= 1
    after = {et: set(v) for et, v in cat.table_members["view_gdl"].items()}
    assert after == before
    assert client.post("/api/scope/resolve", json={"tables": ["view_gdl"]}).json()["n_metric"] == len(before["metric"])


def test_resync_incremental_is_noop(client):
    r = client.post("/api/entities/resync", headers=ADMIN).json()
    assert r["encoded"] == 0
    assert r["removed"] == 0


# ----------------------------------------------------------------------
# Agent / Scope
# ----------------------------------------------------------------------
def test_agent_register_does_not_vectorize(client):
    st0 = client.get("/api/entities/status").json()
    r = client.put("/api/agents/ut_agent", json={"agent_name": "单元测试", "scope": {"tables": ["view_gdl"]}}, headers=ADMIN)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["version"] >= 1
    assert body["resolved"]["n_tables"] == 1
    st1 = client.get("/api/entities/status").json()
    assert st0["catalog_version"] == st1["catalog_version"]
    assert st0["entities"] == st1["entities"]

    a = client.get("/api/agents/ut_agent").json()
    assert a["scope"]["tables"] == ["view_gdl"]
    assert any(x["agent_id"] == "ut_agent" for x in client.get("/api/agents").json()["agents"])


def test_recall_with_agent_is_restricted(client):
    q = "宝安昨天的供电量"
    full = client.post("/api/recall", json={"query": q, "use_rewrite": False}).json()
    scoped = client.post("/api/recall", json={"query": q, "use_rewrite": False, "agent_id": "ut_agent"}).json()
    assert full["scope"]["unrestricted"] is True
    assert scoped["scope"]["unrestricted"] is False
    view_gdl_id = get_sync_service().catalog.table_id_of_name["view_gdl"]
    for c in scoped["candidates"]:
        if c["entity_type"] in ("table", "metric", "dimension", "dim_value"):
            # legal_tables 已按范围裁剪：只剩 view_gdl
            assert set(c.get("legal_tables") or []) <= {view_gdl_id}
    # 内联 scope 与 agent 等价
    inline = client.post("/api/recall", json={"query": q, "use_rewrite": False,
                                              "scope": {"tables": ["view_gdl"]}}).json()
    assert [c["entity_key"] for c in inline["candidates"]] == [c["entity_key"] for c in scoped["candidates"]]


def test_slim_and_prod_honor_scope(client):
    r = client.post("/api/recall/slim", json={"query": "宝安昨天的供电量", "use_rewrite": False,
                                              "agent_id": "ut_agent", "use_llm_judge": False}).json()
    assert r["scope"]["unrestricted"] is False
    dm = r["database_meta"]
    assert {t["table_name"] for t in dm.get("table_summaries", [])} <= {"view_gdl"}
    for m in dm.get("available_metrics", []):
        assert set(m.get("tables", [])) <= {"view_gdl"}

    r = client.post("/api/recall/prod", json={"query": "宝安昨天的供电量", "agent_id": "not_exists"})
    assert r.status_code == 200
    assert r.json()["code"] == 404


def test_unknown_agent_raises(client):
    with pytest.raises(AgentNotFound):
        get_scope_resolver().resolve(agent_id="definitely_missing")
    r = client.post("/api/recall", json={"query": "x", "agent_id": "definitely_missing"})
    assert r.status_code == 404


def test_scope_resolve_endpoint(client):
    r = client.post("/api/scope/resolve", json={"tables": ["view_gdl"]}).json()
    assert r["tables"] == ["view_gdl"]
    assert r["unrestricted"] is False


def test_agent_update_invalidates_cache_and_delete(client):
    client.put("/api/agents/ut_agent", json={"scope": {"tables": ["view_gdl"], "metrics": ["daypowersupply"]}}, headers=ADMIN)
    a = client.get("/api/agents/ut_agent").json()
    assert a["version"] >= 2
    assert a["resolved"]["n_metric"] >= 1
    r = client.delete("/api/agents/ut_agent", headers=ADMIN)
    assert r.status_code == 200
    assert client.get("/api/agents/ut_agent").status_code == 404


# ----------------------------------------------------------------------
# 兼容接口
# ----------------------------------------------------------------------
def test_legacy_endpoints_still_work(client):
    assert client.get("/health").json()["status"] == "ok"
    s = client.get("/api/metadata/stats").json()
    assert s["tables"] >= 1
    lst = client.get("/api/metadata/list/metric?limit=5").json()
    assert lst["count"] >= 1 and "entity_key" in lst["items"][0]
    dm = client.get("/api/metadata/database-meta").json()
    assert "table_summaries" in dm and "available_metrics" in dm
    # 兼容接口 rebuild 为异步任务：提交后轮询 status
    r = client.post("/api/metadata/rebuild", headers=ADMIN)
    assert r.status_code == 200
    import time as _t
    for _ in range(100):
        st = client.get("/api/metadata/rebuild/status").json()
        if not st.get("running"):
            break
        _t.sleep(0.1)
    assert not st.get("running")
    r = client.get("/api/recall", params={"query": "供电量"}).json()
    assert r["candidates"]
