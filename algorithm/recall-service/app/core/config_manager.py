"""
召回参数配置中心：
- 从 recall_config 表读取，按 (path_name, entity_type) 索引
- 提供 get / update / reload 接口
- 支持后台定时刷新
"""
import threading
import time
from typing import Dict
from dataclasses import dataclass
from datetime import datetime

from app.core.database import DBClient, get_db
from app.utils.logger import get_logger

logger = get_logger("config_mgr", "config_mgr.log")


@dataclass
class PathConfig:
    path_name: str
    entity_type: str
    recall_mode: str          # 'top_k' / 'threshold' / 'hybrid'
    top_k: int
    threshold: float
    enabled: bool = True
    description: str = ""


class RecallConfigManager:
    def __init__(self, db: DBClient = None, refresh_interval: int = 30):
        self.db = db or get_db()
        self.refresh_interval = refresh_interval
        self._configs: Dict[str, PathConfig] = {}
        self._lock = threading.RLock()
        self._last_refresh = datetime.min
        self.reload()
        self._start_auto_refresh()

    def reload(self):
        rows = self.db.query(
            "SELECT path_name, entity_type, recall_mode, top_k, threshold, enabled, description "
            "FROM recall_config"
        )
        new_configs = {}
        for r in rows:
            cfg = PathConfig(
                path_name=r["path_name"],
                entity_type=r["entity_type"],
                recall_mode=r["recall_mode"],
                top_k=int(r["top_k"]),
                threshold=float(r["threshold"]),
                enabled=bool(r["enabled"]),
                description=r.get("description") or "",
            )
            new_configs[f"{cfg.path_name}:{cfg.entity_type}"] = cfg
        with self._lock:
            self._configs = new_configs
            self._last_refresh = datetime.now()
        logger.info(f"reload {len(new_configs)} configs")

    def get(self, path_name: str, entity_type: str) -> PathConfig:
        with self._lock:
            return self._configs.get(
                f"{path_name}:{entity_type}",
                PathConfig(path_name, entity_type, "top_k", 20, 0.5),
            )

    def list_all(self):
        with self._lock:
            return list(self._configs.values())

    def update(self, path_name: str, entity_type: str, **kwargs) -> PathConfig:
        allowed = {"recall_mode", "top_k", "threshold", "enabled", "description"}
        upd = {k: v for k, v in kwargs.items() if k in allowed and v is not None}
        if not upd:
            raise ValueError("No valid fields to update")

        if "recall_mode" in upd and upd["recall_mode"] not in {"top_k", "threshold", "hybrid"}:
            raise ValueError("recall_mode must be one of top_k/threshold/hybrid")
        if "top_k" in upd and not (1 <= int(upd["top_k"]) <= 500):
            raise ValueError("top_k must be in [1, 500]")
        if "threshold" in upd and not (0.0 <= float(upd["threshold"]) <= 1.0):
            raise ValueError("threshold must be in [0.0, 1.0]")
        if "enabled" in upd:
            upd["enabled"] = 1 if upd["enabled"] else 0

        # 检查是否存在；不存在则插入
        existing = self.db.query(
            "SELECT 1 FROM recall_config WHERE path_name=%s AND entity_type=%s",
            [path_name, entity_type],
        )
        if not existing:
            self.db.execute(
                "INSERT INTO recall_config(path_name, entity_type, recall_mode, top_k, threshold, enabled) "
                "VALUES(%s,%s,%s,%s,%s,%s)",
                [path_name, entity_type,
                 upd.get("recall_mode", "top_k"),
                 upd.get("top_k", 20),
                 upd.get("threshold", 0.5),
                 upd.get("enabled", 1)],
            )
        else:
            sets = ", ".join(f"{k}=%s" for k in upd.keys())
            params = list(upd.values()) + [path_name, entity_type]
            self.db.execute(
                f"UPDATE recall_config SET {sets} WHERE path_name=%s AND entity_type=%s",
                params,
            )
        self.reload()
        return self.get(path_name, entity_type)

    def _start_auto_refresh(self):
        def loop():
            while True:
                time.sleep(self.refresh_interval)
                try:
                    self.reload()
                except Exception as e:
                    logger.error(f"auto reload error: {e}")
        t = threading.Thread(target=loop, daemon=True)
        t.start()


_GLOBAL_CFG: RecallConfigManager = None


def get_config_manager() -> RecallConfigManager:
    global _GLOBAL_CFG
    if _GLOBAL_CFG is None:
        _GLOBAL_CFG = RecallConfigManager()
    return _GLOBAL_CFG
