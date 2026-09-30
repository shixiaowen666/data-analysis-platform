"""
Database module for MySQL operations.
"""

import json
import logging
from typing import Optional
from datetime import datetime, date, timedelta
from decimal import Decimal

from system_b.config import Config

logger = logging.getLogger(__name__)


def _pymysql():
    """延迟导入 pymysql：其依赖链（cryptography→bcrypt）含原生 DLL，
    损坏时不应阻断与数据库无关的单元测试收集。"""
    import pymysql

    return pymysql


def get_connection():
    """Get MySQL connection."""
    logger.debug(
        "[DB] MySQL connection: host=%s, port=%s, user=%s, database=%s",
        Config.MYSQL_HOST,
        Config.MYSQL_PORT,
        Config.MYSQL_USER,
        Config.MYSQL_DATABASE,
    )
    pymysql = _pymysql()
    return pymysql.connect(
        host=Config.MYSQL_HOST,
        port=Config.MYSQL_PORT,
        user=Config.MYSQL_USER,
        password=Config.MYSQL_PASSWORD,
        database=Config.MYSQL_DATABASE,
        cursorclass=pymysql.cursors.DictCursor,
    )


# ==============================================================================
# Old prompt_template table (kept for backward compatibility, not used)
# ==============================================================================

def get_prompts(prompt_id: int = 1) -> tuple:
    """Get system_prompt and user_prompt. Returns (system_prompt, user_prompt) or (None, None) if DB unavailable."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT system_prompt, user_prompt FROM prompt_template WHERE id = %s",
                (prompt_id,),
            )
            result = cursor.fetchone()
            if result:
                return result["system_prompt"], result["user_prompt"]
            logger.info(f"[DB] No prompt row found in prompt_template for id={prompt_id}; using defaults")
            return None, None
    except Exception as exc:
        logger.exception(f"[DB] Failed to load prompts from MySQL for id={prompt_id}: {exc}")
        return None, None
    finally:
        if conn:
            try:
                conn.close()
            except Exception:
                pass


def get_prompt_record(prompt_id: int = 1) -> Optional[dict]:
    """Get full prompt record by id. Returns None if not found or unavailable."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT id, system_prompt, user_prompt FROM prompt_template WHERE id = %s",
                (prompt_id,),
            )
            result = cursor.fetchone()
            if result:
                return result
            logger.info(f"[DB] No prompt record found in prompt_template for id={prompt_id}")
            return None
    except Exception as exc:
        logger.exception(f"[DB] Failed to load prompt record from MySQL for id={prompt_id}: {exc}")
        return None
    finally:
        if conn:
            try:
                conn.close()
            except Exception:
                pass


