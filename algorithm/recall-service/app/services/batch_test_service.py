"""
批量测试服务：Excel 导入 → 并发召回 → 命中判定 → 统计/导出/历史

流程：
  1. 解析测试集 xlsx（6 列模板：题号/问题/期望指标/期望表/期望维度/期望维度值）
  2. ThreadPoolExecutor 并发调用 slim pipeline（精判可选，默认关闭）
  3. 命中判定：期望集合 ⊆ 实际集合（归一化包含匹配），4 个期望列全部命中才算行命中
  4. 结果落盘 data/batch_runs/，保留历史可回看
"""
import io
import json
import re
import string
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Dict, List, Optional

from app.utils.logger import get_logger

logger = get_logger("batch_test", "batch_test.log")

# 任务结果在内存中最多保留个数
MAX_RUNS = 20

TEMPLATE_COLUMNS = ["题号", "问题", "期望指标", "期望表", "期望维度", "期望维度值"]
EXPECT_KEYS = ["metric", "table", "dimension", "dim_value"]
EXPECT_LABELS = {
    "metric": "期望指标", "table": "期望表", "dimension": "期望维度",
    "dim_value": "期望维度值",
}
# 期望列 -> slim 导出中实际集合的取值来源（list_key, name_field, judge_selected_key）
ACTUAL_FIELDS = {
    "metric": ("available_metrics", "metric_name", "selected_metrics"),
    "dimension": ("available_dimensions", "dimension_name", "selected_dimensions"),
}

_RUNS: Dict[str, Dict] = {}
_LOCKS: Dict[str, threading.Lock] = {}
_LOCK = threading.Lock()


def _lock_of(run_id: str) -> threading.Lock:
    with _LOCK:
        if run_id not in _LOCKS:
            _LOCKS[run_id] = threading.Lock()
        return _LOCKS[run_id]


# ----------------------------------------------------------
# 名称归一化与集合解析
# ----------------------------------------------------------
def _norm(s: Any) -> str:
    """名称归一化：全角转半角、小写、去空白与下划线连字符"""
    s = str(s or "")
    tbl = {ord(c) + 0xFEE0: ord(c) for c in string.ascii_letters + string.digits}
    s = s.translate(tbl)
    s = s.lower()
    return re.sub(r"[\s_\-·．.]+", "", s)


def parse_name_set(raw: Any) -> List[str]:
    """期望单元格 -> 名称列表，按 、 ， , ; / 空白 拆分"""
    if raw is None:
        return []
    if isinstance(raw, (list, tuple)):
        return [str(x).strip() for x in raw if str(x).strip()]
    parts = re.split(r"[、，,;；/|\s]+", str(raw))
    return [p.strip() for p in parts if p.strip()]


def _contains_any(name: str, name_set: List[str]) -> bool:
    """命中规则：期望名与实际名归一化后互相包含（前缀式子串）"""
    n = _norm(name)
    if not n:
        return False
    for e in name_set:
        en = _norm(e)
        if en and (en in n or n in en):
            return True
    return False


def parse_expect(case: Dict[str, Any]) -> Dict[str, List[str]]:
    exp = case.get("expect") or {}
    return {k: parse_name_set(exp.get(k)) for k in EXPECT_KEYS}


def _recall_index(slim: dict) -> Dict[str, dict]:
    """召回候选索引：norm(名称) -> {score, types}，用于给实际集合标注得分、解释缺失。
    中文名与英文标识名（entity_name，如表 table_name）都入索引，两套命名都能查到分数。"""
    idx: Dict[str, dict] = {}

    def _add(name: str, sc, et):
        k = _norm(name)
        if not k:
            return
        cur = idx.get(k)
        if cur is None:
            idx[k] = {"score": sc if isinstance(sc, (int, float)) else None,
                      "types": {et}}
            return
        if isinstance(sc, (int, float)) and (
                cur["score"] is None or sc > cur["score"]):
            cur["score"] = sc
        cur["types"].add(et)

    for c in (slim.get("recall_candidates") or []):
        if not isinstance(c, dict):
            continue
        et = c.get("entity_type")
        sc = c.get("score")
        _add(str(c.get("display_name") or ""), sc, et)
        _add(str(c.get("entity_name") or ""), sc, et)
    return idx


