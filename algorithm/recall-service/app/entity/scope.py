"""
ScopeResolver —— 请求级范围解析

请求可携带三种形式之一（优先级从高到低）：
  1. scope（内联）：{"tables":[...], "metrics":[...], "dimensions":[...]}   —— 无状态，上游自己维护
  2. agent_id：本模块从 agent / agent_scope 表读出范围（带内存缓存，agent.version 或 catalog.version 变化即失效）
  3. 都不带：全库（兼容旧调用）

返回 ResolvedScope（见 catalog.py），召回、门校验、导出全部用它做过滤。
"""
import threading
from typing import Optional, Tuple

from app.entity.catalog import Catalog, ResolvedScope, get_catalog
from app.entity.store import EntityStore, get_entity_store
from app.utils.logger import get_logger

logger = get_logger("entity.scope", "recall.log")


class AgentNotFound(Exception):
    pass


class ScopeResolver:
    def __init__(self, store: EntityStore = None, catalog: Catalog = None):
        self.store = store or get_entity_store()
        self.catalog = catalog or get_catalog()
        self._lock = threading.Lock()
        self._agents: dict = {}          # agent_id → (version, scope_dict)

    def invalidate(self, agent_id: str = None):
        with self._lock:
            if agent_id:
                self._agents.pop(agent_id, None)
            else:
                self._agents.clear()

    def _agent_scope(self, agent_id: str) -> Tuple[int, dict]:
        with self._lock:
            hit = self._agents.get(agent_id)
        if hit:
            return hit
        a = self.store.load_agent(agent_id)
        if a is None:
            raise AgentNotFound(agent_id)
        item = (int(a["version"]), a["scope"])
        with self._lock:
            self._agents[agent_id] = item
        return item

    def resolve(self, agent_id: Optional[str] = None, scope: Optional[dict] = None) -> ResolvedScope:
        if scope and any(scope.get(k) for k in ("tables", "metrics", "dimensions")):
            return self.catalog.resolve_scope(scope, cache_key=None)
        if agent_id:
            ver, sc = self._agent_scope(agent_id)
            return self.catalog.resolve_scope(sc, cache_key=f"agent:{agent_id}@{ver}")
        return self.catalog.resolve_scope(None)


_resolver: Optional[ScopeResolver] = None


def get_scope_resolver() -> ScopeResolver:
    global _resolver
    if _resolver is None:
        _resolver = ScopeResolver()
    return _resolver
