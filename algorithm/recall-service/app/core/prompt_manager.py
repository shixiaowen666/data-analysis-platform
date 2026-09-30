"""
提示词管理（可动态修改的 LLM 模板）
- 模板存 data/prompts/ 目录下的 md 文件（rewrite.md / judgment.md），改完即生效，无需重启
- 文件缺失或为空时，回退到代码内置默认模板（由 QueryRewriter / LLMJudge register_default 注入）
- 页面"恢复默认"= 把代码内置模板写回文件
"""
from typing import Dict, Optional
from app.config import DATA_DIR
from app.utils.logger import get_logger

logger = get_logger("prompt", "prompt.log")

PROMPT_DIR = DATA_DIR / "prompts"

# prompt_key -> 文件名
PROMPT_FILES = {
    "rewrite": "rewrite.md",
    "judge": "judgment.md",
    "enrich_metric": "enrich_metric.md",
    "enrich_dim": "enrich_dim.md",
    "enrich_table": "enrich_table.md",
    "enrich_alias": "enrich_alias.md",
}

# prompt_key -> 代码默认模板
# rewrite/judge 由服务模块 register_default 注入（避免循环依赖）；
# enrich_* 来自零依赖的 enrich_prompts，直接注册，保证 /api/prompts 无需先触发补全服务即可编辑
PROMPT_DEFAULTS: Dict[str, Optional[str]] = {
    "rewrite": None,
    "judge": None,
    "enrich_metric": None,
    "enrich_dim": None,
    "enrich_table": None,
    "enrich_alias": None,
}

try:
    from app.services.enrich_prompts import ENRICH_DEFAULTS as _ENRICH_DEFAULTS
    PROMPT_DEFAULTS.update(_ENRICH_DEFAULTS)
except ImportError:  # pragma: no cover
    pass

PROMPT_PLACEHOLDERS = {
    "rewrite": {
        "query": "用户原始问题",
        "knowledge": "检索到的业务知识条目文本（每行一条）",
    },
    "judge": {
        "query": "用户原始问题",
        "table_candidates": "表级候选列表文本",
        "metric_candidates": "指标候选列表文本",
        "dimension_candidates": "维度候选列表文本",
        "dim_value_candidates": "维度值候选列表文本",
        "derived_candidates": "派生指标/业务术语候选列表文本",
    },
    "enrich_metric": {
        "tables": "全库表清单（表名/中文名/用途）",
        "dimensions": "全库维度清单",
        "metrics": "全库指标清单",
        "output_scope": "本片要求输出的指标 code 列表",
    },
    "enrich_dim": {
        "tables": "全库表清单",
        "metrics": "全库指标清单",
        "dimensions": "全库维度清单",
    },
    "enrich_table": {
        "tables": "全库表清单（含字段）",
        "metrics_and_dimensions": "指标与维度全局清单",
    },
    "enrich_alias": {
        "knowledge": "业务知识条目（编号. 正文）",
        "names": "全库指标维度名称",
    },
}

PROMPT_TITLES = {
    "rewrite": "查询改写提示词（链路①）",
    "judge": "LLM 精判提示词（链路④）",
    "enrich_metric": "AI补全：指标描述生成",
    "enrich_dim": "AI补全：维度描述生成",
    "enrich_table": "AI补全：表描述生成",
    "enrich_alias": "AI补全：知识别名生成",
}


def register_default(key: str, template: str):
    PROMPT_DEFAULTS[key] = template


def _prompt_path(key: str):
    return PROMPT_DIR / PROMPT_FILES[key]


def _read_file(key: str) -> Optional[str]:
    try:
        return _prompt_path(key).read_text(encoding="utf-8")
    except FileNotFoundError:
        return None


def _write_file(key: str, content: str):
    PROMPT_DIR.mkdir(parents=True, exist_ok=True)
    _prompt_path(key).write_text(content, encoding="utf-8")
    logger.info(f"prompt '{key}' written to {PROMPT_FILES[key]} ({len(content)} chars)")


def get_prompt_template(key: str) -> Optional[str]:
    """取当前生效模板：文件内容优先；文件缺失/为空时回退代码默认模板"""
    if key not in PROMPT_FILES:
        return None
    content = _read_file(key)
    if content and content.strip():
        return content
    return PROMPT_DEFAULTS.get(key)


def list_prompts() -> Dict[str, dict]:
    out = {}
    for key in PROMPT_FILES:
        content = _read_file(key) or ""
        default = PROMPT_DEFAULTS.get(key) or ""
        out[key] = {
            "key": key,
            "title": PROMPT_TITLES.get(key, key),
            "content": content,
            "placeholders": PROMPT_PLACEHOLDERS.get(key, {}),
            "updated_at": None,
            "is_custom": content.strip() != default.strip() if default else bool(content.strip()),
        }
    return out


def update_prompt(key: str, content: str) -> bool:
    if key not in PROMPT_FILES:
        raise ValueError(f"未知提示词: {key}")
    if not content or not content.strip():
        raise ValueError("提示词内容不能为空")
    _write_file(key, content)
    return True


def reset_prompt(key: str) -> Optional[str]:
    """恢复代码默认模板（写回文件）"""
    default = PROMPT_DEFAULTS.get(key)
    if key not in PROMPT_FILES or default is None:
        raise ValueError(f"未知提示词: {key}")
    _write_file(key, default)
    return default
