"""FastAPI 路由：召回、配置、元数据"""
import json
import re
import threading
import time
from pathlib import Path
from fastapi import APIRouter, HTTPException, Body, UploadFile, File, Depends, Header
from fastapi.responses import Response
from typing import List, Dict, Any, Optional

from app import config
from app.api.schemas import (
    RecallRequest, RecallResponse, CandidateItem,
    ConfigUpdateRequest, BatchUpdateItem, ConfigResponse,
    SlimRecallRequest, SyncRequest, SyncStatusResponse,
    BatchRunRequest, EntityUpsertRequest, AgentUpsertRequest, ScopeSpec,
)
from app.core.database import get_db
from app.core.index_cache import save_cache, current_database_meta_hash
from app.entity.sync_service import get_sync_service
from app.entity.scope import get_scope_resolver, AgentNotFound
from app.entity.store import get_entity_store
from app.core.prompt_manager import list_prompts as _list_prompts
from app.core.prompt_manager import update_prompt as _update_prompt
from app.core.prompt_manager import reset_prompt as _reset_prompt
from app.core.config_manager import get_config_manager
from app.core.llm import get_llm_client
from app.services.recall_service import RecallService
from app.services.llm_judge import LLMJudge, extract_selected_ids
from app.services.meta_sync import get_meta_sync_service
from app.services.description_sync import description_sync_service, trigger_auto_push
from app.services.query_rewriter import get_query_rewriter
from app.services.relation_registry import get_relation_registry, reload_relation_registry
from app.services.slim_exporter import SlimExporter
from app.services.slim_pipeline import SlimPipeline
from app.services import batch_test_service as bts
from app.utils.logger import get_logger

logger = get_logger("api", "api.log")

router = APIRouter()


# ----------------------------------------------------------
# 管理接口鉴权：ADMIN_API_KEY 非空时，写操作需带 X-API-Key
# ----------------------------------------------------------
def require_admin(x_api_key: Optional[str] = Header(None, alias="X-API-Key")):
    if config.ADMIN_API_KEY and x_api_key != config.ADMIN_API_KEY:
        raise HTTPException(401, "invalid or missing X-API-Key")
    return True


ADMIN = [Depends(require_admin)]

# 这些会在 main.py 启动时填充
_state: Dict[str, Any] = {}
_DATABASE_META_PATH = config.DATA_DIR / "database_meta.json"

# 重建/刷索引全程互斥：重建中索引与库数据短暂不一致，禁止并发触发
_rebuild_lock = threading.Lock()

# rebuild 进度状态（后台线程写，/rebuild/status 轮询读）
_rebuild_status: Dict[str, Any] = {"running": False, "percent": 0, "message": "", "result": None}


def _load_business_context() -> list:
    """业务知识来自实体层源对象（src_knowledge）"""
    return [{"knowledge_id": k["knowledge_id"], "knowledgeAlias": k["knowledgeAlias"],
             "knowledgeElement": k["knowledgeElement"]} for k in get_entity_store().load_knowledge()]


def _write_meta_mirror():
    """把实体层源对象导出为 data/database_meta.json（镜像，供备份/离线查看；原子写）"""
    if not config.META_MIRROR_ENABLED:
        return
    try:
        import os, tempfile
        dm = get_sync_service().export_database_meta()
        tmp = tempfile.NamedTemporaryFile("w", delete=False, dir=str(_DATABASE_META_PATH.parent),
                                          encoding="utf-8", suffix=".tmp")
        with tmp:
            json.dump({"database_meta": dm}, tmp, ensure_ascii=False, indent=2)
        os.replace(tmp.name, _DATABASE_META_PATH)
        save_cache()
    except Exception as e:
        logger.warning(f"database_meta.json mirror write failed: {e}")


def set_index_state(loaded_at: float = None):
    """记录当前内存索引状态（启动/重建后调用），供 /health 暴露"""
    svc = get_sync_service()
    _state["index_state"] = {
        "database_meta_hash": current_database_meta_hash(),
        "loaded_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "catalog_version": svc.catalog.version,
        "index": svc.index.stats(),
    }


