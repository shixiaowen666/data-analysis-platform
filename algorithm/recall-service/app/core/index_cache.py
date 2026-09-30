"""database_meta.json 文件指纹缓存：用于启动时判断是否需要全量重建。

- 缓存文件: data/index_cache.json，记录上次成功 rebuild 时 database_meta.json 的 SHA256
- 启动时比对：一致 → 直接从 DB 加载索引；不一致/缺失 → 触发全量 rebuild
- rebuild 接口成功后写入最新指纹，保证"接口重建"与"启动检测"用同一份依据
"""
import hashlib
import json
import time
from pathlib import Path
from typing import Optional

from app.utils.logger import get_logger

logger = get_logger("index_cache", "index_cache.log")

_CACHE_PATH = Path(__file__).resolve().parent.parent.parent / "data" / "index_cache.json"
_DATABASE_META_PATH = _CACHE_PATH.parent / "database_meta.json"


def sha256_of(path: Path) -> Optional[str]:
    if not path.exists():
        return None
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def current_database_meta_hash() -> Optional[str]:
    """database_meta.json 不存在时返回 None（全新部署/未 rebuild 的正常状态）。"""
    return sha256_of(_DATABASE_META_PATH)


def load_cached_hash() -> Optional[str]:
    if not _CACHE_PATH.exists():
        return None
    try:
        return json.loads(_CACHE_PATH.read_text(encoding="utf-8")).get("database_meta_hash")
    except Exception as e:
        logger.warning(f"index_cache.json unreadable, treat as changed: {e}")
        return None


def needs_rebuild() -> bool:
    """DB 由调用方判断空库；这里只回答"文件是否比上次 rebuild 时新"。"""
    if not _DATABASE_META_PATH.exists():
        return False
    return load_cached_hash() != current_database_meta_hash()


def save_cache(database_meta_hash: Optional[str] = None):
    final_hash = database_meta_hash or current_database_meta_hash()
    _CACHE_PATH.write_text(
        json.dumps({
            "database_meta_hash": final_hash,
            "updated_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    logger.info(f"index_cache.json updated: hash={ (final_hash or 'none')[:12] }...")
