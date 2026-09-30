# -*- coding: utf-8 -*-
"""给 mock_system_a/app.py 的 DATABASE_META 补充 table_summaries（线上 meta 结构，
含 ptdate/monthpowersupply 列，用于激活月累计查询匹配与 few-shot 路径）。临时脚本，用后可删。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

P = "mock_system_a/app.py"
s = open(P, encoding="utf-8", newline="").read()

old = '    "table_summaries": [],\r\n'
new = (
    '    "table_summaries": [\r\n'
    "        {\r\n"
    '            "table_name": "dwd_power_supply_day",\r\n'
    '            "description": "供电量日表：日供电量与月/年累计供电量",\r\n'
    '            "columns": [\r\n'
    '                {"column_name": "ptdate", "data_type": "date", "description": "日期"},\r\n'
    '                {"column_name": "daypowersupply", "data_type": "float", "description": "日供电量"},\r\n'
    '                {"column_name": "monthpowersupply", "data_type": "float", "description": "月累计供电量"},\r\n'
    '                {"column_name": "yearpowersupply", "data_type": "float", "description": "年累计供电量"},\r\n'
    "            ],\r\n"
    "        },\r\n"
    "    ],\r\n"
)
assert old in s, "table_summaries anchor missing"
s = s.replace(old, new, 1)
open(P, "w", encoding="utf-8", newline="").write(s)
print("table_summaries patched OK")