@router.post("/api/metadata/rebuild", dependencies=ADMIN)
def rebuild_metadata(encode: bool = True, raw_path: str = None, force: bool = False, import_file: bool = False):
    """
    [兼容接口] 全量重建（后台线程执行，立即返回）。
    新语义：按实体层源对象重建全部实体，**仍只对 text_hash 变化的实体重新编码**；
      - force=true：忽略 hash/缓存全部重编（换 embedding 模型或文本模板后使用）
      - import_file=true：先把 data/database_meta.json（或 raw_path）导入为源对象再重建
    进度通过 GET /api/metadata/rebuild/status 轮询获取
    """
    if _rebuild_status["running"] or not _rebuild_lock.acquire(blocking=False):
        raise HTTPException(409, "另一个重建任务正在进行中，请稍后重试")
    _rebuild_status.update(running=True, percent=0, message="任务提交", result=None, started_at=time.time())

    def _run():
        try:
            t0 = time.time()
            svc = get_sync_service()
            if import_file or raw_path:
                _rebuild_status.update(percent=10, message="导入 database_meta.json")
                p = Path(raw_path) if raw_path else _DATABASE_META_PATH
                if not p.is_absolute():
                    p = config.DATA_DIR / p
                if not p.exists() or config.DATA_DIR not in p.resolve().parents and p.resolve() != _DATABASE_META_PATH.resolve():
                    raise ValueError(f"raw_path 必须位于 data 目录下且存在: {p}")
                dm = json.loads(p.read_text(encoding="utf-8")).get("database_meta", {})
                _rebuild_status.update(percent=30, message="重建实体与向量")
                res = svc.import_database_meta(dm, replace=True, force=force)
            else:
                _rebuild_status.update(percent=30, message="重建实体与向量")
                res = svc.resync(force=force)
            _rebuild_status.update(percent=90, message="刷新派生缓存")
            _refresh_derived_caches()
            _write_meta_mirror()
            set_index_state()
            elapsed = time.time() - t0
            counts = _entity_counts()
            logger.info(f"Metadata rebuilt in {elapsed:.1f}s, counts={counts}, sync={res}")
            _rebuild_status.update(running=False, percent=100, message="重建完成",
                                   result={"status": "ok", "counts": counts, "sync": res,
                                           "elapsed_seconds": round(elapsed, 2)})
        except Exception as e:
            logger.exception("Metadata rebuild failed")
            _rebuild_status.update(running=False, percent=0, message=f"重建失败: {e}",
                                   result={"status": "error", "error": str(e)})
        finally:
            _rebuild_lock.release()

    threading.Thread(target=_run, daemon=True, name="rebuild-metadata").start()
    return {"status": "started"}


def _entity_counts() -> dict:
    c = get_sync_service().store.entity_counts()
    return {"tables": c.get("table", 0), "metrics": c.get("metric", 0), "dimensions": c.get("dimension", 0),
            "dim_values": c.get("dim_value", 0), "topics": c.get("topic", 0),
            "derived_metrics": c.get("knowledge", 0)}


@router.get("/api/metadata/rebuild/status")
def rebuild_status():
    """当前 rebuild 的进度状态（前端轮询）"""
    return _rebuild_status


def init_state(recall_service: RecallService, llm_judge: LLMJudge, slim_exporter: SlimExporter = None):
    _state["recall"] = recall_service
    _state["judge"] = llm_judge
    _state["slim_exporter"] = slim_exporter or SlimExporter()
    _state["pipeline"] = SlimPipeline(recall_service, _state["slim_exporter"], llm_judge)


# ----------------------------------------------------------
# 健康检查
# ----------------------------------------------------------
@router.get("/health")
def health():
    loaded = _state.get("index_state") or {}
    svc = get_sync_service()
    return {
        "status": "ok", "ts": int(time.time()),
        "index": {
            "loaded_at": loaded.get("loaded_at"),
            "catalog_version": svc.catalog.version,
            "counts": svc.index.stats(),
            "embed_model": svc.model_tag,
            # 兼容字段：实体层不再依赖文件指纹，stale 恒为 false
            "database_meta_hash": current_database_meta_hash(),
            "file_hash": current_database_meta_hash(),
            "stale": False,
        },
    }


# ----------------------------------------------------------
# 1. 召回主接口
# ----------------------------------------------------------
@router.post("/api/recall", response_model=RecallResponse)
def recall(req: RecallRequest):
    rs: RecallService = _state.get("recall")
    if rs is None:
        raise HTTPException(503, "Recall service not ready")
    t0 = time.time()
    try:
        scope = get_scope_resolver().resolve(agent_id=req.agent_id,
                                             scope=req.scope.model_dump() if req.scope else None)
    except AgentNotFound as e:
        raise HTTPException(404, f"agent not found: {e}")
    out = rs.recall(req.query, parallel=req.parallel, scope=scope)
    if req.use_llm_judge:
        judge: LLMJudge = _state["judge"]
        t_j = time.time()
        try:
            judgement = judge.judge(req.query, out["candidates"])
            out["llm_judge"] = judgement
            logger.info(f"[recall] LLM judge ok in {time.time()-t_j:.2f}s")
        except Exception as e:
            logger.error(f"[recall] LLM judge fail in {time.time()-t_j:.2f}s: {e}")
            out["llm_judge"] = {"error": str(e)}

    tm = out.get("timings_ms", {})
    logger.info(
        f"[recall] api_total={time.time()-t0:.3f}s candidates={len(out['candidates'])} "
        f"(enc={tm.get('encode_ms')}ms ret={tm.get('retrieval_ms')}ms "
        f"mer={tm.get('merge_ms')}ms) judge={req.use_llm_judge} parallel={req.parallel}"
    )
    return out


@router.get("/api/recall")
def recall_get(query: str, use_llm_judge: bool = False, agent_id: str = None):
    """GET 方式调用，便于 curl 测试"""
    return recall(RecallRequest(query=query, use_llm_judge=use_llm_judge, agent_id=agent_id))


# ----------------------------------------------------------
# 2. 配置管理（动态调整召回参数）
# ----------------------------------------------------------
@router.get("/api/recall/config", response_model=List[ConfigResponse])
def list_configs():
    mgr = get_config_manager()
    return [
        ConfigResponse(
            path_name=c.path_name, entity_type=c.entity_type,
            recall_mode=c.recall_mode, top_k=c.top_k,
            threshold=c.threshold, enabled=c.enabled,
            description=c.description,
        ) for c in mgr.list_all()
    ]