# ----------------------------------------------------------
# Excel 解析
# ----------------------------------------------------------
class ParseError(Exception):
    pass


def parse_xlsx(data: bytes, filename: str = "") -> Dict:
    """解析测试集 xlsx，返回 {total, cases, errors, filename}"""
    import openpyxl

    try:
        wb = openpyxl.load_workbook(io.BytesIO(data), read_only=True, data_only=True)
    except Exception as e:
        raise ParseError(f"无法解析 Excel 文件：{e}")
    ws = wb.worksheets[0]
    rows = list(ws.iter_rows(values_only=True))
    if not rows:
        raise ParseError("Excel 内容为空")

    header = [str(c).strip() if c is not None else "" for c in rows[0]]
    missing = [c for c in TEMPLATE_COLUMNS if c not in header]
    if missing:
        raise ParseError(f"模板表头缺少列：{ '、'.join(missing) }（需包含 {'/'.join(TEMPLATE_COLUMNS)}）")
    idx = {c: header.index(c) for c in TEMPLATE_COLUMNS}

    cases, errors = [], []
    for i, row in enumerate(rows[1:], start=2):
        if row is None or all(c is None or str(c).strip() == "" for c in row):
            continue
        def cell(col):
            v = row[idx[col]] if idx[col] < len(row) else None
            if v is None:
                return ""
            if isinstance(v, float) and v.is_integer():
                return str(int(v))
            return str(v).strip()
        no = cell("题号")
        q = cell("问题")
        if not q:
            errors.append({"row": i, "msg": "问题为空，已跳过"})
            continue
        case = {
            "no": no or str(i - 1),
            "query": q,
            "expect": {k: cell(EXPECT_LABELS[k]) for k in EXPECT_KEYS},
        }
        cases.append(case)

    if not cases:
        raise ParseError("未解析到有效用例（问题列全为空）")
    return {"total": len(cases), "cases": cases, "errors": errors,
            "filename": filename or "测试问题.xlsx"}


# ----------------------------------------------------------
# 命中判定
# ----------------------------------------------------------
def _actual_sets(slim: dict) -> Dict[str, List[str]]:
    """从 slim 导出中提取各列实际名称集合"""
    db_meta = slim.get("database_meta") or {}
    judge = slim.get("llm_judge") or {}
    actual = {}

    def _names_of_selected(key):
        return [x.get("name", "") for x in (judge.get(key) or []) if isinstance(x, dict)]

    def _pick(list_key, field, selected_key):
        # 精判开启且有选中实体时，以精判选中为准；否则用导出全集
        if judge.get(selected_key):
            return _names_of_selected(selected_key)
        items = db_meta.get(list_key) or []
        return [str(it.get(field, "")) for it in items if isinstance(it, dict)]

    m = ACTUAL_FIELDS["metric"]
    actual["metric"] = _pick(m[0], m[1], m[2])
    d = ACTUAL_FIELDS["dimension"]
    actual["dimension"] = _pick(d[0], d[1], d[2])

    # 表：全部表摘要
    tables = [str(t.get("table_name", "")) for t in (db_meta.get("table_summaries") or [])
              if isinstance(t, dict)]
    actual["table"] = tables

    # 维度值：精判选中优先；否则扫 possible_values
    dv = _names_of_selected("selected_dim_values")
    if not dv:
        dv = []
        for dim in (db_meta.get("available_dimensions") or []):
            if not isinstance(dim, dict):
                continue
            for v in (dim.get("possible_values") or []):
                dv.append(str(v))
    actual["dim_value"] = dv
    return actual


