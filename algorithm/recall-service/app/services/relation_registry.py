"""
关系字典（兼容层）：对外保持旧 RelationRegistry 的接口，数据底座切换为实体层 Catalog。

- 所有 id 均为实体 faiss_id（稳定 int64）；表 id = 表实体的 faiss_id
- legal_tables / is_orphan / table_votes 语义与旧版一致
- 不再从 rel_* 表加载；Catalog 随 SyncService 增量维护，本层无状态
- 支持按 ResolvedScope 收缩：legal_tables ∩ scope.tables（范围外的表不参与投票/回补）
"""
from typing import Dict, List, Optional, Set, Tuple

from app import config
from app.entity.catalog import Catalog, ResolvedScope, get_catalog
from app.utils.logger import get_logger

logger = get_logger("relation_registry", "relation_registry.log")


class RelationRegistry:
    def __init__(self, catalog: Catalog = None):
        self.catalog = catalog or get_catalog()

    # ---- 兼容旧接口 ----
    def load(self) -> dict:
        c = self.catalog
        return {"entities": len(c.by_key), "tables": len(c.table_members), "version": c.version}

    def _table_ids(self, names: Set[str], scope: ResolvedScope = None) -> Set[int]:
        if scope is not None and not scope.unrestricted:
            names = names & scope.tables
        return {self.catalog.table_id_of_name[n] for n in names if n in self.catalog.table_id_of_name}

    def legal_tables(self, entity_type: str, entity_id: int, dimension_id: Optional[int] = None,
                     scope: ResolvedScope = None) -> Set[int]:
        if entity_type == "table":
            return {entity_id} if (scope is None or scope.contains("table", entity_id)) else set()
        if entity_type in ("metric", "dimension", "dim_value"):
            names = self.catalog.legal_tables(entity_id)
            if entity_type == "dim_value" and not names and dimension_id is not None:
                names = self.catalog.legal_tables(dimension_id)
            return self._table_ids(names, scope)
        return set()

    def is_orphan(self, entity_type: str, entity_id: int) -> bool:
        return self.catalog.is_orphan(entity_id)

    def resolve_table(self, name: str) -> Optional[int]:
        return self.catalog.table_id(name)

    def table_name(self, table_id: int) -> Optional[str]:
        return self.catalog.table_name_of(table_id)

    # ---- 受限反推（写透版 §3.3，语义不变） ----
    _VOTE_WEIGHTS = {
        "metric": config.VOTE_WEIGHT_METRIC,
        "dimension": config.VOTE_WEIGHT_DIMENSION,
        "dim_value": config.VOTE_WEIGHT_DIM_VALUE,
    }

    def table_votes(self, entity_ids: List[dict], scope: ResolvedScope = None) -> Tuple[Dict[int, float], Dict[int, float]]:
        votes: Dict[int, float] = {}
        raw_votes: Dict[int, float] = {}
        for e in entity_ids:
            et = e.get("entity_type")
            w_type = self._VOTE_WEIGHTS.get(et)
            if w_type is None:
                continue
            score = float(e.get("score", 0.0) or 0.0)
            eid = e.get("entity_id")
            legal = set(e.get("legal_tables") or []) or self.legal_tables(et, eid, e.get("dimension_id"), scope)
            if scope is not None and not scope.unrestricted:
                legal &= scope.ids.get("table", set())
            if not legal:
                continue
            w = score * w_type
            for tid in legal:
                raw_votes[tid] = raw_votes.get(tid, 0.0) + w
                if score >= config.VOTE_MIN_SCORE:
                    votes[tid] = votes.get(tid, 0.0) + w
        return votes, raw_votes


_registry: Optional[RelationRegistry] = None


def get_relation_registry() -> RelationRegistry:
    global _registry
    if _registry is None:
        _registry = RelationRegistry()
    return _registry


def reload_relation_registry() -> dict:
    return get_relation_registry().load()