@router.get("/api/recall/config/{path_name}/{entity_type}", response_model=ConfigResponse)
def get_config(path_name: str, entity_type: str):
    mgr = get_config_manager()
    cfg = mgr.get(path_name, entity_type)
    return ConfigResponse(
        path_name=cfg.path_name, entity_type=cfg.entity_type,
        recall_mode=cfg.recall_mode, top_k=cfg.top_k,
        threshold=cfg.threshold, enabled=cfg.enabled,
        description=cfg.description,
    )


@router.put("/api/recall/config/{path_name}/{entity_type}", response_model=ConfigResponse, dependencies=ADMIN)
def update_config(path_name: str, entity_type: str, req: ConfigUpdateRequest):
    mgr = get_config_manager()
    upd = req.model_dump(exclude_none=True)
    if not upd:
        raise HTTPException(400, "至少需要提供一个待更新字段")
    try:
        cfg = mgr.update(path_name, entity_type, **upd)
    except ValueError as e:
        logger.warning(f"[config] update rejected {path_name}/{entity_type}: {e}")
        raise HTTPException(400, str(e))
    logger.info(f"[config] updated {path_name}/{entity_type}: {upd}")
    return ConfigResponse(
        path_name=cfg.path_name, entity_type=cfg.entity_type,
        recall_mode=cfg.recall_mode, top_k=cfg.top_k,
        threshold=cfg.threshold, enabled=cfg.enabled,
        description=cfg.description,
    )


@router.post("/api/recall/config/batch", response_model=List[ConfigResponse], dependencies=ADMIN)
def batch_update_configs(items: List[BatchUpdateItem]):
    mgr = get_config_manager()
    results = []
    for item in items:
        upd = item.model_dump(exclude={"path_name", "entity_type"}, exclude_none=True)
        if not upd:
            continue
        try:
            cfg = mgr.update(item.path_name, item.entity_type, **upd)
            results.append(ConfigResponse(
                path_name=cfg.path_name, entity_type=cfg.entity_type,
                recall_mode=cfg.recall_mode, top_k=cfg.top_k,
                threshold=cfg.threshold, enabled=cfg.enabled,
                description=cfg.description,
            ))
        except ValueError as e:
            raise HTTPException(400, f"{item.path_name}/{item.entity_type}: {e}")
    return results


@router.post("/api/recall/config/reload", dependencies=ADMIN)
def reload_configs():
    mgr = get_config_manager()
    mgr.reload()
    return {"status": "ok", "count": len(mgr._configs)}


# ----------------------------------------------------------
# 3. 元数据管理
# ----------------------------------------------------------
@router.post("/api/metadata/reload-index", dependencies=ADMIN)
def reload_index():
    """[兼容接口] 从数据库重新装载内存索引（不编码）。实体层增量写入后索引已实时更新，
    通常无需调用；仅在多实例部署、其他实例写库后用于刷新本实例。"""
    if not _rebuild_lock.acquire(blocking=False):
        raise HTTPException(409, "另一个重建任务正在进行中，请稍后重试")
    try:
        t0 = time.time()
        stats = get_sync_service().load_from_db()
        get_scope_resolver().invalidate()
        _refresh_derived_caches()
        set_index_state()
        return {"status": "ok", "elapsed": round(time.time() - t0, 2), **stats}
    finally:
        _rebuild_lock.release()


def _refresh_derived_caches():
    """刷新链统一入口：RelationRegistry + QueryRewriter 词典与知识库"""
    try:
        reload_relation_registry()
    except Exception as e:
        logger.warning(f"RelationRegistry refresh failed: {e}")
    try:
        rw = get_query_rewriter()
        rw.load_dictionary()
        rw.load_knowledge()
    except Exception as e:
        logger.warning(f"QueryRewriter refresh failed: {e}")


@router.get("/api/metadata/stats")
def metadata_stats():
    return _entity_counts()


@router.get("/api/metadata/list/{entity_type}")
def list_metadata(entity_type: str, limit: int = 100, table: str = None):
    """列出实体（table/metric/dimension/dim_value/topic/derived_metric）；table= 按表过滤"""
    cat = get_sync_service().catalog
    et = "knowledge" if entity_type == "derived_metric" else entity_type
    if et not in ("table", "metric", "dimension", "dim_value", "topic", "knowledge"):
        raise HTTPException(400, f"Unknown entity_type: {entity_type}")
    items = []
    for m in cat.by_key.values():
        if m["entity_type"] != et:
            continue
        if table and table not in m["legal_tables"]:
            continue
        items.append({"entity_id": m["faiss_id"], "entity_key": m["entity_key"], "code": m["code"],
                      "display_name": m["display_name"], "description": m.get("description") or "",
                      "synonyms": m.get("synonyms") or "", "unit": m.get("unit") or "",
                      "table_name": m.get("table_name") or "", "dimension_code": m.get("dimension_code") or "",
                      "legal_tables": sorted(m["legal_tables"])})
        if len(items) >= int(limit):
            break
    items.sort(key=lambda x: x["entity_key"])
    return {"count": len(items), "items": items}


@router.get("/api/metadata/database-meta")
def get_database_meta():
    """返回全量 database_meta（来自实体层源对象，与 database_meta.json 同构）"""
    return get_sync_service().export_database_meta()


