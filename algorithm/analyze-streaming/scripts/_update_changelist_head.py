# -*- coding: utf-8 -*-
"""更新 metadata改动清单.md 头部状态标注。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\工作\公司\项目\问数\web-json\analyze-streaming-v2"
P = ROOT + r"\docs\v1.0.2\metadata改动清单.md"

t = open(P, encoding="utf-8").read()
print("原文件长度:", len(t))
i = t.index("> **对象文件**")
j = t.index("> **追加句式统一为**")
head = (
    "> **状态**：✅ **已于 2026-09-15 全部应用到 database_meta_ddb2295c513a.txt**"
    "（应用脚本 scripts/_apply_migration.py；原文件备份 database_meta_ddb2295c513a.backup-20260915.txt；"
    "system_b/config/semantic_overrides.yaml 已退役清空，历史归档 semantic_overrides.yaml.retired-20260915.yaml）\n"
    ">\n"
    "> **对象文件**：docs/v1.0.2/database_meta_ddb2295c513a.txt（65 表 / 297 指标列 / 42 维度）\n"
    ">\n"
    "> **依据**：system_b/config/semantic_overrides.yaml 全部 92 条（90 overrides + 2 promote）"
    "逐条对照 metadata 诊断后的迁移方案（清单已退役，历史归档同目录）\n"
    ">\n"
    "> **改动总量**：63 行条目 → 实际落盘 61 处唯一变更"
    "（metrics 描述 58 行中 kwh_num、user_num 各对应两张表、共享同一 metrics 条目，声明写一次覆盖两表；"
    "列级描述 3 处；is_measure 2 处）\n\n"
)
open(P, "w", encoding="utf-8", newline="").write(t[:i] + head + t[j:])
print("头部已更新，新长度:", len(open(P, encoding="utf-8").read()))
