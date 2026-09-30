# -*- coding: utf-8 -*-
import io
import sys
from collections import Counter

import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

d = yaml.safe_load(open(r"system_b/config/semantic_overrides.yaml", encoding="utf-8"))
ovr = d.get("overrides") or []
pro = d.get("promote_to_measure") or []
print("overrides 条数:", len(ovr))
print("promote_to_measure 条数:", len(pro))
print()
print("promote_to_measure 全部内容:")
print(yaml.dump(pro, allow_unicode=True))
print("overrides agg_type 分布:", dict(Counter(o.get("agg_type") for o in ovr)))
print("涉及表数:", len(set(o.get("table") for o in ovr)))
print()
print("全部 status 值:", dict(Counter(o.get("status") for o in ovr)))
