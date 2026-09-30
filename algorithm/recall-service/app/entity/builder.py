"""
EntityBuilder —— 源对象 → 原子可检索实体（纯函数，不碰数据库、不调 embedding）

实体键（永不漂移，faiss_id 与之一一绑定）：
  table:{table_name}
  metric:{code}                      规范变体（列描述 == 全局指标名，或该 code 唯一的变体）
  metric:{code}#{hash8(列描述)}       同 code 在其他表里含义不同的变体（解决 ll_rate=分区/分压/分台线损率）
  dimension:{code}                   维度全局一份（同 code 各表列描述并入别名）
  value:{dimension_code}:{value}     维度值
  knowledge:{knowledge_id}           业务知识（原 derived_metric / business_context）
  topic:{topic_name}                 业务主题（domain_knowledge.topic_definitions，仅保留表存在的）

变更传播（谁的文本引用了谁）：
  table.display_name       → 表下所有 metric 实体（"所属数据表"）
  metric.name/alias        → 所属表实体（"包含指标"）
  dimension.name/alias     → 所属表实体（"包含维度"）+ 该维度全部 value 实体（"所属维度"）
  value 增删               → dimension 实体（"可选值示例"）
  description（任意实体）  → 仅自身
affected_keys() 按上述规则给出保守的受影响实体集合；真正是否重新编码由 text_hash 决定。
"""
import hashlib
import json
from collections import defaultdict
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

from app import config
from app.core.text_builder import EmbeddingTextBuilder

TEMPLATE_VERSION = "v2"   # 模板改动时 +1 → 全量 text_hash 变化 → 全量重编（这是唯一需要全量的场景）


def _h8(s: str) -> str:
    return hashlib.sha1(s.encode("utf-8")).hexdigest()[:8]


def text_hash(embedding_text: str) -> str:
    return hashlib.sha256(f"{TEMPLATE_VERSION}|{embedding_text}".encode("utf-8")).hexdigest()


def _load_overrides() -> dict:
    """可选的人工口径（保留对 domain_knowledge.json 的兼容：dim_display_name_map / dim_synonyms / table_enhancement / topic_definitions）"""
    p = config.DATA_DIR / "domain_knowledge.json"
    if not p.exists():
        return {}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {}


