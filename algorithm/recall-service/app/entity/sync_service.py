"""
SyncService —— 增量向量化编排（本模块唯一的"写入口"）

apply(change) 流程：
  1. 源对象落库（src_*）
  2. EntityBuilder.build_for(受影响源对象) → 目标实体集合（含传播）
  3. 与 recall_entity 现状做 diff：
       - text_hash 相同且 embed_model 相同 → 只更新元数据/legal_tables，不编码
       - 否则 → 查 recall_embedding_cache（同文本同模型直接命中）→ 仍缺的才调 embedding API
       - 该源对象前缀下已不存在的实体（删掉的维度值 / 消失的同名变体）→ 删除
  4. 写回 recall_entity + IndexStore.upsert/remove + Catalog.upsert/remove
  5. 返回统计：{built, encoded, cache_hits, unchanged, removed, elapsed_ms}

全量入口 resync(full=False)：把 src_* 全量重建实体，但仍按 text_hash 只编变化的；
force=True 时忽略 hash/cache 全部重编（换模型、换模板时用）。

线程安全：同一时间只允许一个写操作（_lock）；读侧（召回）不阻塞——IndexStore/Catalog
的替换都是原子引用替换或在各自锁内完成。
"""
import json
import threading
import time
from typing import Dict, Iterable, List, Optional, Sequence, Set

import numpy as np

from app import config
from app.core.embedding import get_embedding_client
from app.entity.builder import EntityBuilder, TEMPLATE_VERSION
from app.entity.catalog import Catalog, get_catalog
from app.entity.index_store import IndexStore
from app.entity.store import EntityStore, get_entity_store
from app.utils.logger import get_logger

logger = get_logger("entity.sync", "meta_processor.log")


def _prefix_match(key: str, prefix: str) -> bool:
    """value:{dim}: / topic: 为真前缀；metric:{code} 允许变体后缀 #；其余精确匹配
    （避免 metric:ll_rate 误伤 metric:ll_rate_total）"""
    if prefix.endswith(":"):
        return key.startswith(prefix)
    if key == prefix:
        return True
    return prefix.startswith("metric:") and key.startswith(prefix + "#")


