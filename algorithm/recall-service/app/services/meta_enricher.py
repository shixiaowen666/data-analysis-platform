# -*- coding: utf-8 -*-
"""
meta_enricher —— LLM 全局补全 database_meta 四类字段（方案见 docs/描述生成/方案.md）

设计要点（v2 全局上下文版）：
- 输入永远全量：每类目标一次调用，全库元数据（70表+52维度+272指标）完整进上下文
- 仅输出侧分片：指标 272 条一次输出约 13k tokens 贴输出上限，切 2 片，每片带完整上下文，
  指令限定"本片只输出以下 code"；缺 code 自动该片重试一次，仍缺的逐条补跑
- 结果先进候选池（data/database_meta_enrich.json），页面人工审核采纳后才合入主文件
"""
import json
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

from app import config
from app.core.llm import get_llm_client
from app.core.prompt_manager import get_prompt_template, register_default
from app.services.enrich_prompts import ENRICH_DEFAULTS
from app.utils.logger import get_logger

logger = get_logger("enrich", "enrich.log")

# 注册默认模板（prompt_manager 文件优先，缺失时回落到这里）
for _k, _t in ENRICH_DEFAULTS.items():
    register_default(_k, _t)

ENRICH_PATH = config.DATA_DIR / "database_meta_enrich.json"
IGNORE_PATH = config.DATA_DIR / "enrich_ignore.json"

# 各类目标输出分片大小（条/片）。按 LLM_MAX_TOKENS=4096 设计留余量；
# 前端调试页可临时改片大小（仅调试），正式任务固定用这里的值
PART_SIZES = {"metrics": 50, "dims": 60, "tables": 30, "knowledge": 60}

TARGETS = ("metrics", "dims", "tables", "knowledge")

# 各目标在主文件中的定位：list key / 主键字段 / 读写目标字段
TARGET_SPEC = {
    "metrics":   {"list": "available_metrics",   "key": "metric_code",   "field": "description"},
    "dims":      {"list": "available_dimensions", "key": "dimension_code", "field": "description"},
    "tables":    {"list": "table_summaries",      "key": "table_name",    "field": "description"},
    "knowledge": {"list": "business_context",     "key": "knowledgeElement", "field": "knowledgeAlias"},
}


def _fmt(template: str, **kw: str) -> str:
    """{name} 占位符替换。不用 str.format：模板里 JSON 花括号无需转义，方便在线编辑"""
    out = template
    for k, v in kw.items():
        out = out.replace("{" + k + "}", v)
    return out