class EntityBuilder:
    def __init__(self, tables: List[dict], metrics: List[dict], dimensions: List[dict],
                 knowledge: List[dict], overrides: dict = None):
        self.tb = EmbeddingTextBuilder()
        self.ov = overrides if overrides is not None else _load_overrides()
        self.dim_name_map: dict = self.ov.get("dim_display_name_map", {}) or {}
        self.dim_syn: dict = self.ov.get("dim_synonyms", {}) or {}
        self.table_enh: dict = self.ov.get("table_enhancement", {}) or {}
        self.topics: list = self.ov.get("topic_definitions", []) or []

        self.tables = {t["table_name"]: t for t in tables}
        self.metrics = {m["metric_code"]: m for m in metrics}
        self.dimensions = {d["dimension_code"]: d for d in dimensions}
        self.knowledge = knowledge

        # 关系推断（列 → 指标/维度）
        self.table_metric_cols: Dict[str, List[Tuple[str, str]]] = defaultdict(list)   # table → [(code, col_desc)]
        self.table_dim_cols: Dict[str, List[Tuple[str, str]]] = defaultdict(list)
        self.metric_tables: Dict[str, Set[str]] = defaultdict(set)
        self.dim_tables: Dict[str, Set[str]] = defaultdict(set)
        self.extra_metrics: Dict[str, dict] = {}
        self.extra_dims: Dict[str, dict] = {}
        self._infer()

    # ------------------------------------------------------------------
    def _infer(self):
        m_by_name = {m["metric_name"]: c for c, m in self.metrics.items() if m.get("metric_name")}
        d_by_name = {d["dimension_name"]: c for c, d in self.dimensions.items() if d.get("dimension_name")}
        for tn, t in self.tables.items():
            for c in t.get("columns") or []:
                col = (c.get("column_name") or "").strip()
                if not col:
                    continue
                desc = (c.get("description") or "").strip()
                if c.get("is_measure"):
                    code = col if col in self.metrics else m_by_name.get(desc, col)
                    if code not in self.metrics and code not in self.extra_metrics:
                        self.extra_metrics[code] = {"metric_code": code, "metric_name": desc or code,
                                                    "description": "", "unit": "", "data_type": c.get("data_type", ""),
                                                    "caliber_scope": "", "aliases": []}
                    self.table_metric_cols[tn].append((code, desc))
                    self.metric_tables[code].add(tn)
                if c.get("is_dimension"):
                    code = col if col in self.dimensions else d_by_name.get(desc, col)
                    if code not in self.dimensions and code not in self.extra_dims:
                        self.extra_dims[code] = {"dimension_code": code,
                                                 "dimension_name": self.dim_name_map.get(code, desc or code),
                                                 "description": desc, "data_type": c.get("data_type", ""),
                                                 "aliases": [], "possible_values": []}
                    self.table_dim_cols[tn].append((code, desc))
                    self.dim_tables[code].add(tn)

    def _metric(self, code) -> dict:
        return self.metrics.get(code) or self.extra_metrics.get(code) or {"metric_code": code, "metric_name": code}

    def _dim(self, code) -> dict:
        return self.dimensions.get(code) or self.extra_dims.get(code) or {"dimension_code": code, "dimension_name": code}

    def _table_display(self, tn) -> str:
        t = self.tables.get(tn, {})
        return (self.table_enh.get(tn, {}).get("display_name") or t.get("display_name") or tn).strip()

    def _dim_display(self, code) -> str:
        return self.dim_name_map.get(code) or self._dim(code).get("dimension_name") or code

    # ------------------------------------------------------------------
    # 指标变体：同 code 按"列描述"分组
    # ------------------------------------------------------------------
    def metric_variants(self, code: str) -> List[Tuple[str, str, List[str]]]:
        """返回 [(entity_key, display_name, tables)]"""
        m = self._metric(code)
        gname = (m.get("metric_name") or code).strip()
        groups: Dict[str, List[str]] = defaultdict(list)
        for tn in sorted(self.metric_tables.get(code, ())):
            desc = next((d for c, d in self.table_metric_cols[tn] if c == code), "")
            name = desc if desc and desc != code else gname
            groups[name].append(tn)
        if not groups:
            return [(f"metric:{code}", gname, [])]
        # 规范变体：名字等于全局名；否则表最多的那个
        canon = gname if gname in groups else max(groups, key=lambda n: (len(groups[n]), n))
        out = [(f"metric:{code}", canon, groups[canon])]
        for name, tns in groups.items():
            if name != canon:
                out.append((f"metric:{code}#{_h8(name)}", name, tns))
        return out

    # ------------------------------------------------------------------
    # 构建单个实体
    # ------------------------------------------------------------------
    def build_table(self, tn: str) -> Optional[dict]:
        t = self.tables.get(tn)
        if t is None:
            return None
        enh = self.table_enh.get(tn, {})
        display = self._table_display(tn)
        desc = (enh.get("description") or t.get("description") or "").strip() or display
        metric_names = list(dict.fromkeys(
            (d if d and d != c else self._metric(c).get("metric_name", c)) for c, d in self.table_metric_cols.get(tn, [])))
        dim_names = list(dict.fromkeys(self._dim_display(c) for c, _ in self.table_dim_cols.get(tn, [])))
        text = self.tb.build_table_text({
            "table_name": tn, "display_name": display, "description": desc,
            "business_topics": enh.get("business_topics", []),
            "metric_names": metric_names, "dimension_names": dim_names,
            "typical_questions": enh.get("typical_questions", []) or (t.get("extra") or {}).get("typical_questions", []),
        })
        return {"entity_key": f"table:{tn}", "entity_type": "table", "code": tn, "table_name": tn,
                "display_name": display, "description": desc, "legal_tables": [tn],
                "embedding_text": text, "text_hash": text_hash(text)}

    def build_metric_entities(self, code: str) -> List[dict]:
        m = self._metric(code)
        out = []
        for key, name, tns in self.metric_variants(code):
            unit = (m.get("unit") or "").strip()
            display = name if (not unit or unit in name) else f"{name}({unit})"
            aliases = [a for a in (m.get("aliases") or []) if a and a != code and a != name]
            gname = (m.get("metric_name") or "").strip()
            if gname and gname != name:
                aliases.insert(0, gname)
            aliases = list(dict.fromkeys(aliases))
            src_table = self._table_display(tns[0]) if tns else ""
            text = self.tb.build_metric_text({
                "metric_name": code, "display_name": display,
                "description": (m.get("description") or "").strip() or name,
                "business_topics": [], "caliber_scope": m.get("caliber_scope") or "none",
                "source_table": src_table, "synonyms": "、".join(aliases), "unit": unit,
                "typical_questions": (m.get("extra") or {}).get("typical_questions", []),
            })
            out.append({"entity_key": key, "entity_type": "metric", "code": code,
                        "table_name": tns[0] if tns else "", "display_name": display,
                        "description": (m.get("description") or "").strip(), "synonyms": "、".join(aliases),
                        "unit": unit, "caliber_scope": m.get("caliber_scope") or "",
                        "legal_tables": sorted(tns), "embedding_text": text, "text_hash": text_hash(text)})
        return out

    def build_dimension(self, code: str) -> dict:
        d = self._dim(code)
        display = self._dim_display(code)
        # 各表列描述并入别名（同 code 不同叫法，如 org_name=供电局名称/分局/供电局）
        col_descs = {desc for tn in self.dim_tables.get(code, ()) for c, desc in self.table_dim_cols[tn]
                     if c == code and desc and desc != code and desc != display}
        aliases = [a for a in (d.get("aliases") or []) if a and a != code and a != display]
        aliases = list(dict.fromkeys(aliases + sorted(col_descs) + list(self.dim_syn.get(code, []))))
        pv = d.get("possible_values") or []
        text = self.tb.build_dimension_text({
            "dimension_name": code, "display_name": display,
            "description": (d.get("description") or "").strip() or display,
            "synonyms": "、".join(aliases), "sample_values": pv[:10],
        })
        return {"entity_key": f"dimension:{code}", "entity_type": "dimension", "code": code,
                "dimension_code": code, "display_name": display,
                "description": (d.get("description") or "").strip(), "synonyms": "、".join(aliases),
                "legal_tables": sorted(self.dim_tables.get(code, ())),
                "embedding_text": text, "text_hash": text_hash(text)}

    def build_values(self, code: str) -> List[dict]:
        d = self._dim(code)
        display = self._dim_display(code)
        vsyn = d.get("value_synonyms") or {}
        dim_extra = [a for a in (d.get("aliases") or []) if a and a != code]
        legal = sorted(self.dim_tables.get(code, ()))
        out = []
        for v in d.get("possible_values") or []:
            v = str(v).strip()
            if not v:
                continue
            syn = list(vsyn.get(v) or [])
            if "-" in v:
                syn.append(v.split("-", 1)[1])
            syn = list(dict.fromkeys(syn + dim_extra))
            text = self.tb.build_dim_value_text({"display_name": v, "dimension_display_name": display,
                                                 "description": f"{display}：{v}", "synonyms": "、".join(syn)})
            out.append({"entity_key": f"value:{code}:{v}", "entity_type": "dim_value", "code": v,
                        "dimension_code": code, "display_name": v, "description": f"{display}：{v}",
                        "synonyms": "、".join(syn), "legal_tables": legal,
                        "embedding_text": text, "text_hash": text_hash(text)})
        return out

    def build_knowledge(self, k: dict) -> Optional[dict]:
        content = (k.get("knowledgeElement") or "").strip()
        if not content:
            return None
        aliases = [a for a in (k.get("knowledgeAlias") or []) if a]
        name = aliases[0] if aliases else content[:12]
        text = self.tb.build_derived_metric_text({"display_name": name, "description": content,
                                                  "calculation_rule": content, "aliases": "、".join(aliases)})
        return {"entity_key": f"knowledge:{k['knowledge_id']}", "entity_type": "knowledge",
                "code": str(k["knowledge_id"]), "display_name": name, "description": content,
                "synonyms": "、".join(aliases), "legal_tables": [],
                "embedding_text": text, "text_hash": text_hash(text)}

    def build_topics(self) -> List[dict]:
        out = []
        for td in self.topics:
            tns = [t for t in td.get("tables", []) if t in self.tables]
            if not tns:
                continue
            text = self.tb.build_topic_text({"topic_name": td["topic_name"], "description": td.get("description", ""),
                                             "typical_queries": td.get("typical_queries", [])})
            out.append({"entity_key": f"topic:{td['topic_name']}", "entity_type": "topic", "code": td["topic_name"],
                        "display_name": td["topic_name"], "description": td.get("description", ""),
                        "legal_tables": sorted(tns), "embedding_text": text, "text_hash": text_hash(text)})
        return out

    # ------------------------------------------------------------------
    # 全量 / 受影响集合
    # ------------------------------------------------------------------
    def all_codes(self):
        metric_codes = set(self.metrics) | set(self.extra_metrics)
        dim_codes = set(self.dimensions) | set(self.extra_dims)
        return sorted(self.tables), sorted(metric_codes), sorted(dim_codes)

    def build_all(self) -> List[dict]:
        tns, mcs, dcs = self.all_codes()
        out = [e for tn in tns if (e := self.build_table(tn))]
        for c in mcs:
            out.extend(self.build_metric_entities(c))
        for c in dcs:
            out.append(self.build_dimension(c))
            out.extend(self.build_values(c))
        for k in self.knowledge:
            if (e := self.build_knowledge(k)):
                out.append(e)
        out.extend(self.build_topics())
        return out

    def build_for(self, tables: Iterable[str] = (), metrics: Iterable[str] = (),
                  dimensions: Iterable[str] = (), knowledge_ids: Iterable[str] = ()) -> Tuple[List[dict], Set[str]]:
        """按受影响的源对象构建实体；返回 (entities, 这些源对象对应的全部可能 entity_key 前缀集合)。
        传播规则见模块 docstring。返回的 prefixes 用于检测"该源对象下已不存在的实体"（如删掉的维度值/变体）。"""
        tables, metrics, dimensions = set(tables), set(metrics), set(dimensions)
        # 传播
        for tn in list(tables):
            metrics |= {c for c, _ in self.table_metric_cols.get(tn, [])}
        for c in list(metrics):
            tables |= self.metric_tables.get(c, set())
        for c in list(dimensions):
            tables |= self.dim_tables.get(c, set())
        # 表变化会改变其下维度的 legal_tables（不影响文本），一并重建保证关系正确
        for tn in list(tables):
            dimensions |= {c for c, _ in self.table_dim_cols.get(tn, [])}

        out, prefixes = [], set()
        for tn in sorted(tables):
            prefixes.add(f"table:{tn}")
            if (e := self.build_table(tn)):
                out.append(e)
        for c in sorted(metrics):
            prefixes.add(f"metric:{c}")
            out.extend(self.build_metric_entities(c))
        for c in sorted(dimensions):
            prefixes.add(f"dimension:{c}")
            prefixes.add(f"value:{c}:")
            out.append(self.build_dimension(c))
            out.extend(self.build_values(c))
        kmap = {str(k["knowledge_id"]): k for k in self.knowledge}
        for kid in knowledge_ids:
            prefixes.add(f"knowledge:{kid}")
            if kid in kmap and (e := self.build_knowledge(kmap[kid])):
                out.append(e)
        if tables:
            prefixes.add("topic:")
            out.extend(self.build_topics())
        return out, prefixes
