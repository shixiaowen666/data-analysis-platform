"""
LLM Utility Module
====================
Provides a unified interface for LLM API calls used in:
1. Step decomposition
2. Analyze (reason analysis)
3. Summarize
"""

import json
import logging
import re
import time
import uuid
from typing import Optional

from system_b.config import Config
from system_b.models.exceptions import LLMError
from system_b.utils.llm_call_log import write_llm_call

logger = logging.getLogger(__name__)


def _ensure_request_id(request_id: str) -> str:
    """Return the given request_id, or generate one when the caller
    hasn't been wired to pass it yet, so downstream logging still works."""
    if request_id and request_id.strip():
        return request_id
    generated = f"test-{uuid.uuid4().hex[:12]}"
    logger.debug(f"[LLM] request_id empty, generated fallback: {generated}")
    return generated


def _get_client():
    """Get OpenAI client with configuration.

    Many self-hosted / proxied OpenAI-compatible endpoints (e.g. a private
    DashScope/vLLM gateway addressed via LLM_API_BASE_URL) do NOT require an
    API key. The official OpenAI SDK, however, refuses to construct a client
    when ``api_key`` is empty and raises "Missing credentials". To support
    keyless endpoints we fall back to a harmless non-empty placeholder when
    LLM_API_KEY is not configured — the placeholder is only sent if the
    backend actually checks it.
    """
    try:
        from openai import OpenAI
        api_key = (Config.LLM_API_KEY or "").strip() or "EMPTY"
        if not (Config.LLM_API_KEY or "").strip():
            logger.warning(
                "LLM_API_KEY is empty; using placeholder 'EMPTY' for the "
                "OpenAI-compatible client (assumed keyless endpoint at "
                f"{Config.LLM_API_BASE_URL})."
            )
        client = OpenAI(
            api_key=api_key,
            base_url=Config.LLM_API_BASE_URL,
        )
        return client
    except Exception as e:
        logger.error(f"Failed to create OpenAI client: {e}")
        raise LLMError(f"Failed to create OpenAI client: {e}")


def _log_prompt_size(system_prompt: str, user_prompt: str, tag: str):
    """Log the character and estimated token size of prompts."""
    sys_chars = len(system_prompt)
    usr_chars = len(user_prompt)
    total_chars = sys_chars + usr_chars
    est_tokens = total_chars // 2
    logger.info(
        f"[LLM] {tag} prompt size: system={sys_chars} chars, "
        f"user={usr_chars} chars, total={total_chars} chars, "
        f"est_tokens≈{est_tokens}"
    )


def call_llm(
    system_prompt: str,
    user_prompt: str,
    temperature: Optional[float] = None,
    max_tokens: Optional[int] = None,
    json_mode: bool = False,
    request_id: str = "",
    source: str = "",
    sys_prompt_ref: Optional[dict] = None,
    usr_template_ref: Optional[dict] = None,
    usr_vars: Optional[dict] = None,
) -> str:
    """
    Call LLM API and return the response text.

    Args:
        system_prompt: System role message
        user_prompt: User message
        temperature: Override default temperature
        max_tokens: Override default max tokens
        json_mode: If True, request JSON output format
        request_id: The API request ID this call belongs to
        source: "decompose" | "step_analysis" | "summary" | "test"
        sys_prompt_ref: {"group": "prompt-main", "version": "v1.0.0"}
        usr_template_ref: {"group": "prompt-main", "version": "v1.0.0"}
        usr_vars: {"query": "...", "db_meta_hash": "...", ...}

    Returns:
        Response text from LLM
    """
    request_id = _ensure_request_id(request_id)
    client = _get_client()
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]

    kwargs = {
        "model": Config.LLM_MODEL,
        "messages": messages,
        "temperature": temperature if temperature is not None else Config.LLM_TEMPERATURE,
        "max_tokens": max_tokens or Config.LLM_MAX_TOKENS,
        "extra_body": {
            "chat_template_kwargs": {"enable_thinking": False}
        },
    }

    if json_mode:
        kwargs["response_format"] = {"type": "json_object"}

    _log_prompt_size(system_prompt, user_prompt, "call_llm")
    t0 = time.time()
    logger.info(f"[LLM] Calling {Config.LLM_MODEL}, json_mode={json_mode}, req={request_id}, source={source}")
    try:
        response = client.chat.completions.create(**kwargs)
        result = response.choices[0].message.content
        elapsed = time.time() - t0
        logger.info(
            f"[LLM] Response received, length={len(result)}, "
            f"elapsed={elapsed:.2f}s, req={request_id}"
        )
        write_llm_call(
            request_id=request_id,
            source=source,
            model=Config.LLM_MODEL,
            sys_prompt_ref=sys_prompt_ref or {},
            usr_template_ref=usr_template_ref or {},
            usr_vars=usr_vars or {},
            resp={"chars": len(result), "ms": int(elapsed * 1000), "finish": "stop"},
        )
        return result
    except Exception as e:
        elapsed = time.time() - t0
        logger.error(f"[LLM] API call failed after {elapsed:.2f}s: {e}")
        write_llm_call(
            request_id=request_id,
            source=source,
            model=Config.LLM_MODEL,
            sys_prompt_ref=sys_prompt_ref or {},
            usr_template_ref=usr_template_ref or {},
            usr_vars=usr_vars or {},
            resp={"chars": 0, "ms": int(elapsed * 1000), "finish": "error"},
            error=str(e),
        )
        raise LLMError(f"LLM API call failed: {e}")


