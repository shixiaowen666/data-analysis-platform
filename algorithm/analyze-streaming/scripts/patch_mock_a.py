# -*- coding: utf-8 -*-
"""给 Mock System A 加故障注入开关 + 深圳月累计供电量测试数据（临时复现脚本，用后可删）"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

P = "mock_system_a/app.py"
s = open(P, encoding="utf-8", newline="").read()

# 1. import os
old = "import json\r\nimport logging\r\nimport sys"
new = "import json\r\nimport logging\r\nimport os\r\nimport sys"
assert old in s, "anchor1 (imports) missing"
s = s.replace(old, new, 1)

# 2. 故障开关全局变量 + compute_result 空响应注入
old = "_compute_results = []"
new = (
    "_compute_results = []\r\n"
    "\r\n"
    "# 故障注入开关：复现线上 System A 返回 200+空body 的场景。\r\n"
    "# 开启：curl -X POST http://127.0.0.1:5001/__toggle_empty_response -d '{\"enabled\": true}'\r\n"
    "_empty_response_mode = os.getenv(\"MOCK_A_EMPTY_RESPONSE\", \"0\") == \"1\"\r\n"
    "\r\n"
    "\r\n"
    "@app.route(\"/__toggle_empty_response\", methods=[\"POST\", \"GET\"])\r\n"
    "def toggle_empty_response():\r\n"
    "    global _empty_response_mode\r\n"
    "    if request.method == \"POST\":\r\n"
    "        data = request.get_json(silent=True) or {}\r\n"
    "        _empty_response_mode = bool(data.get(\"enabled\"))\r\n"
    "    return jsonify({\"empty_response_mode\": _empty_response_mode})\r\n"
)
assert old in s, "anchor2 (_compute_results) missing"
s = s.replace(old, new, 1)

# 3. receive_compute_result 开头注入空响应
old = (
    "def receive_compute_result():\r\n"
    "    \"\"\"\r\n"
    "    Endpoint for System B to push back computation results.\r\n"
    "    Stores the result for logging/auditing purposes.\r\n"
    "    \"\"\"\r\n"
    "    try:\r\n"
)
new = (
    "def receive_compute_result():\r\n"
    "    \"\"\"\r\n"
    "    Endpoint for System B to push back computation results.\r\n"
    "    Stores the result for logging/auditing purposes.\r\n"
    "    \"\"\"\r\n"
    "    if _empty_response_mode:\r\n"
    "        logger.warning(\"[MockA] FAULT-INJECTION: returning HTTP 200 with EMPTY body for /api/v1/compute_result\")\r\n"
    "        return \"\", 200\r\n"
    "    try:\r\n"
)
assert old in s, "anchor3 (receive_compute_result) missing"
s = s.replace(old, new, 1)

open(P, "w", encoding="utf-8", newline="").write(s)
print("app.py patched OK")

# ---- 数据注册：深圳月累计供电量 ----
P2 = "mock_system_a/data_registry_grid.py"
s2 = open(P2, encoding="utf-8", newline="").read()

entry = '''    # 深圳 2026年7月 月累计供电量（复现 月累计 查询场景）
    {
        "keywords": ["深圳", "供电量", "月累计", "累计供电量", "2026-07", "2026年7月"],
        "data": {
            "columns": [
                {"name": "ptdate", "data_type": "date", "description": "日期"},
                {"name": "daypowersupply", "data_type": "float", "description": "日供电量"},
                {"name": "monthpowersupply", "data_type": "float", "description": "月累计供电量"},
            ],
            "rows": [
                {"ptdate": "2026-07-01", "daypowersupply": 1200.5, "monthpowersupply": 1200.5},
                {"ptdate": "2026-07-02", "daypowersupply": 1180.2, "monthpowersupply": 2380.7},
                {"ptdate": "2026-07-31", "daypowersupply": 1250.8, "monthpowersupply": 38900.6},
            ],
        },
    },
'''

old = "DATA_REGISTRY = [\r\n"
new = "DATA_REGISTRY = [\r\n" + entry
assert old in s2, "anchor4 (DATA_REGISTRY head) missing"
s2 = s2.replace(old, new, 1)

open(P2, "w", encoding="utf-8", newline="").write(s2)
print("data_registry_grid.py patched OK")
