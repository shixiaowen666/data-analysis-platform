# -*- coding: utf-8 -*-
"""一次性脚本：prompt_manager 文件存储 update/reset 往返测试"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# 先导入 services 触发 register_default（与服务启动链路一致）
import app.services.query_rewriter  # noqa: F401
import app.services.llm_judge  # noqa: F401

from app.core.prompt_manager import (
    PROMPT_DIR, get_prompt_template, update_prompt, reset_prompt,
)

key = "rewrite"

# 固定到已知状态：先 reset，确保拿到的是代码默认模板
reset_prompt(key)
orig = get_prompt_template(key)
assert orig and "{query}" in orig and "{knowledge}" in orig, "默认模板异常"

update_prompt(key, "测试内容 {query} {knowledge}")
assert get_prompt_template(key) == "测试内容 {query} {knowledge}", "update 后读取不一致"

reset_prompt(key)
assert get_prompt_template(key) == orig, "reset 后未还原"

# 空文件回退：模拟用户写空文件
p = PROMPT_DIR / "rewrite.md"
p.write_text("", encoding="utf-8")
assert get_prompt_template(key) == orig, "空文件未回退默认模板"

# 还原现场
p.write_text(orig, encoding="utf-8")
assert get_prompt_template(key) == orig, "最终还原失败"
print("ALL PASS: reset-seed / update / reset / empty-fallback / restore")