def judge_case(case: dict, slim: dict) -> dict:
    """单条用例命中判定，返回明细（含每列命中状态）"""
    expect = parse_expect(case)
    actual = _actual_sets(slim)
    # 召回候选索引：给实际集合标注相似度分数（精判选中的实体在候选中查不到则无分）
    idx = _recall_index(slim)

    def _scores(names: List[str]) -> Dict[str, float]:
        out = {}
        for n in names:
            c = idx.get(_norm(n))
            if c and isinstance(c.get("score"), (int, float)):
                out[n] = round(c["score"], 4)
        return out

    score_map = {k: _scores(actual.get(k) or []) for k in EXPECT_KEYS}

    def _sort_by_score(names: List[str], sc: Dict[str, float]) -> List[str]:
        # 有分的按分数降序；无分的保持原顺序排在后面
        decorated = [(n, sc.get(n)) for n in names]
        decorated.sort(key=lambda t: (t[1] is None, -(t[1] or 0)))
        return [n for n, _ in decorated]

    for k in EXPECT_KEYS:
        actual[k] = _sort_by_score(actual.get(k) or [], score_map[k])

    cols, all_hit = {}, True

    for key in EXPECT_KEYS:
        exp = expect[key]
        act = actual.get(key) or []
        if not exp:
            hit = True  # 该列无期望 → 不参与判定
            missing = []
        else:
            missing = [e for e in exp if not _contains_any(e, act)]
            hit = not missing
        if exp and not hit:
            all_hit = False
        cols[key] = {"expect": exp, "hit": hit, "missing": missing}

    return {
        "no": case["no"],
        "query": case["query"],
        "status": "OK",
        "error": None,
        "hit": all_hit,
        "cols": cols,
        "actual": {
            "metric": actual["metric"], "dimension": actual["dimension"],
            "dim_value": actual["dim_value"], "tables": actual["table"],
        },
        "actual_scores": {
            "metric": score_map["metric"],
            "dimension": score_map["dimension"],
            "dim_value": score_map["dim_value"],
            "tables": score_map["table"],
        },
        "mode": (slim.get("recall_stats") or {}).get("mode", ""),
        "recall_query": str(slim.get("recall_query") or ""),  # 改写后实际用于向量检索的文本
    }


def _build_stats(details: List[dict], elapsed_s: float) -> dict:
    total = len(details)
    ok_rows = [d for d in details if d["status"] == "OK"]
    hits = [d for d in ok_rows if d["hit"]]
    col_stats = {}
    for key in EXPECT_KEYS:
        with_exp = [d for d in ok_rows if d["cols"][key]["expect"]]
        hit_n = sum(1 for d in with_exp if d["cols"][key]["hit"])
        col_stats[EXPECT_LABELS[key]] = {
            "total": len(with_exp),
            "hit": hit_n,
            "rate": round(hit_n / len(with_exp), 4) if with_exp else None,
        }
    mss = sorted(d["wall_ms"] for d in ok_rows)
    def _pct(p):
        if not mss:
            return None
        i = min(len(mss) - 1, int(round(p / 100 * (len(mss) - 1))))
        return mss[i]
    return {
        "total": total,
        "ok": len(ok_rows),
        "failed": total - len(ok_rows),
        "hit": len(hits),
        "hit_rate": round(len(hits) / len(ok_rows), 4) if ok_rows else None,
        "cols": col_stats,
        "avg_ms": round(sum(mss) / len(mss)) if mss else None,
        "p95_ms": _pct(95),
        "elapsed_s": round(elapsed_s, 1),
    }


# ----------------------------------------------------------
# 运行管理（内存 + 落盘历史）
# ----------------------------------------------------------
def _runs_dir() -> Path:
    d = Path(__file__).resolve().parent.parent.parent / "data" / "batch_runs"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _persist(run: dict):
    try:
        p = _runs_dir() / f"{run['run_id']}.json"
        p.write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")
    except Exception as e:
        logger.warning(f"[batch] persist failed: {e}")


def list_runs(limit: int = MAX_RUNS) -> List[dict]:
    """历史列表（新→旧），从磁盘读取"""
    items = []
    for p in sorted(_runs_dir().glob("*.json"), reverse=True)[:limit]:
        try:
            d = json.loads(p.read_text(encoding="utf-8"))
            st = d.get("stats") or {}
            items.append({
                "run_id": d["run_id"], "created_at": d.get("created_at"),
                "filename": d.get("filename"), "total": st.get("total"),
                "hit": st.get("hit"), "hit_rate": st.get("hit_rate"),
                "status": d.get("status"), "use_judge": d.get("use_judge"),
                "concurrency": d.get("concurrency"),
            })
        except Exception:
            continue
    return items


