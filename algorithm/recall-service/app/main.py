"""
FastAPI 应用入口
启动顺序：
  1. 初始化数据库（旧 schema 保留 recall_config / app_prompt；新实体层 schema）
  2. 加载召回配置
  3. 从实体库装载内存索引（IndexIDMap2 + Catalog）—— 不编码
     实体库为空且 AUTO_IMPORT_ON_EMPTY=1 时自动导入 data/database_meta.json（首次部署）
  4. 注入 RecallService / SlimExporter / LLMJudge 到 API state，挂接增量刷新 listener
"""
import json
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

from app import config
from app.core.schema import init_schema
from app.core.config_manager import get_config_manager
from app.core.embedding import get_embedding_client
from app.entity.schema import init_entity_schema
from app.entity.sync_service import get_sync_service
from app.entity.scope import get_scope_resolver
from app.services.recall_service import RecallService
from app.services.slim_exporter import SlimExporter
from app.services.llm_judge import LLMJudge
from app.api.endpoints import router, init_state, set_index_state
from app.utils.logger import get_logger

logger = get_logger("main", "main.log")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("====== 召回服务启动 ======")
    init_schema()
    init_entity_schema()
    cfg_mgr = get_config_manager()
    embed = get_embedding_client()

    svc = get_sync_service()
    stats = svc.load_from_db()
    if stats["loaded"] == 0 and config.AUTO_IMPORT_ON_EMPTY:
        p = config.DATA_DIR / "database_meta.json"
        if p.exists():
            logger.warning(f"entity store empty — auto importing {p}")
            try:
                dm = json.loads(p.read_text(encoding="utf-8")).get("database_meta", {})
                res = svc.import_database_meta(dm, replace=True)
                logger.info(f"auto import done: {res}")
            except Exception as e:
                logger.exception(f"auto import failed: {e}")
        else:
            logger.warning("entity store empty and no database_meta.json — recall will return nothing; "
                           "push data via POST /api/entities/upsert or /api/entities/import")
    logger.info(f"Index ready: {svc.index.stats()}")

    recall_service = RecallService(embed_client=embed, index=svc.index, catalog=svc.catalog,
                                   config_manager=cfg_mgr)
    slim_exporter = SlimExporter(store=svc.store, catalog=svc.catalog)
    judge = LLMJudge()
    init_state(recall_service, judge, slim_exporter)

    # 增量写入后的刷新链：导出器缓存 / 改写词典 / 范围缓存
    svc.add_listener(slim_exporter.invalidate)
    try:
        from app.services.query_rewriter import get_query_rewriter
        rw = get_query_rewriter()
        rw.refresh()
        svc.add_listener(rw.refresh)
    except Exception as e:
        logger.warning(f"QueryRewriter init failed (rewrite disabled): {e}")
    svc.add_listener(lambda _keys: get_scope_resolver().invalidate())

    set_index_state()
    logger.info("====== Service Ready ======")
    yield
    logger.info("====== Service Shutdown ======")


app = FastAPI(
    title="智能问数召回服务",
    version="2.0.0",
    description="表/指标/维度/维度值召回服务：增量向量化 + 智能体范围检索",
    lifespan=lifespan,
)
app.include_router(router)

_static_dir = Path(__file__).resolve().parent.parent / "static"
app.mount("/static", StaticFiles(directory=str(_static_dir)), name="static")


@app.get("/")
@app.get("/index.html")
async def index():
    return FileResponse(str(_static_dir / "index.html"))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=config.HOST, port=config.PORT, log_level=config.LOG_LEVEL.lower())
