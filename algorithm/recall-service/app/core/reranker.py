"""
Rerank 客户端 —— Qwen3-Reranker-0.6B via LM Studio legacy /v1/completions

实测结论（探测脚本 _tmp_probe_rerank.py，2025）：
  - 该 LM Studio 实例不支持 /v1/rerank 端点，chat/completions 均不返回 logprobs
  - 可行方案：Qwen3-Reranker 官方判定模板 + /v1/completions，模型输出 yes/no
  - 模板要点：assistant 段必须以 <think>\\n\\n</think> 空思考块结尾（跳过思考），
    instruct 必须用官方英文措辞，否则判别方向失效（全部输出 No）

分数语义：yes=1.0，no/失败=0.0；整批全部失败返回 None（调用方跳过重排）
"""
from concurrent.futures import ThreadPoolExecutor, as_completed
from concurrent.futures import TimeoutError as FuturesTimeoutError
from typing import List, Optional

import httpx

from app import config
from app.utils.logger import get_logger

logger = get_logger("reranker", "reranker.log")

# Qwen3-Reranker 官方判定模板（格式改动会破坏判别方向，勿动）
_PREFIX = ('<|im_start|>system\n'
           'Judge whether the Document meets the requirements based on the Query '
           'and the Instruct provided. Note that the answer can only be "yes" or "no".'
           '<|im_end|>\n<|im_start|>user\n')
_SUFFIX = '<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n'
_INSTRUCT = 'Given a web search query, retrieve relevant passages that answer the query'

_MAX_WORKERS = 6      # 并发线程数（服务端为单模型实例，6 并发实测 1.2s/12 条）


class RerankClient:

    def __init__(self):
        self.base_url = config.RERANK_API_BASE_URL.rstrip("/")
        self.model = config.RERANK_MODEL
        self.timeout = config.RERANK_TIMEOUT
        # 整批预算：单条超时的 3 倍，超预算未完成的文档记 0 分
        self.batch_budget = self.timeout * 3
        self.client = httpx.Client(
            proxy=None,
            trust_env=False,
            timeout=httpx.Timeout(self.timeout, connect=min(self.timeout, 5.0)),
        )
        self.executor = ThreadPoolExecutor(max_workers=_MAX_WORKERS)
        logger.info(f"RerankClient init: base_url={self.base_url}, model={self.model}, "
                    f"workers={_MAX_WORKERS}, timeout={self.timeout}s, budget={self.batch_budget}s")

    def _judge(self, query: str, document: str) -> float:
        prompt = (f'{_PREFIX}<Instruct>: {_INSTRUCT}\n<Query>: {query}\n'
                  f'<Document>: {document}{_SUFFIX}')
        resp = self.client.post(
            f"{self.base_url}/v1/completions",
            json={
                "model": self.model,
                "prompt": prompt,
                "max_tokens": 3,
                "temperature": 0,
            },
        )
        resp.raise_for_status()
        text = resp.json()["choices"][0]["text"].strip().lower()
        return 1.0 if text.startswith("yes") else 0.0

    def rerank(self, query: str, documents: List[str]) -> Optional[List[float]]:
        """
        返回与 documents 等长的分数数组（yes=1.0，no/失败=0.0）；
        整批全部失败返回 None，调用方应保持原排序。
        """
        if not documents:
            return []
        futures = {self.executor.submit(self._judge, query, d): i
                   for i, d in enumerate(documents)}
        scores = [0.0] * len(documents)
        ok = 0
        try:
            for fut in as_completed(futures, timeout=self.batch_budget):
                idx = futures[fut]
                try:
                    scores[idx] = fut.result()
                    ok += 1
                except Exception as e:
                    logger.warning(f"rerank doc#{idx} failed: {e}")
        except FuturesTimeoutError:
            logger.warning(f"rerank batch budget ({self.batch_budget}s) exceeded, "
                           f"{len(documents) - ok} docs scored 0")
        if ok == 0:
            logger.warning(f"rerank all {len(documents)} docs failed, skip rerank")
            return None
        return scores


# 全局单例
_client: RerankClient = None


def get_rerank_client() -> RerankClient:
    global _client
    if _client is None:
        _client = RerankClient()
    return _client