def get_run(run_id: str, with_details: bool = True) -> Optional[dict]:
    run = _RUNS.get(run_id)
    if run is None:
        p = _runs_dir() / f"{run_id}.json"
        if not p.exists():
            return None
        try:
            run = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            return None
    out = dict(run)
    if not with_details:
        out.pop("details", None)
    else:
        lk = _lock_of(run_id)
        with lk:
            out["details"] = list(run.get("details") or [])
    return out


def delete_run(run_id: str) -> bool:
    """删除一次运行（内存 + 磁盘）；运行中不可删"""
    with _LOCK:
        run = _RUNS.get(run_id)
        if run is not None and run.get("status") == "running":
            raise RuntimeError("运行进行中，无法删除")
        _RUNS.pop(run_id, None)
        _LOCKS.pop(run_id, None)
    p = _runs_dir() / f"{run_id}.json"
    if p.exists():
        p.unlink()
        return True
    return run is not None


def run_status(run_id: str) -> Optional[dict]:
    run = _RUNS.get(run_id)
    if run is None:
        p = _runs_dir() / f"{run_id}.json"
        if p.exists():
            return {"run_id": run_id, "status": "done", "done": 1, "total": 1}
        return None
    lk = _lock_of(run_id)
    with lk:
        details = run.get("details") or []
        return {
            "run_id": run_id, "status": run["status"], "done": len(details),
            "total": run["total"], "current": run.get("current"),
            "stop_requested": run.get("stop_requested", False),
            "hit": sum(1 for d in details if d.get("hit")),
            "failed": sum(1 for d in details if d.get("status") == "FAIL"),
            "elapsed_s": round(time.time() - run["t0"], 1) if run["status"] == "running" else run.get("stats", {}).get("elapsed_s"),
        }


def stop_run(run_id: str) -> bool:
    """请求停止运行中的批量测试：不再派发新用例，在跑的用例自然结束后收尾"""
    run = _RUNS.get(run_id)
    if run is None or run["status"] != "running":
        return False
    with _lock_of(run_id):
        if run["status"] != "running":
            return False
        run["stop_requested"] = True
    logger.info(f"[batch] run {run_id} stop requested")
    return True


