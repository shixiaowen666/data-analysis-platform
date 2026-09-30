"""
Catalog —— 实体层的内存目录：关系字典 + 范围解析（替代旧 RelationRegistry 的数据底座）

由 recall_entity 装载，随 SyncService 的 upsert/remove 增量维护：
  - by_key / by_id                 entity_key ↔ faiss_id ↔ meta
  - table_members[table_name]      该表下的 {metric_ids, dimension_ids, value_ids}
  - value_ids_of_dim[dim_code]     维度值 id 集合
  - legal_tables(entity)           指标/维度/维度值 → 挂靠表集合

Scope（智能体范围）解析：
  scope = {tables:[...], metrics:[code...], dimensions:[code...]}
    允许表     = tables 非空 ? tables : 全部表
    允许指标   = metrics 非空 ? (metrics 且挂靠在允许表) : 允许表下全部指标
    允许维度   = dimensions 非空 ? (dimensions 且挂靠在允许表) : 允许表下全部维度
    允许维度值 = 允许维度下全部值
    knowledge/topic 不受范围限制（业务知识全局），但 topic 展开出的实体要 ∩ scope
  结果为按 entity_type 分组的 faiss_id 集合；按 (agent_id, agent.version, catalog.version) 缓存。
"""
import threading
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Set

from app.utils.logger import get_logger

logger = get_logger("entity.catalog", "relation_registry.log")


@dataclass
class ResolvedScope:
    tables: Set[str]                       # 允许表名
    ids: Dict[str, Set[int]]               # entity_type → faiss_id 集合（table/metric/dimension/dim_value）
    unrestricted: bool = False             # True = 无范围（全库）
    label: str = ""

    def allowed(self, entity_type: str) -> Optional[Set[int]]:
        """给 IndexStore.search 用：None=不过滤"""
        if self.unrestricted or entity_type in ("knowledge", "topic"):
            return None
        return self.ids.get(entity_type, set())

    def contains(self, entity_type: str, faiss_id: int) -> bool:
        if self.unrestricted or entity_type in ("knowledge", "topic"):
            return True
        return faiss_id in self.ids.get(entity_type, ())

    def summary(self) -> dict:
        return {"unrestricted": self.unrestricted, "label": self.label,
                "n_tables": len(self.tables), **{f"n_{t}": len(v) for t, v in self.ids.items()}}


