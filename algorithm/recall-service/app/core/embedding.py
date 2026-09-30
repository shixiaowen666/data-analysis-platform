"""
Embedding 客户端 —— 使用 DashScope text-embedding-v4
- 与 OpenAI 接口兼容（自动 L2 归一化）
- 自动批处理（DashScope 单次最多 10 条）
- 支持 query 类型 instruct（DashScope 端区分 query/document）
"""
from typing import List
import numpy as np
import httpx
from openai import OpenAI
from tenacity import retry, stop_after_attempt, wait_exponential
from app import config
from app.utils.logger import get_logger

logger = get_logger("embedding", "embedding.log")


class EmbeddingClient:
    """DashScope text-embedding-v4 调用封装"""

    def __init__(self):
        self.client = OpenAI(
            api_key=config.EMBEDDING_API_KEY,
            base_url=config.EMBEDDING_API_BASE_URL,
            # trust_env=False: 忽略 Windows 系统注册表代理（DashScope 国内直连）
            # 显式短超时：DashScope 偶发请求挂起不返回，SDK 默认 600s 超时会表现为进程静默卡死
            http_client=httpx.Client(
                proxy=None,
                trust_env=False,
                timeout=httpx.Timeout(60.0, connect=10.0),
            ),
        )
        self.model = config.EMBEDDING_MODEL
        self.dim = config.EMBEDDING_DIM
        self.batch_size = config.EMBEDDING_BATCH_SIZE
        logger.info(f"EmbeddingClient init: model={self.model}, dim={self.dim}")

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=8))
    def _encode_batch(self, texts: List[str]) -> np.ndarray:
        # text-embedding-v4 支持 dimensions 参数
        try:
            resp = self.client.embeddings.create(
                model=self.model,
                input=texts,
                dimensions=self.dim,
                encoding_format="float",
            )
        except TypeError:
            # 旧版 SDK 没有 dimensions 关键字
            resp = self.client.embeddings.create(
                model=self.model,
                input=texts,
            )
        embs = np.array([item.embedding for item in resp.data], dtype=np.float32)
        # L2 归一化（与 FAISS METRIC_INNER_PRODUCT 配合作余弦相似度）
        norms = np.linalg.norm(embs, axis=1, keepdims=True)
        norms[norms == 0] = 1.0
        embs = embs / norms
        return embs

    def encode(self, texts: List[str], desc: str = "") -> np.ndarray:
        """
        批量编码文本，返回 (N, dim) 的归一化向量
        Args:
            texts: 文本列表
            desc:  日志描述（如 'tables', 'query'）
        """
        if not texts:
            return np.zeros((0, self.dim), dtype=np.float32)
        all_emb = []
        n = len(texts)
        for i in range(0, n, self.batch_size):
            batch = texts[i:i + self.batch_size]
            try:
                emb = self._encode_batch(batch)
            except Exception as e:
                logger.error(f"encode batch [{i}:{i+len(batch)}] failed: {e}")
                raise
            all_emb.append(emb)
            if n > self.batch_size:
                logger.debug(f"encode {desc} progress: {i+len(batch)}/{n}")
        out = np.concatenate(all_emb, axis=0).astype(np.float32)
        if out.shape[1] != self.dim:
            # 后兜底：若服务端返回了不同维度则直接用返回值并更新 self.dim
            logger.warning(f"encode return dim={out.shape[1]} != configured {self.dim}; using returned dim")
        return out


class FakeEmbeddingClient:
    """离线/单测用：确定性哈希向量（字符 n-gram 特征 + 归一化），不出网。
    同一文本永远得到同一向量，且字面相近的文本余弦更高，足以验证增量/范围逻辑。"""

    def __init__(self):
        self.model = "fake-hash"
        self.dim = config.EMBEDDING_DIM
        self.batch_size = 1000
        logger.info(f"FakeEmbeddingClient init: dim={self.dim}")

    def _one(self, text: str) -> np.ndarray:
        import hashlib
        v = np.zeros(self.dim, dtype=np.float32)
        t = text or ""
        grams = [t[i:i + n] for n in (1, 2, 3) for i in range(max(0, len(t) - n + 1))]
        for g in grams:
            h = int.from_bytes(hashlib.md5(g.encode("utf-8")).digest()[:8], "little")
            v[h % self.dim] += 1.0 if (h >> 63) == 0 else -1.0
        n = np.linalg.norm(v)
        return v / n if n > 0 else v

    def encode(self, texts: List[str], desc: str = "") -> np.ndarray:
        if not texts:
            return np.zeros((0, self.dim), dtype=np.float32)
        return np.stack([self._one(t) for t in texts]).astype(np.float32)


# 全局单例
_client = None


def get_embedding_client():
    global _client
    if _client is None:
        _client = FakeEmbeddingClient() if config.EMBEDDING_PROVIDER == "fake" else EmbeddingClient()
    return _client
