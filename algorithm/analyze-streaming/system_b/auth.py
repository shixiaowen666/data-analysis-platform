"""
JWT 鉴权模块
===========
与 Java 端 JwtTokenUtil 对应，使用 HMAC-SHA256 对称签名验证 JWT。
从 token 中提取 user_id 和 username（sub）。
"""

import base64
import hashlib
import hmac
import json
import time
import logging
from functools import wraps
from flask import request, g, jsonify

from system_b.config import Config

logger = logging.getLogger(__name__)

# ---- 白名单：不需要鉴权的路径 ----
_AUTH_WHITELIST = {
    "/health",
    # 静态测试页（页面内 API 调用仍需各自携带 token）
    "/test_logs",
    "/test_prompts",
    "/test_analyze_stream",
    "/test_all",
    "/test_meta",
    "/test_model_config",
    "/test_db",
}


# ---- Token 解析 ----

def _b64url_decode(data: str) -> bytes:
    """Base64URL 解码（自动补齐 padding）"""
    return base64.urlsafe_b64decode(data + "=" * (-len(data) % 4))


def parse_token(token: str) -> dict:
    """
    校验签名并解析 JWT，返回 claims 字典。

    Args:
        token: JWT 字符串，可带 "Bearer " 前缀

    Returns:
        claims 字典，包含 user_id, sub, exp 等字段

    Raises:
        ValueError: token 格式错误或签名校验失败
    """
    # 剥离 Bearer 前缀（与 Java 端 String authToken = token.startsWith("Bearer ") ? token.substring(7) : token 对应）
    if token.startswith("Bearer "):
        token = token[7:]

    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("token 格式错误：非标准 JWT 三段式")

    header_b64, payload_b64, sig_b64 = parts

    # HMAC-SHA256 签名校验
    expected_sig = base64.urlsafe_b64encode(
        hmac.new(
            Config.JWT_SECRET.encode(),
            f"{header_b64}.{payload_b64}".encode(),
            hashlib.sha256,
        ).digest()
    ).rstrip(b"=").decode()

    if not hmac.compare_digest(expected_sig, sig_b64):
        raise ValueError("token 签名校验失败")

    return json.loads(_b64url_decode(payload_b64))


def get_user(token: str) -> tuple:
    """
    从 token 中提取用户信息，并检查过期。

    Args:
        token: JWT 字符串，可带 "Bearer " 前缀

    Returns:
        (user_id, username) 元组

    Raises:
        ValueError: token 无效、签名错误或已过期
    """
    claims = parse_token(token)
    if claims.get("exp") is not None and claims["exp"] < time.time():
        raise ValueError("token 已过期")
    user_id = claims.get("user_id")
    # Java 端签发的 user_id 可能是数字类型，统一转字符串，下游按字符串处理
    return (str(user_id) if user_id is not None else None), claims.get("sub")


# ---- Flask 中间件集成 ----

def register_auth(app):
    """注册全局鉴权中间件到 Flask app。"""

    @app.before_request
    def _before_request_auth():
        # 白名单豁免
        if request.path in _AUTH_WHITELIST:
            return None

        # 提取 Authorization header
        auth_header = request.headers.get("Authorization", "")
        if not auth_header:
            logger.warning(f"[Auth] 缺少 Authorization header: {request.method} {request.path}")
            return jsonify({
                "code": 401,
                "message": "未授权访问：缺少 Authorization header",
                "data": None,
            }), 401

        try:
            user_id, username = get_user(auth_header)
        except ValueError as e:
            logger.warning(f"[Auth] Token 验证失败: {e} | {request.method} {request.path}")
            return jsonify({
                "code": 401,
                "message": f"未授权访问：{e}",
                "data": None,
            }), 401

        # 注入到 flask.g，后续路由处理函数直接使用
        g.current_user_id = user_id
        g.current_username = username
        logger.debug(f"[Auth] 用户验证通过: user_id={user_id}, username={username}")

        return None


def require_auth(f):
    """
    可选：装饰器方式，用于单个路由的鉴权控制。
    如果已使用 register_auth 全局中间件，则不需要此装饰器。
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header:
            return jsonify({
                "code": 401,
                "message": "未授权访问：缺少 Authorization header",
                "data": None,
            }), 401
        try:
            user_id, username = get_user(auth_header)
            g.current_user_id = user_id
            g.current_username = username
        except ValueError as e:
            return jsonify({
                "code": 401,
                "message": f"未授权访问：{e}",
                "data": None,
            }), 401
        return f(*args, **kwargs)
    return decorated