class Catalog:
    def __init__(self):
        self._lock = threading.RLock()
        self.version = 0
        self.by_key: Dict[str, dict] = {}
        self.by_id: Dict[int, dict] = {}
        self.table_members: Dict[str, Dict[str, Set[int]]] = {}
        self.value_ids_of_dim: Dict[str, Set[int]] = {}
        self.metric_ids_of_code: Dict[str, Set[int]] = {}
        self.dim_id_of_code: Dict[str, int] = {}
        self.table_id_of_name: Dict[str, int] = {}
        self._scope_cache: Dict[str, ResolvedScope] = {}

    # ------------------------------------------------------------------
    def load(self, entities: Iterable[dict]):
        with self._lock:
            self.by_key.clear(); self.by_id.clear(); self.table_members.clear()
            self.value_ids_of_dim.clear(); self.metric_ids_of_code.clear()
            self.dim_id_of_code.clear(); self.table_id_of_name.clear()
            for e in entities:
                self._add(e)
            self._bump()
        logger.info(f"Catalog loaded: {len(self.by_key)} entities, {len(self.table_members)} tables")

    def upsert(self, entities: Iterable[dict]):
        with self._lock:
            for e in entities:
                old = self.by_key.get(e["entity_key"])
                if old:
                    self._remove(old)
                self._add(e)
            self._bump()

    def remove(self, keys: Iterable[str]):
        with self._lock:
            for k in keys:
                old = self.by_key.get(k)
                if old:
                    self._remove(old)
                    # 真正删除表实体：成员的 legal_tables 已在同一次 apply 中重算，摘掉成员登记
                    if old["entity_type"] == "table":
                        self.table_members.pop(old["table_name"], None)
            self._bump()

    def _bump(self):
        self.version += 1
        self._scope_cache.clear()

    def _add(self, e: dict):
        fid = int(e["faiss_id"]); et = e["entity_type"]
        m = {k: e.get(k) for k in ("entity_key", "entity_type", "code", "table_name", "dimension_code",
                                   "display_name", "description", "synonyms", "unit", "caliber_scope")}
        m["faiss_id"] = fid
        m["legal_tables"] = set(e.get("legal_tables") or [])
        self.by_key[e["entity_key"]] = m
        self.by_id[fid] = m
        if et == "table":
            self.table_id_of_name[e["table_name"]] = fid
            self.table_members.setdefault(e["table_name"], {"metric": set(), "dimension": set(), "dim_value": set()})
        elif et in ("metric", "dimension", "dim_value"):
            for tn in m["legal_tables"]:
                self.table_members.setdefault(tn, {"metric": set(), "dimension": set(), "dim_value": set()})[et].add(fid)
            if et == "metric":
                self.metric_ids_of_code.setdefault(e["code"], set()).add(fid)
            elif et == "dimension":
                self.dim_id_of_code[e["code"]] = fid
            else:
                self.value_ids_of_dim.setdefault(e["dimension_code"], set()).add(fid)

    def _remove(self, m: dict):
        fid, et = m["faiss_id"], m["entity_type"]
        self.by_key.pop(m["entity_key"], None); self.by_id.pop(fid, None)
        if et == "table":
            # 注意：这里不能清 table_members —— upsert 表实体时会先 _remove 再 _add，
            # 成员登记必须保留（成员 legal_tables 未变）。真正删表走 remove()。
            self.table_id_of_name.pop(m["table_name"], None)
        elif et in ("metric", "dimension", "dim_value"):
            for tn in m["legal_tables"]:
                self.table_members.get(tn, {}).get(et, set()).discard(fid)
            if et == "metric":
                self.metric_ids_of_code.get(m["code"], set()).discard(fid)
            elif et == "dimension":
                if self.dim_id_of_code.get(m["code"]) == fid:
                    self.dim_id_of_code.pop(m["code"], None)
            else:
                self.value_ids_of_dim.get(m["dimension_code"], set()).discard(fid)

    # ------------------------------------------------------------------
    # 关系查询（与旧 RelationRegistry 语义对齐）
    # ------------------------------------------------------------------
    def meta(self, faiss_id: int) -> Optional[dict]:
        return self.by_id.get(faiss_id)

    def legal_tables(self, faiss_id: int) -> Set[str]:
        m = self.by_id.get(faiss_id)
        return set(m["legal_tables"]) if m else set()

    def is_orphan(self, faiss_id: int) -> bool:
        m = self.by_id.get(faiss_id)
        return bool(m) and m["entity_type"] in ("metric", "dimension") and not m["legal_tables"]

    def table_name_of(self, faiss_id: int) -> Optional[str]:
        m = self.by_id.get(faiss_id)
        return m["table_name"] if m and m["entity_type"] == "table" else None

    def table_id(self, table_name: str) -> Optional[int]:
        return self.table_id_of_name.get(table_name)

    def members(self, table_name: str, entity_type: str) -> Set[int]:
        return set(self.table_members.get(table_name, {}).get(entity_type, set()))

    def all_ids(self, entity_type: str) -> Set[int]:
        return {fid for fid, m in self.by_id.items() if m["entity_type"] == entity_type}

    # ------------------------------------------------------------------
    # 范围解析
    # ------------------------------------------------------------------
    def resolve_scope(self, scope: Optional[dict], cache_key: str = None) -> ResolvedScope:
        if not scope or not any(scope.get(k) for k in ("tables", "metrics", "dimensions")):
            return ResolvedScope(tables=set(self.table_members), ids={}, unrestricted=True, label="all")
        if cache_key and cache_key in self._scope_cache:
            return self._scope_cache[cache_key]
        with self._lock:
            tables = {t for t in (scope.get("tables") or []) if t in self.table_members} or \
                     (set(self.table_members) if not scope.get("tables") else set())
            metric_ids, dim_ids = set(), set()
            for tn in tables:
                mem = self.table_members.get(tn, {})
                metric_ids |= mem.get("metric", set())
                dim_ids |= mem.get("dimension", set())
            if scope.get("metrics"):
                want = set()
                for code in scope["metrics"]:
                    want |= self.metric_ids_of_code.get(code, set())
                metric_ids &= want
            if scope.get("dimensions"):
                want = {self.dim_id_of_code[c] for c in scope["dimensions"] if c in self.dim_id_of_code}
                dim_ids &= want
            value_ids = set()
            for did in dim_ids:
                code = self.by_id[did]["code"]
                value_ids |= self.value_ids_of_dim.get(code, set())
            table_ids = {self.table_id_of_name[t] for t in tables if t in self.table_id_of_name}
            rs = ResolvedScope(tables=tables, ids={"table": table_ids, "metric": metric_ids,
                                                   "dimension": dim_ids, "dim_value": value_ids},
                               unrestricted=False, label=cache_key or "inline")
            if cache_key:
                self._scope_cache[cache_key] = rs
            return rs


_catalog: Optional[Catalog] = None


def get_catalog() -> Catalog:
    global _catalog
    if _catalog is None:
        _catalog = Catalog()
    return _catalog
