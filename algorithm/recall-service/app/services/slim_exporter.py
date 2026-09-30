"""
slim 导出器：按召回命中的实体组装缩圈版 database_meta
输出结构与 database_meta.json 的 database_meta 完全同构，下游可直接替换使用。

数据来源（全部来自实体层，不再依赖 database_meta.json 文件与 meta_* 表）：
  - 实体元信息 / 挂靠表：Catalog（内存）
  - 指标/维度/表原始定义（unit、possible_values、columns、aliases…）：EntityStore 源对象表（带缓存，
    SyncService 每次写后通过 listener 失效）
"""
import threading
from typing import Dict, List, Optional, Set

from app.entity.catalog import Catalog, ResolvedScope, get_catalog
from app.entity.store import EntityStore, get_entity_store
from app.utils.logger import get_logger

logger = get_logger("slim_exporter", "slim_exporter.log")


class SlimExporter:
    def __init__(self, store: EntityStore = None, catalog: Catalog = None):
        self.store = store or get_entity_store()
        self.catalog = catalog or get_catalog()
        self._lock = threading.Lock()
        self._metric_raw: Dict[str, dict] = {}
        self._dim_raw: Dict[str, dict] = {}
        self._table_raw: Dict[str, dict] = {}
        self._biz_ctx: List[dict] = []
        self._loaded = False

    # ------------------------------------------------------------------
    # 源对象缓存
    # ------------------------------------------------------------------
    def _ensure(self):
        if self._loaded:
            return
        with self._lock:
            if self._loaded:
                return
            self._metric_raw = {m["metric_code"]: m for m in self.store.load_metrics()}
            self._dim_raw = {d["dimension_code"]: d for d in self.store.load_dimensions(with_values=True)}
            self._table_raw = {t["table_name"]: t for t in self.store.load_tables()}
            self._biz_ctx = [{"knowledgeAlias": k["knowledgeAlias"], "knowledgeElement": k["knowledgeElement"]}
                             for k in self.store.load_knowledge()]
            self._loaded = True
            logger.info(f"SlimExporter cache ready: metrics={len(self._metric_raw)} dims={len(self._dim_raw)} "
                        f"tables={len(self._table_raw)} biz_ctx={len(self._biz_ctx)}")

    def invalidate(self, _keys=None):
        """SyncService listener：任何写操作后失效缓存"""
        self._loaded = False

    # 兼容旧调用名
    def reload_database_meta(self):
        self.invalidate()

    def _ensure_raw_fresh(self):
        self._ensure()

    @property
    def _raw(self):
        self._ensure()
        return {"database_meta": self.database_meta()}

    def database_meta(self) -> dict:
        self._ensure()
        return {
            "available_metrics": [self._metric_out(m) for m in self._metric_raw.values()],
            "available_dimensions": [self._dim_out(d, d.get("possible_values") or []) for d in self._dim_raw.values()],
            "table_summaries": [self._table_out(t) for t in self._table_raw.values()],
            "business_context": list(self._biz_ctx),
        }

    # ------------------------------------------------------------------
    # 输出段开关
    # ------------------------------------------------------------------
    @staticmethod
    def apply_output_switches(slim: dict, out_table: bool = True, out_metric: bool = True,
                              out_dimension: bool = True) -> dict:
        if not out_dimension:
            slim.get("database_meta", {}).pop("available_dimensions", None)
        if not out_metric:
            slim.get("database_meta", {}).pop("available_metrics", None)
        if not out_table:
            slim.get("database_meta", {}).pop("table_summaries", None)
        db_meta = slim.get("database_meta", {})
        if "recall_stats" in slim:
            st = slim["recall_stats"]
            st.update({
                "n_metrics": len(db_meta.get("available_metrics") or []),
                "n_dimensions": len(db_meta.get("available_dimensions") or []),
                "n_tables": len(db_meta.get("table_summaries") or []),
                "n_dim_values": sum(len(d.get("possible_values") or [])
                                    for d in db_meta.get("available_dimensions") or []),
            })
        return slim

    # ------------------------------------------------------------------
    # 主入口
    # ------------------------------------------------------------------
    def export(self, candidates: List[dict], include_business_context: bool = True,
               dim_value_topn: int = 30, selected: Dict[str, Set[int]] = None,
               table_votes: Dict[int, float] = None, scope: ResolvedScope = None) -> dict:
        self._ensure()
        if selected:
            return self._export_selected(candidates, selected, include_business_context,
                                         dim_value_topn, table_votes, scope)
        return self._export_candidates(candidates, include_business_context, dim_value_topn,
                                       table_votes, scope)

    @staticmethod
    def _best_table(legal: Set[int], candidate_tables: Set[int],
                    table_votes: Dict[int, float]) -> Set[int]:
        if not legal:
            return set()
        if table_votes:
            best = max(legal, key=lambda t: (table_votes.get(t, 0.0), -t))
        else:
            best = min(legal)
        in_scope = legal & candidate_tables
        if in_scope:
            return in_scope | {best}
        return {best}

    @staticmethod
    def _recalled_values(candidates: List[dict], dim_ids: Set[int]) -> Dict[int, List[str]]:
        out: Dict[int, List[str]] = {}
        for c in candidates:
            if c.get("entity_type") == "dim_value" and c.get("dimension_id") in dim_ids:
                name = c.get("display_name") or c.get("entity_name") or ""
                if name:
                    out.setdefault(c["dimension_id"], []).append(str(name))
        return out

    def _scope_tables(self, scope: Optional[ResolvedScope]) -> Optional[Set[int]]:
        if scope is None or scope.unrestricted:
            return None
        return scope.ids.get("table", set())

    def _export_candidates(self, candidates: List[dict], include_business_context: bool,
                           dim_value_topn: int, table_votes: Dict[int, float] = None,
                           scope: ResolvedScope = None) -> dict:
        metric_ids: Set[int] = set()
        dimension_ids: Set[int] = set()
        table_ids: Set[int] = set()
        for c in candidates:
            et, eid = c.get("entity_type"), c.get("entity_id")
            if et == "metric":
                metric_ids.add(eid)
            elif et == "dimension":
                dimension_ids.add(eid)
            elif et == "dim_value" and c.get("dimension_id"):
                dimension_ids.add(c["dimension_id"])
            elif et == "table" and isinstance(eid, int):
                table_ids.add(eid)

        candidate_tables = {c["entity_id"] for c in candidates
                            if c.get("entity_type") == "table" and isinstance(c.get("entity_id"), int)}
        legal_by_type: Dict[str, Dict[int, list]] = {"metric": {}, "dimension": {}}
        for c in candidates:
            et, eid = c.get("entity_type"), c.get("entity_id")
            if et in legal_by_type and isinstance(eid, int):
                legal_by_type[et][eid] = list(c.get("legal_tables") or [])

        st = self._scope_tables(scope)
        if metric_ids or dimension_ids:
            for et, ids in (("metric", metric_ids), ("dimension", dimension_ids)):
                by_id = legal_by_type[et]
                for eid in ids:
                    legal = set(by_id.get(eid) or [])
                    if st is not None:
                        legal &= st
                    backfill = self._best_table(legal, candidate_tables, table_votes or {})
                    if len(legal) <= 5:
                        table_ids |= legal
                    else:
                        table_ids |= backfill
        if st is not None:
            table_ids &= st

        metrics = self._build_metrics(metric_ids, scope)
        dimensions = self._build_dimensions(dimension_ids, dim_value_topn,
                                            self._recalled_values(candidates, dimension_ids))
        tables = self._build_tables(table_ids)
        out = {"database_meta": {
            "available_metrics": metrics, "available_dimensions": dimensions, "table_summaries": tables,
            **({"business_context": self._biz_ctx} if include_business_context else {}),
        }}
        out["recall_stats"] = {
            "n_metrics": len(metrics), "n_dimensions": len(dimensions), "n_tables": len(tables),
            "n_dim_values": sum(len(d.get("possible_values") or []) for d in dimensions),
            "n_business_context": len(self._biz_ctx) if include_business_context else 0,
            "n_candidates": len(candidates),
        }
        return out

    def _export_selected(self, candidates: List[dict], selected: Dict[str, Set[int]],
                         include_business_context: bool, dim_value_topn: int,
                         table_votes: Dict[int, float] = None, scope: ResolvedScope = None) -> dict:
        sel_tables = set(selected.get("table", set()))
        sel_metrics = set(selected.get("metric", set()))
        sel_dims = set(selected.get("dimension", set()))
        sel_derived = set(selected.get("derived_metric", set()))
        sel_values = set(selected.get("dim_value", set()))
        st = self._scope_tables(scope)

        candidate_tables = {c["entity_id"] for c in candidates
                            if c.get("entity_type") == "table" and isinstance(c.get("entity_id"), int)}
        allowed_tables: Set[int] = set(sel_tables)
        for c in candidates:
            et, eid = c.get("entity_type"), c.get("entity_id")
            pool = {"metric": sel_metrics, "dimension": sel_dims, "dim_value": sel_values}.get(et, set())
            if eid in pool:
                legal = set(c.get("legal_tables") or [])
                if st is not None:
                    legal &= st
                allowed_tables |= self._best_table(legal, candidate_tables, table_votes or {})
        if st is not None:
            allowed_tables &= st

        keep_metrics, keep_dims = set(sel_metrics), set(sel_dims)
        for c in candidates:
            et, eid = c.get("entity_type"), c.get("entity_id")
            legal = set(c.get("legal_tables") or [])
            if legal and (legal & allowed_tables):
                if et == "metric":
                    keep_metrics.add(eid)
                elif et == "dimension":
                    keep_dims.add(eid)

        keep_values = set(sel_values)
        for c in candidates:
            if (c.get("entity_type") == "dim_value" and c.get("dimension_id") in keep_dims
                    and not (set(c.get("legal_tables") or []) - allowed_tables)):
                keep_values.add(c["entity_id"])
        dim_ids = set(keep_dims)
        for c in candidates:
            if c.get("entity_type") == "dim_value" and c["entity_id"] in keep_values and c.get("dimension_id"):
                dim_ids.add(c["dimension_id"])

        metrics = self._build_metrics(keep_metrics, scope)
        dimensions = self._build_dimensions(dim_ids, dim_value_topn, self._recalled_values(candidates, dim_ids))
        tables = self._build_tables(allowed_tables)
        out = {"database_meta": {
            "available_metrics": metrics, "available_dimensions": dimensions, "table_summaries": tables,
            **({"business_context": self._biz_ctx} if include_business_context else {}),
        }}
        out["recall_stats"] = {
            "n_metrics": len(metrics), "n_dimensions": len(dimensions), "n_tables": len(tables),
            "n_dim_values": sum(len(d.get("possible_values") or []) for d in dimensions),
            "n_business_context": len(self._biz_ctx) if include_business_context else 0,
            "n_candidates": len(candidates), "mode": "judge_selected",
            "n_selected": {"table": len(sel_tables), "metric": len(sel_metrics), "dimension": len(sel_dims),
                           "dim_value": len(sel_values), "derived_metric": len(sel_derived)},
        }
        return out

    # ------------------------------------------------------------------
    # 组装各段（实体 faiss_id → Catalog meta → 源对象）
    # ------------------------------------------------------------------
    @staticmethod
    def _metric_out(raw: dict, display: str = None) -> dict:
        return {
            "metric_name": display or raw.get("metric_name", ""),
            "metric_code": raw.get("metric_code", ""),
            "description": raw.get("description") or "",
            "unit": raw.get("unit") or "",
            "data_type": raw.get("data_type", ""),
            "caliber_scope": raw.get("caliber_scope", ""),
            "aliases": raw.get("aliases") or [],
        }

    @staticmethod
    def _dim_out(raw: dict, possible: list) -> dict:
        return {
            "dimension_name": raw.get("dimension_name", ""),
            "dimension_code": raw.get("dimension_code", ""),
            "description": raw.get("description") or "",
            "data_type": raw.get("data_type", ""),
            "possible_values": possible,
            "aliases": raw.get("aliases") or [],
        }

    @staticmethod
    def _table_out(raw: dict) -> dict:
        return {
            "table_name": raw.get("table_name", ""),
            "display_name": raw.get("display_name") or "",
            "description": raw.get("description") or "",
            "columns": raw.get("columns", []),
        }

    def _build_metrics(self, metric_ids: Set[int], scope: ResolvedScope = None) -> List[dict]:
        out, seen = [], set()
        scope_tables = None if (scope is None or scope.unrestricted) else scope.tables
        for fid in sorted(metric_ids):
            m = self.catalog.meta(fid)
            if not m:
                continue
            code = m["code"]
            raw = self._metric_raw.get(code) or {"metric_code": code, "metric_name": m["display_name"],
                                                 "unit": m.get("unit"), "caliber_scope": m.get("caliber_scope")}
            # 同 code 不同变体：以变体名输出（如 分压线损率 / 分区线损率），去重键 (code, name)
            name = self._strip_unit(m["display_name"], raw.get("unit") or m.get("unit") or "")
            if (code, name) in seen:
                continue
            seen.add((code, name))
            item = self._metric_out(raw, display=name)
            legal = set(m.get("legal_tables") or [])
            if scope_tables is not None:
                legal &= scope_tables
            if legal:
                item["tables"] = sorted(legal)
            out.append(item)
        out.sort(key=lambda x: x["metric_name"])
        return out

    @staticmethod
    def _strip_unit(display: str, unit: str) -> str:
        if unit and display.endswith(f"({unit})"):
            return display[: -len(unit) - 2]
        return display

    def _build_dimensions(self, dimension_ids: Set[int], dim_value_topn: int,
                          recalled_values: Optional[Dict[int, List[str]]] = None) -> List[dict]:
        recalled_values = recalled_values or {}
        out = []
        for fid in sorted(dimension_ids):
            m = self.catalog.meta(fid)
            if not m:
                continue
            raw = self._dim_raw.get(m["code"]) or {"dimension_code": m["code"], "dimension_name": m["display_name"]}
            possible = list(raw.get("possible_values") or [])
            if dim_value_topn > 0:
                recalled = recalled_values.get(fid, [])
                merged = recalled + [v for v in possible if str(v) not in set(recalled)]
                possible = merged[:dim_value_topn]
            out.append(self._dim_out(raw, possible))
        out.sort(key=lambda x: x["dimension_name"])
        return out

    def _build_tables(self, table_ids: Set[int]) -> List[dict]:
        out = []
        for fid in sorted(table_ids):
            tn = self.catalog.table_name_of(fid)
            if not tn:
                continue
            raw = self._table_raw.get(tn) or {"table_name": tn, "display_name": self.catalog.meta(fid)["display_name"]}
            out.append(self._table_out(raw))
        out.sort(key=lambda x: x["table_name"])
        return out