class SyncService:
    def __init__(self, store: EntityStore = None, index: IndexStore = None, catalog: Catalog = None):
        self.store = store or get_entity_store()
        self.index = index or IndexStore(dim=config.EMBEDDING_DIM)
        self.catalog = catalog or get_catalog()
        self._lock = threading.Lock()
        # 以真实使用的 embedding 客户端为准（fake / dashscope 不同模型不能互相复用缓存）
        self.model_tag = f"{get_embedding_client().model}@{config.EMBEDDING_DIM}"
        self.last_result: dict = {}
        self._listeners = []

    # 其他组件（QueryRewriter 词典 / SlimExporter 镜像）挂到这里，写操作后统一通知
    def add_listener(self, fn):
        self._listeners.append(fn)

    def _notify(self, keys: Set[str]):
        for fn in self._listeners:
            try:
                fn(keys)
            except Exception as e:
                logger.warning(f"sync listener failed: {e}")

    # ------------------------------------------------------------------
    # 启动装载：DB → 内存索引（不编码）
    # ------------------------------------------------------------------
    def load_from_db(self) -> dict:
        t0 = time.time()
        rows = self.store.load_entities(with_vec=True)
        ents, missing = [], 0
        for r in rows:
            if r.get("embedding_json"):
                r["embedding"] = np.asarray(json.loads(r.pop("embedding_json")), dtype="float32")
                ents.append(r)
            else:
                missing += 1
        self.index.load(ents)
        self.catalog.load(rows)
        stats = {"loaded": len(ents), "missing_vectors": missing, "elapsed_ms": round((time.time() - t0) * 1000)}
        logger.info(f"load_from_db: {stats}")
        return stats

    # ------------------------------------------------------------------
    # 变更入口
    # ------------------------------------------------------------------
    def apply(self, tables: Sequence[dict] = (), metrics: Sequence[dict] = (), dimensions: Sequence[dict] = (),
              dim_values: Sequence[dict] = (), knowledge: Sequence[dict] = (),
              deletes: Dict[str, Sequence[str]] = None, force: bool = False) -> dict:
        """
        tables/metrics/dimensions/knowledge：上游对象（database_meta 同构）
        dim_values: [{dimension_code, values:[...], mode:"replace"|"upsert"|"delete"}]
        deletes: {"tables":[name], "metrics":[code], "dimensions":[code], "knowledge":[id]}
        """
        with self._lock:
            t0 = time.time()
            deletes = deletes or {}
            # 1. 源对象落库
            self.store.upsert_tables(tables)
            self.store.upsert_metrics(metrics)
            self.store.upsert_dimensions(dimensions, replace_values=True)
            touched_dims = {d["dimension_code"] for d in dimensions}
            for dv in dim_values:
                code, mode = dv["dimension_code"], dv.get("mode", "upsert")
                if mode == "replace":
                    self.store.replace_dim_values(code, dv.get("values") or [])
                elif mode == "delete":
                    self.store.delete_dim_values(code, [v if isinstance(v, str) else v.get("value_name") for v in dv.get("values") or []])
                else:
                    self.store.upsert_dim_values(code, dv.get("values") or [])
                touched_dims.add(code)
            self.store.upsert_knowledge(knowledge)
            removed_prefixes: Set[str] = set()
            for kind, keys in deletes.items():
                for k in keys or []:
                    self.store.delete_source(kind[:-1] if kind.endswith("s") else kind, k)
                    removed_prefixes |= {f"{kind[:-1] if kind.endswith('s') else kind}:{k}"}
                    if kind.startswith("dimension"):
                        removed_prefixes.add(f"value:{k}:")

            # 2. 受影响源对象：显式变更的 + 被删对象曾挂靠的表（legal_tables 要收缩）
            aff_tables = {t["table_name"] for t in tables} | set(deletes.get("tables") or [])
            aff_metrics = {m["metric_code"] for m in metrics} | set(deletes.get("metrics") or [])
            aff_dims = touched_dims | set(deletes.get("dimensions") or [])
            aff_know = {str(k.get("knowledge_id") or k.get("id")) for k in knowledge} | set(deletes.get("knowledge") or [])
            for tn in list(aff_tables):
                # 表被删/改列：旧实体里挂靠该表的指标/维度都要重算 legal_tables
                for m in list(self.catalog.by_key.values()):
                    if tn in m["legal_tables"]:
                        if m["entity_type"] == "metric":
                            aff_metrics.add(m["code"])
                        elif m["entity_type"] in ("dimension", "dim_value"):
                            aff_dims.add(m["dimension_code"] or m["code"])
            # 已删源对象的实体：直接移除（后面 build_for 不会再产出它们）
            builder = self._builder()
            entities, prefixes = builder.build_for(tables=aff_tables - set(deletes.get("tables") or []),
                                                   metrics=aff_metrics - set(deletes.get("metrics") or []),
                                                   dimensions=aff_dims - set(deletes.get("dimensions") or []),
                                                   knowledge_ids=aff_know - set(deletes.get("knowledge") or []))
            prefixes |= removed_prefixes
            result = self._commit(entities, prefixes, force=force)
            result["elapsed_ms"] = round((time.time() - t0) * 1000)
            result["affected"] = {"tables": len(aff_tables), "metrics": len(aff_metrics),
                                  "dimensions": len(aff_dims), "knowledge": len(aff_know)}
            self.last_result = result
            logger.info(f"apply: {result}")
            return result

    def resync(self, force: bool = False) -> dict:
        """按 src_* 现状全量重建实体；force=False 时仍只编 text_hash 变化的"""
        with self._lock:
            t0 = time.time()
            builder = self._builder()
            entities = builder.build_all()
            # 全量：不在目标集合里的实体全部删除
            keep = {e["entity_key"] for e in entities}
            stale = [k for k in self.catalog.by_key if k not in keep]
            result = self._commit(entities, prefixes=set(), force=force, explicit_remove=stale)
            result["elapsed_ms"] = round((time.time() - t0) * 1000)
            self.last_result = result
            logger.info(f"resync(force={force}): {result}")
            return result

    def import_database_meta(self, dm: dict, replace: bool = True, force: bool = False) -> dict:
        """整份 database_meta（{available_metrics, available_dimensions, table_summaries, business_context}）导入。
        replace=True：源对象以本次为准（不在其中的删除）；False：仅 upsert。"""
        with self._lock:
            if replace:
                self.store.clear_sources()
            self.store.upsert_tables(dm.get("table_summaries") or [])
            self.store.upsert_metrics(dm.get("available_metrics") or [])
            self.store.upsert_dimensions(dm.get("available_dimensions") or [], replace_values=True)
            bc = []
            for i, k in enumerate(dm.get("business_context") or []):
                if isinstance(k, str):
                    bc.append({"knowledge_id": f"bc_{i+1}", "knowledgeAlias": [], "knowledgeElement": k, "sort_no": i})
                else:
                    kid = k.get("knowledge_id") or k.get("id") or f"bc_{i+1}"
                    bc.append({"knowledge_id": str(kid), "knowledgeAlias": k.get("knowledgeAlias") or [],
                               "knowledgeElement": k.get("knowledgeElement") or "", "sort_no": i})
            self.store.upsert_knowledge(bc)
        return self.resync(force=force)

    def export_database_meta(self) -> dict:
        """源对象 → database_meta 同构（供 /api/metadata/database-meta 与 slim 导出用）"""
        return {
            "available_metrics": [{k: v for k, v in m.items() if k not in ("extra",)} | (m.get("extra") or {})
                                  for m in self.store.load_metrics()],
            "available_dimensions": [{k: v for k, v in d.items() if k not in ("extra", "value_synonyms")} | (d.get("extra") or {})
                                     for d in self.store.load_dimensions()],
            "table_summaries": [{k: v for k, v in t.items() if k not in ("extra",)} | (t.get("extra") or {})
                                for t in self.store.load_tables()],
            "business_context": [{"knowledge_id": k["knowledge_id"], "knowledgeAlias": k["knowledgeAlias"],
                                  "knowledgeElement": k["knowledgeElement"]} for k in self.store.load_knowledge()],
        }

    # ------------------------------------------------------------------
    def _builder(self) -> EntityBuilder:
        return EntityBuilder(self.store.load_tables(), self.store.load_metrics(),
                             self.store.load_dimensions(with_values=True), self.store.load_knowledge())

    def _commit(self, entities: List[dict], prefixes: Set[str], force: bool,
                explicit_remove: Sequence[str] = ()) -> dict:
        keys = [e["entity_key"] for e in entities]
        ids = self.store.alloc_faiss_ids(keys)
        for e in entities:
            e["faiss_id"] = ids[e["entity_key"]]

        # 现状
        current = {r["entity_key"]: r for r in self.store.load_entities(keys=keys)} if keys else {}
        # 删除：前缀下存在但本次未产出的
        target = set(keys)
        to_remove = set(explicit_remove)
        if prefixes:
            for k in list(self.catalog.by_key):
                if k not in target and any(_prefix_match(k, p) for p in prefixes):
                    to_remove.add(k)

        # diff
        unchanged, dirty = [], []
        for e in entities:
            cur = current.get(e["entity_key"])
            if (not force and cur and cur["text_hash"] == e["text_hash"]
                    and cur.get("embed_model") == self.model_tag):
                unchanged.append(e)
            else:
                dirty.append(e)

        # 缓存命中
        cache_hits = 0
        need_api: List[dict] = []
        if dirty:
            cached = {} if force else self.store.cache_get([e["text_hash"] for e in dirty], self.model_tag)
            for e in dirty:
                vj = cached.get(e["text_hash"])
                if vj:
                    e["embedding_json"] = vj
                    e["embedding"] = np.asarray(json.loads(vj), dtype="float32")
                    cache_hits += 1
                else:
                    need_api.append(e)

        # 调 API（按 text_hash 去重，同文本只编一次）
        encoded = 0
        if need_api:
            uniq: Dict[str, dict] = {}
            for e in need_api:
                uniq.setdefault(e["text_hash"], e)
            texts = [e["embedding_text"] for e in uniq.values()]
            vecs = get_embedding_client().encode(texts, desc="entities")
            put = {}
            for (h, e), v in zip(uniq.items(), vecs):
                put[h] = json.dumps(v.tolist())
            self.store.cache_put(put, self.model_tag)
            for e in need_api:
                e["embedding_json"] = put[e["text_hash"]]
                e["embedding"] = np.asarray(json.loads(e["embedding_json"]), dtype="float32")
            encoded = len(uniq)

        # unchanged 复用旧向量（只更新元数据 / legal_tables）
        if unchanged:
            old = {r["entity_key"]: r for r in self.store.load_entities(keys=[e["entity_key"] for e in unchanged], with_vec=True)}
            for e in unchanged:
                vj = old[e["entity_key"]]["embedding_json"]
                e["embedding_json"] = vj
                e["embedding"] = np.asarray(json.loads(vj), dtype="float32") if vj else None

        for e in entities:
            e["embed_model"] = self.model_tag

        # 写回
        self.store.upsert_entities(entities)
        if to_remove:
            self.store.delete_entities(sorted(to_remove))
            self.index.remove(to_remove)
            self.catalog.remove(to_remove)
        self.index.upsert(entities)
        self.catalog.upsert(entities)
        self._notify(set(keys) | to_remove)
        return {"built": len(entities), "encoded": encoded, "cache_hits": cache_hits,
                "unchanged": len(unchanged), "removed": len(to_remove),
                "embed_model": self.model_tag, "template_version": TEMPLATE_VERSION}

    # ------------------------------------------------------------------
    def status(self) -> dict:
        return {"index": self.index.stats(), "entities": self.store.entity_counts(),
                "catalog_version": self.catalog.version, "embed_model": self.model_tag,
                "template_version": TEMPLATE_VERSION, "last_result": self.last_result}


_svc: Optional[SyncService] = None


def get_sync_service() -> SyncService:
    global _svc
    if _svc is None:
        _svc = SyncService()
    return _svc
