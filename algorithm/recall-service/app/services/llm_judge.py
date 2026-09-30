"""
LLM 精判（链路第⑤环）：从召回候选集中筛选与 query 真正相关的实体。
对应《全链路方案写透版》§3.5。

相比旧版的三处关键改造：
1. 候选行带 entity_id 与表归属（含同名消歧提示）——LLM 靠名称无法区分同名实体
2. 输出 selected_* 改为 [{id, name}] 结构——下游 slim 裁剪按 id 落地，名称只做对账
3. 后置校验兜底：id 不在候选集中的输出剔除；全空/异常时回退召回候选（逃生门）

输入用原始 query（不改写文本），召回才用改写文本——两侧职责分离。
"""
import json
from typing import List, Optional
from app.core.llm import LLMClient, get_llm_client
from app.core.prompt_manager import register_default, get_prompt_template
from app.utils.logger import path_logger


class LLMJudge:
    PROMPT = """你是一个数据分析助手。用户提出了一个数据查询问题，
我已经从数据库中初步筛选了一批可能相关的数据元素。
请你根据用户的问题，从候选列表中选出所有与用户问题相关的数据元素。

## 规则
1. 宁可多选，不要漏掉，但是要避免明显不相关的元素
2. 注意调度口径区分（地调/统调/中调），用户明确指定的口径才选对应指标，未指定的不带任何后缀
3. 维度值只在用户明确提到或暗示时才选取（例如"广东"才选维度值"广东"）
4. 如果候选中有"业务术语/派生指标"，优先选择，并保留它依赖的底层字段
5. 时间相关维度（日期、月份、季度、年度）一般不需要选取（时间已在外层处理）
6. 对于"计数"类问题（如"跳闸次数"），应选择对应表的主键/计数字段作为指标
7. 同名候选出现在多张表时（标注了所属表），根据问题语义选所属表正确的那个；无法判断时全部保留
8. 每个输出项必须带上候选列表中标注的 id，否则视为无效
9. 成对性约束：选出的指标和维度必须能同时存在于同一张表（参考每项标注的所属表）。
   若指标所在表没有某维度，不要选该维度，除非其他选中表包含它；找不到任何公共表时，
   优先保留指标、放弃维度，不要生成字段来源冲突的组合
10. 维度值必须属于你选中的某个维度（参考维值的所属维度），选了维度值就必须同时选它所属的维度

## 用户问题
{query}

## 候选数据表
{table_candidates}

## 候选指标（已按相关度排序）
{metric_candidates}

## 候选维度
{dimension_candidates}

## 候选维度值（仅当用户明确提到才选）
{dim_value_candidates}

## 候选业务术语 / 派生指标
{derived_candidates}

## 输出（必须是合法 JSON，仅返回 JSON，不要任何其它文字）
{{
  "selected_tables":     [{{"id": 1, "name": "表名1"}}],
  "selected_metrics":    [{{"id": 12, "name": "指标名1"}}],
  "selected_dimensions": [{{"id": 5, "name": "维度名1"}}],
  "selected_dim_values": [{{"id": 33, "name": "维度值1"}}],
  "selected_derived":    [{{"id": 2, "name": "派生指标名1"}}],
  "reasoning": "简要说明选择理由"
}}"""

    def __init__(self, llm_client: LLMClient = None):
        self.llm = llm_client or get_llm_client()
        self.logger = path_logger("judge")

    # ----------------------------------------------------------
    # 候选行格式化：带 id 与表归属
    # ----------------------------------------------------------
    @staticmethod
    def _format_line(c: dict, tables_by_id: dict) -> str:
        line = (f"  - [id={c['entity_id']}] 【{c['display_name']}】"
                f"{c.get('description','')[:80]} (score={c.get('score',0):.3f}, src={c.get('source','')})")
        if c["entity_type"] == "table":
            tname = tables_by_id.get(c["entity_id"], "")
            if tname:
                line += f" (table_name={tname})"
        else:
            legal = c.get("legal_tables") or []
            names = [tables_by_id.get(t, str(t)) for t in legal[:5]]
            if names:
                suffix = f" 等共{len(legal)}张表" if len(legal) > 5 else ""
                line += f" (所属表: {'、'.join(names)}{suffix})"
            else:
                line += " (所属表: 未知/孤儿)"
        return line

    def judge(self, query: str, candidates: List[dict], top_per_bucket: int = 30) -> dict:
        # id → 表名（表归属展示与输出对账都要用）
        tables_by_id = {c["entity_id"]: c["display_name"]
                        for c in candidates if c["entity_type"] == "table"}

        buckets = {"table": [], "metric": [], "dimension": [], "dim_value": [], "derived_metric": []}
        for c in candidates:
            bucket = buckets.get(c["entity_type"])
            if bucket is not None:
                bucket.append(self._format_line(c, tables_by_id))

        limits = {"table": top_per_bucket, "metric": top_per_bucket,
                  "dimension": top_per_bucket, "dim_value": 50, "derived_metric": 20}
        sections = {
            "table_candidates": "table", "metric_candidates": "metric",
            "dimension_candidates": "dimension", "dim_value_candidates": "dim_value",
            "derived_candidates": "derived_metric",
        }
        fmt = {k: ("\n".join(buckets[v][:limits[v]]) if buckets[v] else "（无）")
               for k, v in sections.items()}

        template = get_prompt_template("judge") or self.PROMPT
        prompt = template.format(query=query, **fmt)
        prompt_vars = {"query": query, **fmt}
        try:
            ans = self.llm.generate_json(prompt)
        except Exception as e:
            self.logger.error(f"judge fail: {e}; query={query}")
            return {
                "selected_tables": [], "selected_metrics": [], "selected_dimensions": [],
                "selected_dim_values": [], "selected_derived": [],
                "reasoning": f"LLM_FAIL:{e}", "fallback": True,
                "prompt_rendered": prompt, "prompt_vars": prompt_vars,
            }
        out = self._validate(ans, candidates)
        out["prompt_rendered"] = prompt
        out["prompt_vars"] = prompt_vars
        return out

    # ----------------------------------------------------------
    # 后置校验：id 对账 + 空结果兜底
    # ----------------------------------------------------------
    VALID_KEYS = {
        "selected_tables": "table", "selected_metrics": "metric",
        "selected_dimensions": "dimension", "selected_dim_values": "dim_value",
        "selected_derived": "derived_metric",
    }

    def _validate(self, ans: dict, candidates: List[dict]) -> dict:
        valid_ids = {(c["entity_type"], c["entity_id"]): c for c in candidates}
        out, dropped = {}, []
        for key, et in self.VALID_KEYS.items():
            items = ans.get(key) or []
            cleaned = []
            for it in items:
                if isinstance(it, dict):
                    eid, name = it.get("id"), it.get("name")
                elif isinstance(it, (int, str)) and str(it).isdigit():
                    eid, name = int(it), str(it)  # LLM 偷懒只给 id 时兼容
                else:
                    eid, name = None, it
                hit = valid_ids.get((et, eid)) if eid is not None else None
                if hit is None and name:
                    # id 缺失/错误时按名称对账（同名多表时可能命中多条，全保留由下游消歧）
                    hit = next((c for c in candidates
                                if c["entity_type"] == et and c["display_name"] == name), None)
                if hit is not None:
                    cleaned.append({"id": hit["entity_id"], "name": hit["display_name"],
                                    "legal_tables": hit.get("legal_tables") or []})
                else:
                    dropped.append(f"{key}:{name or eid}")
            out[key] = cleaned
        out["reasoning"] = ans.get("reasoning", "")
        if dropped:
            out["dropped_invalid"] = dropped
            self.logger.warning(f"judge dropped invalid refs: {dropped[:10]}")
        n_selected = sum(len(out[k]) for k in self.VALID_KEYS)
        if n_selected == 0:
            out["fallback"] = True  # 全空时调用方应回退召回候选（逃生门）
            self.logger.warning("judge selected nothing, fallback flag set")
        self.logger.info(f"judge: tables={len(out['selected_tables'])} "
                         f"metrics={len(out['selected_metrics'])} dims={len(out['selected_dimensions'])} "
                         f"vals={len(out['selected_dim_values'])} derived={len(out['selected_derived'])} "
                         f"fallback={out.get('fallback', False)}")
        return out


def extract_selected_ids(judgement: dict) -> dict:
    """judge 输出 → {entity_type: set(entity_id)}，供 slim 裁剪消费"""
    key_map = {
        "selected_tables": "table", "selected_metrics": "metric",
        "selected_dimensions": "dimension", "selected_dim_values": "dim_value",
        "selected_derived": "derived_metric",
    }
    ids: dict = {}
    for key, et in key_map.items():
        s = set()
        for it in judgement.get(key) or []:
            if isinstance(it, dict) and isinstance(it.get("id"), int):
                s.add(it["id"])
        if s:
            ids[et] = s
    return ids


# 代码默认模板注册到提示词管理（data/prompts 文件缺失/为空时以此兜底）
register_default("judge", LLMJudge.PROMPT)
