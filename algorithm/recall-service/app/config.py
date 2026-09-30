"""
全局配置模块
- LLM/Embedding API（DashScope OpenAI 兼容接口）
- 数据库连接（MySQL 优先，SQLite 兜底）
- 服务参数
"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"


def _resolve_dir(raw: str, default: Path) -> Path:
    """日志目录：绝对路径直接用，相对路径基于 BASE_DIR 解析；未配置用默认"""
    if not raw:
        return default
    p = Path(raw)
    return p if p.is_absolute() else (BASE_DIR / p)


def _load_env_file(path: Path) -> None:
    """加载 KEY=VALUE 配置文件（不覆盖已有环境变量）。"""
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        k, v = k.strip(), v.strip()
        if k and v and k not in os.environ:
            os.environ[k] = v


def _find_env_file(start: Path) -> Path:
    """向上搜索第一个存在的 .env：本地在项目根目录（webapp 的父级），容器内与 app 同层(/code)"""
    for d in (start, *start.parents):
        p = d / ".env"
        if p.is_file():
            return p
    return start / ".env"


_load_env_file(_find_env_file(BASE_DIR))

# 日志目录（.env 的 LOG_DIR 可配置；须在 _load_env_file 之后读取）
LOG_DIR = _resolve_dir(os.getenv("LOG_DIR", ""), BASE_DIR / "logs")
LOG_DIR.mkdir(parents=True, exist_ok=True)
DATA_DIR.mkdir(parents=True, exist_ok=True)

# 日志保留天数（按天滚动；0 = 只保留当天，不备份历史）
LOG_RETENTION_DAYS = int(os.getenv("LOG_RETENTION_DAYS", "14"))

# ========== LLM 配置 ==========
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_API_BASE_URL = os.getenv("LLM_API_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "qwen3-235b-a22b-instruct-2507")
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.1"))
# 指标 50 条/片 × 50 字描述 + 全库上下文，4096 容易触发输出截断导致 JSON 解析失败，最低给 8192
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "8192"))

# ========== Embedding 配置 ==========
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY", LLM_API_KEY)
EMBEDDING_API_BASE_URL = os.getenv("EMBEDDING_API_BASE_URL", LLM_API_BASE_URL)
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-v4")
# text-embedding-v4 默认输出 1024 维，可指定 dimensions 参数
EMBEDDING_DIM = int(os.getenv("EMBEDDING_DIM", "1024"))
EMBEDDING_BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "10"))  # DashScope 单次最多 10 条
# EMBEDDING_PROVIDER：dashscope（默认，OpenAI 兼容接口）| fake（离线/单测用，确定性哈希向量，不出网）
EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "dashscope").strip().lower()
# LLM 请求超时（秒）；DashScope 偶发挂起，SDK 默认 600s 会表现为进程静默卡死
LLM_TIMEOUT = float(os.getenv("LLM_TIMEOUT", "120"))

# ========== 数据库配置 ==========
# 生产元数据库（只读来源，仅供 SELECT）；.env 使用 USE_MYSQL_* 前缀，兼容旧 MYSQL_* 变量名
MYSQL_HOST = os.getenv("USE_MYSQL_HOST", os.getenv("MYSQL_HOST", "localhost"))
MYSQL_PORT = int(os.getenv("USE_MYSQL_PORT", os.getenv("MYSQL_PORT", "3306")))
MYSQL_USER = os.getenv("USE_MYSQL_USER", os.getenv("MYSQL_USER", "root"))
MYSQL_PASSWORD = os.getenv("USE_MYSQL_PASSWORD", os.getenv("MYSQL_PASSWORD", ""))
MYSQL_DB = os.getenv("USE_MYSQL_DB", os.getenv("MYSQL_DB", "descartes"))
# 只读保护：meta_sync / gen_database_meta 强制只执行 SELECT
META_SOURCE_READONLY = os.getenv("META_SOURCE_READONLY", "1") == "1"

# 本地服务库（recall 自己的 SQLite，可写；URL 优先环境变量）
DB_URL = os.getenv("DB_URL", "")
if not DB_URL:
    SQLITE_PATH = os.getenv("LOCAL_DB_PATH", "data/recall_service.db")
    DB_URL = f"sqlite:///{BASE_DIR / SQLITE_PATH}"

# SQLite 兜底地址（当 MySQL 连不上时使用）
SQLITE_FALLBACK_URL = f"sqlite:///{DATA_DIR}/recall_service.db"

# 是否允许自动降级
ALLOW_SQLITE_FALLBACK = os.getenv("ALLOW_SQLITE_FALLBACK", "1") == "1"

# ========== 受限反推参数（写透版 §3.3） ==========
# 实体投票权重：指标定表作用最强，维值经维度间接关联且绑定面广，压最低
VOTE_WEIGHT_METRIC = float(os.getenv("VOTE_WEIGHT_METRIC", "0.5"))
VOTE_WEIGHT_DIMENSION = float(os.getenv("VOTE_WEIGHT_DIMENSION", "0.4"))
VOTE_WEIGHT_DIM_VALUE = float(os.getenv("VOTE_WEIGHT_DIM_VALUE", "0.2"))
# 低分实体不投票（召回分数低于此线的实体视为噪声）
VOTE_MIN_SCORE = float(os.getenv("VOTE_MIN_SCORE", "0.5"))
# 反推表候选截断（控制门校验与 judge 的候选表规模）
VOTE_TOP_K = int(os.getenv("VOTE_TOP_K", "15"))

# 路径 B（主题）/ 路径 D（派生指标）召回分数衰减系数：
# 借道中间层（主题/派生）命中的实体置信度低于直连命中，按系数打折
SCORE_DECAY_PATH_B = float(os.getenv("SCORE_DECAY_PATH_B", "0.9"))
SCORE_DECAY_PATH_D = float(os.getenv("SCORE_DECAY_PATH_D", "0.85"))

# ========== 意图拆分（多子查询 max 融合）==========
# 复合意图（如"互感器总数、表箱数、线路条数"）单向量会被代表意图支配，
# 非代表意图在同表指标竞争中挤出 topK。开启后按词典锚定拆子查询，
# 每个子查询独立向量检索，实体取各子查询下的最大相似度。
INTENT_SPLIT_ENABLED = os.getenv("INTENT_SPLIT_ENABLED", "1") == "1"
INTENT_SPLIT_MAX_QUERIES = int(os.getenv("INTENT_SPLIT_MAX_QUERIES", "4"))

# ========== 生产接口开关（/api/recall/prod，默认全部关闭） ==========
# 三个可选链路：查询改写、LLM 改写、LLM 精判，用 true/false 控制，
# prod 接口固定按此配置执行，不受调用方传参影响
PROD_USE_REWRITE = os.getenv("PROD_USE_REWRITE", "false").strip().lower() == "true"
PROD_USE_REWRITE_LLM = os.getenv("PROD_USE_REWRITE_LLM", "false").strip().lower() == "true"
PROD_USE_LLM_JUDGE = os.getenv("PROD_USE_LLM_JUDGE", "false").strip().lower() == "true"

# ========== Rerank 配置 ==========
RERANK_ENABLED = os.getenv("RERANK_ENABLED", "0") == "1"          # 默认关闭
RERANK_API_BASE_URL = os.getenv("RERANK_API_BASE_URL", "http://127.0.0.1:8012")
RERANK_MODEL = os.getenv("RERANK_MODEL", "qwen3-reranker-0.6b")
RERANK_TOP_N = int(os.getenv("RERANK_TOP_N", "30"))               # 参与重排的候选数
RERANK_TIMEOUT = float(os.getenv("RERANK_TIMEOUT", "3.0"))        # 秒

# ========== 描述回写同步（推送描述到 Java 侧） ==========
# JAVA_SYNC_BASE_URL 为空 = 不启用推送（自动/手动均提示未配置）
JAVA_SYNC_BASE_URL = os.getenv("JAVA_SYNC_BASE_URL", "").rstrip("/")
JAVA_SYNC_TIMEOUT = float(os.getenv("JAVA_SYNC_TIMEOUT", "5"))   # 单次请求超时（秒）
JAVA_SYNC_RETRY = int(os.getenv("JAVA_SYNC_RETRY", "3"))         # 失败重试次数（指数退避）

# ========== 服务配置 ==========
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "5003"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

# 配置热加载间隔（秒）
CONFIG_REFRESH_INTERVAL = int(os.getenv("CONFIG_REFRESH_INTERVAL", "30"))

# 索引文件路径（持久化）
INDEX_DIR = DATA_DIR / "indexes"
INDEX_DIR.mkdir(parents=True, exist_ok=True)

# ========== 管理接口鉴权 ==========
# ADMIN_API_KEY 非空时，所有写操作/管理接口需携带 Header: X-API-Key
ADMIN_API_KEY = os.getenv("ADMIN_API_KEY", "").strip()

# ========== 实体层 / 启动行为 ==========
# AUTO_IMPORT_ON_EMPTY：实体库为空时，启动自动导入 data/database_meta.json（首次部署零操作）
AUTO_IMPORT_ON_EMPTY = os.getenv("AUTO_IMPORT_ON_EMPTY", "1") == "1"
# META_MIRROR_ENABLED：写操作后把实体层导出镜像到 data/database_meta.json（备份/离线查看；单测可关）
META_MIRROR_ENABLED = os.getenv("META_MIRROR_ENABLED", "1") == "1"

if not LLM_API_KEY and EMBEDDING_PROVIDER != "fake":
    raise RuntimeError(
        "LLM_API_KEY 未配置：请在项目根目录的 .env 中设置 LLM_API_KEY=sk-xxx"
        "（不要把密钥写进代码）"
    )