def start_run(cases: List[dict], concurrency: int = 3, use_judge: bool = False,
              options: Optional[dict] = None) -> dict:
    """创建并启动一次批量测试，立即返回 run 信息"""
    from app.api.endpoints import _state
    from app.core.index_cache import current_database_meta_hash

    pipeline = _state.get("pipeline")
    if pipeline is None:
        raise RuntimeError("召回服务未就绪")

    # 索引新鲜度校验：内存索引指纹与 database_meta.json 不一致时拒绝批测
    # （历史上出现过：重建后索引陈旧，批测静默漏召回，如 2026-09-17 用例174）
    idx_state = _state.get("index_state") or {}
    loaded_hash = idx_state.get("database_meta_hash")
    file_hash = current_database_meta_hash()
    if loaded_hash and file_hash and loaded_hash != file_hash:
        raise RuntimeError(
            f"索引与 database_meta.json 不一致（加载于 {idx_state.get('loaded_at')}），"
            "批测结果不可信。请先 POST /api/metadata/rebuild 全量重建，"
            "或 POST /api/metadata/reload-index 刷索引后再运行批测"
        )

    run_id = time.strftime("%Y%m%d_%H%M%S") + "_" + uuid.uuid4().hex[:6]
    options = options or {}
    req_defaults = {
        "use_rewrite": bool(options.get("use_rewrite", True)),
        "use_rewrite_llm": bool(options.get("use_rewrite_llm", False)),
        "dim_value_topn": int(options.get("dim_value_topn", 30)),
    }
    if options.get("intent_split") is not None:
        req_defaults["intent_split"] = bool(options["intent_split"])
    if options.get("agent_id"):
        req_defaults["agent_id"] = options["agent_id"]
    if options.get("scope"):
        req_defaults["scope"] = options["scope"]

    run = {
        "run_id": run_id,
        "status": "running",
        "created_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "filename": options.get("filename", ""),
        "total": len(cases),
        "concurrency": concurrency,
        "use_judge": use_judge,
        "options": req_defaults,
        "current": None,
        "stop_requested": False,
        "details": [],
        "stats": None,
        "t0": time.time(),
    }
    with _LOCK:
        _RUNS[run_id] = run
        # 内存中只保留最近 MAX_RUNS 个已完成任务
        done_ids = [k for k, v in _RUNS.items() if v["status"] != "running"]
        while len(done_ids) > MAX_RUNS - 1:
            _RUNS.pop(done_ids.pop(0), None)

    pool = ThreadPoolExecutor(max_workers=max(1, concurrency),
                              thread_name_prefix="batch")

    def work(case):
        from app.api.schemas import SlimRecallRequest
        t1 = time.time()
        slim = None
        err = None
        try:
            req = SlimRecallRequest(query=case["query"], use_llm_judge=use_judge, **req_defaults)
            slim = pipeline.run(req)
        except Exception as e:
            err = str(e)
            logger.warning(f"[batch] case {case['no']} failed: {e}")
        detail = {
            "no": case["no"], "query": case["query"],
            "status": "OK" if slim is not None else "FAIL",
            "error": err,
            "hit": False,
            "wall_ms": round((time.time() - t1) * 1000),
        }
        if slim is not None:
            detail.update(judge_case(case, slim))
        return detail

    def runner():
        futs = [pool.submit(work, c) for c in cases]
        try:
            for fut in as_completed(futs):
                if run["stop_requested"] and fut.cancel():
                    continue
                d = fut.result()
                lk = _lock_of(run_id)
                with lk:
                    run["details"].append(d)
                    run["current"] = d["query"]
        finally:
            pool.shutdown(wait=False)
            lk = _lock_of(run_id)
            with lk:
                details = sorted(run["details"], key=lambda x: x["no"])
                run["details"] = details
                run["stats"] = _build_stats(details, time.time() - run["t0"])
                run["status"] = "stopped" if run.pop("stop_requested", False) else "done"
                run["current"] = None
                run.pop("t0", None)
            _persist(run)
            logger.info(f"[batch] run {run_id} done: hit={run['stats']['hit']}/{run['stats']['ok']}")

    threading.Thread(target=runner, daemon=True, name=f"batch-{run_id}").start()
    return {"run_id": run_id, "status": "running", "total": len(cases),
            "concurrency": concurrency, "use_judge": use_judge}