@router.get("/api/metadata/domain-knowledge")
def get_domain_knowledge():
    """返回业务领域知识：主题定义 + 派生指标依赖规则（来自 domain_knowledge.json）"""
    p = Path(config.DATA_DIR) / "domain_knowledge.json"
    if not p.exists():
        return {"topic_definitions": [], "derived_dependency_rules": []}
    try:
        dk = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        raise HTTPException(500, f"domain_knowledge.json parse failed: {e}")
    return {
        "topic_definitions": dk.get("topic_definitions", []),
        "derived_dependency_rules": dk.get("derived_dependency_rules", []),
    }


@router.put("/api/metadata/database-meta", dependencies=ADMIN)
def save_database_meta(payload: Dict[str, Any] = Body(...), rebuild: bool = True):
    """[兼容接口] 整体覆盖 database_meta：导入为源对象并增量重建（只重编文本变化的实体）。
    rebuild=false 仅落源对象不重建（不推荐）。"""
    if not isinstance(payload, dict) or not any(
            k in payload for k in ("available_metrics", "available_dimensions", "table_summaries")):
        raise HTTPException(400, "payload must be a database_meta object")
    svc = get_sync_service()
    if rebuild:
        res = svc.import_database_meta(payload, replace=True)
        _refresh_derived_caches()
        _write_meta_mirror()
        set_index_state()
    else:
        with svc._lock:
            svc.store.clear_sources()
            svc.store.upsert_tables(payload.get("table_summaries") or [])
            svc.store.upsert_metrics(payload.get("available_metrics") or [])
            svc.store.upsert_dimensions(payload.get("available_dimensions") or [])
        res = {"imported_only": True}
    return {"ok": True, "saved": True, "path": str(_DATABASE_META_PATH), "sync": res}


# ----------------------------------------------------------
# 3.5 提示词管理（动态修改 LLM 模板，改完即生效）
# ----------------------------------------------------------
@router.get("/api/prompts")
def list_prompts():
    return {"prompts": list(_list_prompts().values())}


@router.put("/api/prompts/{key}", dependencies=ADMIN)
def update_prompt(key: str, body: Dict[str, str] = Body(...)):
    content = (body or {}).get("content", "")
    try:
        _update_prompt(key, content)
    except ValueError as e:
        raise HTTPException(400, str(e))
    return {"status": "ok", "key": key}


@router.post("/api/prompts/{key}/reset", dependencies=ADMIN)
def reset_prompt(key: str):
    try:
        content = _reset_prompt(key)
    except ValueError as e:
        raise HTTPException(400, str(e))
    return {"status": "ok", "key": key, "content": content}


# ----------------------------------------------------------
# 3.6 改写词典（L1 同义词 / 集合包含规则 / 业务知识库）
# ----------------------------------------------------------
@router.get("/api/rewrite/dict")
def rewrite_dict():
    db = get_db()
    rw = get_query_rewriter()
    rw._ensure_loaded()
    rw._ensure_knowledge()

    # L1 同义词：标准名 -> 别名列表（来自实体层指标+维度）
    synonyms = []
    for m in get_sync_service().catalog.by_key.values():
        if m["entity_type"] not in ("metric", "dimension"):
            continue
        syns = _split_synonyms(m.get("synonyms"))
        synonyms.append({"entity_type": m["entity_type"], "id": m["faiss_id"],
                         "name": m["display_name"], "aliases": syns, "alias_count": len(syns)})

    # 业务知识库（knowledge 实体，即 database_meta.json 的 business_context）
    knowledge = [{
        "id": k["id"], "name": k["name"],
        "aliases": k["aliases"], "description": k["description"],
    } for k in rw._knowledge]

    # 集合包含规则：从知识文本解析"X 是指/包含 A、B、C"句式（当前未参与改写，仅展示）
    collections = []
    for k in knowledge:
        members = _parse_collection_rule(k["description"])
        if members:
            collections.append({"name": k["name"], "members": members,
                                "source_text": k["description"], "implemented": False})

    return {
        "synonyms": synonyms,
        "synonym_total": sum(s["alias_count"] for s in synonyms),
        "knowledge": knowledge,
        "collections": collections,
        "dict_size": len(rw._dict),
        "loaded": rw._loaded and rw._knowledge_loaded,
    }


def _split_synonyms(raw) -> list:
    if not raw:
        return []
    items = raw if isinstance(raw, list) else str(raw).replace("、", ",").split(",")
    return [s.strip() for s in items if s and s.strip()]


def _parse_collection_rule(text: str) -> list:
    """解析"X 是指/包含 A、B、C(/，D)"句式 -> [A, B, C, D]；不匹配返回 []"""
    if not text:
        return []
    import re
    m = re.match(r"^.+?(是指|包含|包括)(.+)$", text)
    if not m:
        return []
    tail = m.group(2).lstrip("：: 、，, ")
    if any(k in tail for k in ("记录数", "时间", "口径", "名称")):
        return []
    members = [p.strip(" 。；;") for p in re.split(r"[、，,]", tail) if p.strip(" 。；;")]
    return members if len(members) >= 2 else []


@router.post("/api/rewrite/reload", dependencies=ADMIN)
def rewrite_reload():
    """手动重载改写词典与知识库（database_meta.json 更新或重建后调用）"""
    rw = get_query_rewriter()
    rw.load_dictionary()
    rw.load_knowledge()
    return {"status": "ok", "dict_size": len(rw._dict), "knowledge": len(rw._knowledge)}


