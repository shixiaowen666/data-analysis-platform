# -*- coding: utf-8 -*-
"""一次性脚本：把代码内置默认提示词模板导出到 data/prompts/*.md"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.services.query_rewriter import QueryRewriter
from app.services.llm_judge import LLMJudge

p = Path(__file__).resolve().parent.parent / "data" / "prompts"
p.mkdir(parents=True, exist_ok=True)
(p / "rewrite.md").write_text(QueryRewriter._LLM_PROMPT, encoding="utf-8")
(p / "judgment.md").write_text(LLMJudge.PROMPT, encoding="utf-8")
print("rewrite.md:", len(QueryRewriter._LLM_PROMPT), "chars")
print("judgment.md:", len(LLMJudge.PROMPT), "chars")
