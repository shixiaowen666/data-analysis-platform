# -*- coding: utf-8 -*-
"""批量跑 15 类代表性问题（单进程内循环版，跳过已有结果）。

进度实时写 logs/_batch_v2_progress.txt，每题结果写 docs/v1.0.2/结果/<name>.json
"""
import io
import json
import os
import sys
import time
import traceback
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")
os.environ.setdefault("NO_PROXY", "dashscope.aliyuncs.com")

TEMPLATE = ROOT / "docs/v1.0.2/v1.0.5.txt"
META_TXT = ROOT / "docs/bug记录/月累计拆解死循环元数据缺列0915/database_meta_ddb2295c513a.txt"
OUT_DIR = ROOT / "docs/v1.0.2/结果"
PROGRESS = ROOT / "logs/_batch_v2_progress.txt"
OUT_DIR.mkdir(parents=True, exist_ok=True)

QUESTIONS = [
    ("01_供电量查询", "2026 年 7 月 20 日深圳、龙华、南山的工作日和周末的供电量同比有没有明显差异？周末跟工作日比是偏高还是偏低？"),
    ("03_光伏新能源", "2026-08-01 龙华供电局的光伏发电用户数是多少？"),
    ("04_线损率查询", "2026 年 8 月 1 日龙华供电局的分区线损率是多少？"),
    ("05_线路设备", "2026-08-01 坪山供电局的分线总数是多少？"),
    ("06_采集指标", "2026-08-01 光明供电局采集指标总在线率和总体数据完整率分别是多少？"),
    ("07_负荷查询", "2026-08-01 深圳供电量日最大负荷、月最大负荷、年最大负荷是多少？"),
    ("08_自动抄表率", "昨天深圳供电局自动抄表率是多少？"),
    ("09_区局对比", "2026 年 7 月深圳各区供电局的分线合格率和分台总合格率排名情况如何？"),
    ("10_趋势分析", "2026 年 3 月到 7 月，宝安供电局的日供电量变化趋势如何？"),
    ("11_节假日分析", "2026 年 7 月排除节假日后，深圳局每周五的日供电量均值是多少？"),
    ("12_电压等级", "2026-08-01 深圳供电局按电压等级统计的分压线损率分别是多少？"),
    ("13_市场化交易", "2026-07-10 深圳市场化交易用户数、优购用户数、电网代购用户数分别是多少？"),
    ("14_大客户", "2026-08-01 深圳市各行业用电量排名前五的行业是哪些？"),
    ("15_综合多指标", "2026-08-01 宝安、南山、福田三个供电局的月供电量、分区线损率、分线合格率、采集指标总在线率分别是多少？"),
]


def log(msg: str):
    line = f"{datetime.now().strftime('%H:%M:%S')} {msg}"
    print(line, flush=True)
    with open(PROGRESS, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def run_one(question: str, name: str):
    out = OUT_DIR / f"{name}.json"
    if out.exists():
        log(f"[skip] {name}")
        return
    t0 = time.time()
    try:
        _raw = TEMPLATE.read_text(encoding="utf-8")
        system_prompt, user_template = _raw.split("=====USER_TEMPLATE=====", 1)
        system_prompt, user_template = system_prompt.strip(), user_template.strip()

        from system_b.utils.meta_governance import merge_database_meta, render_database_meta
        from system_b.utils.prompt_template import render_template
        from system_b.computation.function_registry import get_functions_prompt_text

        database_meta = json.loads(META_TXT.read_text(encoding="utf-8"))
        database_meta_text = render_database_meta(merge_database_meta(database_meta))
        functions_text = get_functions_prompt_text()
        business_context = database_meta.get("business_context") or "无特定业务知识。"

        user_prompt = render_template(
            user_template,
            current_date=datetime.now().date().isoformat(),
            database_meta_text=database_meta_text,
            functions_text=functions_text,
            query=question,
            business_logic_knowledge=business_context,
        )
        (OUT_DIR / f"_rendered_{name}.txt").write_text(
            system_prompt + "\n=====USER_TEMPLATE=====\n" + user_prompt, encoding="utf-8")

        from openai import OpenAI
        API_KEY = os.environ.get("LLM_API_KEY", "sk-bcebd07345bc4c6ca6b38c029d6a9113")
        BASE_URL = os.environ.get("LLM_API_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
        MODEL = os.environ.get("LLM_MODEL", "qwen3-235b-a22b-instruct-2507")
        client = OpenAI(api_key=API_KEY, base_url=BASE_URL)
        resp = client.chat.completions.create(
            model=MODEL,
            messages=[{"role": "system", "content": system_prompt},
                      {"role": "user", "content": user_prompt}],
            temperature=0, max_tokens=4096, stream=True,
            extra_body={"chat_template_kwargs": {"enable_thinking": False}},
        )
        chunks, ttft = [], None
        for chunk in resp:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta
            if delta and delta.content:
                if ttft is None:
                    ttft = time.time() - t0
                chunks.append(delta.content)
        full_text = "".join(chunks)
        elapsed = time.time() - t0

        try:
            response_field = json.loads(full_text)
        except json.JSONDecodeError:
            response_field = full_text

        # R4: 对齐生产校验器规则十九——query步骤必须至少含一个measure列
        measure_violation = []
        if isinstance(response_field, dict):
            for st in response_field.get("steps", []):
                if st.get("step_type") == "query":
                    cols = st.get("params", {}).get("expected_columns", [])
                    if cols and not any(c.get("role") == "measure" for c in cols):
                        measure_violation.append(st.get("step_id"))
        record = {
            "measure_violation": measure_violation,
            "category": name, "question": question, "model": MODEL,
            "prompt_template": "docs/v1.0.2/v1.0.5.txt",
            "meta_source": META_TXT.name,
            "meta_rendered_chars": len(database_meta_text),
            "user_prompt_chars": len(user_prompt),
            "started_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "elapsed_sec": round(elapsed, 1),
            "ttft_sec": round(ttft, 3) if ttft else None,
            "response_text": response_field,
        }
        out.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
        log(f"[OK  ] {name}  {elapsed:.1f}s  -> {out.name}")
    except Exception as e:
        log(f"[FAIL] {name}  {type(e).__name__}: {e}")
        with open(PROGRESS, "a", encoding="utf-8") as f:
            f.write(traceback.format_exc() + "\n")


def main():
    if PROGRESS.exists():
        PROGRESS.unlink()
    log("BATCH START")
    for name, q in QUESTIONS:
        run_one(q, name)
    log("BATCH DONE")


if __name__ == "__main__":
    main()
