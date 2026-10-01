"""
Prompt version manager.
Handles prompt file storage, versioning, and memory caching.
"""

import logging
import re
from datetime import datetime
from pathlib import Path
from typing import Optional

from system_b.config import Config
from system_b.utils import db
from system_b.utils.tuning_support import get_prompt_override

logger = logging.getLogger(__name__)

# In-memory cache: {group_name: (version, content)}
_cache: dict = {}

# Preset groups that cannot be deleted
_PRESET_GROUPS = {"prompt-main", "prompt-summary"}


def invalidate_cache(group_name: str):
    """Invalidate the cache for a specific group."""
    _cache.pop(group_name, None)
    logger.debug(f"[PromptManager] Cache invalidated for group '{group_name}'")


def invalidate_all_cache():
    """Invalidate all cached prompts."""
    _cache.clear()
    logger.info("[PromptManager] All caches invalidated")


def _read_file_content(file_path: str) -> str:
    """Read content from a prompt file."""
    prompts_base = Path(Config.PROMPTS_DIR)
    full_path = prompts_base / file_path
    if not full_path.exists():
        logger.warning(f"[PromptManager] File not found: {full_path}")
        return ""
    return full_path.read_text(encoding="utf-8")


def _write_file_content(file_path: str, content: str):
    """Write content to a prompt file, creating directories as needed."""
    prompts_base = Path(Config.PROMPTS_DIR)
    full_path = prompts_base / file_path
    full_path.parent.mkdir(parents=True, exist_ok=True)
    full_path.write_text(content, encoding="utf-8")


def _delete_file(file_path: str):
    """Delete a prompt file."""
    prompts_base = Path(Config.PROMPTS_DIR)
    full_path = prompts_base / file_path
    if full_path.exists():
        full_path.unlink()


def _increment_version(version: str) -> str:
    """
    Auto-increment the patch number of a version string.
    v1.0.0 -> v1.0.1, v1.0.9 -> v1.0.10
    Falls back to appending '.1' if version format is unrecognized.
    """
    match = re.match(r"^(v?\d+\.\d+)\.(\d+)$", version)
    if match:
        prefix = match.group(1)
        patch = int(match.group(2))
        return f"{prefix}.{patch + 1}"
    return f"{version}.1"


# ==============================================================================
# Get current prompt (used by step_decomposer)
# ==============================================================================


def get_current_prompt(group_name: str) -> tuple:
    """
    Get the current active prompt content and metadata for a group.

    Returns:
        (content, meta) where meta is {"group": group_name, "version": "v1.0.0"}
        Returns ("", {}) if group or active version not found.
    Uses memory cache. If cache miss, reads from DB and file system.
    """
    # 调优草稿验证：prompt_overrides 指定非激活版本（线程级覆盖，不写缓存、不影响线上）
    override_version = get_prompt_override(group_name)
    if override_version:
        group = db.get_prompt_group_by_name(group_name)
        if not group:
            logger.warning(f"[PromptManager] Group '{group_name}' not found (override)")
            return "", {}
        target = db.get_version_by_group_and_version(group["id"], override_version)
        if not target:
            raise ValueError(f"prompt_overrides 指定的版本不存在: {group_name}/{override_version}")
        content = _read_file_content(target["file_path"])
        logger.info(
            f"[PromptManager] OVERRIDE '{group_name}' version={override_version} "
            f"(draft verification, not cached)"
        )
        return content, {"group": group_name, "version": override_version, "override": True}

    # Check cache
    if group_name in _cache:
        version, content = _cache[group_name]
        logger.debug(f"[PromptManager] Cache hit for '{group_name}' version={version}")
        return content, {"group": group_name, "version": version}

    # Get group by name
    group = db.get_prompt_group_by_name(group_name)
    if not group:
        logger.warning(f"[PromptManager] Group '{group_name}' not found")
        return "", {}

    # Get active version
    active = db.get_active_version(group["id"])
    if not active:
        logger.warning(f"[PromptManager] No active version for group '{group_name}'")
        return "", {}

    content = _read_file_content(active["file_path"])
    _cache[group_name] = (active["version"], content)
    logger.info(
        f"[PromptManager] Loaded '{group_name}' version={active['version']} "
        f"file={active['file_path']} length={len(content)}"
    )
    return content, {"group": group_name, "version": active["version"]}


