"""
slim 全链路编排服务（写透版 §3.0）
①知识注入与查询改写 → ②四路召回 → ③受限反推+门校验 → ④LLM精判 → ⑤按选中裁剪导出
降级链：judge 失败/全空 → 回退纯召回候选导出（L2 逃生门）

从 endpoints.recall_slim 抽出，端点只做就绪检查与转发；
req 为 SlimRecallRequest（duck-typed，服务层不依赖 api schema）。
"""
import time
from typing import Optional

from app.services.recall_service import RecallService
from app.entity.scope import get_scope_resolver, AgentNotFound
from app.services.relation_registry import get_relation_registry
from app.services.slim_exporter import SlimExporter
from app.services.table_gate import TableGate
from app.services.llm_judge import LLMJudge, extract_selected_ids
from app.services.query_rewriter import get_query_rewriter
from app.utils.logger import get_logger

logger = get_logger("slim_pipeline", "api.log")


class SlimPipeline:
    def __init__(self, recall: RecallService, exporter: SlimExporter,
                 judge: Optional[LLMJudge]):
        self.recall = recall
        self.exporter = exporter
        self.judge = judge

    def run(self, req) -> dict:
        pipeline_log = []
        t_start = time.time()

        # ⓪ 范围解析：scope（内联）> agent_id > 全库
        scope = get_scope_resolver().resolve(agent_id=getattr(req, "agent_id", None),
                                             scope=getattr(req, "scope", None))
        pipeline_log.append({"step": "scope", **scope.summary()})

        # ① 知识注入与查询改写（召回用改写文本，judge 用原始 query）
        recall_query = req.query
        if req.use_rewrite:
            rw = get_query_rewriter()
            t_r = time.time()
            rewrite_out = rw.rewrite(req.query, use_llm=req.use_rewrite_llm,
                                     llm_client=self.judge.llm if self.judge else None)
            recall_query = rewrite_out["rewritten"]
            pipeline_log.append({"step": "rewrite", "level": rewrite_out["level"],
                                 "original": rewrite_out["original"],
                                 "rewritten": rewrite_out["rewritten"],
                                 "replacements": rewrite_out["replacements"],
                                 "knowledge": rewrite_out["knowledge"],
                                 "note": rewrite_out["note"],
                                 "prompt_rendered": rewrite_out.get("prompt_rendered"),
                                 "elapsed_ms": round((time.time() - t_r) * 1000, 1)})

        # ② 四路召回（向量用改写文本；复合意图拆多子查询取实体最大分）
        out = self.recall.recall(recall_query, parallel=req.parallel,
                                 intent_split=getattr(req, "intent_split", None), scope=scope)
        out["query"] = req.query  # 对外保留原始问题
        candidates = out["candidates"]

        # ③ 受限反推 + 门校验
        table_votes: dict = {}
        try:
            registry = get_relation_registry()
            votes, raw_votes = registry.table_votes(candidates, scope=scope)
            table_votes = dict(raw_votes or votes)
            gate = TableGate(registry)
            gated = gate.check(candidates, votes, raw_votes)
            candidates = gated["gated"]
            pipeline_log.append({"step": "gate", **gated["stats"]})
        except Exception as e:
            logger.warning(f"[slim] gate skipped: {e}")
            pipeline_log.append({"step": "gate", "error": str(e)})

        # ④⑤ LLM 精判 + 按选中裁剪导出；失败/全空回退旧路径（逃生门）
        slim = None
        if req.use_llm_judge:
            t_j = time.time()
            try:
                judgement = self.judge.judge(req.query, candidates)
                selected = extract_selected_ids(judgement)
                if selected and not judgement.get("fallback"):
                    slim = self.exporter.export(
                        candidates,
                        include_business_context=req.include_business_context,
                        dim_value_topn=req.dim_value_topn,
                        selected=selected, table_votes=table_votes, scope=scope)
                    slim["llm_judge"] = judgement
                    pipeline_log.append({"step": "judge+select", "mode": "judge_selected",
                                         "elapsed": round(time.time() - t_j, 2)})
                else:
                    pipeline_log.append({"step": "judge+select", "mode": "fallback_empty_or_fail"})
            except Exception as e:
                logger.error(f"[slim] judge fail in {time.time()-t_j:.2f}s: {e}")
                pipeline_log.append({"step": "judge+select", "error": str(e)})

        if slim is None:
            slim = self.exporter.export(candidates,
                                        include_business_context=req.include_business_context,
                                        dim_value_topn=req.dim_value_topn,
                                        table_votes=table_votes, scope=scope)
            slim["recall_stats"]["mode"] = "recall_only"

        slim["query"] = req.query
        slim["scope"] = scope.summary()
        slim["recall_query"] = recall_query  # 改写后实际用于向量检索的文本
        self.exporter.apply_output_switches(
            slim, out_table=req.out_table, out_metric=req.out_metric,
            out_dimension=req.out_dimension)
        # 召回候选明细（gate 后、实际用于导出的那批），供页面展示
        slim["recall_candidates"] = [
            c.to_dict() if hasattr(c, "to_dict") else dict(c) for c in candidates
        ]
        slim["timings_ms"] = out.get("timings_ms", {})
        slim["timings_ms"]["wall_total_ms"] = round((time.time() - t_start) * 1000, 1)
        slim["pipeline"] = pipeline_log

        db_meta = slim.get("database_meta", {})
        logger.info(
            f"[slim] Q={req.query!r} recall_text={recall_query!r} in={len(candidates)} -> "
            f"metrics={len(db_meta.get('available_metrics', []))} "
            f"dims={len(db_meta.get('available_dimensions', []))} "
            f"tables={len(db_meta.get('table_summaries', []))} "
            f"mode={slim['recall_stats'].get('mode')}"
        )
        return slim
