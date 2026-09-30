# -*- coding: utf-8 -*-
"""问题.txt 前 10 条 -> 逐条调用 /api/recall -> 输出 Markdown 报告"""
import json
import time
import urllib.request
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
QFILE = BASE / "docs" / "测试数据" / "问题.txt"
OUT = BASE / "docs" / "测试数据" / "recall_test_result_10.md"

questions = [l.strip() for l in QFILE.read_text(encoding="utf-8").splitlines() if l.strip()][:10]

lines = ["# 召回全流程测试报告（问题.txt 前 10 条）", ""]
ok = 0
for i, q in enumerate(questions, 1):
    t0 = time.time()
    req = urllib.request.Request(
        "http://localhost:5003/api/recall",
        data=json.dumps({"query": q, "use_llm_judge": False}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            data = json.loads(r.read().decode("utf-8"))
        cands = data.get("candidates", [])
        tm = data.get("timings_ms", {})
        ok += 1
        lines.append(f"## {i}. {q}")
        lines.append("")
        lines.append(
            f"- 候选数: {len(cands)} | 总耗时 {time.time() - t0:.2f}s"
            f"（编码 {tm.get('encode_ms', '?')}ms / 检索 {tm.get('retrieval_ms', '?')}ms / 合并 {tm.get('merge_ms', '?')}ms）"
        )
        lines.append(f"- 清洗后 query: {data.get('clean_query', '')} | 时间词: {data.get('time_terms', [])}")
        lines.append("- Top 5 候选:")
        for c in cands[:5]:
            lines.append(
                f"  - [{c['source']}] {c['entity_type']} | {c['display_name']} | score={c['score']:.4f}"
            )
        lines.append("")
        print(f"[{i}/10] OK candidates={len(cands)} {time.time() - t0:.2f}s")
    except Exception as e:
        lines.append(f"## {i}. {q}")
        lines.append("")
        lines.append(f"- 请求失败: {e}")
        lines.append("")
        print(f"[{i}/10] FAIL {e}")

lines.insert(1, f"测试时间: {time.strftime('%Y-%m-%d %H:%M:%S')} | 成功 {ok}/{len(questions)}")
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"done -> {OUT}")
