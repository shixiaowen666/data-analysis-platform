"""
四路并行召回服务（路径 A / B / C / D）
严格遵循技术方案 §五：
  - 路径 A：先召回 Top-N 张表，再在每张表内召回指标/维度/维度值
  - 路径 B：主题召回 → 主题下全部实体
  - 路径 C：全局指标+维度兜底
  - 路径 D：派生指标 + 依赖的指标/维度/维度值

日志：
  - 每路在自己专属 logger（path_a/b/c/d.log）输出召回明细
  - 主 logger 输出耗时与候选数
"""
import re
import time
import json
import numpy as np
from dataclasses import dataclass, asdict
from typing import List, Optional, Tuple, Dict, Any
from concurrent.futures import ThreadPoolExecutor

from app import config
from app.core.embedding import EmbeddingClient, get_embedding_client
from app.core.reranker import get_rerank_client
from app.core.config_manager import RecallConfigManager, PathConfig, get_config_manager
from app.entity.catalog import Catalog, ResolvedScope, get_catalog
from app.entity.index_store import IndexStore
from app.services.intent_splitter import split_intents
from app.services.query_rewriter import get_query_rewriter
from app.utils.logger import get_logger, path_logger

main_logger = get_logger("recall.service", "recall.log")
log_a = path_logger("path_a")
log_b = path_logger("path_b")
log_c = path_logger("path_c")
log_d = path_logger("path_d")
log_merge = path_logger("merge")


@dataclass
class RecallResult:
    entity_type: str
    entity_id: int
    display_name: str
    description: str
    score: float
    source: str
    table_id: Optional[int] = None
    dimension_id: Optional[int] = None
    # 英文标识名（表为 table_name，其余实体对应 code 名），供前端展示/判定匹配
    entity_name: Optional[str] = None
    # 完整挂靠表集合（merge 时由 RelationRegistry 补全 ∪ 各路径观测，见写透版炸弹1）
    legal_tables: Optional[List[int]] = None
    # cross-encoder 重排分（未参与重排/重排关闭时为 None）
    rerank_score: Optional[float] = None
    # 稳定业务键（table:{name} / metric:{code}[#variant] / dimension:{code} / value:{dim}:{v} / knowledge:{id}）
    entity_key: Optional[str] = None

    def to_dict(self):
        d = asdict(self)
        if d.get("legal_tables") is None:
            d["legal_tables"] = []
        return d


def _entity_id_of(entity: Dict) -> Optional[int]:
    et = entity.get("entity_type")
    key_map = {
        "table": "table_id", "metric": "metric_id",
        "dimension": "dimension_id", "dim_value": "value_id",
        "topic": "topic_id", "derived_metric": "derived_id",
    }
    key = key_map.get(et)
    if key is None:
        return None
    val = entity.get(key)
    return int(val) if val is not None else None


def _apply_recall_mode(scores: np.ndarray, ids: np.ndarray,
                       cfg: PathConfig) -> List[Tuple[int, float]]:
    """根据召回模式过滤 (id, score) 序列"""
    pairs = [(int(i), float(s)) for i, s in zip(ids, scores) if i >= 0]
    return _apply_mode_pairs(pairs, cfg)


def _apply_mode_pairs(pairs: List[Tuple[int, float]], cfg: PathConfig) -> List[Tuple[int, float]]:
    if cfg.recall_mode == "top_k":
        return pairs[:cfg.top_k]
    elif cfg.recall_mode == "threshold":
        return [(i, s) for i, s in pairs if s >= cfg.threshold]
    elif cfg.recall_mode == "hybrid":
        filtered = [(i, s) for i, s in pairs if s >= cfg.threshold]
        if filtered:
            return filtered[:cfg.top_k]
        return pairs[:cfg.top_k]
    return pairs[:cfg.top_k]


