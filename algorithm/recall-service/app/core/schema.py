"""
建表 DDL —— 服务自身配置表
  recall_config : 四路召回参数（热更新）
  app_prompt    : 提示词模板

v2.0 起，元数据/向量已迁移到实体层（app/entity/schema.py 的 src_* / recall_entity 等表），
旧的 meta_* / rel_* 表不再创建、不再使用；若历史库中仍存在可手工 DROP。
适配 MySQL / SQLite。
"""
from sqlalchemy import text
from app.core.database import ENGINE, DB_BACKEND
from app.utils.logger import get_logger

logger = get_logger("schema", "db.log")


DDL_MYSQL = """
CREATE TABLE IF NOT EXISTS recall_config (
  config_id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  path_name       VARCHAR(50)  NOT NULL,
  entity_type     VARCHAR(30)  NOT NULL,
  recall_mode     VARCHAR(20)  NOT NULL DEFAULT 'top_k',
  top_k           INT          NOT NULL DEFAULT 20,
  threshold       DECIMAL(5,4) NOT NULL DEFAULT 0.5000,
  enabled         TINYINT      NOT NULL DEFAULT 1,
  description     VARCHAR(200),
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by      VARCHAR(50),
  UNIQUE KEY uk_path_entity(path_name, entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_prompt (
  prompt_key      VARCHAR(50)  PRIMARY KEY,
  content         TEXT         NOT NULL,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""


DDL_SQLITE = """
CREATE TABLE IF NOT EXISTS recall_config (
  config_id       INTEGER PRIMARY KEY AUTOINCREMENT,
  path_name       VARCHAR(50)  NOT NULL,
  entity_type     VARCHAR(30)  NOT NULL,
  recall_mode     VARCHAR(20)  NOT NULL DEFAULT 'top_k',
  top_k           INTEGER      NOT NULL DEFAULT 20,
  threshold       REAL         NOT NULL DEFAULT 0.5,
  enabled         INTEGER      NOT NULL DEFAULT 1,
  description     VARCHAR(200),
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by      VARCHAR(50),
  UNIQUE (path_name, entity_type)
);

CREATE TABLE IF NOT EXISTS app_prompt (
  prompt_key      VARCHAR(50)  PRIMARY KEY,
  content         TEXT         NOT NULL,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
"""


DEFAULT_RECALL_CONFIGS = [
    ('path_a_table',     'table',          'top_k',     3,   0.5000, '路径A第一阶段：表级召回，默认3张表'),
    ('path_a_entity',    'metric',         'top_k',     20,  0.5500, '路径A第二阶段：表内指标召回'),
    ('path_a_entity',    'dimension',      'top_k',     20,  0.5500, '路径A第二阶段：表内维度召回'),
    ('path_a_entity',    'dim_value',      'top_k',     20,  0.5500, '路径A第二阶段：表内维度值召回'),
    ('path_b_topic',     'topic',          'threshold', 5,   0.6000, '路径B：主题召回，按阈值过滤'),
    ('path_c_global',    'metric',         'top_k',     20,  0.5000, '路径C：全局指标向量召回'),
    ('path_c_global',    'dimension',      'top_k',     20,  0.5000, '路径C：全局维度向量召回'),
    ('path_d_derived',   'derived_metric', 'hybrid',    10,  0.5500, '路径D：派生指标召回'),
    ('path_d_derived',   'metric',         'top_k',     20,  0.0000, '路径D：依赖指标随派生指标召回'),
    ('path_d_derived',   'dimension',      'top_k',     20,  0.0000, '路径D：依赖维度随派生指标召回'),
    ('path_d_derived',   'dim_value',      'top_k',     20,  0.0000, '路径D：依赖维度值随派生指标召回'),
]


def init_schema():
    ddl = DDL_SQLITE if DB_BACKEND == "sqlite" else DDL_MYSQL
    statements = [s.strip() for s in ddl.split(";") if s.strip()]
    with ENGINE.begin() as conn:
        for s in statements:
            try:
                conn.execute(text(s))
            except Exception as e:
                logger.error(f"DDL execute fail: {s[:80]}... err={e}")
                raise
    logger.info(f"Schema initialized on {DB_BACKEND} ({len(statements)} stmts)")

    with ENGINE.begin() as conn:
        rs = conn.execute(text("SELECT COUNT(*) AS c FROM recall_config"))
        n = rs.scalar()
        if n == 0:
            for path_name, entity_type, mode, k, th, desc in DEFAULT_RECALL_CONFIGS:
                conn.execute(text(
                    "INSERT INTO recall_config (path_name, entity_type, recall_mode, top_k, threshold, description) "
                    "VALUES (:p,:e,:m,:k,:t,:d)"
                ), dict(p=path_name, e=entity_type, m=mode, k=k, t=th, d=desc))
            logger.info(f"Inserted {len(DEFAULT_RECALL_CONFIGS)} default recall configs")


if __name__ == "__main__":
    init_schema()
