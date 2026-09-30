# -*- coding: utf-8 -*-
"""一次性脚本：统计 meta_metric / meta_dimension 的 synonyms 填充率"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.database import get_db

db = get_db()
for t in ["meta_metric", "meta_dimension"]:
    r = db.query(
        "SELECT COUNT(*) AS total, "
        "SUM(CASE WHEN synonyms IS NOT NULL AND synonyms != '' THEN 1 ELSE 0 END) AS filled "
        f"FROM {t}"
    )[0]
    print(f"{t}: total={r['total']} filled={r['filled']}")
    for x in db.query(
        f"SELECT display_name, synonyms FROM {t} "
        "WHERE synonyms IS NOT NULL AND synonyms != '' LIMIT 5"
    ):
        print(f"  sample: {x['display_name']} -> {x['synonyms']}")
