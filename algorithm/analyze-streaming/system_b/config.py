"""
System B Configuration Module
Centralized configuration management with environment variable support.
"""

import os
from pathlib import Path
from datetime import date
from dotenv import load_dotenv

# Project root: config.py → system_b/ → project_root/
PROJECT_ROOT = Path(__file__).resolve().parent.parent

# .env 是唯一配置来源：显式定位项目根目录的 .env（不依赖启动时的工作目录），
# 并强制覆盖系统环境变量，避免部署机上 shell/systemd export 的值压过 .env
load_dotenv(PROJECT_ROOT / ".env", override=True)


class Config:
    """System B Configuration"""

    # System A connection dev/prod
    ENV: str = os.getenv("ENV", "").lower()
    # System A connection
    SYSTEM_A_BASE_URL: str = os.getenv("SYSTEM_A_BASE_URL", "http://127.0.0.1:5001")
    SYSTEM_A_TIMEOUT: int = int(os.getenv("SYSTEM_A_TIMEOUT", "60"))
    SYSTEM_A_MAX_RETRIES: int = int(os.getenv("SYSTEM_A_MAX_RETRIES", "2"))

    # Chat history clear (System A)
    CHAT_CLEAR_TIMEOUT: int = int(os.getenv("CHAT_CLEAR_TIMEOUT", "10"))

    # ChatBi platform (test page: create real session for request_id)
    CHATBI_BASE_URL: str = os.getenv("CHATBI_BASE_URL", "http://47.111.6.75:8091")
    CHATBI_USERNAME: str = os.getenv("CHATBI_USERNAME", "admin")
    CHATBI_PASSWORD: str = os.getenv("CHATBI_PASSWORD", "")

    # Recall service (metadata shrinking)
    # 页面配置 system_b/data/recall_config.json 优先级更高（见 utils/recall_client.py）
    RECALL_ENABLED: bool = os.getenv("RECALL_ENABLED", "").lower() in ("1", "true", "yes", "on")
    RECALL_BASE_URL: str = os.getenv("RECALL_BASE_URL", "http://127.0.0.1:5003")
    RECALL_TIMEOUT: int = int(os.getenv("RECALL_TIMEOUT", "20"))

    # System B server
    SYSTEM_B_HOST: str = os.getenv("SYSTEM_B_HOST", "0.0.0.0")
    SYSTEM_B_PORT: int = int(os.getenv("SYSTEM_B_PORT", "5000"))

    REDIS_URL: str = os.getenv("REDIS_HOST", "redis://8.145.36.163:6379/0")
    REDIS_STREAM_NAME = "llm_progress_zhangjinduodeMacBook-Pro.local"

    # LLM Configuration
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
    LLM_API_BASE_URL: str = os.getenv("LLM_API_BASE_URL", "https://api.openai.com/v1")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "gpt-4o")
    LLM_TEMPERATURE: float = float(os.getenv("LLM_TEMPERATURE", "0.1"))
    LLM_MAX_TOKENS: int = int(os.getenv("LLM_MAX_TOKENS", "4096"))

    # DAG Engine
    DAG_THREAD_POOL_SIZE: int = int(os.getenv("DAG_THREAD_POOL_SIZE", "4"))

    # Logging
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")

    # Base dir for all logs. Relative values (e.g. "./logs" from .env) are
    # resolved against PROJECT_ROOT so behavior is independent of the
    # working directory the server is started from.
    LOG_DIR: str = os.getenv("LOG_DIR", str(PROJECT_ROOT / "logs"))
    if not Path(LOG_DIR).is_absolute():
        LOG_DIR = str(PROJECT_ROOT / LOG_DIR)

    # LLM call logs (separate from run logs, with hash-based archive)
    _llm_default = str(Path(LOG_DIR) / "llm")
    LLM_LOG_DIR: str = os.getenv("LLM_LOG_DIR", _llm_default)
    if not Path(LLM_LOG_DIR).is_absolute():
        LLM_LOG_DIR = str(PROJECT_ROOT / LLM_LOG_DIR)

    # Data truncation
    MAX_ROWS_FOR_SUMMARY: int = 200
    TRUNCATE_HEAD: int = 15
    TRUNCATE_TAIL: int = 5
    TRUNCATE_RANK_HEAD: int = 10
    TRUNCATE_RANK_TAIL: int = 10
    TRUNCATE_TIMESERIES_HEAD: int = 5
    TRUNCATE_TIMESERIES_TAIL: int = 5

    # Current date (for time resolution)
    CURRENT_DATE: str = date.today().isoformat()

    # MySQL Configuration
    MYSQL_HOST: str = os.getenv("MYSQL_HOST", "localhost")
    MYSQL_PORT: int = int(os.getenv("MYSQL_PORT", "3306"))
    MYSQL_USER: str = os.getenv("MYSQL_USER", "root")
    MYSQL_PASSWORD: str = os.getenv("MYSQL_PASSWORD", "")
    MYSQL_DATABASE: str = os.getenv("MYSQL_DATABASE", "webapp")

    # JWT 鉴权
    JWT_SECRET: str = os.getenv("JWT_SECRET", "chatbi-jwt-secret-key-for-production-2026")

    # User operation logs — plan requires {LOG_DIR}/{user_id}/{yyyy-MM-dd}.log
    LOGS_DIR: str = os.getenv("LOGS_DIR", LOG_DIR)
    if not Path(LOGS_DIR).is_absolute():
        LOGS_DIR = str(PROJECT_ROOT / LOGS_DIR)

    # Prompt versioning — defaults to system_b/prompts/
    PROMPTS_DIR: str = os.getenv(
        "PROMPTS_DIR",
        str(Path(__file__).resolve().parent / "prompts"),
    )

print("=" * 50)
print(f"当前运行环境: {Config.ENV}")
print(f"是否为开发环境(dev): {Config.ENV == 'dev'}")
print(f"SystemA 服务地址: {Config.SYSTEM_A_BASE_URL}")
print("=" * 50)