@router.post("/api/rewrite/debug")
def rewrite_debug(body: Dict[str, Any] = Body(...)):
    """改写调试：单条 query 直接跑改写链路，返回全过程明细"""
    query = (body.get("query") or "").strip()
    if not query:
        raise HTTPException(status_code=400, detail="query 不能为空")
    use_llm = bool(body.get("use_llm", False))

    rw = get_query_rewriter()
    rw._ensure_loaded()
    rw._ensure_knowledge()
    llm = get_llm_client() if use_llm else None
    result = rw.rewrite(query, use_llm=use_llm, llm_client=llm)
    result["dict_size"] = len(rw._dict)
    return result


@router.post("/api/judge/debug")
def judge_debug(body: Dict[str, Any] = Body(...)):
    """精判调试：跑召回候选 → LLM 精判，返回候选列表与精判全过程"""
    query = (body.get("query") or "").strip()
    if not query:
        raise HTTPException(status_code=400, detail="query 不能为空")

    recall: RecallService = _state["recall"]
    try:
        scope = get_scope_resolver().resolve(agent_id=body.get("agent_id"), scope=body.get("scope"))
    except AgentNotFound as e:
        raise HTTPException(404, f"agent not found: {e}")
    rr = recall.recall(query, scope=scope)
    candidates = rr.get("candidates", [])
    if not candidates:
        raise HTTPException(status_code=404, detail="召回结果为空，无法精判")

    judge: LLMJudge = _state["judge"]
    t_j = time.time()
    judgement = judge.judge(query, candidates)
    judgement["judge_elapsed_ms"] = round((time.time() - t_j) * 1000, 1)
    judgement["candidate_count"] = len(candidates)
    return {
        "query": query,
        "candidates": candidates,
        "judgement": judgement,
        "recall_elapsed_ms": rr.get("elapsed_ms"),
    }


# ----------------------------------------------------------
# 4. slim 召回：全链路编排（写透版 §3.0）
#    ①改写 → ②四路召回 → ③受限反推+门校验 → ④LLM精判 → ⑤按选中裁剪导出
#    降级链：judge 失败/全空 → 回退纯召回候选导出（L2 逃生门）
# ----------------------------------------------------------
@router.post("/api/recall/slim")
def recall_slim(req: SlimRecallRequest):
    pipeline: SlimPipeline = _state.get("pipeline")
    if pipeline is None:
        raise HTTPException(503, "Recall service not ready")
    try:
        return pipeline.run(req)
    except AgentNotFound as e:
        raise HTTPException(404, f"agent not found: {e}")


# ----------------------------------------------------------
# 4b. 生产召回接口：样式固定，不受 judge/reranker 开关影响
#     逻辑与 /api/recall/slim 同一套（SlimPipeline.run），
#     仅把 database_meta 展开到 data 顶层，去掉调试字段
# ----------------------------------------------------------
@router.post("/api/recall/prod")
def recall_prod(req: SlimRecallRequest):
    pipeline: SlimPipeline = _state.get("pipeline")
    if pipeline is None:
        raise HTTPException(503, "Recall service not ready")
    # 生产接口按配置强制覆盖三个开关，忽略调用方传参（默认全部关闭）
    req = req.model_copy(update={
        "use_rewrite": config.PROD_USE_REWRITE,
        "use_rewrite_llm": config.PROD_USE_REWRITE_LLM,
        "use_llm_judge": config.PROD_USE_LLM_JUDGE,
    })
    try:
        raw = pipeline.run(req)
    except AgentNotFound as e:
        return {"code": 404, "message": f"智能体不存在：{e}", "data": None}
    except Exception as e:
        logger.error(f"[recall/prod] fail: {e}")
        return {"code": 500, "message": f"召回失败：{e}", "data": None}
    meta = raw.get("database_meta", {})
    return {
        "code": 200,
        "message": "操作成功",
        "data": {
            "query": raw.get("query", req.query),
            "agent_id": req.agent_id,
            "database_meta": {
                "available_metrics": meta.get("available_metrics", []),
                "available_dimensions": meta.get("available_dimensions", []),
                "table_summaries": meta.get("table_summaries", []),
                "business_context": meta.get("business_context", []),
            },
        },
    }


# ----------------------------------------------------------
# 5. 元数据同步（接口触发：连库生成 → 覆盖 database_meta.json）
# ----------------------------------------------------------
@router.post("/api/metadata/sync", status_code=202, dependencies=ADMIN)
def trigger_sync(req: SyncRequest = None):
    """触发同步（异步执行，立即返回 task_id）；rebuild=true 额外重建索引"""
    svc = get_meta_sync_service()
    rebuild = bool(req.rebuild) if req else False
    result = svc.trigger(rebuild=rebuild)
    logger.info(f"[sync] triggered task_id={result.get('task_id')} "
                f"rebuild={rebuild} status={result.get('status')}")
    return result


@router.get("/api/metadata/sync/status", response_model=SyncStatusResponse)
def sync_status():
    svc = get_meta_sync_service()
    return SyncStatusResponse(**svc.status())


# ----------------------------------------------------------
# 6. 批量测试（Excel 导入 → 并发召回 → 命中判定 → 导出/历史）
# ----------------------------------------------------------
@router.post("/api/batch-test/import")
async def batch_import(file: UploadFile = File(...)):
    """上传测试集 xlsx，解析校验后返回用例预览（不入库，前端随 run 提交）"""
    data = await file.read()
    if len(data) > 10 * 1024 * 1024:
        raise HTTPException(400, "文件超过 10MB 限制")
    try:
        return bts.parse_xlsx(data, file.filename or "")
    except bts.ParseError as e:
        raise HTTPException(400, str(e))