# ----------------------------------------------------------
# 结果导出 xlsx
# ----------------------------------------------------------
def export_xlsx(run: dict) -> bytes:
    """生成结果 Excel：Sheet1 汇总，Sheet2 明细（未命中标红）"""
    if run.get("status") != "done":
        raise ValueError("run 尚未完成")
    import openpyxl
    from openpyxl.styles import Font, PatternFill, Alignment

    wb = openpyxl.Workbook()

    # ---- Sheet1 汇总 ----
    ws1 = wb.active
    ws1.title = "汇总"
    ws1.column_dimensions["A"].width = 22
    ws1.column_dimensions["B"].width = 40
    st = run.get("stats") or {}
    rows1 = [
        ("运行ID", run.get("run_id")),
        ("时间", run.get("created_at")),
        ("测试集文件", run.get("filename")),
        ("并发数", run.get("concurrency")),
        ("LLM精判", "开启" if run.get("use_judge") else "关闭"),
        ("改写", "开启" if (run.get("options") or {}).get("use_rewrite", True) else "关闭"),
        ("LLM改写", "开启" if (run.get("options") or {}).get("use_rewrite_llm") else "关闭"),
        ("意图拆分", "开启" if (run.get("options") or {}).get("intent_split", True) else "关闭"),
        ("总题数", st.get("total")),
        ("成功", st.get("ok")),
        ("失败", st.get("failed")),
        ("命中题数", st.get("hit")),
        ("命中率", st.get("hit_rate") if st.get("hit_rate") is None else f"{st['hit_rate'] * 100:.1f}%"),
        ("平均耗时ms", st.get("avg_ms")),
        ("P95耗时ms", st.get("p95_ms")),
        ("总耗时s", st.get("elapsed_s")),
        ("", ""),
        ("各列命中率", ""),
    ]
    for k, v in (st.get("cols") or {}).items():
        rate = v.get("rate")
        rows1.append((k, "-" if rate is None else f"{v['hit']}/{v['total']} ({rate * 100:.1f}%)"))
    bold = Font(bold=True)
    for r, (k, v) in enumerate(rows1, start=1):
        ws1.cell(r, 1, k).font = bold
        ws1.cell(r, 2, v)

    # ---- Sheet2 明细 ----
    ws2 = wb.create_sheet("明细")
    red_fill = PatternFill("solid", fgColor="FFC7CE")
    red_font = Font(color="9C0006")
    green_font = Font(color="256029")
    headers = ["题号", "问题", "行命中"]
    for k in EXPECT_KEYS:
        lbl = EXPECT_LABELS[k]
        headers += [lbl, lbl.replace("期望", "") + "-命中", lbl.replace("期望", "") + "-召回分数"]
    headers += ["实际指标", "实际维度", "实际维度值", "实际表", "模式", "召回文本", "耗时ms", "错误"]
    ws2.append(headers)
    for c in ws2[1]:
        c.font = bold
        c.alignment = Alignment(wrap_text=True, vertical="center")
    for w, col in zip([8, 40, 8, 24, 10, 24, 10, 14, 24, 10, 24, 10, 14, 26, 30, 16, 20, 10, 30],
                      "ABCDEFGHIJKLMNOPQRS"):
        ws2.column_dimensions[col].width = w

    for d in run.get("details") or []:
        row = [d.get("no"), d.get("query"), "命中" if d.get("hit") else "未命中"]
        scores = d.get("actual_scores") or {}
        for k in EXPECT_KEYS:
            col = d.get("cols", {}).get(k, {})
            row.append("、".join(col.get("expect") or []) or "-")
            row.append("命中" if col.get("hit", True) else f"缺失:{'、'.join(col.get('missing') or [])}")
            # 该列期望项对应的召回分数（仅标出期望中命中的那些）
            col_scores = scores.get({"metric": "metric", "table": "tables",
                                     "dimension": "dimension", "dim_value": "dim_value"}[k]) or {}
            sc = [f"{e}={col_scores[e]}" for e in (col.get("expect") or []) if e in col_scores]
            row.append("、".join(sc) or "-")
        act = d.get("actual") or {}
        row += [
            "、".join(act.get("metric") or [])[:500] or "-",
            "、".join(act.get("dimension") or [])[:300] or "-",
            "、".join(act.get("dim_value") or [])[:500] or "-",
            "、".join(act.get("tables") or [])[:500] or "-",
            d.get("mode") or "-", (d.get("recall_query") or "")[:300],
            d.get("wall_ms") or d.get("client_ms") or "-",
            d.get("error") or "",
        ]
        ws2.append(row)
        r = ws2.max_row
        hit_cell = ws2.cell(r, 3)
        hit_cell.font = green_font if d.get("hit") else red_font
        if not d.get("hit"):
            hit_cell.fill = red_fill
            ci = 4
            for k in EXPECT_KEYS:
                if d.get("cols", {}).get(k, {}).get("hit") is False:
                    ws2.cell(r, ci).fill = red_fill
                    ws2.cell(r, ci + 1).fill = red_fill
                ci += 2
        if d.get("status") == "FAIL":
            for c in ws2[r]:
                c.fill = red_fill

    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


def export_filename(run: dict) -> str:
    return f"批量测试结果_{run.get('run_id', 'run')}.xlsx"
