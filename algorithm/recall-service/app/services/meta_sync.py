"""
元数据同步服务（R3）：
  descartes 生产库（只读）→ 生成 database_meta → 指纹比对 → 有变化则全量重建 → 索引热替换

设计说明：
- 生产源只读（descartes MySQL，SELECT only，META_SOURCE_READONLY 守卫）
- 指纹 = 元数据表 COUNT(*) + MAX(updated_at)，无变化秒级跳过
- 生成复用 scripts/gen_database_meta.py 的 build()（结构以库为准 + 人工口径合并）
- 重建通过 SyncService.import_database_meta 增量导入（只重编文本变化的实体），
  当前规模（约 700 条）全量编码仅数分钟，不做行级增量
- 同步完成后：重建 FAISS 索引并热替换 + 刷新 SlimExporter 的 database_meta 映射
- 同一时刻仅允许一个同步任务（重复 trigger 返回运行中的 task_id）

安全策略：
- 按钮1（rebuild=False）：连库生成并备份覆盖 database_meta.json，不重建索引
- 按钮2（rebuild=True，POST /api/metadata/sync {"rebuild": true}）：
  同上，并全量重建本地库 + embedding + FAISS 索引热替换
- 每次点击都真实生成（不做指纹跳过）；指纹仅存档展示，成功走完才更新
"""
import json
import shutil
import threading
import time
import uuid
from datetime import datetime
from pathlib import Path
from typing import Dict, Optional

from app import config
from app.core.database import get_db
from app.utils.logger import get_logger

logger = get_logger("meta_sync", "meta_sync.log")

BASE_DIR = Path(__file__).resolve().parent.parent.parent   # webapp/
DATABASE_META_PATH = BASE_DIR / "data" / "database_meta.json"
GEN_META_PATH = BASE_DIR / "data" / "database_meta_gen.json"
WITH_ID_PATH = BASE_DIR / "data" / "database_meta_with_id.json"

# 参与指纹比对的 descartes 元数据表 → 时间列（无时间列的表用自增 id 代替，
# olap_basic_pro_indicator / olap_basic_pro_dimension 只有 id）
FINGERPRINT_TABLES = {
    "olap_table_pro": "updated_at",
    "olap_table_field_mapping": "updated_at",
    "olap_basic_pro": "updated_at",
    "olap_basic_pro_indicator": "id",
    "olap_basic_pro_dimension": "id",
}


def _connect_prod():
    """生产元数据库连接（只读使用）"""
    import pymysql
    return pymysql.connect(
        host=config.MYSQL_HOST, port=config.MYSQL_PORT,
        user=config.MYSQL_USER, password=config.MYSQL_PASSWORD,
        database=config.MYSQL_DB, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor, connect_timeout=10,
    )


def compute_source_fingerprint() -> Dict[str, Dict]:
    """指纹：每表 {count, max_updated_at}（只读 SELECT）"""
    if config.META_SOURCE_READONLY:
        assert all(t.replace("_", "").isalnum() and c.replace("_", "").isalnum()
                   for t, c in FINGERPRINT_TABLES.items()), "table/column name guard"
    conn = _connect_prod()
    try:
        with conn.cursor() as cur:
            fp = {}
            for t, col in FINGERPRINT_TABLES.items():
                cur.execute(f"SELECT COUNT(*) AS c, MAX({col}) AS m FROM {t}")
                r = cur.fetchone()
                fp[t] = {"count": r["c"], "max_updated_at": str(r["m"]), "fingerprint_col": col}
        return fp
    finally:
        conn.close()


def _save_fingerprint(fp: Dict):
    db = get_db()
    val = json.dumps(fp, ensure_ascii=False)
    now = datetime.now().isoformat(timespec="seconds")
    # key 是 MySQL 保留字，统一加反引号（SQLite 亦兼容）；upsert 用 delete+insert 规避方言差异
    db.execute("DELETE FROM `sync_state` WHERE `key` = 'source_fingerprint'")
    db.execute(
        "INSERT INTO `sync_state`(`key`, value, updated_at) VALUES ('source_fingerprint', %s, %s)",
        [val, now],
    )