# ==============================================================================
# Group management
# ==============================================================================

def list_groups(keyword: str = "") -> list:
    """List all prompt groups."""
    return db.list_prompt_groups(keyword=keyword)


def create_group(name: str, label: str, description: str = "") -> dict:
    """Create a new prompt group and its directory."""
    existing = db.get_prompt_group_by_name(name)
    if existing:
        raise ValueError(f"分组 '{name}' 已存在")

    group_id = db.create_prompt_group(name, label, description)

    # Create directory
    prompts_base = Path(Config.PROMPTS_DIR)
    group_dir = prompts_base / name
    group_dir.mkdir(parents=True, exist_ok=True)

    logger.info(f"[PromptManager] group_create: group='{name}' label='{label}'")
    return {
        "id": group_id,
        "name": name,
        "label": label,
        "description": description,
    }


def update_group(group_id: int, label: str = None, description: str = None) -> int:
    """Update a prompt group."""
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")
    return db.update_prompt_group(group_id, label=label, description=description)


def delete_group(group_id: int):
    """
    Delete a prompt group (DB records only, files are kept).
    Preset groups (prompt-main, prompt-summary) cannot be deleted.
    """
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    if group["name"] in _PRESET_GROUPS:
        raise ValueError(f"预设分组 '{group['name']}' 不可删除")

    # Invalidate cache
    invalidate_cache(group["name"])

    # Delete DB records (version records cascade)
    db.delete_prompt_group(group_id)
    logger.info(f"[PromptManager] Group '{group['name']}' deleted (files preserved)")


# ==============================================================================
# Version management
# ==============================================================================

def update_version(
    group_id: int,
    content: str,
    version: str = None,
    description: str = "",
) -> dict:
    """
    Create/update a prompt version.

    - If version is provided: use it directly; if it already exists,
      auto-increment (v1.0.0 → v1.0.1 → …) until a free version is found.
      Does NOT auto-activate.
    - If version is omitted: start from v1.0.0 and auto-increment if needed.
      Auto-activates.
    """
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    if version:
        if not re.match(r"^v?\d+\.\d+\.\d+$", version):
            raise ValueError("version 格式错误，应为 vX.Y.Z")
        while db.get_version_by_group_and_version(group_id, version):
            version = _increment_version(version)
        set_active = False
    else:
        version = "v1.0.0"
        while db.get_version_by_group_and_version(group_id, version):
            version = _increment_version(version)
        set_active = True

    file_path = f"{group['name']}/{version}.txt"
    _write_file_content(file_path, content)

    is_active = 1 if set_active else 0
    if set_active:
        _deactivate_all_versions(group_id)

    version_id = db.insert_prompt_version(
        group_id=group_id,
        version=version,
        file_path=file_path,
        description=description,
        is_active=is_active,
    )

    if set_active:
        invalidate_cache(group["name"])

    result = {
        "version_id": version_id,
        "version": version,
        "file_path": file_path,
        "is_active": bool(is_active),
    }

    logger.info(
        f"[PromptManager] Version saved: '{group['name']}' {version}"
    )
    return result


def list_versions(
    group_id: int,
    keyword: str = "",
    is_active: bool = None,
    page: int = 1,
    page_size: int = 20,
) -> dict:
    """List versions for a group."""
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    result = db.query_prompt_versions(
        group_id=group_id,
        keyword=keyword,
        is_active=is_active,
        page=page,
        page_size=page_size,
    )

    # Add current active version info
    active = db.get_active_version(group_id)
    result["current_version"] = active["version"] if active else None

    return result


