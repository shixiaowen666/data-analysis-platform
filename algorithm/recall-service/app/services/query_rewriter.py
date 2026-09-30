"""
知识注入与查询改写器（链路第①环，方案0）
对应《全链路方案》①知识注入与查询改写：
  - 同义词/别名/黑话映射           -> L1 字典改写（词表来自实体层 Catalog 的 synonyms）
  - 外部知识文本检索 + 大模型改写   -> L2（先从派生指标/业务知识库语义检索相关知识，
                                      再连同原问题交给 LLM 做指代消解与口语转术语）

关键约束：改写只用于召回（生成查询变体），judge 输入保留原始问题。
L2 任一环节失败自动降级 L1 结果（逃生门）。
"""
import json
import re
import time
from typing import Dict, List, Optional

from app import config
from app.core.database import get_db
from app.core.prompt_manager import register_default, get_prompt_template
from app.utils.logger import get_logger

logger = get_logger("query_rewriter", "query_rewriter.log")

try:
    from app.core.embedding import get_embedding_client
except Exception:  # pragma: no cover
    get_embedding_client = None


class QueryRewriter:
    def __init__(self):
        # synonym（小写）→ 标准名
        self._dict: Dict[str, str] = {}
        self._loaded = False
        # 知识库缓存：[(derived_id, display_name, description, aliases)]
        self._knowledge: List[dict] = []
        self._knowledge_loaded = False

    # ----------------------------------------------------------
    # 词表加载（L1）
    # ----------------------------------------------------------
    def load_dictionary(self):
        """从实体层（Catalog）加载同义词表：指标/维度 display_name + synonyms。
        剔除纯英文 code 类别名（对中文提问零贡献且拖慢最长匹配）。"""
        from app.entity.catalog import get_catalog
        d: Dict[str, str] = {}
        for m in get_catalog().by_key.values():
            if m["entity_type"] in ("metric", "dimension"):
                self._collect(d, m["display_name"], m.get("synonyms"))
        self._dict = {k: v for k, v in d.items() if not re.fullmatch(r"[a-z0-9_]+", k)}
        self._loaded = True
        logger.info(f"QueryRewriter dictionary loaded: {len(d)} synonyms")

    @staticmethod
    def _collect(d: Dict[str, str], display_name: str, synonyms):
        if not display_name:
            return
        std = display_name.lower()
        d[std] = display_name
        # display_name 常带单位后缀（如"转供电量(万千瓦时)"），需额外注册剥离单位的
        # 裸名，否则"昨天转供电量"里的裸名不受最长匹配保护，被短别名"供电量"内部替换
        bare = re.sub(r"[（(][^()（）]*[）)]\s*$", "", display_name).strip()
        if len(bare) >= 2 and bare.lower() != std:
            d[bare.lower()] = display_name
        if not synonyms:
            return
        items = synonyms if isinstance(synonyms, list) else str(synonyms).replace("、", ",").split(",")
        for s in items:
            s = str(s).strip()
            if s and s.lower() != std:  # 别名与标准名相同时跳过（避免"天→天"类无效替换）
                d[s.lower()] = display_name

    def _ensure_loaded(self):
        if not self._loaded:
            self.load_dictionary()

    # ----------------------------------------------------------
    # 知识库加载（L2 用）：业务知识/派生指标条目
    # ----------------------------------------------------------
    def load_knowledge(self):
        """从实体层源对象加载业务知识（src_knowledge）"""
        from app.entity.store import get_entity_store
        bc = get_entity_store().load_knowledge()
        self._knowledge = []
        for i, e in enumerate(bc):
            if isinstance(e, dict):
                aliases = e.get("knowledgeAlias") or []
                if isinstance(aliases, str):
                    aliases = [a.strip() for a in re.split(r"[、,，;；]", aliases) if a.strip()]
                text = (e.get("knowledgeElement") or "").strip()
            else:
                text = (e or "").strip()
                # 兼容旧字符串：【标签】正文 → 标签作别名
                m = re.match(r"^【([^】]+)】\s*(.+)$", text, re.S)
                aliases = [m.group(1).strip()] if m else []
                if m:
                    text = m.group(2).strip()
            if not text:
                continue
            name = aliases[0] if aliases else text[:12]
            if name not in aliases:
                aliases = [name] + aliases
            self._knowledge.append({
                "id": e.get("knowledge_id", i) if isinstance(e, dict) else i,
                "name": name,
                "description": text,
                "aliases": aliases,
            })
        self._knowledge_loaded = True
        logger.info(f"QueryRewriter knowledge loaded from entity store: {len(self._knowledge)} entries")

    def refresh(self, _keys=None):
        """SyncService listener"""
        self.load_dictionary()
        self.load_knowledge()

    def _ensure_knowledge(self):
        if not self._knowledge_loaded:
            self.load_knowledge()

    # ----------------------------------------------------------
    # L1：字典最长匹配替换（纯内存）
    # ----------------------------------------------------------
    def rewrite_l1(self, query: str):
        self._ensure_loaded()
        if not self._dict or not query:
            return query, []
        replaced: List[dict] = []
        spans = []
        lower = query.lower()
        for term in sorted(self._dict, key=len, reverse=True):
            start = 0
            while True:
                idx = lower.find(term, start)
                if idx < 0:
                    break
                end = idx + len(term)
                # 与已有替换区间不重叠
                if all(end <= s or idx >= e for s, e in spans):
                    std_name = self._dict[term]
                    if query[idx:end] == std_name:
                        # 原文已是标准名：占位区间，防止更短别名在标准名内部替换。
                        # 必须推进 start，否则 find 永远命中同一位置导致死循环。
                        spans.append((idx, end))
                        start = end
                        continue
                    spans.append((idx, end))
                    replaced.append({
                        "from": query[idx:end], "to": std_name,
                    })
                start = end
        if not spans:
            return query, []
        out = query
        for s, e in sorted(spans, reverse=True):
            std = self._dict[out[s:e].lower()]
            out = out[:s] + std + out[e:]
        return out, replaced

    # ----------------------------------------------------------
    # L2a：知识文本检索——两级：先词面命中（别名/描述含查询词），不足再语义检索
    # ----------------------------------------------------------
    def retrieve_knowledge(self, query: str, top_k: int = 3) -> List[dict]:
        """返回相关知识条目 [{id, name, description, aliases, score, via}]"""
        self._ensure_knowledge()
        if not self._knowledge or not query:
            return []
        hits = self._knowledge_keyword_hits(query)
        if len(hits) < top_k:
            semantic = self._knowledge_semantic_hits(query, top_k)
            seen = {h["id"] for h in hits}
            for s in semantic:
                if s["id"] not in seen:
                    hits.append(s)
                    seen.add(s["id"])
        hits.sort(key=lambda x: -x["score"])
        return hits[:top_k]

    def _knowledge_keyword_hits(self, query: str) -> List[dict]:
        hits = []
        ql = query.lower()
        for k in self._knowledge:
            best = 0.0
            for term in [k["name"]] + k["aliases"]:
                t = (term or "").strip().lower()
                if t and t in ql:
                    best = max(best, 0.95 - len(t) * 0.001)
            if best <= 0 and k["name"] and k["name"] in query:
                best = 0.9
            if best > 0:
                hits.append({**k, "score": round(best, 4), "via": "keyword"})
        return hits

    def _knowledge_semantic_hits(self, query: str, top_k: int) -> List[dict]:
        if get_embedding_client is None:
            return []
        try:
            embed = get_embedding_client()
            q_vec = embed.encode([query])[0]
        except Exception as e:
            logger.warning(f"knowledge semantic retrieval skipped: {e}")
            return []
        texts = [
            (k["name"] + "，" + k["description"] + "，别名：" + "、".join(k["aliases"])).strip("，")
            for k in self._knowledge
        ]
        try:
            k_vecs = embed.encode(texts)
        except Exception as e:
            logger.warning(f"knowledge semantic embed fail: {e}")
            return []
        import numpy as np
        qn, kn = np.array(q_vec), np.array(k_vecs)
        qn = qn / (np.linalg.norm(qn) + 1e-9)
        kn = kn / (np.linalg.norm(kn, axis=1, keepdims=True) + 1e-9)
        sims = kn @ qn
        order = np.argsort(-sims)[:top_k]
        out = []
        for i in order:
            if sims[i] < 0.35:  # 低分噪声不注入
                continue
            out.append({**self._knowledge[int(i)],
                        "score": round(float(sims[i]), 4), "via": "semantic"})
        return out

    # ----------------------------------------------------------
    # L2：LLM 改写（知识注入 + 指代消解 + 口语转术语；失败降级 L1）
    # ----------------------------------------------------------
    _LLM_PROMPT = """你是数据查询预处理模块。下面是用户问题、本次检索到的业务知识条目。
请把用户问题改写为规范的元数据检索查询。

## 规则
1. 利用"业务知识条目"中的别名/口径信息，把问题里的口语词、别名单向映射为标准指标/维度名
2. 消解指代（"它/该表/上面"），改写为明确实体名
3. 口语转术语（"卖了多少"→"销量"），禁止编造知识条目和系统元数据中不存在的指标名
4. 保留时间表达原样，不要展开计算
5. 只输出改写后的查询文本，不要任何解释

## 业务知识条目
{knowledge}

## 用户问题
{query}

改写后的查询："""

    def rewrite_l2(self, query: str, l1_result: str, llm_client,
                   knowledge: List[dict]) -> tuple:
        """返回 (改写结果, 实际使用的改写层级, 注入知识, 说明, 渲染后prompt)。失败时降级返回 L1。"""
        if not knowledge:
            return l1_result, "l1_fallback_no_knowledge", [], "未检索到相关知识，跳过 LLM", None
        kb_text = "\n".join(
            f"- {k['name']}（别名：{'、'.join(k['aliases']) or '无'}）：{k['description']}"
            for k in knowledge
        )
        try:
            template = get_prompt_template("rewrite") or self._LLM_PROMPT
            prompt = template.format(query=query, knowledge=kb_text)
            ans = llm_client.generate(prompt)
            rewritten = (ans or "").strip().strip('"')
            if not rewritten or len(rewritten) > 200:
                return l1_result, "l1_fallback_empty", knowledge, "LLM 输出为空/超长，降级 L1", prompt
            return rewritten, "l2", knowledge, "知识注入 + LLM 改写成功", prompt
        except Exception as e:
            logger.warning(f"L2 rewrite failed, fallback to L1: {e}")
            return l1_result, "l1_fallback_error", knowledge, f"LLM 调用失败：{e}", None

    # ----------------------------------------------------------
    # 主入口
    # ----------------------------------------------------------
    def rewrite(self, query: str, use_llm: bool = False, llm_client=None) -> dict:
        t0 = time.time()
        rewritten_l1, replaced = self.rewrite_l1(query)
        final, level, knowledge, note = rewritten_l1, "l1", [], "字典改写"
        prompt_rendered = None
        if use_llm and llm_client is not None:
            t_k = time.time()
            knowledge = self.retrieve_knowledge(query)
            t_k = (time.time() - t_k) * 1000
            final, level, knowledge, note, prompt_rendered = self.rewrite_l2(
                query, rewritten_l1, llm_client, knowledge)
        elapsed_ms = round((time.time() - t0) * 1000, 1)
        return {
            "original": query,
            "rewritten": final,
            "level": level,
            "note": note,
            "replacements": replaced,
            "knowledge": knowledge,
            "prompt_rendered": prompt_rendered,
            "elapsed_ms": elapsed_ms,
        }


_rewriter: Optional[QueryRewriter] = None


def get_query_rewriter() -> QueryRewriter:
    global _rewriter
    if _rewriter is None:
        _rewriter = QueryRewriter()
    return _rewriter


# 代码默认模板注册到提示词管理（data/prompts 文件缺失/为空时以此兜底）
register_default("rewrite", QueryRewriter._LLM_PROMPT)