class RecallService:
    # QUERY_INSTRUCT 已移除：实测 bge-small-zh-v1.5 下指令前缀把无关英文 code
    # 指标（data_from 等）相似度抬高到 0.71，正确指标从全局第 1 跌到 287 名

    _TIME_PATTERNS = [
        r"今[天日]", r"昨[天日]", r"明[天日]",
        r"本[周月年季度]", r"上[周月年]", r"下[周月年]",
        r"近\d+[天日周月年]", r"过去\d+[天日周月年]",
        r"\d{4}[-/年]\d{1,2}([-/月]\d{1,2})?[日号]?",
        r"\d{1,2}月份?", r"\d{1,2}月\d{1,2}[日号]",
        r"第[一二三四1234]季度",
        r"\d{1,2}月前\d+天", r"前\d+[天日]",
        r"去年", r"今年", r"明年",
    ]

    def __init__(self,
                 embed_client: EmbeddingClient = None,
                 index: IndexStore = None,
                 catalog: Catalog = None,
                 config_manager: RecallConfigManager = None):
        self.embed = embed_client or get_embedding_client()
        self.index = index
        self.catalog = catalog or get_catalog()
        self.cfg_mgr = config_manager or get_config_manager()
        self.score_decay_b = config.SCORE_DECAY_PATH_B
        self.score_decay_d = config.SCORE_DECAY_PATH_D

    # ---- 命中 → RecallResult ----
    def _result(self, fid: int, score: float, source: str, table_hint: str = None) -> Optional[RecallResult]:
        m = self.catalog.meta(fid)
        if m is None:
            return None
        et = m["entity_type"]
        legal_names = m["legal_tables"]
        legal_ids = sorted(self.catalog.table_id_of_name[n] for n in legal_names if n in self.catalog.table_id_of_name)
        table_id = None
        if et == "table":
            table_id = fid
        elif table_hint and table_hint in self.catalog.table_id_of_name:
            table_id = self.catalog.table_id_of_name[table_hint]
        elif legal_ids:
            table_id = legal_ids[0]
        dim_id = self.catalog.dim_id_of_code.get(m["dimension_code"]) if et == "dim_value" else None
        out_type = "derived_metric" if et == "knowledge" else et
        return RecallResult(
            entity_type=out_type, entity_id=fid, display_name=m["display_name"] or "",
            description=m.get("description") or "", score=score, source=source,
            table_id=table_id, dimension_id=dim_id,
            entity_name=(m["table_name"] if et == "table" else m["code"]),
            legal_tables=legal_ids, entity_key=m["entity_key"],
        )

    def _strip_time(self, query: str) -> Tuple[str, List[str]]:
        time_terms = []
        clean = query
        for pat in self._TIME_PATTERNS:
            for m in re.finditer(pat, clean):
                time_terms.append(m.group(0))
            clean = re.sub(pat, " ", clean)
        clean = re.sub(r"\s+", " ", clean).strip()
        return clean or query, time_terms

    def encode_query(self, query: str) -> Tuple[np.ndarray, str, List[str]]:
        clean_query, time_terms = self._strip_time(query)
        query_text = clean_query
        vec = self.embed.encode([query_text])
        return vec, clean_query, time_terms

    def _intent_sub_queries(self, clean_query: str,
                            enabled: Optional[bool] = None) -> List[str]:
        """复合意图拆分：返回 [完整查询, 子查询...]；未启用/无多锚点时为 [完整查询]。
        完整查询作为基准轮保留全句上下文，子查询轮补足被代表意图压制的实体。"""
        base = [clean_query]
        if enabled is False or (enabled is None and not config.INTENT_SPLIT_ENABLED):
            return base
        if not clean_query:
            return base
        try:
            rewriter = get_query_rewriter()
            rewriter._ensure_loaded()
            subs = split_intents(clean_query, rewriter._dict)
        except Exception as e:
            main_logger.warning(f"intent split skipped: {e}")
            return base
        limit = max(1, config.INTENT_SPLIT_MAX_QUERIES) - 1
        subs = [t for t in subs if t != clean_query][:limit]
        if subs:
            main_logger.info(f"intent split: {clean_query!r} -> {base + subs}")
        return base + subs

    def recall(self, query: str, parallel: bool = True,
               rewritten_query: str = None,
               intent_split: Optional[bool] = None,
               scope: ResolvedScope = None) -> Dict[str, Any]:
        """rewritten_query：改写后查询（链路①注入点）。召回向量用改写文本，
        返回结果中保留原始 query，judge 输入用原文（写透版 §3.1 关键约束）。
        intent_split：复合意图拆分开关，None=跟随全局配置。
        拆分时每个子查询独立走四路召回，实体得分跨轮取最大
        （复用 _merge_and_deduplicate 的 (type,id) 去重取最大分语义）。"""
        t0 = time.time()
        if scope is None:
            scope = self.catalog.resolve_scope(None)
        recall_text = rewritten_query or query
        query_vec, clean_query, time_terms = self.encode_query(recall_text)

        sub_texts = self._intent_sub_queries(clean_query, intent_split)
        if len(sub_texts) > 1:
            # 每个向量必须是 (1, dim) 二维：FAISS search 要求 (n, dim)，一维会崩
            extra = self.embed.encode(sub_texts[1:])
            sub_vecs = [query_vec] + [extra[i:i + 1] for i in range(extra.shape[0])]
        else:
            sub_vecs = [query_vec]
        t1 = time.time()

        path_a: List[RecallResult] = []
        path_b: List[RecallResult] = []
        path_c: List[RecallResult] = []
        path_d: List[RecallResult] = []
        for vec in sub_vecs:
            if parallel:
                # 每次调用独立池：多个请求/批测并发时互不抢占线程配额，
                # 并行度 = 并发请求数 × 4（共享池会把全局并行度恒定卡在 4）
                with ThreadPoolExecutor(max_workers=4) as pool:
                    futA = pool.submit(self._path_a_recall, vec, scope)
                    futB = pool.submit(self._path_b_recall, vec, scope)
                    futC = pool.submit(self._path_c_recall, vec, scope)
                    futD = pool.submit(self._path_d_recall, vec, scope)
                path_a.extend(futA.result())
                path_b.extend(futB.result())
                path_c.extend(futC.result())
                path_d.extend(futD.result())
            else:
                path_a.extend(self._path_a_recall(vec, scope))
                path_b.extend(self._path_b_recall(vec, scope))
                path_c.extend(self._path_c_recall(vec, scope))
                path_d.extend(self._path_d_recall(vec, scope))
        t2 = time.time()

        merged = self._merge_and_deduplicate(path_a, path_b, path_c, path_d)
        merged = self._annotate_legal_tables(merged, scope)
        t3 = time.time()
        reranked_n = self._rerank(merged, clean_query)
        t4 = time.time()

        timings = {
            "encode_ms":   round(1000 * (t1 - t0), 2),
            "retrieval_ms":round(1000 * (t2 - t1), 2),
            "merge_ms":    round(1000 * (t3 - t2), 2),
            "rerank_ms":   round(1000 * (t4 - t3), 2),
            "total_ms":    round(1000 * (t4 - t0), 2),
        }

        main_logger.info(
            f"Q={query!r} | clean={clean_query!r} | subs={len(sub_texts)} | time_terms={time_terms} | "
            f"A={len(path_a)} B={len(path_b)} C={len(path_c)} D={len(path_d)} "
            f"merged={len(merged)} reranked={reranked_n} | enc={timings['encode_ms']}ms "
            f"ret={timings['retrieval_ms']}ms mer={timings['merge_ms']}ms "
            f"rr={timings['rerank_ms']}ms"
        )

        return {
            "query": query,
            "recall_text": recall_text,
            "clean_query": clean_query,
            "time_terms": time_terms,
            "intent_subs": sub_texts if len(sub_texts) > 1 else [],
            "scope": scope.summary(),
            "candidates": [r.to_dict() for r in merged],
            "by_path": {
                "path_a": [r.to_dict() for r in path_a],
                "path_b": [r.to_dict() for r in path_b],
                "path_c": [r.to_dict() for r in path_c],
                "path_d": [r.to_dict() for r in path_d],
            },
            "timings_ms": timings,
        }

    # ============ 路径 A ============
    def _path_a_recall(self, query_vec: np.ndarray, scope: ResolvedScope) -> List[RecallResult]:
        results: List[RecallResult] = []
        cfg_table = self.cfg_mgr.get("path_a_table", "table")
        if not cfg_table.enabled or self.index is None:
            log_a.info("path_a disabled or no index")
            return results

        # Stage1: 表级（scope 内）
        k_search = max(cfg_table.top_k, 50)
        hits = self.index.search("table", query_vec, k_search, scope.allowed("table"))
        recalled = _apply_mode_pairs(hits, cfg_table)
        log_a.info(f"== Stage1 表级召回 mode={cfg_table.recall_mode} top_k={cfg_table.top_k} "
                   f"th={cfg_table.threshold:.4f} scope={scope.label}")
        recalled_tables: List[str] = []
        for fid, score in recalled:
            r = self._result(fid, score, "path_a")
            if r is None:
                continue
            results.append(r)
            recalled_tables.append(r.entity_name)
            log_a.info(f"  [table] {r.entity_name:30s} ({r.display_name}) score={score:.4f}")

        # Stage2: 表内三类实体（该表成员 ∩ scope）
        cfg_metric = self.cfg_mgr.get("path_a_entity", "metric")
        cfg_dim = self.cfg_mgr.get("path_a_entity", "dimension")
        cfg_dv = self.cfg_mgr.get("path_a_entity", "dim_value")
        log_a.info(f"== Stage2 表内召回 metric(top_k={cfg_metric.top_k},on={cfg_metric.enabled}) "
                   f"| dim(top_k={cfg_dim.top_k},on={cfg_dim.enabled}) "
                   f"| dim_value(top_k={cfg_dv.top_k},on={cfg_dv.enabled})")
        for tn in recalled_tables:
            for et, cfg in (("metric", cfg_metric), ("dimension", cfg_dim), ("dim_value", cfg_dv)):
                if not cfg.enabled:
                    continue
                members = self.catalog.members(tn, et)
                allowed = scope.allowed(et)
                if allowed is not None:
                    members &= allowed
                if not members:
                    continue
                hits = self.index.search(et, query_vec, max(cfg.top_k, 20), members)
                for fid, sc in _apply_mode_pairs(hits, cfg):
                    r = self._result(fid, sc, "path_a", table_hint=tn)
                    if r is None:
                        continue
                    results.append(r)
                    log_a.info(f"  [{et}] table={tn} {r.display_name} score={sc:.4f}")
        return results

    # ============ 路径 B ============
    def _path_b_recall(self, query_vec: np.ndarray, scope: ResolvedScope) -> List[RecallResult]:
        results: List[RecallResult] = []
        cfg = self.cfg_mgr.get("path_b_topic", "topic")
        if not cfg.enabled or self.index is None or self.index.ntotal("topic") == 0:
            log_b.info("path_b disabled or no topic index")
            return results
        hits = self.index.search("topic", query_vec, max(cfg.top_k, 20))
        kept = _apply_mode_pairs(hits, cfg)
        log_b.info(f"== 主题召回 mode={cfg.recall_mode} top_k={cfg.top_k} th={cfg.threshold}")
        for fid, score in kept:
            topic = self.catalog.meta(fid)
            if topic is None:
                continue
            log_b.info(f"  [topic] {topic['display_name']} score={score:.4f}")
            # 主题 → 其绑定表（∩ scope）→ 表 + 表内指标/维度
            for tn in sorted(topic["legal_tables"]):
                if not scope.unrestricted and tn not in scope.tables:
                    continue
                tid = self.catalog.table_id(tn)
                if tid is None:
                    continue
                r = self._result(tid, score * self.score_decay_b, "path_b")
                if r:
                    results.append(r)
                for et in ("metric", "dimension"):
                    members = self.catalog.members(tn, et)
                    allowed = scope.allowed(et)
                    if allowed is not None:
                        members &= allowed
                    for mid in sorted(members):
                        r = self._result(mid, score * self.score_decay_b, "path_b", table_hint=tn)
                        if r:
                            results.append(r)
        return results

    # ============ 路径 C ============
    def _path_c_recall(self, query_vec: np.ndarray, scope: ResolvedScope) -> List[RecallResult]:
        results: List[RecallResult] = []
        cfg_metric = self.cfg_mgr.get("path_c_global", "metric")
        cfg_dim = self.cfg_mgr.get("path_c_global", "dimension")
        if not (cfg_metric.enabled or cfg_dim.enabled) or self.index is None:
            log_c.info("path_c disabled or no index")
            return results
        log_c.info(f"== 全局兜底 metric(top_k={cfg_metric.top_k},th={cfg_metric.threshold}) "
                   f"| dim(top_k={cfg_dim.top_k},th={cfg_dim.threshold}) scope={scope.label}")
        for et, cfg in (("metric", cfg_metric), ("dimension", cfg_dim)):
            if not cfg.enabled:
                continue
            hits = self.index.search(et, query_vec, max(cfg.top_k, 50), scope.allowed(et))
            for fid, sc in _apply_mode_pairs(hits, cfg):
                r = self._result(fid, sc, "path_c")
                if r is None:
                    continue
                results.append(r)
                log_c.info(f"  [{et}] {r.display_name} score={sc:.4f}")
        return results

    # ============ 路径 D ============
    def _path_d_recall(self, query_vec: np.ndarray, scope: ResolvedScope) -> List[RecallResult]:
        """业务知识（原派生指标）：命中知识条目本身；知识不绑定实体，故不再展开依赖"""
        results: List[RecallResult] = []
        cfg_derived = self.cfg_mgr.get("path_d_derived", "derived_metric")
        if not cfg_derived.enabled or self.index is None or self.index.ntotal("knowledge") == 0:
            log_d.info("path_d disabled or no knowledge index")
            return results
        hits = self.index.search("knowledge", query_vec, max(cfg_derived.top_k, 30))
        kept = _apply_mode_pairs(hits, cfg_derived)
        log_d.info(f"== 业务知识召回 mode={cfg_derived.recall_mode} top_k={cfg_derived.top_k} "
                   f"th={cfg_derived.threshold}")
        for fid, sc in kept:
            r = self._result(fid, sc, "path_d")
            if r is None:
                continue
            results.append(r)
            log_d.info(f"  [knowledge] {r.display_name} score={sc:.4f}")
        return results

    # ============ 合并去重 ============
    def _merge_and_deduplicate(self, *result_lists) -> List[RecallResult]:
        seen: Dict[Tuple[str, int], RecallResult] = {}
        for lst in result_lists:
            for r in lst:
                if r.entity_id is None:
                    continue
                key = (r.entity_type, r.entity_id)
                if key not in seen:
                    seen[key] = r
                else:
                    cur = seen[key]
                    # 合并各路径观测到的挂靠表（写透版炸弹1：单路径只见局部绑定）
                    if r.table_id is not None:
                        cur_obs = cur.legal_tables or ([cur.table_id] if cur.table_id else [])
                        if r.table_id not in cur_obs:
                            cur_obs.append(r.table_id)
                        cur.legal_tables = cur_obs
                    if r.score > cur.score:
                        cur.score = r.score
                        cur.source = r.source
                        if r.table_id is not None:
                            cur.table_id = r.table_id
        merged = sorted(seen.values(), key=lambda x: x.score, reverse=True)
        log_merge.info(f"merged total={len(merged)}; "
                       f"by_type={{ {', '.join(f'{t}:{sum(1 for r in merged if r.entity_type==t)}' for t in ['table','metric','dimension','dim_value','derived_metric'])} }}")
        return merged

    def _annotate_legal_tables(self, merged: List[RecallResult], scope: ResolvedScope) -> List[RecallResult]:
        """legal_tables 已在 _result 中由 Catalog 给出（全库挂靠）；这里按 scope 收缩，
        并保证 table_id 落在 scope 内（范围外的表不能作为归属表）"""
        if scope.unrestricted:
            return merged
        allowed_tables = scope.ids.get("table", set())
        for r in merged:
            if r.entity_type == "table":
                continue
            r.legal_tables = sorted(t for t in (r.legal_tables or []) if t in allowed_tables)
            if r.table_id is not None and r.table_id not in allowed_tables:
                r.table_id = r.legal_tables[0] if r.legal_tables else None
        return merged

    # ============ Cross-Encoder 重排 ============
    @staticmethod
    def _candidate_text(r: RecallResult) -> str:
        """重排文档文本：与 text_builder.py 入库口径同风格（中文标签+名称+code+描述）"""
        if r.entity_type == "table":
            return f"数据表：{r.display_name}（{r.entity_name or ''}）。描述：{r.description or ''}"
        if r.entity_type == "metric":
            return f"指标：{r.display_name}（{r.entity_name or ''}）。描述：{r.description or ''}"
        if r.entity_type == "dimension":
            return f"维度：{r.display_name}（{r.entity_name or ''}）。描述：{r.description or ''}"
        if r.entity_type == "dim_value":
            return f"维度值：{r.display_name}。描述：{r.description or ''}"
        if r.entity_type == "derived_metric":
            return f"业务术语：{r.display_name}。含义：{r.description or ''}"
        return f"{r.display_name}。{r.description or ''}"

    def _rerank(self, merged: List[RecallResult], query_text: str) -> int:
        """
        对合并后候选前 RERANK_TOP_N 条做 cross-encoder 重排：
          - 候选顺序按 rerank_score 重新排列，score 字段保持原语义（下游阈值体系依赖）
          - 服务不可用/关闭时静默降级，返回 0；成功返回参与重排的候选数
        """
        if not config.RERANK_ENABLED or not merged:
            return 0
        top_n = min(config.RERANK_TOP_N, len(merged))
        candidates = merged[:top_n]
        rest = merged[top_n:]
        try:
            client = get_rerank_client()
        except Exception as e:
            main_logger.warning(f"rerank client init failed, skip rerank: {e}")
            return 0
        scores = client.rerank(query_text, [self._candidate_text(r) for r in candidates])
        if scores is None:
            return 0
        for r, s in zip(candidates, scores):
            r.rerank_score = s if s >= 0 else None
        ranked = sorted(candidates, key=lambda r: (r.rerank_score if r.rerank_score is not None
                                                   else float("-inf")), reverse=True)
        merged[:] = ranked + rest
        main_logger.info(
            f"rerank done: n={top_n} | top3="
            + "; ".join(f"{r.display_name}({r.rerank_score:.3f})" for r in ranked[:3])
        )
        return top_n
