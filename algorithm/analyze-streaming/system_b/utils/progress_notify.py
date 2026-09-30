import asyncio
import json
import logging
import threading
import time
from contextlib import asynccontextmanager
from typing import Optional, Dict, Any

import redis.asyncio as redis

from system_b.config import Config

logger = logging.getLogger(__name__)


class ProgressNotifierRedis:
    """Pushes progress events to a Redis Stream.

    All messages go through one shared connection pool on one background
    event loop: sync callers (e.g. per-token LLM streaming) just submit the
    coroutine and return immediately, instead of paying a fresh TCP connect
    per message.
    """

    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(ProgressNotifierRedis, cls).__new__(cls)
        return cls._instance

    def __init__(
        self, redis_url: Optional[str] = None, channel_prefix: str = "llm_progress"
    ):
        if getattr(self, "_initialized", False):
            return
        self.redis_url = redis_url if redis_url else Config.REDIS_URL
        self.channel_prefix = channel_prefix
        self._connection_pool = None
        self._redis_client: Optional[redis.Redis] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._loop_lock = threading.Lock()
        self._initialized = True

    # ------------------------------------------------------------------
    # 后台事件循环
    # ------------------------------------------------------------------

    def _ensure_loop(self) -> asyncio.AbstractEventLoop:
        with self._loop_lock:
            if self._loop is None or self._loop.is_closed():
                self._loop = asyncio.new_event_loop()
                thread = threading.Thread(
                    target=self._loop.run_forever,
                    name="progress-notify-loop",
                    daemon=True,
                )
                thread.start()
            return self._loop

    def _get_client(self) -> redis.Redis:
        if self._redis_client is None:
            if self._connection_pool is None:
                self._connection_pool = redis.ConnectionPool.from_url(
                    self.redis_url,
                    max_connections=20,
                    socket_connect_timeout=5,
                    socket_timeout=5,
                )
            self._redis_client = redis.Redis(connection_pool=self._connection_pool)
        return self._redis_client

    async def connect(self) -> None:
        """兼容旧接口：确保客户端已创建（连接在首个命令时懒建立）。"""
        self._get_client()

    def _run(self, coro) -> None:
        """Submit a coroutine to the background loop without blocking.

        Errors are handled inside update(); the returned future is dropped
        on purpose so callers never wait on network I/O.
        """
        loop = self._ensure_loop()
        asyncio.run_coroutine_threadsafe(coro, loop)

    # ------------------------------------------------------------------
    # 消息推送
    # ------------------------------------------------------------------

    async def update(
        self,
        request_id: str,
        message: str,
        step_type: str = "",
        event_type: str = "",
        step_id: str = "",
        event_index: int = 1,
        stream_name: Optional[str] = None,
    ) -> None:
        try:
            redis_client = self._get_client()
            channel = stream_name if stream_name else self.channel_prefix
            payload = {
                "request_id": request_id,
                "timestamp": int(time.time()),
                "message": message,
                "step_type": step_type,
                "event_type": event_type,
                "step_id": step_id,
                "event_index": event_index,
            }
            json_payload = json.dumps(payload, ensure_ascii=False)
            await redis_client.xadd(
                channel, {"payload": json_payload}, maxlen=1024
            )
        except Exception as e:
            logger.error(f"redis更新LLM状态失败: {e}")

    def update_sync(
        self,
        request_id: str,
        message: str,
        step_type: str = "",
        event_type: str = "",
        step_id: str = "",
        event_index: int = 1,
        stream_name: Optional[str] = None,
    ) -> None:
        self._run(
            self.update(
                request_id,
                message,
                step_type=step_type,
                event_type=event_type,
                step_id=step_id,
                event_index=event_index,
                stream_name=stream_name,
            )
        )

    async def mark_complete(
        self,
        request_id: str,
        success: bool = True,
        results: Optional[Dict[str, Any]] = None,
        event_index: int = 1,
        stream_name: Optional[str] = None,
    ) -> None:
        """将请求标记为完成：写入流结束消息并设置状态键（24h 过期）。"""
        redis_client = self._get_client()

        status_key = f"request_status_{request_id}"
        status_value = {
            "status": "complete" if success else "failed",
            "completed_at": asyncio.get_running_loop().time(),
            "results": results or {},
        }

        if stream_name:
            payload = {
                "request_id": request_id,
                "timestamp": int(time.time()),
                "message": "",
                "step_type": "close",
                "event_type": "done",
                "step_id": "",
                "event_index": event_index,
            }
            json_payload = json.dumps(payload, ensure_ascii=False)
            await redis_client.xadd(
                stream_name, {"payload": json_payload}, maxlen=1024
            )

        await redis_client.set(status_key, json.dumps(status_value), ex=86400)
        logger.info(f"LLM状态更新完成 {request_id}")

    async def close(self) -> None:
        if self._redis_client:
            await self._redis_client.aclose()
            self._redis_client = None

    @asynccontextmanager
    async def session(self):
        """自动管理连接的上下文管理器

        使用示例:
            async with notifier.session():
                await notifier.update(request_id, "处理中")
        """
        await self.connect()
        try:
            yield self
        finally:
            await self.close()
