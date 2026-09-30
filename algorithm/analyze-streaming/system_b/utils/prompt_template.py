"""
Lenient prompt template renderer
=================================
提示词模板渲染使用简单字段替换而非 str.format()。

原因：str.format() 要求模板中所有非占位符花括号都用 {{ }} 转义，
提示词版本内容由提示词管理页在线编辑，示例 JSON 普遍包含裸花括号，
漏转义会导致 KeyError 全量请求报错（线上事故 2026-08-30、2026-09-01）。
本渲染器只替换 {name} 形式的已知占位符，其余花括号一律原样保留，
同时兼容旧版模板中已转义的 {{ }} 写法（渲染为单花括号）。

占位符语义与 str.format() 的差异：不支持格式规格（{name:>10}）、
索引/属性访问（{data[0]}、{obj.attr}）；当前所有模板只用简单字段名，
如未来需要，请改用显式预处理而非恢复 str.format()。
"""

import re

# 已知占位符名集中注册：两侧调用方共用一个白名单，
# 避免各处散落字面量导致模板与代码不同步。
KNOWN_FIELDS = frozenset({
    # step_decomposer._build_prompts (prompt-main)
    "current_date", "database_meta_text", "functions_text",
    "query", "business_logic_knowledge",
    # dag_engine summarize (prompt-summary)
    "original_question", "glossary_section", "data_sections",
    "format_instruction", "emphasis_instruction",
})

_FIELD_RE = re.compile(r"\{(%s)\}" % "|".join(sorted(KNOWN_FIELDS)))


def render_template(template: str, **kwargs) -> str:
    """Render a prompt template by substituting only known {field} placeholders.

    - {known_field}          -> replaced by kwargs value (KeyError if missing)
    - {{anything}}           -> literal {anything} (back-compat with old escaped templates)
    - any other braces       -> kept verbatim (bare JSON in prompt examples)
    """
    missing = [k for k in _FIELD_RE.findall(template) if k not in kwargs]
    if missing:
        raise KeyError(
            f"模板占位符缺少实参: {sorted(set(missing))}，"
            f"调用方实参: {sorted(kwargs)}"
        )

    # 先还原旧版转义（{{ -> {），再替换占位符：
    # 这样填充值中若含 {{ 也不会被二次处理；旧版模板的嵌套 JSON 也能正确还原。
    text = template.replace("{{", "{").replace("}}", "}")

    def _replace(m: re.Match) -> str:
        name = m.group(1)
        if name in kwargs:
            return str(kwargs[name])
        return m.group(0)  # 已知字段名但未传值时不做替换

    return _FIELD_RE.sub(_replace, text)