def call_llm_json(
    system_prompt: str,
    user_prompt: str,
    temperature: Optional[float] = None,
    max_tokens: Optional[int] = None,
    request_id: str = "",
    source: str = "",
    sys_prompt_ref: Optional[dict] = None,
    usr_template_ref: Optional[dict] = None,
    usr_vars: Optional[dict] = None,
) -> dict:
    """
    Call LLM API and parse the response as JSON.
    Attempts to extract JSON from response even if wrapped in markdown.
    """
    result = call_llm(
        system_prompt, user_prompt, temperature, max_tokens,
        json_mode=True,
        request_id=request_id, source=source,
        sys_prompt_ref=sys_prompt_ref, usr_template_ref=usr_template_ref,
        usr_vars=usr_vars,
    )
    return parse_json_from_text(result)


def call_llm_stream(
    system_prompt: str,
    user_prompt: str,
    temperature: Optional[float] = None,
    max_tokens: Optional[int] = None,
    request_id: str = "",
    source: str = "",
    sys_prompt_ref: Optional[dict] = None,
    usr_template_ref: Optional[dict] = None,
    usr_vars: Optional[dict] = None,
):
    """
    Stream LLM response token-by-token via a generator.
    Compatible with call_llm() parameters, but yields deltas instead of returning a string.
    """
    request_id = _ensure_request_id(request_id)
    client = _get_client()
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]
    kwargs = {
        "model": Config.LLM_MODEL,
        "messages": messages,
        "temperature": temperature if temperature is not None else Config.LLM_TEMPERATURE,
        "max_tokens": max_tokens or Config.LLM_MAX_TOKENS,
        "stream": True,
        "extra_body": {"chat_template_kwargs": {"enable_thinking": False}},
    }

    _log_prompt_size(system_prompt, user_prompt, "call_llm_stream")
    t0 = time.time()
    chunk_count = 0
    logger.info(f"[LLM-Stream] Calling {Config.LLM_MODEL}, req={request_id}, source={source}")
    try:
        stream = client.chat.completions.create(**kwargs)
        parts = []
        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                chunk_count += 1
                delta = chunk.choices[0].delta.content
                parts.append(delta)
                yield delta
        elapsed = time.time() - t0
        full_text = "".join(parts)
        logger.info(
            f"[LLM-Stream] Stream finished, chunks={chunk_count}, "
            f"elapsed={elapsed:.2f}s, req={request_id}"
        )
        write_llm_call(
            request_id=request_id,
            source=source,
            model=Config.LLM_MODEL,
            sys_prompt_ref=sys_prompt_ref or {},
            usr_template_ref=usr_template_ref or {},
            usr_vars=usr_vars or {},
            resp={"chars": len(full_text), "ms": int(elapsed * 1000), "finish": "stop"},
        )
    except Exception as e:
        elapsed = time.time() - t0
        logger.error(f"[LLM-Stream] API call failed after {elapsed:.2f}s: {e}")
        write_llm_call(
            request_id=request_id,
            source=source,
            model=Config.LLM_MODEL,
            sys_prompt_ref=sys_prompt_ref or {},
            usr_template_ref=usr_template_ref or {},
            usr_vars=usr_vars or {},
            resp={"chars": 0, "ms": int(elapsed * 1000), "finish": "error"},
            error=str(e),
        )
        raise LLMError(f"LLM stream call failed: {e}")


def parse_json_from_text(text: str) -> dict:
    """
    Parse JSON from LLM response text.
    Handles cases where JSON is wrapped in ```json ... ``` blocks.
    """
    # Try direct parse first
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Try extracting from code block
    pattern = r"```(?:json)?\s*([\s\S]*?)```"
    match = re.search(pattern, text)
    if match:
        try:
            return json.loads(match.group(1))
        except json.JSONDecodeError:
            pass

    # Try finding the first { ... } block
    brace_start = text.find("{")
    if brace_start >= 0:
        depth = 0
        for i in range(brace_start, len(text)):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(text[brace_start:i + 1])
                    except json.JSONDecodeError:
                        break

    raise LLMError(f"Failed to parse JSON from LLM response: {text[:200]}...")