@router.post("/api/batch-test/run")
def batch_run(req: BatchRunRequest):
    """创建批量测试任务（异步执行，立即返回 run_id）"""
    try:
        return bts.start_run([c.model_dump() for c in req.cases],
                             concurrency=req.concurrency,
                             use_judge=req.use_llm_judge,
                             options={
                                 "use_rewrite": req.use_rewrite,
                                 "use_rewrite_llm": req.use_rewrite_llm,
                                 "dim_value_topn": req.dim_value_topn,
                                 "intent_split": req.intent_split,
                                 "filename": req.filename,
                                 "agent_id": req.agent_id,
                                 "scope": req.scope.model_dump() if req.scope else None,
                             })
    except RuntimeError as e:
        raise HTTPException(503, str(e))


@router.post("/api/batch-test/stop/{run_id}")
def batch_stop(run_id: str):
    """停止运行中的批量测试：不再派发新用例，在跑的用例自然结束后落盘"""
    if not bts.stop_run(run_id):
        raise HTTPException(409, "任务不存在或已结束")
    return {"ok": True, "run_id": run_id, "status": "stopping"}


@router.get("/api/batch-test/runs")
def batch_runs(limit: int = 20):
    """历史运行列表（新→旧）"""
    return {"runs": bts.list_runs(limit)}


@router.delete("/api/batch-test/runs/{run_id}")
def batch_delete_run(run_id: str):
    """删除一次历史运行（内存 + 磁盘文件）"""
    try:
        deleted = bts.delete_run(run_id)
    except RuntimeError as e:
        raise HTTPException(409, str(e))
    if not deleted:
        raise HTTPException(404, f"运行不存在: {run_id}")
    return {"deleted": True, "run_id": run_id}


@router.get("/api/batch-test/status/{run_id}")
def batch_status(run_id: str):
    s = bts.run_status(run_id)
    if s is None:
        raise HTTPException(404, f"运行不存在: {run_id}")
    return s


@router.get("/api/batch-test/result/{run_id}")
def batch_result(run_id: str):
    run = bts.get_run(run_id, with_details=True)
    if run is None:
        raise HTTPException(404, f"运行不存在: {run_id}")
    run.pop("t0", None)
    return run


@router.get("/api/batch-test/export/{run_id}")
def batch_export(run_id: str):
    run = bts.get_run(run_id, with_details=True)
    if run is None:
        raise HTTPException(404, f"运行不存在: {run_id}")
    if run.get("status") != "done":
        raise HTTPException(409, "运行尚未完成，无法导出")
    try:
        data = bts.export_xlsx(run)
    except ValueError as e:
        raise HTTPException(409, str(e))
    from urllib.parse import quote
    return Response(
        content=data,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition":
                 f"attachment; filename*=UTF-8''{quote(bts.export_filename(run))}"},
    )


# ----------------------------------------------------------
# 9. 元数据 AI 补全（LLM 全局生成四类字段，候选池+人工审核，方案见 docs/描述生成/方案.md）
# ----------------------------------------------------------
def _read_meta_raw():
    """AI 补全的输入：实体层源对象导出的 database_meta"""
    dm = get_sync_service().export_database_meta()
    if not dm.get("table_summaries") and not dm.get("available_metrics"):
        return None
    return {"database_meta": dm}


def _current_meta_hash() -> str:
    return f"catalog@{get_sync_service().catalog.version}"


@router.post("/api/enrich/run", dependencies=ADMIN)
def enrich_run(payload: Dict[str, Any] = Body(...)):
    """启动 LLM 补全：{targets:["metric",...], overwrite:bool}；dry_run 走 /api/enrich/dry-run"""
    from app.services.meta_enricher import enricher
    targets = payload.get("targets") or []
    if not targets:
        raise HTTPException(400, "targets 为空")
    overwrite = bool(payload.get("overwrite", False))

    meta_raw = _read_meta_raw()
    if meta_raw is None:
        raise HTTPException(503, "database_meta.json not found")
    ret = enricher.start(targets, overwrite, meta=meta_raw, meta_hash=_current_meta_hash())
    if not ret.get("ok"):
        raise HTTPException(409, ret.get("error", "start failed"))
    return ret


@router.post("/api/enrich/debug-input")
def enrich_debug_input(payload: Dict[str, Any] = Body(...)):
    """描述生成调试第一步：返回模板+变量+合成后的最终提示词，不调 LLM"""
    from app.services.meta_enricher import enricher
    target = payload.get("target")
    if target not in ("metrics", "dims", "tables", "knowledge"):
        raise HTTPException(400, "target 无效")
    meta_raw = _read_meta_raw()
    if meta_raw is None:
        raise HTTPException(503, "database_meta.json not found")
    try:
        return enricher.debug_input(target, meta_raw)
    except Exception as e:
        logger.exception("enrich debug-input failed")
        raise HTTPException(500, f"debug-input failed: {e}")