class MetaEnricher:
    """后台运行的元数据补全器。状态与候选池由实例持有，endpoints 持单例。"""

    def __init__(self):
        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None
        self._status = {"running": False, "stage": "", "part": "", "done": 0, "total": 0,
                        "failed": 0, "error": "", "started_at": "", "finished_at": ""}
        self._results: dict[str, list] = {}

    # ----------------------------------------------------------
    # 状态
    # ----------------------------------------------------------
    def get_status(self) -> dict:
        return dict(self._status)

    def get_results(self) -> dict:
        """候选池：内存优先，服务重启后回落到落盘文件"""
        with self._lock:
            if self._results:
                return {"items": self._flatten(self._results), "source": "memory"}
        if ENRICH_PATH.exists():
            try:
                data = json.loads(ENRICH_PATH.read_text(encoding="utf-8"))
                return {"items": self._flatten(data.get("results", {})), "source": "disk"}
            except Exception as e:
                logger.error(f"read enrich pool failed: {e}")
        return {"items": [], "source": "empty"}

    @staticmethod
    def _flatten(results: dict) -> list:
        out = []
        for target, items in results.items():
            for it in items:
                it = dict(it)
                it["target"] = target
                out.append(it)
        return out

    # ----------------------------------------------------------
    # 主入口
    # ----------------------------------------------------------
    def start(self, targets: list, overwrite: bool,
              meta: Optional[dict] = None, meta_hash: str = "") -> dict:
        if not meta or "database_meta" not in meta:
            return {"ok": False, "error": "database_meta is empty"}
        if self._status["running"]:
            return {"ok": False, "error": "enrich already running"}

        # 合并落盘候选池（增量：重跑只更新涉及的目标，其余保留）
        pool = self._load_pool()
        old_results = pool.get("results", {})

        total = 0
        spec_scope = {}
        for t in targets:
            if t not in TARGET_SPEC:
                continue
            items = meta["database_meta"].get(TARGET_SPEC[t]["list"], [])
            items = self._filter_targets(t, items, overwrite)
            spec_scope[t] = items
            total += len(items)
        if total == 0:
            return {"ok": True, "total": 0,
                    "message": "该类型全部已有描述，无需生成（勾选「允许覆盖已有值」可重新生成）"}

        with self._lock:
            self._status = {"running": True, "stage": "准备", "part": "", "done": 0,
                            "total": total, "failed": 0, "error": "",
                            "started_at": datetime.now().isoformat(timespec="seconds"),
                            "finished_at": ""}
            self._results = old_results

        self._thread = threading.Thread(
            target=self._run, daemon=True,
            args=(targets, meta, meta_hash, spec_scope))
        self._thread.start()
        return {"ok": True, "total": total}

    def _filter_targets(self, target: str, items: list, overwrite: bool) -> list:
        """默认仅空值；overwrite=True 时全部纳入。
        knowledge 的目标字段 knowledgeAlias 是列表，空列表/None 视为空值；
        旧字符串格式条目按无别名的对象处理"""
        spec = TARGET_SPEC[target]
        out = []
        for it in items:
            if not isinstance(it, dict):
                it = {"knowledgeElement": it, "knowledgeAlias": []}
            val = it.get(spec["field"])
            empty = (not val) or (isinstance(val, str) and not val.strip())
            if overwrite or empty:
                out.append(it)
        return out

    # ----------------------------------------------------------
    # 后台执行
    # ----------------------------------------------------------
    def _run(self, targets, meta, meta_hash, spec_scope):
        try:
            llm = get_llm_client()
            for t in targets:
                if t not in spec_scope or not spec_scope[t]:
                    continue
                self._set(stage=t, part="")
                handler = getattr(self, f"_gen_{t}")
                items, failed = handler(llm, meta["database_meta"], spec_scope[t])
                with self._lock:
                    self._results[t] = items
                    self._status["failed"] += failed
                self._save_pool(meta_hash, llm_model=getattr(llm, "model", ""))
        except Exception as e:
            logger.exception("enrich run failed")
            with self._lock:
                self._status["error"] = str(e)
        finally:
            with self._lock:
                self._status["running"] = False
                self._status["finished_at"] = datetime.now().isoformat(timespec="seconds")

    def _set(self, **kw):
        with self._lock:
            self._status.update(kw)

    def _bump(self, n=1):
        with self._lock:
            self._status["done"] += n

    # ----------------------------------------------------------
    # 上下文构造（紧凑一行式，压 token）
    # ----------------------------------------------------------
    @staticmethod
    def _ctx_tables(dbm: dict, with_columns: bool = False) -> str:
        lines = []
        for t in dbm.get("table_summaries", []):
            line = f"- {t.get('table_name','')} / {t.get('display_name','')} / {(t.get('description') or '')[:60]}"
            if with_columns:
                cols = t.get("columns") or []
                coltxt = "; ".join(
                    f"{c.get('field_key','')}({c.get('field_name','')},{c.get('role') or c.get('basic_type','')})"
                    for c in cols)
                line += f"\n  字段: {coltxt}"
            lines.append(line)
        return "\n".join(lines)

    @staticmethod
    def _ctx_dims(dbm: dict) -> str:
        lines = []
        for d in dbm.get("available_dimensions", []):
            pv = d.get("possible_values") or []
            pvt = f" 取值{len(pv)}项如:{pv[0]}" if pv else ""
            lines.append(f"- {d.get('dimension_code','')} / {d.get('dimension_name','')} / {(d.get('description') or '')[:40]}{pvt}")
        return "\n".join(lines)

    @staticmethod
    def _ctx_metrics(dbm: dict) -> str:
        lines = []
        for m in dbm.get("available_metrics", []):
            unit = f" [{m['unit']}]" if m.get("unit") else ""
            lines.append(f"- {m.get('metric_code','')} / {m.get('metric_name','')}{unit} / {(m.get('description') or '')[:50]}")
        return "\n".join(lines)

    @staticmethod
    def _ctx_names(dbm: dict) -> str:
        names = [m.get("metric_name", "") for m in dbm.get("available_metrics", [])]
        names += [d.get("dimension_name", "") for d in dbm.get("available_dimensions", [])]
        return "、".join(n for n in names if n)

    # ----------------------------------------------------------
    # 通用输出分片骨架：输入全量，仅 output_scope 随片变化
    # ----------------------------------------------------------
    def _gen_parted(self, llm, dbm, target, items, build_vars, keys, resp_key_of, val_of,
                    max_len, scope_label):
        """build_vars(dbm) -> {var: content}（不含 output_scope）；
        keys 是条目标识列表（与 items 一一对应，决定 scope 内容）；
        resp_key_of(d) 从 LLM 返回条目解析出同一标识"""
        template = get_prompt_template("enrich_alias" if target == "knowledge" else f"enrich_{target.rstrip('s')}")
        size = PART_SIZES[target]
        parts = [keys[i:i + size] for i in range(0, len(keys), size)]
        out, failed = [], 0
        for pi, part in enumerate(parts, 1):
            self._set(part=f"{target} 片{pi}/{len(parts)}")
            part_set = set(part)
            variables = build_vars(dbm)
            variables["output_scope"] = f"本片只输出以下 {scope_label}：{json.dumps(part, ensure_ascii=False)}"
            prompt = template
            for k, v in variables.items():
                prompt = prompt.replace("{" + k + "}", v)
            got = self._call_part(llm, prompt, part_set,
                                  key_of=resp_key_of, val_of=val_of, max_len=max_len)
            for it, k in zip(items, keys):
                if k in part_set and got.get(k):
                    v = got[k]
                    if target == "knowledge":
                        elem = it.get("knowledgeElement", "") if isinstance(it, dict) else it
                        out.append({"key": elem, "value": v, "preview": str(elem)[:80]})
                    else:
                        out.append({"key": k, "value": v})
            self._bump(len(part))
        failed = sum(1 for o in out if not o.get("value"))
        return out, failed

    def _gen_metrics(self, llm, dbm, items):
        return self._gen_parted(
            llm, dbm, "metrics", items,
            build_vars=lambda d: {"tables": self._ctx_tables(d), "dimensions": self._ctx_dims(d),
                                  "metrics": self._ctx_metrics(d)},
            keys=[str(it["metric_code"]) for it in items],
            resp_key_of=lambda d: str(d.get("code", "")),
            val_of=lambda d: str(d.get("description", "")).strip(), max_len=50,
            scope_label="code")

    def _gen_dims(self, llm, dbm, items):
        return self._gen_parted(
            llm, dbm, "dims", items,
            build_vars=lambda d: {"tables": self._ctx_tables(d), "metrics": self._ctx_metrics(d),
                                  "dimensions": self._ctx_dims(d),
                                  "dim_count": str(len(d.get("available_dimensions", [])))},
            keys=[str(it["dimension_code"]) for it in items],
            resp_key_of=lambda d: str(d.get("code", "")),
            val_of=lambda d: str(d.get("description", "")).strip(), max_len=30,
            scope_label="code")

    def _gen_tables(self, llm, dbm, items):
        return self._gen_parted(
            llm, dbm, "tables", items,
            build_vars=lambda d: {"tables": self._ctx_tables(d, with_columns=True),
                                  "metrics_and_dimensions": self._ctx_metrics(d) + "\n" + self._ctx_dims(d),
                                  "table_count": str(len(d.get("table_summaries", [])))},
            keys=[str(it["table_name"]) for it in items],
            resp_key_of=lambda d: str(d.get("table_name", "")),
            val_of=lambda d: str(d.get("description", "")).strip(), max_len=100,
            scope_label="table_name")

    def _gen_knowledge(self, llm, dbm, items):
        def build_vars(d):
            klines = []
            for idx, it in enumerate(items, 1):
                elem = it.get("knowledgeElement", "") if isinstance(it, dict) else it
                klines.append(f"{idx}. {str(elem)[:500]}")
            return {"knowledge": "\n\n".join(klines), "names": self._ctx_names(d)}

        return self._gen_parted(
            llm, dbm, "knowledge", items, build_vars=build_vars,
            keys=list(range(1, len(items) + 1)),
            resp_key_of=lambda d: int(d.get("id", -1)) if str(d.get("id", "")).lstrip("-").isdigit() else -1,
            val_of=lambda d: [str(a).strip() for a in d.get("aliases", []) if str(a).strip()],
            max_len=0, scope_label="编号")

    # ----------------------------------------------------------
    # 单次调用 + 缺口重试
    # ----------------------------------------------------------
    def _call_part(self, llm, prompt, valid_keys: set, key_of, val_of,
                   max_len: int = 0, max_retry: int = 1):
        """调用并解析 {items:[...]}。返回 {key: value}；缺 key 或 value 超过 max_len 视为缺失，
        带缺口清单重试一次；重试后仍不合格的条目不返回（由调用方计入 failed）"""
        merged: dict = {}
        missing = set(valid_keys)
        cur_prompt = prompt
        for attempt in range(max_retry + 1):
            try:
                data = llm.generate_json(cur_prompt)
            except Exception as e:
                logger.warning(f"enrich call fail (attempt {attempt + 1}): {e}")
                time.sleep(2)
                continue
            for d in data.get("items", []) if isinstance(data, dict) else []:
                k, v = key_of(d), val_of(d)
                if k not in missing or not v:
                    continue
                if max_len and len(v) > max_len:
                    logger.warning(f"enrich value too long, rejected: key={k} len={len(v)}/{max_len}")
                    continue
                merged[k] = v
                missing.discard(k)
            if not missing:
                break
            if attempt < max_retry:
                extra = f"；description 不得超过{max_len}字" if max_len else ""
                cur_prompt = prompt + f"\n\n注意：你上一轮有 {len(missing)} 条不合格（遗漏或 description 超过{max_len}字）{extra}，" \
                                     f"本次必须补全且合格，这些条目：" \
                                     f"{json.dumps(sorted(map(str, missing)), ensure_ascii=False)}"
        return merged

    # ----------------------------------------------------------
    # 描述生成调试：模板/变量 与 LLM 调用拆成两步，支持在线改提示词
    # ----------------------------------------------------------
    def debug_input(self, target: str, meta: dict) -> dict:
        """返回原始模板 + 各占位符变量内容（前端编辑后自行替换，或用 final_prompt 直接试跑）。
        entries 返回该类全部条目（前端按片大小切片后逐片构造 output_scope）"""
        if target not in TARGET_SPEC:
            return {"error": f"unknown target: {target}"}
        dbm = meta["database_meta"]
        lst = dbm.get(TARGET_SPEC[target]["list"], [])

        variables: dict = {}
        entries: list = []
        if target == "metrics":
            template = get_prompt_template("enrich_metric")
            variables["tables"] = self._ctx_tables(dbm)
            variables["dimensions"] = self._ctx_dims(dbm)
            variables["metrics"] = self._ctx_metrics(dbm)
            todo = self._filter_targets(target, lst, overwrite=False)
            first = (todo or lst)[:1]
            sel = str(first[0].get("metric_code", "")) if first else ""
            variables["output_scope"] = f"本片只输出以下 code：{json.dumps([sel], ensure_ascii=False)}"
            entries = [{"key": str(it.get("metric_code", "")),
                        "name": it.get("metric_name", ""),
                        "empty": not str(it.get("description") or "").strip()} for it in lst]
        elif target == "dims":
            template = get_prompt_template("enrich_dim")
            variables["tables"] = self._ctx_tables(dbm)
            variables["metrics"] = self._ctx_metrics(dbm)
            variables["dimensions"] = self._ctx_dims(dbm)
            variables["dim_count"] = str(len(lst))
            variables["output_scope"] = self._debug_scope(lst, "dimension_code", target, "code")
            entries = [{"key": str(it.get("dimension_code", "")),
                        "name": it.get("dimension_name", ""),
                        "empty": not str(it.get("description") or "").strip()} for it in lst]
        elif target == "tables":
            template = get_prompt_template("enrich_table")
            variables["tables"] = self._ctx_tables(dbm, with_columns=True)
            variables["metrics_and_dimensions"] = self._ctx_metrics(dbm) + "\n" + self._ctx_dims(dbm)
            variables["table_count"] = str(len(lst))
            variables["output_scope"] = self._debug_scope(lst, "table_name", target, "table_name")
            entries = [{"key": str(it.get("table_name", "")),
                        "name": it.get("display_name", ""),
                        "empty": not str(it.get("description") or "").strip()} for it in lst]
        else:
            template = get_prompt_template("enrich_alias")
            klines = [f"{i}. {it.get('knowledgeElement', '')[:500]}" for i, it in enumerate(lst, 1)]
            variables["knowledge"] = "\n\n".join(klines)
            variables["names"] = self._ctx_names(dbm)
            variables["output_scope"] = self._debug_scope(
                [{"key": i} for i in range(1, len(lst) + 1)], "key", target, "编号")
            entries = [{"key": i, "name": str(it.get("knowledgeElement", ""))[:40],
                        "empty": not (it.get("knowledgeAlias") or [])}
                       for i, it in enumerate(lst, 1)]

        final = template
        for k, v in variables.items():
            final = final.replace("{" + k + "}", v)
        return {"target": target, "template": template, "variables": variables,
                "entries": entries, "final_prompt": final, "part_size": PART_SIZES[target]}

    @staticmethod
    def _debug_scope(lst, key_field, target, scope_label):
        """调试页初始 output_scope：按默认片大小取第一片"""
        part = [str(it.get(key_field, "")) for it in lst[:PART_SIZES[target]]]
        return f"本片只输出以下 {scope_label}：{json.dumps(part, ensure_ascii=False)}"

    def debug_run(self, prompt: str) -> dict:
        """用给定的最终提示词调一次 LLM，返回原始返回与解析结果（不落候选池）"""
        import re as _re
        import time as _time
        llm = get_llm_client()
        t0 = _time.time()
        raw = llm.generate(prompt)
        elapsed_ms = int((_time.time() - t0) * 1000)
        text = raw
        parse_error = ""
        # 贪婪匹配：嵌套 JSON 必须匹配到最外层 }，非贪婪会截断到第一个内层 }
        m = _re.search(r"```(?:json)?\s*(\{.*\}|\[.*\])\s*```", text, _re.DOTALL)
        if m:
            text = m.group(1)
        parsed = None
        try:
            parsed = json.loads(text)
        except Exception as e1:
            # 尝试 1：max_tokens 截断救回 —— { 开头但末尾 } 丢失，补右括号后重试
            fixed = None
            s = text.strip()
            if s.startswith("{") and not s.endswith("}"):
                try:
                    fixed = json.loads(s + "}")
                except Exception:
                    pass
            if fixed is not None:
                parsed, parse_error = fixed, "原始返回疑似被截断，已自动补右括号救回（截断处内容可能缺失）"
                logger.warning("enrich debug_run: JSON truncated response recovered by brace completion")
            else:
                parse_error = str(e1)
        if parsed is None:
            # 尝试 2：贪婪匹配最外层 {.*}（模型在 JSON 外包了说明文字时有效）
            m2 = _re.search(r"\{.*\}", text, _re.DOTALL)
            if m2:
                try:
                    parsed = json.loads(m2.group(0))
                except Exception:
                    pass
        if parsed is None:
            # 尝试 3：逐个提取完整 {…} 对象（JSON 中段被截断/夹杂时救回已完成的条目）
            objs = _re.findall(r"\{[^{}]*\}", text)
            items = []
            for ob in objs:
                try:
                    it = json.loads(ob)
                except Exception:
                    continue
                if isinstance(it, dict):
                    items.append(it)
            if items:
                parsed = {"items": items}
                parse_error = (parse_error + "；" if parse_error else "") + \
                    f"整体 JSON 非法，仅救回 {len(items)} 个完整对象（其余缺失，多为输出被 max_tokens 截断）"
                logger.warning("enrich debug_run: invalid JSON, recovered %d complete objects", len(items))
            else:
                logger.error("enrich debug_run: JSON parse failed, raw_len=%d, error=%s", len(raw), parse_error)
        return {"raw": raw, "parsed": parsed, "elapsed_ms": elapsed_ms,
                "model": getattr(llm, "model", ""),
                "raw_len": len(raw),
                "parse_error": parse_error,
                "item_count": len(parsed.get("items", [])) if isinstance(parsed, dict) else 0}

    # ----------------------------------------------------------
    # 试跑单类（不落候选池，返回 prompt / 原始返回 / 解析结果）
    # ----------------------------------------------------------
    def dry_run(self, target: str, meta: dict) -> dict:
        inp = self.debug_input(target, meta)
        if "error" in inp:
            return inp
        ret = self.debug_run(inp["final_prompt"])
        ret["target"] = target
        ret["prompt"] = inp["final_prompt"]
        return ret

    # ----------------------------------------------------------
    # 候选池落盘
    # ----------------------------------------------------------
    def _load_pool(self) -> dict:
        if ENRICH_PATH.exists():
            try:
                return json.loads(ENRICH_PATH.read_text(encoding="utf-8"))
            except Exception:
                pass
        return {"updated_at": "", "meta_hash": "", "model": "", "results": {}}

    def _save_pool(self, meta_hash: str, llm_model: str = ""):
        pool = self._load_pool()
        pool["updated_at"] = datetime.now().isoformat(timespec="seconds")
        if meta_hash:
            pool["meta_hash"] = meta_hash
        if llm_model:
            pool["model"] = llm_model
        pool["results"] = self._results
        ENRICH_PATH.write_text(json.dumps(pool, ensure_ascii=False, indent=2), encoding="utf-8")
        logger.info(f"enrich pool saved: {ENRICH_PATH.name}")

    # ----------------------------------------------------------
    # 调试结果写入候选池
    # ----------------------------------------------------------
    def save_draft(self, results: dict, meta: Optional[dict] = None) -> dict:
        """调试 tab 分片运行结果写入候选池（增量合并，同 key 覆盖）。
        results: {target: [{key, value}]}
        knowledge 的 key 是调试序号(1..n)，按 business_context 顺序映射回 knowledgeElement，
        value 统一转成 list（前端传来的是字符串/JSON 文本）"""
        dbm = meta.get("database_meta", {}) if meta else {}
        bc = dbm.get("business_context", []) if dbm else []
        pool = self._load_pool()
        merged = pool.get("results", {})
        counts: dict = {}
        for target, items in results.items():
            if target not in TARGET_SPEC or not isinstance(items, list):
                continue
            cur = {str(r.get("key")): dict(r) for r in merged.get(target, [])}
            added = 0
            for it in items:
                key = it.get("key")
                value = it.get("value")
                if key is None or value is None or value == "":
                    continue
                if target == "knowledge":
                    elem = ""
                    try:
                        idx = int(key) - 1
                        if 0 <= idx < len(bc):
                            b = bc[idx]
                            elem = b.get("knowledgeElement", "") if isinstance(b, dict) else b
                    except (ValueError, TypeError):
                        elem = str(key)
                    if not elem:
                        continue
                    if not isinstance(value, list):
                        try:
                            parsed = json.loads(value) if isinstance(value, str) else value
                            value = [str(a).strip() for a in parsed if str(a).strip()] \
                                if isinstance(parsed, list) else [str(value)]
                        except Exception:
                            value = [str(value)]
                    it = {"key": elem, "value": value, "preview": str(elem)[:80]}
                key = it.get("key")
                cur[str(key)] = it
                added += 1
            if added:
                merged[target] = list(cur.values())
                counts[target] = added
        pool["updated_at"] = datetime.now().isoformat(timespec="seconds")
        pool["results"] = merged
        ENRICH_PATH.write_text(json.dumps(pool, ensure_ascii=False, indent=2), encoding="utf-8")
        logger.info(f"enrich draft saved: {counts}")
        return {"ok": True, "saved": counts}

    # ----------------------------------------------------------
    # 采纳/忽略
    # ----------------------------------------------------------
    def apply(self, accepted: list, ignored: list, meta_raw: dict = None, meta_path: Path = None) -> dict:
        """accepted: [{target,key,value}] → 通过 SyncService 增量合入实体层（只重编受影响实体）；
        ignored: 同构，记忽略清单。meta_raw/meta_path 参数保留兼容，已不再写主文件。"""
        from app.entity.sync_service import get_sync_service
        svc = get_sync_service()
        now = datetime.now().isoformat(timespec="seconds")

        upd_metrics, upd_dims, upd_tables, upd_know = {}, {}, {}, {}
        for acc in accepted:
            t, key, val = acc.get("target"), str(acc.get("key")), acc.get("value")
            if t == "metrics":
                upd_metrics[key] = val
            elif t == "dims":
                upd_dims[key] = val
            elif t == "tables":
                upd_tables[key] = val
            elif t == "knowledge":
                upd_know[key] = val
        st = svc.store
        metrics = [dict(m, description=upd_metrics[m["metric_code"]]) for m in st.load_metrics(list(upd_metrics))]
        dims = [dict(d, description=upd_dims[d["dimension_code"]]) for d in st.load_dimensions(list(upd_dims), with_values=True)]
        tables = [dict(tb, description=upd_tables[tb["table_name"]]) for tb in st.load_tables(list(upd_tables))]
        knowledge = []
        if upd_know:
            for k in st.load_knowledge():
                # knowledge 的 key 是正文（knowledgeElement）或 knowledge_id
                if k["knowledgeElement"] in upd_know:
                    knowledge.append(dict(k, knowledgeAlias=upd_know[k["knowledgeElement"]]))
                elif k["knowledge_id"] in upd_know:
                    knowledge.append(dict(k, knowledgeAlias=upd_know[k["knowledge_id"]]))
        sync_result = {}
        if metrics or dims or tables or knowledge:
            sync_result = svc.apply(tables=tables, metrics=metrics, dimensions=dims, knowledge=knowledge)
        logger.info(f"enrich applied: +{len(accepted)} accepted, {len(ignored)} ignored, sync={sync_result}")

        if ignored:
            ig = {"items": [], "updated_at": ""}
            if IGNORE_PATH.exists():
                try:
                    ig = json.loads(IGNORE_PATH.read_text(encoding="utf-8"))
                except Exception:
                    pass
            for g in ignored:
                ig["items"].append({**g, "ignored_at": now})
            ig["updated_at"] = now
            IGNORE_PATH.write_text(json.dumps(ig, ensure_ascii=False, indent=2), encoding="utf-8")

        # 已采纳的从候选池移除
        pool = self._load_pool()
        acc_keys = {(a.get("target"), str(a.get("key"))) for a in accepted}
        for t in list(pool.get("results", {}).keys()):
            pool["results"][t] = [
                r for r in pool["results"][t]
                if (t, str(r.get("key"))) not in acc_keys]
        pool["updated_at"] = now
        ENRICH_PATH.write_text(json.dumps(pool, ensure_ascii=False, indent=2), encoding="utf-8")

        return {"accepted": len(accepted), "ignored": len(ignored), "sync": sync_result}

    def get_ignored(self) -> list:
        if IGNORE_PATH.exists():
            try:
                return json.loads(IGNORE_PATH.read_text(encoding="utf-8")).get("items", [])
            except Exception:
                pass
        return []


# 单例（endpoints 使用）
enricher = MetaEnricher()
