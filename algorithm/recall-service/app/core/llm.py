"""
LLM 客户端：DashScope OpenAI 兼容接口 + qwen3-235b-a22b-instruct-2507
"""
import json
import re
import httpx
from openai import OpenAI
from tenacity import retry, stop_after_attempt, wait_exponential
from app import config
from app.utils.logger import get_logger

logger = get_logger("llm", "llm.log")


class LLMClient:
    def __init__(self):
        self.client = OpenAI(
            api_key=config.LLM_API_KEY,
            base_url=config.LLM_API_BASE_URL,
            http_client=httpx.Client(proxy=None, trust_env=False,
                                     timeout=httpx.Timeout(config.LLM_TIMEOUT, connect=10.0)),
        )
        self.model = config.LLM_MODEL
        self.temperature = config.LLM_TEMPERATURE
        self.max_tokens = config.LLM_MAX_TOKENS
        logger.info(f"LLMClient init: model={self.model}, temp={self.temperature}")

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=10))
    def _chat(self, messages):
        return self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
        )

    def generate(self, prompt: str, system: str = None) -> str:
        msgs = []
        if system:
            msgs.append({"role": "system", "content": system})
        msgs.append({"role": "user", "content": prompt})
        try:
            resp = self._chat(msgs)
            content = resp.choices[0].message.content
            logger.debug(f"LLM response usage: prompt={resp.usage.prompt_tokens}, "
                        f"completion={resp.usage.completion_tokens}")
            return content
        except Exception as e:
            logger.error(f"LLM generate failed: {e}")
            raise

    def generate_json(self, prompt: str, system: str = None) -> dict:
        """期望返回 JSON，自动剥掉 ```json 围栏"""
        text = self.generate(prompt, system)
        # 剥掉 markdown code fence（贪婪：嵌套 JSON 必须匹配到最外层 } ）
        m = re.search(r"```(?:json)?\s*(\{.*\}|\[.*\])\s*```", text, re.DOTALL)
        if m:
            text = m.group(1)
        # 直接尝试解析
        try:
            return json.loads(text)
        except Exception:
            # 兜底：找出第一个 {...}
            m = re.search(r"\{.*\}", text, re.DOTALL)
            if m:
                try:
                    return json.loads(m.group(0))
                except Exception as e:
                    logger.error(f"json parse fail: {e}\ntext:{text[:300]}")
                    raise
            raise


_client: LLMClient = None


def get_llm_client() -> LLMClient:
    global _client
    if _client is None:
        _client = LLMClient()
    return _client
