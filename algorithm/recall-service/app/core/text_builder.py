"""
EmbeddingTextBuilder —— 与方案 2.2 对齐
为各类元数据实体生成用于向量化的文本
"""
from typing import List, Optional


class EmbeddingTextBuilder:

    @staticmethod
    def build_table_text(t: dict) -> str:
        parts = [
            f"数据表：{t['display_name']}（{t['table_name']}）",
            f"描述：{t['description']}",
        ]
        if t.get("business_topics"):
            parts.append(f"业务主题：{'、'.join(t['business_topics'])}")
        if t.get("metric_names"):
            parts.append(f"包含指标：{'、'.join(t['metric_names'][:30])}")
        if t.get("dimension_names"):
            parts.append(f"包含维度：{'、'.join(t['dimension_names'][:30])}")
        if t.get("typical_questions"):
            parts.append(f"常见问法：{'；'.join(t['typical_questions'][:5])}")
        return "。".join(parts)

    @staticmethod
    def build_metric_text(m: dict) -> str:
        parts = [
            f"指标：{m['display_name']}（{m['metric_name']}）",
            f"描述：{m['description']}",
        ]
        if m.get("business_topics"):
            parts.append(f"业务主题：{'、'.join(m['business_topics'])}")
        if m.get("caliber_scope") and m["caliber_scope"] not in ("none", "", None):
            cmap = {
                "local": "地调（地区调度）",
                "unified": "统调（统一调度）",
                "central": "中调（中级调度）",
            }
            label = cmap.get(m["caliber_scope"], m["caliber_scope"])
            parts.append(f"调度口径：{label}")
        if m.get("source_table"):
            parts.append(f"所属数据表：{m['source_table']}")
        if m.get("synonyms"):
            syn = m["synonyms"] if isinstance(m["synonyms"], list) else [m["synonyms"]]
            parts.append(f"别名：{'、'.join(syn)}")
        if m.get("calculation"):
            parts.append(f"计算方式：{m['calculation']}")
        if m.get("unit"):
            parts.append(f"单位：{m['unit']}")
        if m.get("related_scenarios"):
            parts.append(f"应用场景：{'、'.join(m['related_scenarios'][:3])}")
        if m.get("typical_questions"):
            parts.append(f"常见问法：{'；'.join(m['typical_questions'][:5])}")
        return "。".join(parts)

    @staticmethod
    def build_dimension_text(d: dict) -> str:
        parts = [
            f"维度：{d['display_name']}（{d['dimension_name']}）",
            f"描述：{d['description']}",
        ]
        if d.get("synonyms"):
            syn = d["synonyms"] if isinstance(d["synonyms"], list) else [d["synonyms"]]
            parts.append(f"别名：{'、'.join(syn)}")
        if d.get("sample_values"):
            parts.append(f"可选值示例：{'、'.join(d['sample_values'][:10])}")
        return "。".join(parts)

    @staticmethod
    def build_dim_value_text(v: dict) -> str:
        parts = [
            f"维度值：{v['display_name']}",
            f"所属维度：{v.get('dimension_display_name', '')}",
        ]
        if v.get("description"):
            parts.append(f"描述：{v['description']}")
        if v.get("synonyms"):
            syn = v["synonyms"] if isinstance(v["synonyms"], list) else [v["synonyms"]]
            parts.append(f"别名：{'、'.join(syn)}")
        return "。".join(parts)

    @staticmethod
    def build_topic_text(t: dict) -> str:
        parts = [
            f"业务主题：{t['topic_name']}",
            f"描述：{t['description']}",
        ]
        if t.get("typical_queries"):
            parts.append(f"用户常见问法：{'；'.join(t['typical_queries'][:8])}")
        if t.get("child_topics"):
            parts.append(f"子主题：{'、'.join(t['child_topics'])}")
        return "。".join(parts)

    @staticmethod
    def build_derived_metric_text(d: dict) -> str:
        parts = [
            f"业务术语：{d['display_name']}",
            f"含义：{d['description']}",
            f"计算口径：{d['calculation_rule']}",
        ]
        if d.get("aliases"):
            al = d["aliases"] if isinstance(d["aliases"], list) else [d["aliases"]]
            parts.append(f"别名：{'、'.join(al)}")
        if d.get("dependent_entities"):
            parts.append(f"依赖字段：{'、'.join(d['dependent_entities'])}")
        return "。".join(parts)
