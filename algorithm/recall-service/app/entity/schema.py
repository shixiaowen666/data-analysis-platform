"""
实体层 DDL —— MySQL / SQLite 通用写法（不使用 JSON 类型、不依赖自增）

src_*            上游源对象（业务主键即上游 ID，永不漂移）
recall_entity    原子可检索实体（entity_key 字符串主键 + faiss_id 稳定 int64）
recall_embedding_cache  text_hash → 向量缓存（同文本同模型永不重复调 embedding API）
agent / agent_scope     智能体及其数据范围
recall_kv        杂项状态（faiss_id 计数器、上次导入指纹等）
"""
from sqlalchemy import text
from app.core.database import ENGINE, DB_BACKEND
from app.utils.logger import get_logger

logger = get_logger("entity.schema", "db.log")

_K = "VARCHAR(191)"   # utf8mb4 下可做主键/索引的最大安全长度

DDL = f"""
CREATE TABLE IF NOT EXISTS src_table (
  table_name     {_K} PRIMARY KEY,
  source_id      {_K},
  display_name   VARCHAR(255),
  description    TEXT,
  columns_json   TEXT,
  extra_json     TEXT,
  updated_at     VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS src_metric (
  metric_code    {_K} PRIMARY KEY,
  source_id      {_K},
  metric_name    VARCHAR(255),
  description    TEXT,
  unit           VARCHAR(64),
  data_type      VARCHAR(64),
  caliber_scope  VARCHAR(64),
  aliases_json   TEXT,
  extra_json     TEXT,
  updated_at     VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS src_dimension (
  dimension_code {_K} PRIMARY KEY,
  source_id      {_K},
  dimension_name VARCHAR(255),
  description    TEXT,
  data_type      VARCHAR(64),
  aliases_json   TEXT,
  extra_json     TEXT,
  updated_at     VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS src_dim_value (
  dimension_code {_K} NOT NULL,
  value_name     {_K} NOT NULL,
  synonyms_json  TEXT,
  sort_no        INT DEFAULT 0,
  updated_at     VARCHAR(32),
  PRIMARY KEY (dimension_code, value_name)
);
CREATE TABLE IF NOT EXISTS src_knowledge (
  knowledge_id   {_K} PRIMARY KEY,
  aliases_json   TEXT,
  content        TEXT,
  sort_no        INT DEFAULT 0,
  updated_at     VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS recall_entity (
  entity_key     {_K} PRIMARY KEY,
  faiss_id       BIGINT NOT NULL,
  entity_type    VARCHAR(16) NOT NULL,
  code           {_K},
  table_name     {_K},
  dimension_code {_K},
  display_name   VARCHAR(255),
  description    TEXT,
  synonyms       TEXT,
  unit           VARCHAR(64),
  caliber_scope  VARCHAR(64),
  legal_tables_json TEXT,
  embedding_text TEXT NOT NULL,
  text_hash      CHAR(64) NOT NULL,
  embed_model    VARCHAR(96),
  embedding_json TEXT,
  updated_at     VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_re_type ON recall_entity(entity_type);
CREATE INDEX IF NOT EXISTS idx_re_table ON recall_entity(table_name);
CREATE INDEX IF NOT EXISTS idx_re_dim ON recall_entity(dimension_code);
CREATE INDEX IF NOT EXISTS idx_re_code ON recall_entity(code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_re_faiss ON recall_entity(faiss_id);

CREATE TABLE IF NOT EXISTS recall_embedding_cache (
  text_hash      CHAR(64) NOT NULL,
  embed_model    VARCHAR(96) NOT NULL,
  embedding_json TEXT NOT NULL,
  created_at     VARCHAR(32),
  PRIMARY KEY (text_hash, embed_model)
);

CREATE TABLE IF NOT EXISTS agent (
  agent_id       {_K} PRIMARY KEY,
  agent_name     VARCHAR(255),
  version        INT DEFAULT 1,
  updated_at     VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS agent_scope (
  agent_id       {_K} NOT NULL,
  ref_type       VARCHAR(16) NOT NULL,
  ref            {_K} NOT NULL,
  PRIMARY KEY (agent_id, ref_type, ref)
);

CREATE TABLE IF NOT EXISTS recall_kv (
  k              VARCHAR(64) PRIMARY KEY,
  v              TEXT,
  updated_at     VARCHAR(32)
);
"""


def init_entity_schema():
    stmts = [s.strip() for s in DDL.split(";") if s.strip()]
    with ENGINE.begin() as conn:
        for s in stmts:
            if DB_BACKEND == "mysql" and s.upper().startswith("CREATE INDEX IF NOT EXISTS") or \
               DB_BACKEND == "mysql" and s.upper().startswith("CREATE UNIQUE INDEX IF NOT EXISTS"):
                # MySQL 不支持 CREATE INDEX IF NOT EXISTS：已存在则忽略报错
                try:
                    conn.execute(text(s.replace("IF NOT EXISTS ", "")))
                except Exception as e:  # noqa
                    if "Duplicate key name" not in str(e) and "1061" not in str(e):
                        raise
                continue
            conn.execute(text(s))
    logger.info(f"Entity schema initialized on {DB_BACKEND} ({len(stmts)} stmts)")
