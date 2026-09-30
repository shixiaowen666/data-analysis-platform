"""
ChatBi Platform Client - chatbi_client.py
==========================================
与问数平台（47.111.6.75:8091）通信的唯一通道，供测试页创建真实会话使用。

职责:
- RSA(PKCS1v15) 加密登录（明文 = 密码|毫秒时间戳，公钥硬编码于平台前端 JS）
- token 内存缓存（24h 有效，过期前 30 分钟主动刷新）
- 401 自愈（清缓存重登一次后重试原请求）
- 智能体列表 / 创建真实会话（返回 sessionId@chatId 格式的 request_id）

链路依据（已实测验证，见 docs/并发测试/登录方案.md）:
- POST /upc/user/login                -> data.token
- GET  /api/v1/aibody/list            -> data.list[{name, code}]
- POST /api/v1/chat-server/chat       -> data.chatSessionId + data.chatId
  请求体不传 chatSessionId 即新建会话（会话懒创建），
  chatId 由服务端每次提问新生成，无单独生成接口。
"""

import base64
import logging
import threading
import time

import requests
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_der_public_key

from system_b.config import Config

logger = logging.getLogger(__name__)

# 平台前端 js/app.*.js 中内嵌的 JSEncrypt 公钥（PKCS#1 v1.5, 2048位, DER+base64）
CHATBI_PUBKEY_B64 = (
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArP9YDcoZOEm2sJixPWcL"
    "B9+vxhDHZIajajeBmtm8qrlQVNKp9OtNCdNpZTp4/AjUF6lkp1U8h0rn9CgOGIM3"
    "DFGU/Q1dSTnq1KSDgFv2tB6YX3XMTGTpm4sAFk5dF4GMmhMl3Nk/bF/9vf6sdEGA"
    "c/Ngr9cLeuRjK0N3GgXnSBrsp1rOVX7DdHXrkIHPf4539H58Uc+AlZLFmdCfQpuB"
    "eVT69dSeYbZePb84A3RGN4zZM3cM64A9KRHGgnssokrPwnz6QeVu+76zbQLKOe81"
    "S3Doy+9Q+fD/rk+c+8OsaVJ22kQyb9oW3AEnEF+a3zX5JX2W4Ab9N2ukEHcU8geU"
    "jQIDAQAB"
)

# token 有效期 24h，提前 30 分钟刷新
_TOKEN_TTL_SECONDS = 24 * 3600
_TOKEN_REFRESH_MARGIN = 30 * 60


class ChatBiError(Exception):
    """ChatBi 平台通信异常"""


def _is_success(body: dict) -> bool:
    """平台各接口成功码不统一：login code=1，aibody/list code=200+success=true，
    chat ret=success。统一兼容判定（code=0 为登录失败等明确失败）。"""
    if not isinstance(body, dict):
        return False
    if body.get("success") is True:
        return True
    if body.get("ret") == "success":
        return True
    code = body.get("code")
    return code in (1, 200, "success")