def switch_version(group_id: int, version: str) -> dict:
    """
    Switch the active version of a group.
    Program-controlled: deactivate all, then activate target.
    """
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    target = db.get_version_by_group_and_version(group_id, version)
    if not target:
        raise ValueError(f"版本 '{version}' 不存在")

    old_active = db.get_active_version(group_id)
    old_version = old_active["version"] if old_active else "none"

    db.set_active_version(group_id, target["id"])
    invalidate_cache(group["name"])

    logger.info(
        f"[PromptManager] switch: group='{group['name']}' "
        f"{old_version} -> {version}"
    )
    return {
        "version_id": target["id"],
        "version": version,
        "is_active": True,
    }


def get_version_content(group_id: int, version: str) -> Optional[dict]:
    """Get a specific version's info with content."""
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    target = db.get_version_by_group_and_version(group_id, version)
    if not target:
        return None

    content = _read_file_content(target["file_path"])
    return {
        "version_id": target["id"],
        "version": target["version"],
        "content": content,
        "description": target["description"],
        "is_active": bool(target["is_active"]),
        "updated_at": target["updated_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(target["updated_at"], datetime) else str(target["updated_at"]),
    }


def get_current_version(group_id: int) -> Optional[dict]:
    """Get the current active version info with content."""
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    active = db.get_active_version(group_id)
    if not active:
        return None

    content = _read_file_content(active["file_path"])
    return {
        "version_id": active["id"],
        "version": active["version"],
        "content": content,
        "description": active["description"],
        "updated_at": active["updated_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(active["updated_at"], datetime) else str(active["updated_at"]),
    }


def delete_version(group_id: int, version: str):
    """
    Delete a prompt version. Active version cannot be deleted.
    Deletes both file and DB record.
    """
    group = db.get_prompt_group_by_id(group_id)
    if not group:
        raise ValueError(f"分组 id={group_id} 不存在")

    target = db.get_version_by_group_and_version(group_id, version)
    if not target:
        raise ValueError(f"版本 '{version}' 不存在")

    if target["is_active"]:
        raise ValueError("当前生效版本不可删除，请先切换到其他版本")

    # Delete file
    _delete_file(target["file_path"])

    # Delete DB record
    db.delete_prompt_version(target["id"])

    logger.info(f"[PromptManager] Deleted version '{version}' from '{group['name']}'")


def _deactivate_all_versions(group_id: int):
    """Set is_active=0 for all versions in a group."""
    conn = None
    try:
        conn = db.get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "UPDATE prompt_version SET is_active = 0 WHERE group_id = %s",
                (group_id,),
            )
        conn.commit()
    except Exception as exc:
        logger.exception(f"[PromptManager] Failed to deactivate versions: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def ensure_initial_versions():
    """Ensure each preset group has at least one active version.
    Scans prompts/{group_name}/ for .txt files and creates version records
    if none exist. Idempotent — does nothing if versions already exist.
    """
    from pathlib import Path

    prompts_base = Path(Config.PROMPTS_DIR)
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    for group_name in _PRESET_GROUPS:
        group = db.get_prompt_group_by_name(group_name)
        if not group:
            continue

        # Check if any version already exists
        active = db.get_active_version(group["id"])
        if active:
            continue

        # Scan for .txt files in the group directory
        group_dir = prompts_base / group_name
        txt_files = sorted(group_dir.glob("*.txt")) if group_dir.exists() else []
        if not txt_files:
            continue

        # Create version records for each file, last one is active
        for i, txt_file in enumerate(txt_files):
            version_name = txt_file.stem  # e.g. "v1.0.0"
            file_path_str = f"{group_name}/{txt_file.name}"
            is_active = 1 if i == len(txt_files) - 1 else 0

            existing = db.get_version_by_group_and_version(group["id"], version_name)
            if existing:
                continue

            db.insert_prompt_version(
                group_id=group["id"],
                version=version_name,
                file_path=file_path_str,
                description="",
                is_active=is_active,
            )
            logger.info(
                "[PromptManager] Seeded version '%s' for '%s' (active=%d)",
                version_name, group_name, is_active,
            )

        # Invalidate cache for this group
        invalidate_cache(group_name)