@router.post("/api/enrich/debug-run")
def enrich_debug_run(payload: Dict[str, Any] = Body(...)):
    """描述生成调试第二步：{prompt: 最终提示词} 直接调一次 LLM，不落候选池"""
    from app.services.meta_enricher import enricher
    prompt = str(payload.get("prompt") or "").strip()
    if not prompt:
        raise HTTPException(400, "prompt 为空")
    try:
        return enricher.debug_run(prompt)
    except Exception as e:
        logger.exception("enrich debug-run failed")
        raise HTTPException(500, f"debug-run failed: {e}")


@router.post("/api/enrich/dry-run")
def enrich_dry_run(payload: Dict[str, Any] = Body(...)):
    """试跑单条：{target:"metric|dim|table|knowledge"} → 返回最终prompt/LLM原始返回/解析结果"""
    from app.services.meta_enricher import enricher
    target = payload.get("target")
    if target not in ("metrics", "dims", "tables", "knowledge"):
        raise HTTPException(400, "target 无效")
    meta_raw = _read_meta_raw()
    if meta_raw is None:
        raise HTTPException(503, "database_meta.json not found")
    try:
        return enricher.dry_run(target, meta_raw)
    except Exception as e:
        logger.exception("enrich dry-run failed")
        raise HTTPException(500, f"dry-run failed: {e}")


@router.get("/api/enrich/status")
def enrich_status():
    from app.services.meta_enricher import enricher
    return enricher.get_status()


@router.get("/api/enrich/result")
def enrich_result():
    """候选池（含落盘文件里的历史候选）"""
    from app.services.meta_enricher import enricher
    return enricher.get_results()


@router.post("/api/enrich/apply", dependencies=ADMIN)
def enrich_apply(payload: Dict[str, Any] = Body(...)):
    """采纳/忽略：{accepted:[{target,key,value}], ignored:[{target,key}]}
    采纳项通过实体层增量合入：只重新向量化描述变化的实体，索引实时生效"""
    from app.services.meta_enricher import enricher
    accepted = payload.get("accepted") or []
    ignored = payload.get("ignored") or []
    if not accepted and not ignored:
        raise HTTPException(400, "accepted/ignored 均为空")
    try:
        ret = enricher.apply(accepted, ignored)
    except Exception as e:
        logger.exception("enrich apply failed")
        raise HTTPException(500, f"apply failed: {e}")
    if accepted:
        _write_meta_mirror()
        trigger_auto_push()
    return {"ok": True, **ret}


@router.get("/api/sync/descriptions/status")
def sync_descriptions_status():
    """描述回写推送状态（配置/上次推送/是否有失败待处理）"""
    return description_sync_service.status()


@router.post("/api/sync/descriptions/push", dependencies=ADMIN)
def sync_descriptions_push(payload: Dict[str, Any] = Body(default={})):
    """手动触发描述推送：{mode:"full"|"incremental", background:true}
    background=true 时立即返回并在后台推送；否则同步等待结果"""
    mode = payload.get("mode") or "incremental"
    if mode not in ("full", "incremental"):
        raise HTTPException(400, "mode 仅支持 full / incremental")
    if not description_sync_service.is_configured():
        raise HTTPException(503, "JAVA_SYNC_BASE_URL 未配置，无法推送")
    if payload.get("background"):
        def _run():
            try:
                description_sync_service.run(mode)
            except Exception:
                logger.exception("background description sync failed")
        threading.Thread(target=_run, daemon=True).start()
        return {"ok": True, "message": "后台推送已启动", "mode": mode}
    try:
        return description_sync_service.run(mode)
    except Exception as e:
        logger.exception("description sync failed")
        raise HTTPException(500, f"推送失败: {e}")


@router.post("/api/sync/descriptions/clear-fail", dependencies=ADMIN)
def sync_descriptions_clear_fail():
    """清除失败落盘文件 descriptions_sync_fail.json"""
    from app.services.description_sync import FAIL_PATH
    if FAIL_PATH.exists():
        FAIL_PATH.unlink()
        return {"ok": True, "message": "失败记录已清除"}
    return {"ok": True, "message": "无失败记录"}


@router.get("/api/enrich/ignored")
def enrich_ignored():
    from app.services.meta_enricher import enricher
    return {"items": enricher.get_ignored()}


@router.post("/api/enrich/draft", dependencies=ADMIN)
def enrich_draft(payload: Dict[str, Any] = Body(...)):
    """调试 tab 分片运行结果写入候选池：{results: {target:[{key,value}]}}"""
    from app.services.meta_enricher import enricher
    results = payload.get("results") or {}
    if not results:
        raise HTTPException(400, "results 为空")
    meta_raw = _read_meta_raw()
    try:
        ret = enricher.save_draft(results, meta_raw)
    except Exception as e:
        logger.exception("enrich draft failed")
        raise HTTPException(500, f"draft failed: {e}")
    return ret


# ----------------------------------------------------------
# 10. 实体层：上游数据管理 → 增量同步（新增）
# ----------------------------------------------------------
@router.post("/api/entities/upsert", dependencies=ADMIN)
def entities_upsert(req: EntityUpsertRequest):
    """增量推送变更对象（只传变化的）。只对 embedding_text 实际变化的实体重新向量化。
    返回 {built, encoded, cache_hits, unchanged, removed, affected, elapsed_ms}"""
    svc = get_sync_service()
    try:
        res = svc.apply(tables=req.tables, metrics=req.metrics, dimensions=req.dimensions,
                        dim_values=[d.model_dump() for d in req.dim_values], knowledge=req.knowledge,
                        deletes=req.deletes.model_dump() if req.deletes else None, force=req.force)
    except Exception as e:
        logger.exception("entities upsert failed")
        raise HTTPException(500, f"upsert failed: {e}")
    get_scope_resolver().invalidate()
    _refresh_derived_caches()
    _write_meta_mirror()
    set_index_state()
    return {"ok": True, **res}