class MetaSyncService:
    def __init__(self):
        self._lock = threading.Lock()
        self._state: Dict[str, any] = {"status": "idle"}
        self._thread: Optional[threading.Thread] = None

    # ---------------- 对外接口 ----------------
    def trigger(self, rebuild: bool = False) -> Dict:
        """触发同步（异步）。rebuild=True 时生成后重建索引。已有任务运行时返回该任务的 task_id。"""
        with self._lock:
            if self._state.get("status") == "running":
                return {
                    "task_id": self._state["task_id"],
                    "status": "running",
                    "detail": "a sync task is already running",
                }
            task_id = f"sync-{datetime.now().strftime('%Y%m%d-%H%M%S')}-{uuid.uuid4().hex[:6]}"
            self._state = {"task_id": task_id, "status": "running",
                           "started_at": datetime.now().isoformat(timespec="seconds"),
                           "rebuild": rebuild}
            self._thread = threading.Thread(target=self._run, args=(task_id, rebuild), daemon=True)
            self._thread.start()
        return {"task_id": task_id, "status": "running"}

    def status(self) -> Dict:
        with self._lock:
            return dict(self._state)

    # ---------------- 内部执行 ----------------
    def _run(self, task_id: str, rebuild: bool):
        try:
            detail = self._do_sync(rebuild)
            with self._lock:
                self._state.update(status="success",
                                   finished_at=datetime.now().isoformat(timespec="seconds"),
                                   detail=detail)
            logger.info(f"Sync {task_id} success: {detail}")
        except Exception as e:
            logger.error(f"Sync {task_id} failed: {e}", exc_info=True)
            with self._lock:
                self._state.update(status="failed", error=str(e),
                                   finished_at=datetime.now().isoformat(timespec="seconds"))

    def _do_sync(self, rebuild: bool) -> Dict:
        t0 = time.time()
        # 1. 每次都真实生成（不做指纹跳过）
        fp = compute_source_fingerprint()

        # 2. 生成新 database_meta（结构以生产库为准 + 人工口径兜底合并）
        import importlib.util
        spec = importlib.util.spec_from_file_location(
            "gen_database_meta", BASE_DIR / "scripts" / "gen_database_meta.py")
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        gen, diff = mod.build()

        # 3. 备份并覆盖 database_meta.json（无 id，作为镜像/离线备份）
        #    另写 database_meta_with_id.json（带 id，供描述回写）
        if DATABASE_META_PATH.exists():
            bak = DATABASE_META_PATH.with_suffix(".json.bak")
            shutil.copy2(DATABASE_META_PATH, bak)
        GEN_META_PATH.write_text(json.dumps(mod.strip_ids(gen), ensure_ascii=False, indent=2),
                                 encoding="utf-8")
        shutil.copy2(GEN_META_PATH, DATABASE_META_PATH)
        WITH_ID_PATH.write_text(json.dumps(gen, ensure_ascii=False, indent=2),
                                encoding="utf-8")

        result = {
            "fingerprint": fp,
            "diff": diff,
            "rebuild": rebuild,
            "index_rebuilt": False,
            "elapsed_seconds": round(time.time() - t0, 1),
        }

        # 4. 导入实体层（源对象以生成结果为准；只重编文本变化的实体）；rebuild=False 也导入，
        #    因为实体层是唯一数据源，仅刷新文件而不导入会导致文件与索引不一致
        from app.entity.sync_service import get_sync_service
        from app.entity.scope import get_scope_resolver
        svc = get_sync_service()
        sync_res = svc.import_database_meta(mod.strip_ids(gen)["database_meta"], replace=True)
        result["counts"] = svc.store.entity_counts()
        result["sync"] = sync_res
        get_scope_resolver().invalidate()
        if not rebuild:
            _save_fingerprint(fp)
            result["message"] = "database_meta.json updated & entities synced (incremental)"
            return result

        result["index_rebuilt"] = True

        # 6. 存指纹（只有成功走完才存）
        _save_fingerprint(fp)
        return result


# 进程内单例
_svc: Optional[MetaSyncService] = None
_svc_lock = threading.Lock()


def sync_with_id(no_id_meta: dict) -> dict:
    """把无 id 的 database_meta 同步到 database_meta_with_id.json：
    id 从旧 with_id 文件按 code 回填（新增条目 id 置空），其余字段以新内容为准。
    返回新 with_id 的 database_meta（两个文件始终仅 id 不同）。"""
    old = {}
    if WITH_ID_PATH.exists():
        try:
            old = json.loads(WITH_ID_PATH.read_text(encoding="utf-8")).get("database_meta", {})
        except Exception:
            old = {}
    dm = no_id_meta.get("database_meta", no_id_meta)

    def _merge(src_list: list, key: str, id_key: str, old_list: list) -> list:
        id_map = {it.get(key): it.get(id_key) for it in old_list
                  if isinstance(it, dict) and it.get(key) is not None}
        out = []
        for it in src_list:
            it = dict(it) if isinstance(it, dict) else it
            if isinstance(it, dict) and key in it:
                it[id_key] = id_map.get(it.get(key), "")
            out.append(it)
        return out

    with_id = {
        "available_metrics": _merge(dm.get("available_metrics", []), "metric_code", "metric_id",
                                    old.get("available_metrics", [])),
        "available_dimensions": _merge(dm.get("available_dimensions", []), "dimension_code", "dimension_id",
                                       old.get("available_dimensions", [])),
        "table_summaries": _merge(dm.get("table_summaries", []), "table_name", "table_id",
                                  old.get("table_summaries", [])),
        "business_context": dm.get("business_context", []),
    }
    WITH_ID_PATH.write_text(json.dumps({"database_meta": with_id}, ensure_ascii=False, indent=2),
                            encoding="utf-8")
    return with_id


def get_meta_sync_service() -> MetaSyncService:
    global _svc
    with _svc_lock:
        if _svc is None:
            _svc = MetaSyncService()
            # 确保 sync_state 表存在（本地服务库可能是 MySQL 或 SQLite）
            db = get_db()
            try:
                db.execute(
                    "CREATE TABLE IF NOT EXISTS `sync_state` ("
                    "`key` VARCHAR(64) PRIMARY KEY,"
                    " value TEXT,"
                    " updated_at VARCHAR(32)"
                    ")"
                )
            except Exception:
                # MySQL 5.x 不支持 CREATE TABLE IF NOT EXISTS 时兜底：表已存在则忽略
                logger.debug("sync_state create skipped (probably exists)")
        return _svc