def save_prompts(system_prompt: str, user_prompt: str, prompt_id: Optional[int] = 1) -> int:
    """Save prompts and return the persisted prompt id."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            if prompt_id is None:
                cursor.execute(
                    "INSERT INTO prompt_template (system_prompt, user_prompt) VALUES (%s, %s)",
                    (system_prompt, user_prompt),
                )
                saved_id = cursor.lastrowid
            else:
                cursor.execute(
                    """INSERT INTO prompt_template (id, system_prompt, user_prompt)
                       VALUES (%s, %s, %s)
                       ON DUPLICATE KEY UPDATE system_prompt = %s, user_prompt = %s""",
                    (prompt_id, system_prompt, user_prompt, system_prompt, user_prompt),
                )
                saved_id = prompt_id
        conn.commit()
        logger.info(f"[DB] Prompts saved to prompt_template(id={saved_id})")
        return saved_id
    except Exception as exc:
        logger.exception(f"[DB] Failed to save prompts to MySQL for id={prompt_id}: {exc}")
        raise
    finally:
        if conn:
            try:
                conn.close()
            except Exception:
                pass


# ==============================================================================
# User operation log table
# ==============================================================================

def ensure_user_operation_log_table():
    """Create user_operation_log table if not exists; migrate legacy tables
    that lack the question column."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS user_operation_log (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id     VARCHAR(64)  NOT NULL,
                    username    VARCHAR(128) NOT NULL,
                    log_date    DATE         NOT NULL,
                    log_path    VARCHAR(512) NOT NULL,
                    question    VARCHAR(1024) NOT NULL DEFAULT '',
                    created_at  DATETIME     NOT NULL,
                    updated_at  DATETIME     NOT NULL,
                    INDEX idx_user_id (user_id),
                    INDEX idx_log_date (log_date),
                    INDEX idx_user_date (user_id, log_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            cursor.execute(
                """SELECT COUNT(*) AS cnt FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'user_operation_log'
                     AND COLUMN_NAME = 'question'"""
            )
            if cursor.fetchone()["cnt"] == 0:
                cursor.execute(
                    "ALTER TABLE user_operation_log "
                    "ADD COLUMN question VARCHAR(1024) NOT NULL DEFAULT '' AFTER log_path"
                )
                logger.info("[DB] Migrated user_operation_log: added question column")
        conn.commit()
        logger.info("[DB] Ensured user_operation_log table exists")
    except Exception as exc:
        logger.exception(f"[DB] Failed to ensure user_operation_log table: {exc}")
    finally:
        if conn:
            conn.close()


def insert_operation_log(user_id: str, username: str, log_date: str,
                         log_path: str, question: str,
                         created_at: str = "") -> bool:
    """Insert one row per question. Returns True on success, False on failure (does not raise)."""
    conn = None
    try:
        now = created_at or datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        # JWT 中的 user_id 可能是数字类型，统一转 str 再截断
        question = str(question or "").strip()[:1024]
        username = str(username or "")[:128]
        user_id = str(user_id or "")[:64]
        log_path = str(log_path or "")[:512]
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                """INSERT INTO user_operation_log
                   (user_id, username, log_date, log_path, question, created_at, updated_at)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                (user_id, username, log_date, log_path, question, now, now),
            )
        conn.commit()
        return True
    except Exception as exc:
        logger.exception(f"[DB] Failed to upsert operation log: {exc}")
        return False
    finally:
        if conn:
            conn.close()


def query_operation_logs(
    user_id: str = "",
    username: str = "",
    log_date_start: str = "",
    log_date_end: str = "",
    question: str = "",
    page: int = 1,
    page_size: int = 20,
) -> dict:
    """Query operation logs with filters and pagination."""
    conn = None
    try:
        conn = get_connection()
        conditions = []
        params = []

        if user_id:
            conditions.append("user_id LIKE %s")
            params.append(f"%{user_id}%")
        if username:
            conditions.append("username LIKE %s")
            params.append(f"%{username}%")
        if log_date_start:
            conditions.append("log_date >= %s")
            params.append(log_date_start)
        if log_date_end:
            conditions.append("log_date <= %s")
            params.append(log_date_end)
        if question:
            # 包含匹配：转义用户输入中的 LIKE 通配符，保证按字面匹配
            kw = question.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            conditions.append("question LIKE %s")
            params.append(f"%{kw}%")

        where_clause = " WHERE " + " AND ".join(conditions) if conditions else ""

        with conn.cursor() as cursor:
            cursor.execute(
                f"SELECT COUNT(*) AS total FROM user_operation_log{where_clause}",
                params,
            )
            total = cursor.fetchone()["total"]

            offset = (page - 1) * page_size
            cursor.execute(
                f"""SELECT id, user_id, username, log_date, log_path, question, created_at, updated_at
                    FROM user_operation_log{where_clause}
                    ORDER BY updated_at DESC
                    LIMIT %s OFFSET %s""",
                params + [page_size, offset],
            )
            rows = cursor.fetchall()

        list_rows = []
        if rows and isinstance(rows, list):
            for row in rows:
                row["created_at"] = row["created_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row["created_at"], datetime) else str(row["created_at"])
                row["updated_at"] = row["updated_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row["updated_at"], datetime) else str(row["updated_at"])
                row["log_date"] = row["log_date"].strftime("%Y-%m-%d") if isinstance(row["log_date"], (date, datetime)) else str(row["log_date"])
                list_rows.append(row)

        return {
            "total": total,
            "page": page,
            "page_size": page_size,
            "list": list_rows,
        }
    except Exception as exc:
        logger.exception(f"[DB] Failed to query operation logs: {exc}")
        return {"total": 0, "page": page, "page_size": page_size, "list": []}
    finally:
        if conn:
            conn.close()


def delete_operation_log_by_user_date(user_id: str, log_date: str = None) -> int:
    """Delete user_operation_log records for a user.
    log_date optional: delete all dates when omitted.
    Returns number of rows deleted.
    """
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            if log_date:
                cursor.execute(
                    "DELETE FROM user_operation_log WHERE user_id = %s AND log_date = %s",
                    (user_id, log_date),
                )
            else:
                cursor.execute("DELETE FROM user_operation_log WHERE user_id = %s", (user_id,))
            conn.commit()
            return cursor.rowcount
    except Exception as exc:
        logger.exception(f"[DB] Failed to delete operation log: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def delete_operation_log_by_log_path_prefix(user_id: str, log_date: str, log_paths: list) -> int:
    """Delete user_operation_log rows whose log_path is in the given list.
    Rows are scoped by user_id + log_date for index usage. Returns rows deleted."""
    if not log_paths:
        return 0
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                f"""DELETE FROM user_operation_log
                    WHERE user_id = %s AND log_date = %s
                      AND log_path IN ({', '.join(['%s'] * len(log_paths))})""",
                [user_id, log_date] + list(log_paths),
            )
        conn.commit()
        return cursor.rowcount
    except Exception as exc:
        logger.exception(f"[DB] Failed to delete operation logs by path: {exc}")
        return 0
    finally:
        if conn:
            conn.close()


def get_distinct_users_with_logs() -> list:
    """Get distinct users who have operation logs."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("""
                SELECT t.user_id, t.username
                FROM user_operation_log t
                INNER JOIN (
                    SELECT user_id, MAX(updated_at) AS max_updated
                    FROM user_operation_log
                    GROUP BY user_id
                ) latest ON t.user_id = latest.user_id AND t.updated_at = latest.max_updated
                ORDER BY t.user_id ASC
            """)
            rows = cursor.fetchall()
            return list(rows) if rows else []
    except Exception as exc:
        logger.exception(f"[DB] Failed to get distinct users: {exc}")
        return []
    finally:
        if conn:
            conn.close()


# ==============================================================================
# Batch test history table
# ==============================================================================

def ensure_batch_record_table():
    """Create batch_record table if not exists."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS batch_record (
                    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_id       VARCHAR(48)  NOT NULL UNIQUE,
                    user_id        VARCHAR(64)  NOT NULL,
                    question_count INT          NOT NULL DEFAULT 0,
                    ok_count       INT          NOT NULL DEFAULT 0,
                    err_count      INT          NOT NULL DEFAULT 0,
                    duration_ms    INT          NOT NULL DEFAULT 0,
                    items_json     MEDIUMTEXT,
                    created_at     DATETIME     NOT NULL,
                    updated_at     DATETIME     NOT NULL,
                    INDEX idx_batch_user (user_id),
                    INDEX idx_batch_created (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
        conn.commit()
        logger.info("[DB] Ensured batch_record table exists")
    except Exception as exc:
        logger.exception(f"[DB] Failed to ensure batch_record table: {exc}")
    finally:
        if conn:
            conn.close()


def upsert_batch_record(batch_id: str, user_id: str, items: list,
                        duration_ms: int = 0, created_at: str = "") -> bool:
    """Insert or overwrite a batch snapshot. items 与前端 batchItems 同构
    （[{q, status, ms, error, log_path}]）。 Returns True on success."""
    conn = None
    try:
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        created = created_at or now
        items = items or []
        stats = {
            "total": len(items),
            "ok": sum(1 for x in items if x.get("status") == "ok"),
            "err": sum(1 for x in items if x.get("status") == "err"),
        }
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                """INSERT INTO batch_record
                   (batch_id, user_id, question_count, ok_count, err_count,
                    duration_ms, items_json, created_at, updated_at)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                   ON DUPLICATE KEY UPDATE
                     question_count = VALUES(question_count),
                     ok_count       = VALUES(ok_count),
                     err_count      = VALUES(err_count),
                     duration_ms    = VALUES(duration_ms),
                     items_json     = VALUES(items_json),
                     updated_at     = VALUES(updated_at)""",
                (str(batch_id)[:48], str(user_id)[:64], stats["total"], stats["ok"],
                 stats["err"], int(duration_ms or 0),
                 json.dumps(items, ensure_ascii=False), created, now),
            )
        conn.commit()
        return True
    except Exception as exc:
        logger.exception(f"[DB] Failed to upsert batch_record: {exc}")
        return False
    finally:
        if conn:
            conn.close()


def query_batch_records(user_id: str = "", page: int = 1, page_size: int = 20) -> dict:
    """分页列出批次（不含 items）。"""
    conn = None
    try:
        conn = get_connection()
        conditions, params = [], []
        if user_id:
            conditions.append("user_id = %s")
            params.append(user_id)
        where_clause = " WHERE " + " AND ".join(conditions) if conditions else ""
        with conn.cursor() as cursor:
            cursor.execute(f"SELECT COUNT(*) AS total FROM batch_record{where_clause}", params)
            total = cursor.fetchone()["total"]
            offset = (page - 1) * page_size
            cursor.execute(
                f"""SELECT batch_id, user_id, question_count, ok_count, err_count,
                           duration_ms, created_at, updated_at
                    FROM batch_record{where_clause}
                    ORDER BY created_at DESC
                    LIMIT %s OFFSET %s""",
                params + [page_size, offset],
            )
            rows = cursor.fetchall()
        for row in rows or []:
            for k in ("created_at", "updated_at"):
                row[k] = row[k].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row[k], datetime) else str(row[k])
        return {"total": total, "page": page, "page_size": page_size, "list": rows or []}
    except Exception as exc:
        logger.exception(f"[DB] Failed to query batch_record: {exc}")
        return {"total": 0, "page": page, "page_size": page_size, "list": []}
    finally:
        if conn:
            conn.close()