@router.delete("/api/entities/{kind}/{key:path}", dependencies=ADMIN)
def entities_delete(kind: str, key: str):
    """删除源对象：kind ∈ table/metric/dimension/knowledge；级联删除其实体与索引"""
    if kind not in ("table", "metric", "dimension", "knowledge"):
        raise HTTPException(400, "kind must be table/metric/dimension/knowledge")
    res = get_sync_service().apply(deletes={kind + "s": [key]})
    get_scope_resolver().invalidate()
    _refresh_derived_caches()
    _write_meta_mirror()
    set_index_state()
    return {"ok": True, **res}


@router.post("/api/entities/resync", dependencies=ADMIN)
def entities_resync(force: bool = False):
    """按源对象全量重建实体（仍只重编文本变化的）；force=true 全部重编。同步执行，返回统计。"""
    if not _rebuild_lock.acquire(blocking=False):
        raise HTTPException(409, "另一个重建任务正在进行中，请稍后重试")
    try:
        res = get_sync_service().resync(force=force)
        get_scope_resolver().invalidate()
        _refresh_derived_caches()
        _write_meta_mirror()
        set_index_state()
        return {"ok": True, **res}
    finally:
        _rebuild_lock.release()


@router.post("/api/entities/import", dependencies=ADMIN)
def entities_import(payload: Dict[str, Any] = Body(...), replace: bool = True, force: bool = False):
    """整份 database_meta 导入（{database_meta:{...}} 或直接 {...}）。replace=true 以本次为准；
    仍只重编文本变化的实体。同步执行。"""
    dm = payload.get("database_meta", payload)
    if not isinstance(dm, dict):
        raise HTTPException(400, "invalid payload")
    if not _rebuild_lock.acquire(blocking=False):
        raise HTTPException(409, "另一个重建任务正在进行中，请稍后重试")
    try:
        res = get_sync_service().import_database_meta(dm, replace=replace, force=force)
        get_scope_resolver().invalidate()
        _refresh_derived_caches()
        _write_meta_mirror()
        set_index_state()
        return {"ok": True, **res}
    finally:
        _rebuild_lock.release()


@router.get("/api/entities/status")
def entities_status():
    return get_sync_service().status()


@router.get("/api/entities/{entity_key:path}")
def entities_get(entity_key: str):
    """按 entity_key 查看单个实体（含 embedding_text / text_hash，不含向量）"""
    rows = get_sync_service().store.load_entities(keys=[entity_key])
    if not rows:
        raise HTTPException(404, f"entity not found: {entity_key}")
    return rows[0]


# ----------------------------------------------------------
# 11. 智能体范围（新增）：注册/更新只写关系表，不触发任何向量化
# ----------------------------------------------------------
@router.get("/api/agents")
def agents_list():
    return {"agents": get_entity_store().list_agents()}


@router.get("/api/agents/{agent_id}")
def agents_get(agent_id: str):
    a = get_entity_store().load_agent(agent_id)
    if a is None:
        raise HTTPException(404, f"agent not found: {agent_id}")
    a["resolved"] = get_scope_resolver().resolve(agent_id=agent_id).summary()
    return a


@router.put("/api/agents/{agent_id}", dependencies=ADMIN)
def agents_upsert(agent_id: str, req: AgentUpsertRequest):
    """注册或整体更新智能体范围（幂等）。返回新版本号与解析后的范围规模。"""
    st = get_entity_store()
    ver = st.upsert_agent(agent_id, req.agent_name, req.scope.model_dump())
    get_scope_resolver().invalidate(agent_id)
    # 校验：范围内的表是否都存在
    cat = get_sync_service().catalog
    unknown_tables = [t for t in req.scope.tables if t not in cat.table_members]
    unknown_metrics = [m for m in req.scope.metrics if m not in cat.metric_ids_of_code]
    unknown_dims = [d for d in req.scope.dimensions if d not in cat.dim_id_of_code]
    resolved = get_scope_resolver().resolve(agent_id=agent_id).summary()
    return {"ok": True, "agent_id": agent_id, "version": ver, "resolved": resolved,
            "warnings": {k: v for k, v in (("unknown_tables", unknown_tables),
                                          ("unknown_metrics", unknown_metrics),
                                          ("unknown_dimensions", unknown_dims)) if v}}


@router.delete("/api/agents/{agent_id}", dependencies=ADMIN)
def agents_delete(agent_id: str):
    ok = get_entity_store().delete_agent(agent_id)
    get_scope_resolver().invalidate(agent_id)
    if not ok:
        raise HTTPException(404, f"agent not found: {agent_id}")
    return {"ok": True, "agent_id": agent_id}


@router.post("/api/scope/resolve")
def scope_resolve(scope: ScopeSpec):
    """调试：内联范围 → 解析后的实体规模与表清单"""
    rs = get_scope_resolver().resolve(scope=scope.model_dump())
    return {**rs.summary(), "tables": sorted(rs.tables)}
