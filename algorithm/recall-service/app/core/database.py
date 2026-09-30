"""
数据库连接：MySQL 优先，连接失败时自动降级到 SQLite
SQLAlchemy + 原生 SQL 操作（保持与方案中 SQL 一致的语义）
"""
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from typing import List, Dict, Any, Optional
from app import config
from app.utils.logger import get_logger

logger = get_logger("db", "db.log")


def _try_mysql() -> Optional[Engine]:
    try:
        engine = create_engine(config.DB_URL, pool_pre_ping=True, future=True)
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        logger.info(f"Connected to MySQL: {config.DB_URL.split('@')[-1]}")
        return engine
    except Exception as e:
        logger.warning(f"MySQL connect failed: {e}")
        return None


def _create_sqlite() -> Engine:
    engine = create_engine(config.SQLITE_FALLBACK_URL, future=True)
    logger.warning(f"Falling back to SQLite: {config.SQLITE_FALLBACK_URL}")
    return engine


# 全局 engine
ENGINE: Engine
DB_BACKEND: str  # "mysql" / "sqlite"


def init_engine() -> Engine:
    global ENGINE, DB_BACKEND
    eng = _try_mysql() if config.DB_URL.startswith("mysql") else None
    if eng is not None:
        ENGINE = eng
        DB_BACKEND = "mysql"
    elif config.DB_URL.startswith("sqlite"):
        # 显式指定的 SQLite 地址（测试 / 单机部署）直接使用，不再落到默认兜底文件
        ENGINE = create_engine(config.DB_URL, future=True)
        DB_BACKEND = "sqlite"
        logger.info(f"Using SQLite: {config.DB_URL}")
    elif config.ALLOW_SQLITE_FALLBACK:
        ENGINE = _create_sqlite()
        DB_BACKEND = "sqlite"
    else:
        raise RuntimeError("Cannot connect to MySQL and SQLite fallback disabled")
    return ENGINE


# 立即初始化
init_engine()


class DBClient:
    """轻量 DB 客户端：与方案中 self.db.query / self.db.execute 接口对齐"""

    def __init__(self, engine: Engine = None):
        self.engine = engine or ENGINE

    def query(self, sql: str, params: Optional[List] = None) -> List[Dict[str, Any]]:
        with self.engine.connect() as conn:
            if params:
                rs = conn.execute(_to_qmark(sql), _bind_params(params))
            else:
                rs = conn.execute(text(sql))
            if not rs.returns_rows:
                return []
            cols = list(rs.keys())
            return [dict(zip(cols, row)) for row in rs.fetchall()]

    def execute(self, sql: str, params: Optional[List] = None):
        with self.engine.begin() as conn:
            if params:
                conn.execute(_to_qmark(sql), _bind_params(params))
            else:
                conn.execute(text(sql))

    def execute_returning_id(self, sql: str, params: Optional[List] = None) -> int:
        """对 INSERT 返回 lastrowid（兼容 MySQL/SQLite）"""
        with self.engine.begin() as conn:
            if params:
                rs = conn.execute(_to_qmark(sql), _bind_params(params))
            else:
                rs = conn.execute(text(sql))
            return rs.lastrowid

    def executemany(self, sql: str, params_list: List[List]):
        if not params_list:
            return
        with self.engine.begin() as conn:
            stmt = _to_qmark(sql)
            for p in params_list:
                conn.execute(stmt, _bind_params(p))


def _to_qmark(sql: str):
    """
    SQLAlchemy 不直接接受 %s 占位符（除非用 raw cursor）。
    我们采用列表参数 + :p0 :p1 :p2 ... 命名占位的转换方式。
    """
    # 替换 %s 为 :p0 :p1 ...
    parts = sql.split("%s")
    if len(parts) == 1:
        return text(sql)
    new_sql = parts[0]
    for i, p in enumerate(parts[1:]):
        new_sql += f":p{i}{p}"
    stmt = text(new_sql)
    return stmt


def _bind_params(params: List) -> Dict[str, Any]:
    return {f"p{i}": v for i, v in enumerate(params)}


def get_db() -> DBClient:
    return DBClient()