def get_batch_record(batch_id: str) -> Optional[dict]:
    """按 batch_id 取批次详情（含 items）。不存在返回 None。"""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM batch_record WHERE batch_id = %s", (str(batch_id)[:48],)
            )
            row = cursor.fetchone()
        if not row:
            return None
        for k in ("created_at", "updated_at"):
            row[k] = row[k].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row[k], datetime) else str(row[k])
        try:
            row["items"] = json.loads(row.pop("items_json") or "[]")
        except (ValueError, TypeError):
            row["items"] = []
        return row
    except Exception as exc:
        logger.exception(f"[DB] Failed to get batch_record {batch_id}: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def delete_batch_record(batch_id: str) -> tuple:
    """删除批次记录，返回 (是否删除, items)。"""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("SELECT items_json FROM batch_record WHERE batch_id = %s", (str(batch_id)[:48],))
            row = cursor.fetchone()
            if not row:
                return False, []
            cursor.execute("DELETE FROM batch_record WHERE batch_id = %s", (str(batch_id)[:48],))
        conn.commit()
        try:
            items = json.loads(row["items_json"] or "[]")
        except (ValueError, TypeError):
            items = []
        return True, items
    except Exception as exc:
        logger.exception(f"[DB] Failed to delete batch_record {batch_id}: {exc}")
        return False, []
    finally:
        if conn:
            conn.close()


# ==============================================================================
# Prompt group and version tables
# ==============================================================================

def ensure_prompt_group_table():
    """Create prompt_group table if not exists, and seed default groups."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS prompt_group (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    name        VARCHAR(64)  NOT NULL UNIQUE,
                    label       VARCHAR(128) NOT NULL,
                    description VARCHAR(512) DEFAULT '',
                    created_at  DATETIME     NOT NULL,
                    updated_at  DATETIME     NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
        conn.commit()

        # Seed default groups if not exist
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with conn.cursor() as cursor:
            cursor.execute("SELECT id FROM prompt_group WHERE name='prompt-main'")
            if not cursor.fetchone():
                cursor.execute(
                    """INSERT INTO prompt_group (id, name, label, description, created_at, updated_at)
                       VALUES (1, 'prompt-main', '主提示词', '', %s, %s)""",
                    (now, now),
                )

            cursor.execute("SELECT id FROM prompt_group WHERE name='prompt-summary'")
            if not cursor.fetchone():
                cursor.execute(
                    """INSERT INTO prompt_group (id, name, label, description, created_at, updated_at)
                       VALUES (2, 'prompt-summary', '摘要提示词', '', %s, %s)""",
                    (now, now),
                )

            # Ensure AUTO_INCREMENT starts from 3 for new groups
            cursor.execute("ALTER TABLE prompt_group AUTO_INCREMENT = 3")

        conn.commit()
        logger.info("[DB] Ensured prompt_group table exists")
    except Exception as exc:
        logger.exception(f"[DB] Failed to ensure prompt_group table: {exc}")
    finally:
        if conn:
            conn.close()


def ensure_prompt_version_table():
    """Create prompt_version table if not exists."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS prompt_version (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    group_id    INT          NOT NULL,
                    version     VARCHAR(32)  NOT NULL,
                    file_path   VARCHAR(512) NOT NULL,
                    is_active   TINYINT      NOT NULL DEFAULT 0,
                    description VARCHAR(512) DEFAULT '',
                    created_at  DATETIME     NOT NULL,
                    updated_at  DATETIME     NOT NULL,
                    UNIQUE KEY uk_group_version (group_id, version)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
        conn.commit()
        logger.info("[DB] Ensured prompt_version table exists")
    except Exception as exc:
        logger.exception(f"[DB] Failed to ensure prompt_version table: {exc}")
    finally:
        if conn:
            conn.close()


# ---- Prompt Group CRUD ----

def list_prompt_groups(keyword: str = "") -> list:
    """List all prompt groups with version count and active version."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            if keyword:
                cursor.execute(
                    """SELECT g.*,
                              (SELECT COUNT(*) FROM prompt_version v WHERE v.group_id = g.id) AS version_count,
                              (SELECT v2.version FROM prompt_version v2
                               WHERE v2.group_id = g.id AND v2.is_active = 1 LIMIT 1) AS active_version
                       FROM prompt_group g
                       WHERE g.name LIKE %s OR g.label LIKE %s
                       ORDER BY g.id ASC""",
                    (f"%{keyword}%", f"%{keyword}%"),
                )
            else:
                cursor.execute(
                    """SELECT g.*,
                              (SELECT COUNT(*) FROM prompt_version v WHERE v.group_id = g.id) AS version_count,
                              (SELECT v2.version FROM prompt_version v2
                               WHERE v2.group_id = g.id AND v2.is_active = 1 LIMIT 1) AS active_version
                       FROM prompt_group g
                       ORDER BY g.id ASC"""
                )
            rows = cursor.fetchall()
            result = []
            if rows:
                for row in rows:
                    row["created_at"] = row["created_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row["created_at"], datetime) else str(row["created_at"])
                    row["updated_at"] = row["updated_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row["updated_at"], datetime) else str(row["updated_at"])
                    result.append(row)
            return result
    except Exception as exc:
        logger.exception(f"[DB] Failed to list prompt groups: {exc}")
        return []
    finally:
        if conn:
            conn.close()


def create_prompt_group(name: str, label: str, description: str = "") -> int:
    """Create a new prompt group. Returns the new group id."""
    conn = None
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                """INSERT INTO prompt_group (name, label, description, created_at, updated_at)
                   VALUES (%s, %s, %s, %s, %s)""",
                (name, label, description, now, now),
            )
            group_id = cursor.lastrowid
        conn.commit()
        logger.info(f"[DB] Created prompt_group id={group_id} name={name}")
        return group_id
    except Exception as exc:
        logger.exception(f"[DB] Failed to create prompt group: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def get_prompt_group_by_id(group_id: int) -> Optional[dict]:
    """Get a prompt group by id."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("SELECT * FROM prompt_group WHERE id = %s", (group_id,))
            return cursor.fetchone()
    except Exception as exc:
        logger.exception(f"[DB] Failed to get prompt group: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def get_prompt_group_by_name(name: str) -> Optional[dict]:
    """Get a prompt group by name."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("SELECT * FROM prompt_group WHERE name = %s", (name,))
            return cursor.fetchone()
    except Exception as exc:
        logger.exception(f"[DB] Failed to get prompt group by name: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def update_prompt_group(group_id: int, label: str = None, description: str = None) -> int:
    """Update a prompt group. Returns affected rows."""
    conn = None
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn = get_connection()
        fields = []
        params = []
        if label is not None:
            fields.append("label = %s")
            params.append(label)
        if description is not None:
            fields.append("description = %s")
            params.append(description)
        if not fields:
            return 0
        fields.append("updated_at = %s")
        params.append(now)
        params.append(group_id)

        with conn.cursor() as cursor:
            affected = cursor.execute(
                f"UPDATE prompt_group SET {', '.join(fields)} WHERE id = %s",
                params,
            )
        conn.commit()
        return affected
    except Exception as exc:
        if conn:
            conn.rollback()
        logger.exception(f"[DB] Failed to update prompt group: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def delete_prompt_group(group_id: int) -> int:
    """Delete a prompt group and all its version records (cascade). Returns affected rows."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            affected = cursor.execute("DELETE FROM prompt_group WHERE id = %s", (group_id,))
        conn.commit()
        logger.info(f"[DB] Deleted prompt_group id={group_id}")
        return affected
    except Exception as exc:
        if conn:
            conn.rollback()
        logger.exception(f"[DB] Failed to delete prompt group: {exc}")
        raise
    finally:
        if conn:
            conn.close()


# ---- Prompt Version CRUD ----

def insert_prompt_version(
    group_id: int,
    version: str,
    file_path: str,
    description: str = "",
    is_active: int = 0,
) -> int:
    """Insert a new prompt version. Returns the new version id."""
    conn = None
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                """INSERT INTO prompt_version
                   (group_id, version, file_path, is_active, description, created_at, updated_at)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                (group_id, version, file_path, is_active, description, now, now),
            )
            version_id = cursor.lastrowid
        conn.commit()
        logger.info(f"[DB] Inserted prompt_version id={version_id} version={version}")
        return version_id
    except Exception as exc:
        if conn:
            conn.rollback()
        logger.exception(f"[DB] Failed to insert prompt version: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def query_prompt_versions(
    group_id: int,
    keyword: str = "",
    is_active: Optional[bool] = None,
    page: int = 1,
    page_size: int = 20,
) -> dict:
    """Query prompt versions with filters and pagination."""
    conn = None
    try:
        conn = get_connection()
        conditions = ["group_id = %s"]
        params = [group_id]

        if keyword:
            conditions.append("(version LIKE %s OR description LIKE %s)")
            params.append(f"%{keyword}%")
            params.append(f"%{keyword}%")
        if is_active is not None:
            conditions.append("is_active = %s")
            params.append(1 if is_active else 0)

        where_clause = " WHERE " + " AND ".join(conditions)

        with conn.cursor() as cursor:
            cursor.execute(
                f"SELECT COUNT(*) AS total FROM prompt_version{where_clause}",
                params,
            )
            total = cursor.fetchone()["total"]

            offset = (page - 1) * page_size
            cursor.execute(
                f"""SELECT id, group_id, version, file_path, is_active, description, created_at, updated_at
                    FROM prompt_version{where_clause}
                    ORDER BY is_active DESC, created_at DESC
                    LIMIT %s OFFSET %s""",
                params + [page_size, offset],
            )
            rows = cursor.fetchall()

        list_rows = []
        if rows:
            for row in rows:
                row["created_at"] = row["created_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row["created_at"], datetime) else str(row["created_at"])
                row["updated_at"] = row["updated_at"].strftime("%Y-%m-%d %H:%M:%S") if isinstance(row["updated_at"], datetime) else str(row["updated_at"])
                row["is_active"] = bool(row["is_active"])
                list_rows.append(row)

        return {
            "total": total,
            "page": page,
            "page_size": page_size,
            "list": list_rows,
        }
    except Exception as exc:
        logger.exception(f"[DB] Failed to query prompt versions: {exc}")
        return {"total": 0, "page": page, "page_size": page_size, "list": []}
    finally:
        if conn:
            conn.close()


def get_active_version(group_id: int) -> Optional[dict]:
    """Get the active version of a prompt group."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM prompt_version WHERE group_id = %s AND is_active = 1 LIMIT 1",
                (group_id,),
            )
            return cursor.fetchone()
    except Exception as exc:
        logger.exception(f"[DB] Failed to get active version: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def get_version_by_group_and_version(group_id: int, version: str) -> Optional[dict]:
    """Get a specific version by group_id and version string."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM prompt_version WHERE group_id = %s AND version = %s",
                (group_id, version),
            )
            return cursor.fetchone()
    except Exception as exc:
        logger.exception(f"[DB] Failed to get version: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def set_active_version(group_id: int, version_id: int) -> int:
    """
    Set a version as active (program-controlled, no transaction).
    First sets all versions of the group to is_active=0, then sets target to is_active=1.
    """
    conn = None
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "UPDATE prompt_version SET is_active = 0 WHERE group_id = %s",
                (group_id,),
            )
            affected = cursor.execute(
                "UPDATE prompt_version SET is_active = 1, updated_at = %s WHERE id = %s",
                (now, version_id),
            )
        conn.commit()
        logger.info(f"[DB] Set active version id={version_id} for group_id={group_id}")
        return affected
    except Exception as exc:
        if conn:
            conn.rollback()
        logger.exception(f"[DB] Failed to set active version: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def update_prompt_version(
    version_id: int,
    version: str = None,
    file_path: str = None,
    description: str = None,
) -> int:
    """Update a prompt version record. Returns affected rows."""
    conn = None
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn = get_connection()
        fields = []
        params = []
        if version is not None:
            fields.append("version = %s")
            params.append(version)
        if file_path is not None:
            fields.append("file_path = %s")
            params.append(file_path)
        if description is not None:
            fields.append("description = %s")
            params.append(description)
        if not fields:
            return 0
        fields.append("updated_at = %s")
        params.append(now)
        params.append(version_id)

        with conn.cursor() as cursor:
            affected = cursor.execute(
                f"UPDATE prompt_version SET {', '.join(fields)} WHERE id = %s",
                params,
            )
        conn.commit()
        return affected
    except Exception as exc:
        if conn:
            conn.rollback()
        logger.exception(f"[DB] Failed to update prompt version: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def delete_prompt_version(version_id: int) -> int:
    """Delete a prompt version record. Returns affected rows."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            affected = cursor.execute("DELETE FROM prompt_version WHERE id = %s", (version_id,))
        conn.commit()
        logger.info(f"[DB] Deleted prompt_version id={version_id}")
        return affected
    except Exception as exc:
        if conn:
            conn.rollback()
        logger.exception(f"[DB] Failed to delete prompt version: {exc}")
        raise
    finally:
        if conn:
            conn.close()


def get_latest_version_number(group_id: int) -> Optional[str]:
    """Get the latest version number string for a group (for auto-increment)."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT version FROM prompt_version WHERE group_id = %s ORDER BY id DESC LIMIT 1",
                (group_id,),
            )
            row = cursor.fetchone()
            return row["version"] if row else None
    except Exception as exc:
        logger.exception(f"[DB] Failed to get latest version: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def get_version_by_id(version_id: int) -> Optional[dict]:
    """Get a prompt version by its id."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("SELECT * FROM prompt_version WHERE id = %s", (version_id,))
            return cursor.fetchone()
    except Exception as exc:
        logger.exception(f"[DB] Failed to get version by id: {exc}")
        return None
    finally:
        if conn:
            conn.close()


def migrate_prompt_tables():
    """One-time migration for prompt tables:
    1. Drop foreign key on prompt_version if exists
    2. Ensure prompt_group ids: main=1, prompt-summary=2
    3. Set AUTO_INCREMENT to start from 3
    """
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            # Drop foreign key if exists
            try:
                cursor.execute("""
                    ALTER TABLE prompt_version
                    DROP FOREIGN KEY prompt_version_ibfk_1
                """)
                conn.commit()
                logger.info("[DB] Dropped foreign key prompt_version_ibfk_1")
            except Exception:
                pass  # Foreign key doesn't exist or already dropped

            # Fix prompt_group ids: prompt-main=1, prompt-summary=2
            now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            cursor.execute("SELECT id FROM prompt_group WHERE name='prompt-main'")
            row = cursor.fetchone()
            if row and row["id"] != 1:
                cursor.execute(
                    "UPDATE prompt_group SET id=1, updated_at=%s WHERE name='prompt-main'",
                    (now,),
                )
                # Also update prompt_version group_id references
                cursor.execute(
                    "UPDATE prompt_version SET group_id=1 WHERE group_id=%s",
                    (row["id"],),
                )

            cursor.execute("SELECT id FROM prompt_group WHERE name='prompt-summary'")
            row = cursor.fetchone()
            if row and row["id"] != 2:
                cursor.execute(
                    "UPDATE prompt_group SET id=2, updated_at=%s WHERE name='prompt-summary'",
                    (now,),
                )
                cursor.execute(
                    "UPDATE prompt_version SET group_id=2 WHERE group_id=%s",
                    (row["id"],),
                )

            conn.commit()

            # Set AUTO_INCREMENT to start from 3
            cursor.execute("ALTER TABLE prompt_group AUTO_INCREMENT = 3")
            conn.commit()
            logger.info("[DB] Migration: prompt_group ids fixed, AUTO_INCREMENT=3")
    except Exception as exc:
        logger.exception(f"[DB] Migration failed: {exc}")
    finally:
        if conn:
            conn.close()


# ==============================================================================
# Test-page DB admin helpers: table listing / schema / paged rows / SQL script
# ==============================================================================

# 应用实际使用的表，测试页仅允许浏览这些表（防注入白名单）
MANAGED_TABLES = [
    "user_operation_log",
    "batch_record",
    "prompt_group",
    "prompt_version",
]


def list_tables() -> list:
    """Return table names existing in current database."""
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute("SHOW TABLES")
            return [list(row.values())[0] for row in cursor.fetchall()]
    finally:
        if conn:
            conn.close()


def ensure_database() -> str:
    """Create the configured database if missing. Connects without selecting a
    database first, so it also works when the database does not exist yet.

    Returns "created" or "exists". Raises on connection/permission errors."""
    import re

    if not re.fullmatch(r"[A-Za-z0-9_$]+", Config.MYSQL_DATABASE):
        raise ValueError(f"数据库名包含非法字符: {Config.MYSQL_DATABASE!r}")

    pymysql = _pymysql()
    conn = None
    try:
        conn = pymysql.connect(
            host=Config.MYSQL_HOST,
            port=Config.MYSQL_PORT,
            user=Config.MYSQL_USER,
            password=Config.MYSQL_PASSWORD,
            cursorclass=pymysql.cursors.DictCursor,
        )
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = %s",
                (Config.MYSQL_DATABASE,),
            )
            if cursor.fetchone():
                return "exists"
            cursor.execute(
                f"CREATE DATABASE `{Config.MYSQL_DATABASE}` "
                "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            )
        conn.commit()
        logger.info(f"[DB] Created database {Config.MYSQL_DATABASE}")
        return "created"
    finally:
        if conn:
            conn.close()


def describe_table(table: str) -> list:
    """Return column definitions of a whitelisted table."""
    if table not in MANAGED_TABLES:
        raise ValueError(f"表 {table} 不在允许列表中")
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            cursor.execute(f"DESCRIBE `{table}`")
            return cursor.fetchall()
    finally:
        if conn:
            conn.close()


def _json_default(value):
    if isinstance(value, (datetime, date)):
        return value.strftime("%Y-%m-%d %H:%M:%S")
    if isinstance(value, timedelta):
        total = int(value.total_seconds())
        sign = "-" if total < 0 else ""
        total = abs(total)
        return f"{sign}{total // 3600:02d}:{total % 3600 // 60:02d}:{total % 60:02d}"
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (bytes, bytearray)):
        try:
            return value.decode("utf-8")
        except UnicodeDecodeError:
            return f"<{len(value)} bytes>"
    return str(value)


def query_table_rows(table: str, page: int = 1, page_size: int = 20,
                     keyword: str = "") -> dict:
    """Paged rows of a whitelisted table, newest id first. Optional keyword
    matches any text column via LIKE (escaping wildcards)."""
    if table not in MANAGED_TABLES:
        raise ValueError(f"表 {table} 不在允许列表中")
    page = max(1, int(page))
    page_size = min(100, max(1, int(page_size)))
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            where_sql, params = "", []
            keyword = (keyword or "").strip()
            if keyword:
                columns = [
                    row["Field"] for row in describe_table(table)
                    if row.get("Type") and str(row["Type"]).lower() not in
                    ("blob", "longblob", "mediumblob", "tinyblob", "json")
                ]
                like_parts = []
                for col in columns:
                    like_parts.append(f"CAST(`{col}` AS CHAR) LIKE %s")
                    params.append(f"%{keyword}%")
                if like_parts:
                    where_sql = " WHERE " + " OR ".join(like_parts)
            cursor.execute(f"SELECT COUNT(*) AS n FROM `{table}`{where_sql}", params)
            total = cursor.fetchone()["n"]
            cursor.execute(
                f"SELECT * FROM `{table}`{where_sql} ORDER BY 1 DESC LIMIT %s OFFSET %s",
                params + [page_size, (page - 1) * page_size],
            )
            rows = cursor.fetchall()
        return {
            "table": table,
            "total": total,
            "page": page,
            "page_size": page_size,
            "rows": json.loads(json.dumps(rows, default=_json_default, ensure_ascii=False)),
        }
    finally:
        if conn:
            conn.close()


def _split_sql_statements(script: str) -> list:
    """Split a SQL script into single statements. Handles '...', \"...\", `...`,
    -- line comments, # line comments and /* block comments. Statements with
    only comments/whitespace are dropped."""
    statements, buf = [], []
    i, n = 0, len(script)
    while i < n:
        ch = script[i]
        nxt = script[i + 1] if i + 1 < n else ""
        if ch in ("'", '"', "`"):
            quote = ch
            buf.append(ch)
            i += 1
            while i < n:
                c = script[i]
                buf.append(c)
                if c == "\\" and quote != "`" and i + 1 < n:
                    buf.append(script[i + 1])
                    i += 2
                    continue
                i += 1
                if c == quote:
                    if i < n and script[i] == quote:  # doubled quote escape
                        buf.append(script[i])
                        i += 1
                    else:
                        break
        elif ch == "-" and nxt == "-":
            while i < n and script[i] != "\n":
                i += 1
        elif ch == "#":
            while i < n and script[i] != "\n":
                i += 1
        elif ch == "/" and nxt == "*":
            i += 2
            while i < n and not (script[i] == "*" and i + 1 < n and script[i + 1] == "/"):
                i += 1
            i += 2
        elif ch == ";":
            stmt = "".join(buf).strip()
            if stmt:
                statements.append(stmt)
            buf = []
            i += 1
        else:
            buf.append(ch)
            i += 1
    tail = "".join(buf).strip()
    if tail:
        statements.append(tail)
    return statements


def execute_sql_script(script: str, max_statements: int = 200) -> dict:
    """Execute a SQL script statement by statement. One failed statement does
    not stop the rest (idempotent/incremental scripts may pre-fail). Returns
    per-statement results plus a summary."""
    statements = _split_sql_statements(script)
    if not statements:
        raise ValueError("脚本为空或只包含注释")
    if len(statements) > max_statements:
        raise ValueError(f"语句数 {len(statements)} 超过单次上限 {max_statements}")
    results = []
    conn = None
    try:
        conn = get_connection()
        with conn.cursor() as cursor:
            for idx, stmt in enumerate(statements, 1):
                entry = {
                    "index": idx,
                    "sql": stmt[:200],
                    "status": "success",
                    "affected": 0,
                    "rows_returned": 0,
                    "preview": None,
                    "error": None,
                }
                try:
                    affected = cursor.execute(stmt)
                    if cursor.description:  # SELECT/SHOW/DESC result set
                        rows = cursor.fetchall()
                        entry["rows_returned"] = len(rows)
                        entry["preview"] = json.loads(json.dumps(
                            rows[:20], default=_json_default, ensure_ascii=False))
                    else:
                        entry["affected"] = affected or 0
                        conn.commit()
                except Exception as exc:
                    try:
                        conn.rollback()
                    except Exception:
                        pass
                    entry["status"] = "failed"
                    entry["error"] = str(exc)[:500]
                results.append(entry)
        summary = {
            "total": len(results),
            "success": sum(1 for r in results if r["status"] == "success"),
            "failed": sum(1 for r in results if r["status"] == "failed"),
        }
        return {"summary": summary, "statements": results}
    finally:
        if conn:
            conn.close()