"""
门校验器（链路第④环）：对表级召回做挂靠合法性三态过滤。
对应《全链路方案写透版》§3.4。

三态：
  PASS    —— 挂靠合法，原样通过
  DEMOTE  —— 疑似邻家干扰：表未直接命中，是靠表内实体"借道"进来的，降权排序
  REJECT  —— 直接命中的表与实体集无挂靠关系（越权票），移出主列表（保留在 rejected 供审计）

设计要点：
  - 门校验只处理"表"实体；指标/维度实体的合法性由其 legal_tables 反映，
    在反推与 slim 裁剪环节消费，不在门口拦截。
  - score 不篡改，排序权重单独输出（gate_rank），保证可解释与可回滚。
"""
from typing import Dict, List, Set

from app.services.relation_registry import RelationRegistry
from app.utils.logger import path_logger

log_gate = path_logger("gate")


class TableGate:
    def __init__(self, registry: RelationRegistry, demote_weight: float = 0.5):
        self.registry = registry
        self.demote_weight = demote_weight

    def check(self, candidates: List[dict], table_votes: Dict[int, float],
              raw_votes: Dict[int, float] = None) -> dict:
        """
        candidates: 合并去重后的候选列表（含 table 与 metric/dimension/dim_value）
        table_votes: 低分抑制后的表分 {table_id: vote}
        raw_votes: 全量加权表分（未抑制低分）。提供时三态判定：
          PASS   —— 有抑制票（强关联）
          DEMOTE —— 无抑制票但有原始票（弱关联，降权保留）
          REJECT —— 两种票都没有（越权票，移出主列表，保留在 rejected 供审计）
        返回 {gated: [...], demoted_ids, rejected: [...], stats}
        - gated 候选每条附加 gate 状态与 gate_rank
        """
        raw_votes = raw_votes or {}
        gated, rejected = [], []
        demoted_ids: Set[int] = set()
        n_pass = n_demote = n_reject = 0

        for c in candidates:
            et, eid = c.get("entity_type"), c.get("entity_id")
            c = dict(c)  # 不改原候选

            if et != "table":
                # 非表实体：孤儿标记，供下游降权与审计
                if self.registry.is_orphan(et, eid):
                    c["orphan"] = True
                gated.append(c)
                continue

            votes = table_votes.get(eid, 0.0)
            raw = raw_votes.get(eid, 0.0)
            if votes > 0:
                c["gate"] = "PASS"
                c["gate_rank"] = float(c.get("score", 0.0)) + 0.1 * min(votes, 5.0)
                n_pass += 1
                gated.append(c)
            elif raw > 0:
                # 无强票但全量票面有支撑：弱关联（如低分直命中、仅靠低分实体借道），
                # 降权保留而非拒之门外——直命中的表宁可降权也不能丢
                c["gate"] = "DEMOTE"
                c["gate_reason"] = "weak_votes"
                c["gate_rank"] = float(c.get("score", 0.0)) * self.demote_weight
                demoted_ids.add(eid)
                n_demote += 1
                gated.append(c)
            else:
                # 完全无实体票支撑的表：召回直命中但实体集不挂靠 → 越权票
                c["gate"] = "REJECT"
                c["gate_reason"] = "no_entity_votes"
                rejected.append(c)
                n_reject += 1

        # 孤儿实体统一降权（数据问题在修复前不应参与排序竞争）
        for c in gated:
            if c.pop("orphan", False):
                c["gate"] = c.get("gate") or "DEMOTE"
                c["gate_rank"] = float(c.get("score", 0.0)) * self.demote_weight
                n_demote += 1

        gated.sort(key=lambda x: x.get("gate_rank", x.get("score", 0.0)), reverse=True)

        stats = {"pass": n_pass, "demote": n_demote, "reject": n_reject}
        log_gate.info(f"gate: {stats} rejected={[r['display_name'] for r in rejected[:5]]}")
        return {"gated": gated, "rejected": rejected, "stats": stats}