class ChatBiClient:
    """问数平台客户端：登录、智能体列表、创建真实会话"""

    def __init__(self, base_url: str = None, username: str = None, password: str = None,
                 timeout: int = 15):
        self.base_url = (base_url or Config.CHATBI_BASE_URL).rstrip("/")
        self.username = username or Config.CHATBI_USERNAME
        self.password = password if password is not None else Config.CHATBI_PASSWORD
        self.timeout = timeout
        self._token: str = ""
        self._token_expires_at: float = 0.0
        self._lock = threading.Lock()

    # ------------------------------------------------------------------
    # 配置检查
    # ------------------------------------------------------------------
    def ensure_configured(self):
        if not self.username or not self.password:
            raise ChatBiError(
                "平台账号未配置，请在 .env 设置 CHATBI_USERNAME / CHATBI_PASSWORD"
            )

    # ------------------------------------------------------------------
    # 登录与 token 管理
    # ------------------------------------------------------------------
    @staticmethod
    def _encrypt_password(password: str) -> str:
        """复现平台前端登录加密: RSA(PKCS1v15, password|毫秒时间戳) -> base64"""
        plaintext = f"{password}|{int(time.time() * 1000)}"
        key = load_der_public_key(base64.b64decode(CHATBI_PUBKEY_B64))
        ct = key.encrypt(plaintext.encode("utf-8"), padding.PKCS1v15())
        return base64.b64encode(ct).decode("ascii")

    def _login(self) -> str:
        self.ensure_configured()
        url = f"{self.base_url}/upc/user/login"
        payload = {
            "username": self.username,
            "password": self._encrypt_password(self.password),
        }
        try:
            resp = requests.post(
                url, json=payload, timeout=self.timeout,
                headers={"tenantid": "1"},
            )
            resp.raise_for_status()
            body = resp.json()
        except requests.RequestException as e:
            raise ChatBiError(f"平台登录请求失败: {e}") from e

        if body.get("code") != 1:
            raise ChatBiError(f"平台登录失败: {body.get('message', body)}")

        token = (body.get("data") or {}).get("token") or ""
        if not token:
            raise ChatBiError(f"平台登录响应缺少 token: {body}")

        self._token = token
        self._token_expires_at = time.time() + _TOKEN_TTL_SECONDS
        logger.info("ChatBi 登录成功, token 有效期 24h")
        return token

    def _get_token(self, force_refresh: bool = False) -> str:
        with self._lock:
            if force_refresh or not self._token or time.time() >= (
                self._token_expires_at - _TOKEN_REFRESH_MARGIN
            ):
                self._login()
            return self._token

    # ------------------------------------------------------------------
    # 请求封装（401 自愈：清 token 重登一次后重试）
    # ------------------------------------------------------------------
    def _request(self, method: str, path: str, json_payload: dict = None,
                 params: dict = None) -> dict:
        url = f"{self.base_url}{path}"
        token = self._get_token()
        headers = {"authorization": f"Bearer {token}", "tenantid": "1"}

        resp = requests.request(
            method, url, json=json_payload, params=params,
            headers=headers, timeout=self.timeout,
        )
        if resp.status_code == 401:
            logger.warning("ChatBi token 失效(401), 重新登录后重试: %s", path)
            headers["authorization"] = f"Bearer {self._get_token(force_refresh=True)}"
            resp = requests.request(
                method, url, json=json_payload, params=params,
                headers=headers, timeout=self.timeout,
            )
        try:
            resp.raise_for_status()
        except requests.RequestException as e:
            raise ChatBiError(f"平台请求失败 [{method} {path}] HTTP {resp.status_code}: {e}") from e

        body = resp.json()
        if not _is_success(body):
            raise ChatBiError(f"平台业务失败 [{method} {path}]: {body.get('message', body)}")
        return body

    # ------------------------------------------------------------------
    # 业务接口
    # ------------------------------------------------------------------
    def list_agents(self) -> list:
        """智能体列表 -> [{name, code}]，code 即 chat 接口的 aicode"""
        body = self._request(
            "GET", "/api/v1/aibody/list",
            params={"keyword": "", "page": 1, "pageSize": 50},
        )
        items = (body.get("data") or {}).get("list") or []
        return [
            {"name": it.get("name"), "code": it.get("code"), "status": it.get("status")}
            for it in items
        ]

    def create_session(self, agent_code: str, question: str) -> dict:
        """创建真实会话。

        请求体不传 chatSessionId => 新建会话；响应含本次的 chatSessionId + chatId。
        会触发平台对该 question 的真实 AI 分析（预期内消耗）。
        """
        if not agent_code or not question:
            raise ChatBiError("创建平台会话需要 agent_code 和 question")

        body = self._request(
            "POST", "/api/v1/chat-server/chat",
            json_payload={"aicode": agent_code, "question": question},
        )
        data = body.get("data") or {}
        chat_session_id = data.get("chatSessionId") or ""
        chat_id = data.get("chatId") or ""
        if not chat_session_id or not chat_id:
            raise ChatBiError(f"平台会话响应缺少 ID 字段: {data}")

        return {
            "request_id": f"{chat_session_id}@{chat_id}",
            "chat_session_id": chat_session_id,
            "chat_id": chat_id,
            "host": data.get("host"),
            "timestamp": data.get("timestamp"),
        }


# 模块级单例（测试页两个接口共用，token 缓存复用）
_chatbi_client: ChatBiClient = None
_client_lock = threading.Lock()


def get_chatbi_client() -> ChatBiClient:
    global _chatbi_client
    with _client_lock:
        if _chatbi_client is None:
            _chatbi_client = ChatBiClient()
        return _chatbi_client
