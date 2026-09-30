"""
描述回写同步：把 LLM 生成的描述推送给 Java 侧
POST {JAVA_SYNC_BASE_URL}/api/recall/descriptions/sync（见对接文档 §3.2）

- 全量推送：推送所有 指标/维度/表（过滤时间衍生维度）
- 增量推送：基于 data/descriptions_sync_state.json 记录的上次 hash，只推描述变化的条目；
  无历史状态时退化为全量
- 失败重试（指数退避），仍失败落盘 data/descriptions_sync_fail.json 供手工重放
"""
import hashlib
import json
import threading
import time
from typing import Dict, List, Optional, Tuple

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app import config
from app.utils.logger import get_logger

logger = get_logger("description_sync", "description_sync.log")

# 时间衍生维度 id 前缀（gen 脚本硬编码，生产库无对应记录，推送时过滤）
_DERIVED_DIM_PREFIX = "test_"

STATE_PATH = config.DATA_DIR / "descriptions_sync_state.json"
FAIL_PATH = config.DATA_DIR / "descriptions_sync_fail.json"


def _item_hash(item: dict) -> str:
    """描述 hash：增量推送判变依据（只比对推送字段 description）"""
    return hashlib.sha1((item.get("description") or "").encode("utf-8")).hexdigest()


def _read_meta() -> Optional[dict]:
    path = config.DATA_DIR / "database_meta_with_id.json"
    if not path.exists():
        logger.warning(f"database_meta_with_id.json not found: {path}")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        logger.error(f"database_meta_with_id.json parse failed: {e}")
        return None


def _item_key(item: dict) -> str:
    """推送判变 key：type + code（与推送字段一致，见对接文档 §3.3）"""
    return f"{item['type']}:{item.get('metric_code') or item.get('dimension_code') or item.get('table_name')}"


def build_items(meta: dict) -> Tuple[List[dict], int]:
    """组装推送 items，返回 (items, 被过滤的衍生维度数)。
    只发 type / code / description 三个 key（见对接文档 §3.3：其他字段一律不发）。"""
    items: List[dict] = []
    filtered = 0
    dm = meta.get("database_meta", meta)

    for m in dm.get("available_metrics", []):
        items.append({
            "type": "metric",
            "metric_code": m.get("metric_code") or "",
            "description": m.get("description") or "",
        })

    for d in dm.get("available_dimensions", []):
        if str(d.get("dimension_id", "")).startswith(_DERIVED_DIM_PREFIX):
            filtered += 1
            continue
        items.append({
            "type": "dimension",
            "dimension_code": d.get("dimension_code") or "",
            "description": d.get("description") or "",
        })

    for t in dm.get("table_summaries", []):
        items.append({
            "type": "table",
            "table_name": t.get("table_name") or "",
            "description": t.get("description") or "",
        })

    return items, filtered


class DescriptionSyncService:
    def __init__(self):
        self._lock = threading.Lock()

    # ---------- 状态查询 ----------
    def is_configured(self) -> bool:
        return bool(config.JAVA_SYNC_BASE_URL)

    def status(self) -> dict:
        state = self._read_state()
        pending = 0
        meta = _read_meta()
        if meta is not None:
            items, _ = build_items(meta)
            if items:
                pending = len(self._incremental_select(items))
        return {
            "configured": self.is_configured(),
            "base_url": config.JAVA_SYNC_BASE_URL or "",
            "state_path": str(STATE_PATH),
            "last_sync_time": (state or {}).get("pushed_at", ""),
            "last_mode": (state or {}).get("mode", ""),
            "pushed_count": (state or {}).get("pushed_count", 0),
            "pending_count": pending,
            "failed_count": self._fail_count(),
        }

    def _fail_count(self) -> int:
        if not FAIL_PATH.exists():
            return 0
        try:
            data = json.loads(FAIL_PATH.read_text(encoding="utf-8"))
            return len(data.get("items", []))
        except Exception:
            return 0

    # ---------- 推送 ----------
    def run(self, mode: str = "incremental") -> dict:
        """执行一次推送（可在后台线程调用）。mode: full | incremental"""
        if not self.is_configured():
            return {"ok": False, "message": "JAVA_SYNC_BASE_URL 未配置", "pushed_count": 0}

        with self._lock:
            meta = _read_meta()
            if meta is None:
                return {"ok": False, "message": "database_meta_with_id.json 不存在", "pushed_count": 0}
            items, filtered = build_items(meta)
            if not items:
                return {"ok": False, "message": "无可推送条目", "pushed_count": 0}

            if mode == "full":
                target = items
            else:
                target = self._incremental_select(items)
                if not target:
                    self._write_state(items, mode="incremental", pushed_count=0)
                    return {"ok": True, "message": "无描述变化，无需推送", "pushed_count": 0,
                            "mode": "incremental", "filtered_derived": filtered}

            try:
                self._http_post_items(target)
            except Exception as e:
                self._write_fail(target, mode, str(e))
                logger.exception("description sync push failed")
                return {"ok": False, "message": f"推送失败: {e}", "pushed_count": 0,
                        "mode": mode, "filtered_derived": filtered}

            self._write_state(items, mode=mode, pushed_count=len(target))
            return {"ok": True, "message": "", "pushed_count": len(target),
                    "mode": mode, "filtered_derived": filtered}

    def _incremental_select(self, items: List[dict]) -> List[dict]:
        """对比上次状态，只保留描述变化的条目；无状态时全量返回"""
        state = self._read_state()
        prev = (state or {}).get("items", {})
        return [it for it in items if prev.get(_item_key(it)) != _item_hash(it)]

    # ---------- HTTP ----------
    @retry(stop=stop_after_attempt(config.JAVA_SYNC_RETRY),
           wait=wait_exponential(min=1, max=30), reraise=True)
    def _http_post_items(self, items: List[dict]):
        url = f"{config.JAVA_SYNC_BASE_URL}/api/recall/descriptions/sync"
        resp = httpx.post(url, json={"items": items}, timeout=config.JAVA_SYNC_TIMEOUT)
        resp.raise_for_status()
        data = resp.json()
        if data.get("code") not in (200, None):
            raise RuntimeError(f"java sync failed: {data.get('message')}")
        return data

    # ---------- 状态持久化 ----------
    def _read_state(self) -> Optional[dict]:
        if not STATE_PATH.exists():
            return None
        try:
            return json.loads(STATE_PATH.read_text(encoding="utf-8"))
        except Exception as e:
            logger.error(f"state file parse failed: {e}")
            return None

    def _write_state(self, items: List[dict], mode: str, pushed_count: int):
        state = {
            "pushed_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "mode": mode,
            "pushed_count": pushed_count,
            "items": {_item_key(it): _item_hash(it) for it in items},
        }
        try:
            STATE_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception as e:
            logger.error(f"write state failed: {e}")

    def _write_fail(self, items: List[dict], mode: str, error: str):
        fail = {
            "failed_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "mode": mode,
            "error": error,
            "items": items,
        }
        try:
            FAIL_PATH.write_text(json.dumps(fail, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception as e:
            logger.error(f"write fail file failed: {e}")


description_sync_service = DescriptionSyncService()


def trigger_auto_push():
    """采纳合入后自动增量推送（后台线程，不阻塞主流程）"""
    if not description_sync_service.is_configured():
        return

    def _run():
        try:
            r = description_sync_service.run("incremental")
            if not r.get("ok"):
                logger.warning(f"auto description sync failed: {r.get('message')}")
        except Exception:
            logger.exception("auto description sync error")

    threading.Thread(target=_run, daemon=True).start()
