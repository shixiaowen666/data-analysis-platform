"""
EntityStore —— 实体层的全部数据库访问（源对象 / 实体 / 向量缓存 / 智能体范围 / KV）

约定：
- 所有 JSON 字段以 TEXT 存储，读写时序列化，MySQL/SQLite 通吃
- upsert 统一用 DELETE + INSERT（规避方言差异；单条量级下开销可忽略）
- faiss_id 从 recall_kv['faiss_id_seq'] 单调分配，entity_key 一旦分配过就复用
"""
import json
import time
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set

from app.core.database import DBClient, get_db
from app.utils.logger import get_logger

logger = get_logger("entity.store", "entity.log")


def _now() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


def _j(v) -> str:
    return json.dumps(v if v is not None else [], ensure_ascii=False)


def _uj(s, default=None):
    if s is None or s == "":
        return [] if default is None else default
    try:
        return json.loads(s)
    except Exception:
        return [] if default is None else default


class EntityStore:
    def __init__(self, db: DBClient = None):
        self.db = db or get_db()

    # ------------------------------------------------------------------
    # KV
    # ------------------------------------------------------------------
    def kv_get(self, k: str, default: str = None) -> Optional[str]:
        r = self.db.query("SELECT v FROM recall_kv WHERE k=%s", [k])
        return r[0]["v"] if r else default

    def kv_set(self, k: str, v: str):
        self.db.execute("DELETE FROM recall_kv WHERE k=%s", [k])
        self.db.execute("INSERT INTO recall_kv(k, v, updated_at) VALUES (%s,%s,%s)", [k, v, _now()])

    # ------------------------------------------------------------------
    # 源对象：表 / 指标 / 维度 / 维度值 / 知识
    # ------------------------------------------------------------------
    def upsert_tables(self, items: Iterable[dict]):
        for t in items:
            self.db.execute("DELETE FROM src_table WHERE table_name=%s", [t["table_name"]])
            self.db.execute(
                "INSERT INTO src_table(table_name, source_id, display_name, description, columns_json, extra_json, updated_at) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s)",
                [t["table_name"], str(t.get("table_id") or t.get("source_id") or ""),
                 t.get("display_name") or "", t.get("description") or "",
                 _j(t.get("columns") or []), _j(t.get("extra") or {}), _now()])

    def upsert_metrics(self, items: Iterable[dict]):
        for m in items:
            self.db.execute("DELETE FROM src_metric WHERE metric_code=%s", [m["metric_code"]])
            self.db.execute(
                "INSERT INTO src_metric(metric_code, source_id, metric_name, description, unit, data_type, caliber_scope, aliases_json, extra_json, updated_at) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                [m["metric_code"], str(m.get("metric_id") or m.get("source_id") or ""),
                 m.get("metric_name") or m["metric_code"], m.get("description") or "",
                 m.get("unit") or "", m.get("data_type") or "", m.get("caliber_scope") or "",
                 _j(m.get("aliases") or []), _j(m.get("extra") or {}), _now()])

    def upsert_dimensions(self, items: Iterable[dict], replace_values: bool = True):
        for d in items:
            code = d["dimension_code"]
            self.db.execute("DELETE FROM src_dimension WHERE dimension_code=%s", [code])
            self.db.execute(
                "INSERT INTO src_dimension(dimension_code, source_id, dimension_name, description, data_type, aliases_json, extra_json, updated_at) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
                [code, str(d.get("dimension_id") or d.get("source_id") or ""),
                 d.get("dimension_name") or code, d.get("description") or "",
                 d.get("data_type") or "", _j(d.get("aliases") or []), _j(d.get("extra") or {}), _now()])
            if replace_values and "possible_values" in d:
                self.replace_dim_values(code, d.get("possible_values") or [])

    def replace_dim_values(self, dimension_code: str, values: Sequence):
        self.db.execute("DELETE FROM src_dim_value WHERE dimension_code=%s", [dimension_code])
        self.upsert_dim_values(dimension_code, values)

    def upsert_dim_values(self, dimension_code: str, values: Sequence):
        """values: [str] 或 [{value_name, synonyms}]；新值追加到现有顺序之后"""
        seen = set()
        r = self.db.query("SELECT MAX(sort_no) AS m FROM src_dim_value WHERE dimension_code=%s", [dimension_code])
        base = int(r[0]["m"]) + 1 if r and r[0]["m"] is not None else 0
        for i, v in enumerate(values, start=base):
            if isinstance(v, dict):
                name, syn = str(v.get("value_name") or v.get("value") or "").strip(), v.get("synonyms") or []
            else:
                name, syn = str(v).strip(), []
            if not name or name in seen:
                continue
            seen.add(name)
            self.db.execute("DELETE FROM src_dim_value WHERE dimension_code=%s AND value_name=%s", [dimension_code, name])
            self.db.execute(
                "INSERT INTO src_dim_value(dimension_code, value_name, synonyms_json, sort_no, updated_at) VALUES (%s,%s,%s,%s,%s)",
                [dimension_code, name, _j(syn), i, _now()])

    def delete_dim_values(self, dimension_code: str, names: Sequence[str]):
        for n in names:
            self.db.execute("DELETE FROM src_dim_value WHERE dimension_code=%s AND value_name=%s", [dimension_code, n])

    def upsert_knowledge(self, items: Iterable[dict]):
        for i, k in enumerate(items):
            kid = str(k.get("knowledge_id") or k.get("id") or "")
            if not kid:
                continue
            self.db.execute("DELETE FROM src_knowledge WHERE knowledge_id=%s", [kid])
            self.db.execute(
                "INSERT INTO src_knowledge(knowledge_id, aliases_json, content, sort_no, updated_at) VALUES (%s,%s,%s,%s,%s)",
                [kid, _j(k.get("knowledgeAlias") or k.get("aliases") or []),
                 k.get("knowledgeElement") or k.get("content") or "", int(k.get("sort_no", i)), _now()])

    def delete_source(self, kind: str, key: str) -> int:
        """kind ∈ table/metric/dimension/knowledge；返回删除行数（0/1）"""
        col = {"table": ("src_table", "table_name"), "metric": ("src_metric", "metric_code"),
               "dimension": ("src_dimension", "dimension_code"), "knowledge": ("src_knowledge", "knowledge_id")}[kind]
        n = len(self.db.query(f"SELECT 1 FROM {col[0]} WHERE {col[1]}=%s", [key]))
        self.db.execute(f"DELETE FROM {col[0]} WHERE {col[1]}=%s", [key])
        if kind == "dimension":
            self.db.execute("DELETE FROM src_dim_value WHERE dimension_code=%s", [key])
        return n

    def clear_sources(self):
        for t in ("src_table", "src_metric", "src_dimension", "src_dim_value", "src_knowledge"):
            self.db.execute(f"DELETE FROM {t}")

    # ---- 读源对象 ----
    def load_tables(self, names: Sequence[str] = None) -> List[dict]:
        rows = self._select("src_table", "table_name", names)
        return [{"table_name": r["table_name"], "table_id": r["source_id"], "display_name": r["display_name"] or "",
                 "description": r["description"] or "", "columns": _uj(r["columns_json"]),
                 "extra": _uj(r["extra_json"], {})} for r in rows]

    def load_metrics(self, codes: Sequence[str] = None) -> List[dict]:
        rows = self._select("src_metric", "metric_code", codes)
        return [{"metric_code": r["metric_code"], "metric_id": r["source_id"], "metric_name": r["metric_name"] or "",
                 "description": r["description"] or "", "unit": r["unit"] or "", "data_type": r["data_type"] or "",
                 "caliber_scope": r["caliber_scope"] or "", "aliases": _uj(r["aliases_json"]),
                 "extra": _uj(r["extra_json"], {})} for r in rows]

    def load_dimensions(self, codes: Sequence[str] = None, with_values: bool = True) -> List[dict]:
        rows = self._select("src_dimension", "dimension_code", codes)
        out = [{"dimension_code": r["dimension_code"], "dimension_id": r["source_id"],
                "dimension_name": r["dimension_name"] or "", "description": r["description"] or "",
                "data_type": r["data_type"] or "", "aliases": _uj(r["aliases_json"]),
                "extra": _uj(r["extra_json"], {}), "possible_values": []} for r in rows]
        if with_values and out:
            vals = self.load_dim_values([d["dimension_code"] for d in out])
            by = {}
            for v in vals:
                by.setdefault(v["dimension_code"], []).append(v)
            for d in out:
                d["possible_values"] = [v["value_name"] for v in by.get(d["dimension_code"], [])]
                d["value_synonyms"] = {v["value_name"]: v["synonyms"] for v in by.get(d["dimension_code"], []) if v["synonyms"]}
        return out

    def load_dim_values(self, codes: Sequence[str] = None) -> List[dict]:
        rows = self._select("src_dim_value", "dimension_code", codes, order="dimension_code, sort_no, value_name")
        return [{"dimension_code": r["dimension_code"], "value_name": r["value_name"],
                 "synonyms": _uj(r["synonyms_json"]), "sort_no": r["sort_no"]} for r in rows]

    def load_knowledge(self) -> List[dict]:
        rows = self.db.query("SELECT * FROM src_knowledge ORDER BY sort_no, knowledge_id")
        return [{"knowledge_id": r["knowledge_id"], "knowledgeAlias": _uj(r["aliases_json"]),
                 "knowledgeElement": r["content"] or "", "sort_no": r["sort_no"]} for r in rows]

    def _select(self, table, keycol, keys, order=None):
        order_sql = f" ORDER BY {order}" if order else f" ORDER BY {keycol}"
        if keys is None:
            return self.db.query(f"SELECT * FROM {table}{order_sql}")
        keys = list(dict.fromkeys(keys))
        if not keys:
            return []
        out = []
        for i in range(0, len(keys), 500):
            chunk = keys[i:i + 500]
            ph = ",".join(["%s"] * len(chunk))
            out.extend(self.db.query(f"SELECT * FROM {table} WHERE {keycol} IN ({ph}){order_sql}", chunk))
        return out

    # ------------------------------------------------------------------
    # 实体
    # ------------------------------------------------------------------
    def alloc_faiss_ids(self, keys: Sequence[str]) -> Dict[str, int]:
        """为 entity_key 分配稳定 faiss_id：已有则复用，新键从计数器递增"""
        keys = list(dict.fromkeys(keys))
        existing = {}
        for i in range(0, len(keys), 500):
            chunk = keys[i:i + 500]
            ph = ",".join(["%s"] * len(chunk))
            for r in self.db.query(f"SELECT entity_key, faiss_id FROM recall_entity WHERE entity_key IN ({ph})", chunk):
                existing[r["entity_key"]] = int(r["faiss_id"])
        # 历史分配记录（实体删除后 id 也不回收，保证幂等）
        hist = _uj(self.kv_get("faiss_id_map"), {})
        seq = int(self.kv_get("faiss_id_seq", "0") or 0)
        out, changed = {}, False
        for k in keys:
            if k in existing:
                out[k] = existing[k]
            elif k in hist:
                out[k] = int(hist[k])
            else:
                seq += 1
                out[k] = seq
                hist[k] = seq
                changed = True
        if changed:
            self.kv_set("faiss_id_seq", str(seq))
            self.kv_set("faiss_id_map", json.dumps(hist, ensure_ascii=False))
        return out

    def load_entities(self, entity_type: str = None, keys: Sequence[str] = None,
                      with_vec: bool = False) -> List[dict]:
        cols = ("entity_key, faiss_id, entity_type, code, table_name, dimension_code, display_name, description, "
                "synonyms, unit, caliber_scope, legal_tables_json, embedding_text, text_hash, embed_model, updated_at"
                + (", embedding_json" if with_vec else ""))
        where, params = [], []
        if entity_type:
            where.append("entity_type=%s"); params.append(entity_type)
        rows = []
        if keys is not None:
            keys = list(dict.fromkeys(keys))
            for i in range(0, len(keys), 500):
                chunk = keys[i:i + 500]
                ph = ",".join(["%s"] * len(chunk))
                w = " AND ".join(where + [f"entity_key IN ({ph})"])
                rows.extend(self.db.query(f"SELECT {cols} FROM recall_entity WHERE {w}", params + chunk))
        else:
            w = (" WHERE " + " AND ".join(where)) if where else ""
            rows = self.db.query(f"SELECT {cols} FROM recall_entity{w}", params or None)
        for r in rows:
            r["legal_tables"] = _uj(r.pop("legal_tables_json"))
            r["faiss_id"] = int(r["faiss_id"])
        return rows

    def load_entity_index(self) -> Dict[str, dict]:
        """轻量索引：entity_key → {faiss_id, entity_type, text_hash, embed_model, legal_tables, display_name...}"""
        return {r["entity_key"]: r for r in self.load_entities()}

    def upsert_entities(self, rows: Iterable[dict]):
        for e in rows:
            self.db.execute("DELETE FROM recall_entity WHERE entity_key=%s", [e["entity_key"]])
            self.db.execute(
                "INSERT INTO recall_entity(entity_key, faiss_id, entity_type, code, table_name, dimension_code, display_name, "
                "description, synonyms, unit, caliber_scope, legal_tables_json, embedding_text, text_hash, embed_model, embedding_json, updated_at) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                [e["entity_key"], int(e["faiss_id"]), e["entity_type"], e.get("code") or "", e.get("table_name") or "",
                 e.get("dimension_code") or "", e.get("display_name") or "", e.get("description") or "",
                 e.get("synonyms") or "", e.get("unit") or "", e.get("caliber_scope") or "",
                 _j(sorted(e.get("legal_tables") or [])), e["embedding_text"], e["text_hash"],
                 e.get("embed_model") or "", e.get("embedding_json"), _now()])

    def delete_entities(self, keys: Sequence[str]):
        for k in keys:
            self.db.execute("DELETE FROM recall_entity WHERE entity_key=%s", [k])

    def clear_entities(self):
        self.db.execute("DELETE FROM recall_entity")

    def entity_counts(self) -> Dict[str, int]:
        rows = self.db.query("SELECT entity_type, COUNT(*) AS c FROM recall_entity GROUP BY entity_type")
        return {r["entity_type"]: int(r["c"]) for r in rows}

    # ------------------------------------------------------------------
    # 向量缓存
    # ------------------------------------------------------------------
    def cache_get(self, hashes: Sequence[str], model: str) -> Dict[str, str]:
        out = {}
        hashes = list(dict.fromkeys(hashes))
        for i in range(0, len(hashes), 500):
            chunk = hashes[i:i + 500]
            ph = ",".join(["%s"] * len(chunk))
            for r in self.db.query(
                    f"SELECT text_hash, embedding_json FROM recall_embedding_cache WHERE embed_model=%s AND text_hash IN ({ph})",
                    [model] + chunk):
                out[r["text_hash"]] = r["embedding_json"]
        return out

    def cache_put(self, items: Dict[str, str], model: str):
        for h, vj in items.items():
            self.db.execute("DELETE FROM recall_embedding_cache WHERE text_hash=%s AND embed_model=%s", [h, model])
            self.db.execute(
                "INSERT INTO recall_embedding_cache(text_hash, embed_model, embedding_json, created_at) VALUES (%s,%s,%s,%s)",
                [h, model, vj, _now()])

    # ------------------------------------------------------------------
    # 智能体范围
    # ------------------------------------------------------------------
    def upsert_agent(self, agent_id: str, name: str = "", scope: Dict[str, Sequence[str]] = None) -> int:
        """scope: {"tables": [...], "metrics": [...], "dimensions": [...]}；返回新版本号"""
        r = self.db.query("SELECT version FROM agent WHERE agent_id=%s", [agent_id])
        ver = (int(r[0]["version"]) + 1) if r else 1
        self.db.execute("DELETE FROM agent WHERE agent_id=%s", [agent_id])
        self.db.execute("INSERT INTO agent(agent_id, agent_name, version, updated_at) VALUES (%s,%s,%s,%s)",
                        [agent_id, name or (r and "") or "", ver, _now()])
        if scope is not None:
            self.db.execute("DELETE FROM agent_scope WHERE agent_id=%s", [agent_id])
            for rt in ("tables", "metrics", "dimensions"):
                for ref in dict.fromkeys(scope.get(rt) or []):
                    self.db.execute("INSERT INTO agent_scope(agent_id, ref_type, ref) VALUES (%s,%s,%s)",
                                    [agent_id, rt[:-1], str(ref)])
        return ver

    def delete_agent(self, agent_id: str) -> bool:
        n = len(self.db.query("SELECT 1 FROM agent WHERE agent_id=%s", [agent_id]))
        self.db.execute("DELETE FROM agent_scope WHERE agent_id=%s", [agent_id])
        self.db.execute("DELETE FROM agent WHERE agent_id=%s", [agent_id])
        return n > 0

    def load_agent(self, agent_id: str) -> Optional[dict]:
        r = self.db.query("SELECT agent_id, agent_name, version, updated_at FROM agent WHERE agent_id=%s", [agent_id])
        if not r:
            return None
        a = dict(r[0])
        a["scope"] = {"tables": [], "metrics": [], "dimensions": []}
        for s in self.db.query("SELECT ref_type, ref FROM agent_scope WHERE agent_id=%s ORDER BY ref", [agent_id]):
            a["scope"][s["ref_type"] + "s"].append(s["ref"])
        return a

    def list_agents(self) -> List[dict]:
        rows = self.db.query("SELECT agent_id, agent_name, version, updated_at FROM agent ORDER BY agent_id")
        cnt = {}
        for s in self.db.query("SELECT agent_id, ref_type, COUNT(*) AS c FROM agent_scope GROUP BY agent_id, ref_type"):
            cnt.setdefault(s["agent_id"], {})[s["ref_type"] + "s"] = int(s["c"])
        return [dict(r, scope_counts=cnt.get(r["agent_id"], {})) for r in rows]


_store: Optional[EntityStore] = None


def get_entity_store() -> EntityStore:
    global _store
    if _store is None:
        _store = EntityStore()
    return _store
