"""
IndexStore —— 每种实体类型一个 IndexIDMap2(IndexFlatIP)

- 精确内积检索（向量已 L2 归一化 = 余弦），千~万级规模下比 HNSW 更快更准且支持增删
- faiss_id 稳定（来自 recall_entity.faiss_id），上层用 IDSelectorBatch 做范围过滤（pre-filter）
- 线程安全：写用互斥锁，读不加锁（faiss 读并发安全；写时用新索引整体替换引用）
"""
import threading
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

import faiss
import numpy as np

from app.utils.logger import get_logger

logger = get_logger("entity.index", "index_builder.log")

ENTITY_TYPES = ("table", "metric", "dimension", "dim_value", "knowledge", "topic")


class IndexStore:
    def __init__(self, dim: int):
        self.dim = dim
        self._lock = threading.Lock()
        self._idx: Dict[str, faiss.Index] = {t: self._new() for t in ENTITY_TYPES}
        # faiss_id → entity meta（检索命中后取元信息，避免回库）
        self.meta: Dict[int, dict] = {}
        self.key_to_id: Dict[str, int] = {}

    def _new(self) -> faiss.Index:
        return faiss.IndexIDMap2(faiss.IndexFlatIP(self.dim))

    # ------------------------------------------------------------------
    def load(self, entities: Iterable[dict]):
        """全量装载：entities 需含 faiss_id / entity_type / embedding(np.ndarray 或 list)"""
        by_type: Dict[str, Tuple[List[int], List[np.ndarray]]] = {t: ([], []) for t in ENTITY_TYPES}
        meta, k2i = {}, {}
        for e in entities:
            vec = e.get("embedding")
            if vec is None:
                continue
            et = e["entity_type"]
            if et not in by_type:
                continue
            by_type[et][0].append(int(e["faiss_id"]))
            by_type[et][1].append(np.asarray(vec, dtype="float32"))
            meta[int(e["faiss_id"])] = self._meta_of(e)
            k2i[e["entity_key"]] = int(e["faiss_id"])
        new = {}
        for et, (ids, vecs) in by_type.items():
            idx = self._new()
            if ids:
                idx.add_with_ids(np.stack(vecs), np.asarray(ids, dtype="int64"))
            new[et] = idx
        with self._lock:
            self._idx, self.meta, self.key_to_id = new, meta, k2i
        logger.info("IndexStore loaded: " + ", ".join(f"{t}={self._idx[t].ntotal}" for t in ENTITY_TYPES))

    def upsert(self, entities: Iterable[dict]):
        """增量：先 remove 再 add（IDMap2 支持）"""
        with self._lock:
            for e in entities:
                vec = e.get("embedding")
                if vec is None:
                    continue
                et, fid = e["entity_type"], int(e["faiss_id"])
                idx = self._idx[et]
                # 同一 faiss_id 若曾属于其他类型（不应发生），也清掉
                for t, ix in self._idx.items():
                    if t != et and fid in self.meta and self.meta[fid]["entity_type"] == t:
                        ix.remove_ids(np.asarray([fid], dtype="int64"))
                idx.remove_ids(np.asarray([fid], dtype="int64"))
                idx.add_with_ids(np.asarray(vec, dtype="float32").reshape(1, -1), np.asarray([fid], dtype="int64"))
                self.meta[fid] = self._meta_of(e)
                self.key_to_id[e["entity_key"]] = fid

    def remove(self, keys: Iterable[str]):
        with self._lock:
            for k in keys:
                fid = self.key_to_id.pop(k, None)
                if fid is None:
                    continue
                m = self.meta.pop(fid, None)
                if m:
                    self._idx[m["entity_type"]].remove_ids(np.asarray([fid], dtype="int64"))

    @staticmethod
    def _meta_of(e: dict) -> dict:
        return {k: e.get(k) for k in ("entity_key", "entity_type", "code", "table_name", "dimension_code",
                                      "display_name", "description", "synonyms", "unit", "caliber_scope",
                                      "legal_tables")}

    # ------------------------------------------------------------------
    def search(self, entity_type: str, query_vec: np.ndarray, k: int,
               allowed: Optional[Set[int]] = None) -> List[Tuple[int, float]]:
        """返回 [(faiss_id, score)]；allowed=None 表示不过滤，allowed=空集合表示无候选"""
        idx = self._idx.get(entity_type)
        if idx is None or idx.ntotal == 0 or k <= 0:
            return []
        if allowed is not None:
            if not allowed:
                return []
            sel = faiss.IDSelectorBatch(np.fromiter(allowed, dtype="int64", count=len(allowed)))
            params = faiss.SearchParameters(sel=sel)
            k = min(k, len(allowed))
            D, I = idx.search(query_vec.reshape(1, -1).astype("float32"), k, params=params)
        else:
            k = min(k, idx.ntotal)
            D, I = idx.search(query_vec.reshape(1, -1).astype("float32"), k)
        return [(int(i), float(d)) for i, d in zip(I[0], D[0]) if i >= 0]

    def ntotal(self, entity_type: str = None) -> int:
        if entity_type:
            return self._idx[entity_type].ntotal
        return sum(ix.ntotal for ix in self._idx.values())

    def stats(self) -> Dict[str, int]:
        return {t: self._idx[t].ntotal for t in ENTITY_TYPES}

    def ids_of_type(self, entity_type: str) -> Set[int]:
        return {fid for fid, m in self.meta.items() if m["entity_type"] == entity_type}
