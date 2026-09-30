"""
Recall Client Module
====================
接入 recall 服务（POST /api/recall/prod）做元数据缩圈。

- 配置优先级：页面配置 system_b/data/recall_config.json > config.py/.env（每次现读，改配置无需重启）
- 调用链路：analyze 入口拿到请求体全量 meta 后，若 recall 启用则调 prod 接口，
  用返回的缩圈 database_meta 替换请求体全量；失败/超时/空结果降级走原全量并记录日志。
- business_context 归一化：recall 返回 object[]，只拼 knowledgeElement 为多行文本
  （消费方 _format_business_context / merge_database_meta 均只做字符串化，见对接方案 4.2）。
"""

import json
import logging
import os
import time
from pathlib import Path
from typing import Optional

import requests

from system_b.config import Config

logger = logging.getLogger(__name__)

# 页面保存的 recall 配置（测试页写入，优先级高于 config.py）
RECALL_CONFIG_FILE = Path(__file__).resolve().parent.parent / "data" / "recall_config.json"


def _load_page_config() -> dict:
    """读取页面保存的配置；不存在/损坏返回空 dict。"""
    try:
        if RECALL_CONFIG_FILE.exists():
            data = json.loads(RECALL_CONFIG_FILE.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                return data
    except (OSError, ValueError) as exc:
        logger.warning("[RecallClient] 读取页面配置失败，回退 config.py: %s", exc)
    return {}


def get_effective_config() -> dict:
    """返回当前生效配置：页面配置字段存在则覆盖 config.py，source 标注来源。"""
    cfg = {
        "enabled": bool(Config.RECALL_ENABLED),
        "base_url": Config.RECALL_BASE_URL,
        "timeout": Config.RECALL_TIMEOUT,
        "source": "config",
    }
    page = _load_page_config()
    if "enabled" in page:
        cfg["enabled"] = bool(page["enabled"])
    if str(page.get("base_url") or "").strip():
        cfg["base_url"] = str(page["base_url"]).strip().rstrip("/")
    if isinstance(page.get("timeout"), (int, float)) and page["timeout"] > 0:
        cfg["timeout"] = int(page["timeout"])
    if page:
        cfg["source"] = "page"
    return cfg


def is_recall_enabled() -> bool:
    """recall 是否启用（页面配置或 config.py）。"""
    return get_effective_config()["enabled"]


def save_page_config(payload: dict) -> dict:
    """持久化页面配置到 system_b/data/recall_config.json，返回保存后的生效配置。

    payload 支持 {enabled: bool, base_url: str, timeout: int}；base_url 为空时不覆盖已有值。
    """
    existing = _load_page_config()
    if "enabled" in payload:
        existing["enabled"] = bool(payload["enabled"])
    if str(payload.get("base_url") or "").strip():
        existing["base_url"] = str(payload["base_url"]).strip().rstrip("/")
    if "timeout" in payload:
        try:
            t = int(payload["timeout"])
        except (TypeError, ValueError):
            raise ValueError("timeout 必须是数字（秒）")
        if t <= 0:
            raise ValueError("timeout 必须大于 0")
        existing["timeout"] = t
    if not existing:
        raise ValueError("无有效配置字段（enabled / base_url / timeout）")
    RECALL_CONFIG_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = RECALL_CONFIG_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(existing, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, RECALL_CONFIG_FILE)
    return get_effective_config()


def clear_page_config() -> dict:
    """删除页面配置，回退 config.py；返回回退后的生效配置。"""
    RECALL_CONFIG_FILE.unlink(missing_ok=True)
    return get_effective_config()


def normalize_business_context(ctx) -> str:
    """recall 返回 business_context object[]，只拼 knowledgeElement 为多行文本。"""
    if isinstance(ctx, str):
        return ctx
    if not isinstance(ctx, list):
        return str(ctx or "")
    return "\n".join(
        str(item.get("knowledgeElement") or "").strip()
        for item in ctx
        if isinstance(item, dict) and item.get("knowledgeElement")
    )


def _call_recall(query: str, cfg: dict) -> tuple:
    """调 /api/recall/prod；返回 (database_meta | None, message, elapsed)。"""
    url = cfg["base_url"].rstrip("/") + "/api/recall/prod"
    start = time.time()
    try:
        resp = requests.post(url, json={"query": query}, timeout=cfg["timeout"])
        elapsed = round(time.time() - start, 2)
        if resp.status_code != 200:
            return None, f"recall HTTP {resp.status_code}", elapsed
        body = resp.json()
        if body.get("code") != 200:
            return None, f"recall code={body.get('code')} message={body.get('message')}", elapsed
        data = body.get("data") or {}
        meta = data.get("database_meta")
        if not isinstance(meta, dict) or not meta.get("available_metrics"):
            return None, "recall 返回空 database_meta", elapsed
        meta["business_context"] = normalize_business_context(meta.get("business_context"))
        return meta, "操作成功", elapsed
    except requests.RequestException as exc:
        elapsed = round(time.time() - start, 2)
        return None, f"recall 请求失败: {exc}", elapsed
    except ValueError as exc:
        elapsed = round(time.time() - start, 2)
        return None, f"recall 响应解析失败: {exc}", elapsed


def fetch_database_meta_with_info(query: str) -> tuple:
    """生产链路调用：返回 (缩圈 meta | None, info)。

    info 字段：
      enabled     recall 是否启用
      used        本次是否实际使用 recall 缩圈结果
      source      配置来源 config/page
      elapsed     recall 调用耗时（秒，未启用为 0）
      metrics/dimensions/tables  缩圈后数量（未使用时为 0）
      message     成功为"操作成功"，否则为未使用原因
    """
    cfg = get_effective_config()
    base = {
        "enabled": bool(cfg["enabled"]),
        "used": False,
        "source": cfg["source"],
        "elapsed": 0,
        "metrics": 0,
        "dimensions": 0,
        "tables": 0,
    }
    if not cfg["enabled"]:
        return None, {**base, "message": "recall 未启用"}
    meta, message, elapsed = _call_recall(query, cfg)
    if meta is None:
        logger.warning(
            "[RecallClient] 降级：%s（耗时 %.2fs），改用请求体全量 database_meta", message, elapsed
        )
        return None, {**base, "elapsed": elapsed, "message": message}
    n_m = len(meta.get("available_metrics") or [])
    n_d = len(meta.get("available_dimensions") or [])
    n_t = len(meta.get("table_summaries") or [])
    logger.info(
        "[RecallClient] recall 缩圈成功：metrics=%d dims=%d tables=%d（耗时 %.2fs，来源 %s）",
        n_m, n_d, n_t, elapsed, cfg["source"],
    )
    return meta, {
        **base,
        "used": True,
        "elapsed": elapsed,
        "metrics": n_m,
        "dimensions": n_d,
        "tables": n_t,
        "message": "操作成功",
    }


def fetch_database_meta(query: str) -> Optional[dict]:
    """兼容薄壳：只返回缩圈 meta，失败返回 None（调用方降级）。"""
    meta, _ = fetch_database_meta_with_info(query)
    return meta


def preview_recall(query: str) -> dict:
    """测试页实时预览：返回 {ok, message, elapsed, database_meta, source, stats}。"""
    cfg = get_effective_config()
    if not cfg["enabled"]:
        return {"ok": False, "message": "recall 未启用（请先在配置中开启）", "elapsed": 0,
                "database_meta": None, "source": cfg["source"], "stats": None}
    meta, message, elapsed = _call_recall(query, cfg)
    if meta is None:
        return {"ok": False, "message": message, "elapsed": elapsed,
                "database_meta": None, "source": cfg["source"], "stats": None}
    return {
        "ok": True,
        "message": message,
        "elapsed": elapsed,
        "database_meta": meta,
        "source": cfg["source"],
        "stats": {
            "metrics": len(meta.get("available_metrics") or []),
            "dimensions": len(meta.get("available_dimensions") or []),
            "tables": len(meta.get("table_summaries") or []),
            "size_kb": round(len(json.dumps(meta, ensure_ascii=False)) / 1024, 1),
        },
    }
