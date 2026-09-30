# -*- coding: utf-8 -*-
"""切换 system_a_client.py 空响应防御代码的启用/禁用，用于复现与验证（临时脚本，用后可删）。
用法：
  python scripts/toggle_system_a_fix.py off   # 移除空响应防御 -> 复现原始 bug
  python scripts/toggle_system_a_fix.py on    # 恢复空响应防御
"""
import io
import shutil
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

TARGET = "system_b/communication/system_a_client.py"
BACKUP = TARGET + ".fixed.bak"

GUARD = (
    "            if not resp.text.strip():\r\n"
    "                # 线上 System A 对该接口存在返回 200+空body 的情况（真实环境已确认），\r\n"
    "                # 直接 resp.json() 会抛 JSONDecodeError: Expecting value\r\n"
    "                logger.warning(\"[SystemAClient] Compute result sync: HTTP 200 but empty response body\")\r\n"
    "                return {\"status\": \"failed\", \"error_message\": \"System A returned 200 with empty response body\"}\r\n"
)

mode = sys.argv[1] if len(sys.argv) > 1 else ""
if mode not in ("on", "off"):
    print("usage: toggle_system_a_fix.py on|off")
    sys.exit(1)

if mode == "on":
    if not __import__("os").path.exists(BACKUP):
        print("backup missing, nothing to restore")
        sys.exit(1)
    shutil.copyfile(BACKUP, TARGET)
    print("fix ON (restored from backup)")
else:
    s = open(TARGET, encoding="utf-8", newline="").read()
    if GUARD not in s:
        print("guard not found (already off?)")
        sys.exit(1)
    # 首次关闭前备份含修复的版本
    if not __import__("os").path.exists(BACKUP):
        shutil.copyfile(TARGET, BACKUP)
    s = s.replace(GUARD, "", 1)
    open(TARGET, "w", encoding="utf-8", newline="").write(s)
    print("fix OFF (guard removed, backup saved)")
