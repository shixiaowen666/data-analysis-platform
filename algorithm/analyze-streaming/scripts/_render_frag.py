# -*- coding: utf-8 -*-
"""确认 dq_power_month/year 与 distribution_hscust_cust 三列新旧渲染的完整 JSON 片段。"""
import io
import json
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

import system_b.utils.meta_governance as mg
from system_b.utils.meta_governance import merge_database_meta, render_database_meta

OLD = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.backup-20260915.txt")
NEW = os.path.join(ROOT, "docs", "v1.0.2", "database_meta_ddb2295c513a.txt")

r_old = render_database_meta(merge_database_meta(json.load(open(OLD, encoding="utf-8"))))
mg._overrides_cache = {"overrides": [], "promote_to_measure": []}
try:
    r_new = render_database_meta(merge_database_meta(json.load(open(NEW, encoding="utf-8"))))
finally:
    mg._overrides_cache = None

TARGETS = ["distribution_hscust_cust", "dq_power_month", "dq_power_year"]
for name in TARGETS:
    print(f"===== {name} =====")
    for label, r in (("旧(清单模式)", r_old), ("新(无清单)", r_new)):
        m = re.search(r'\{"name":"%s"[^\}]*\}' % re.escape(name), r)
        print(f"  {label}: {m.group(0) if m else '(未找到)'}")
    print()
