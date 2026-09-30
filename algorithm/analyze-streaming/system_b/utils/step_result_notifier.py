import logging
from typing import Optional

from system_b.utils.progress_notify import ProgressNotifierRedis

logger = logging.getLogger(__name__)

_TITLE_MAP = {
    "query": "查询数据",
    "compute": "执行计算",
    "analyze": "生成分析报告",
    "summarize": "总结",
}


class AnalysisProgressReporter:
    """Centralize analysis progress reporting for app and DAG execution."""

    def __init__(
        self,
        request_id: str,
        stream_name: str,
        notifier: Optional[ProgressNotifierRedis] = None,
    ):
        self.request_id = request_id
        self.stream_name = stream_name
        self.notifier = notifier or ProgressNotifierRedis()
        # 拆解尝试轮次：1 起步，重试时随 attempt_started 递增，
        # 成功后沿用成功轮次直到 close（对应 payload 的 event_index）
        self._attempt = 1

    def _send(
        self,
        message: str,
        step_type: str,
        event_type: str,
        step_id: str = "",
    ) -> None:
        # decompose（模型原始输出）只进 SSE 页面通道（progress_push 桥在 _send
        # 外层发布），供前端调试用：不写日志，也不写 Redis Stream 协议
        if step_type == "decompose":
            return
        # 后台按换行符分隔消息：非空且非流式 token 的消息末尾统一加换行
        if message and event_type != "token":
            message = f"{message}\n"
        # Streaming token events fire once per LLM token (often a bare space
        # or quote); at INFO they flood the log file. The Redis push below is
        # untouched - set log level to DEBUG to see token lines again.
        log = logger.debug if event_type == "token" else logger.info
        log(
            f"[WS-MSG] request_id={self.request_id} step_id={step_id} "
            f"step_type={step_type} event_type={event_type} message={message[:100]}"
        )
        self.notifier.update_sync(
            self.request_id,
            message,
            step_type=step_type,
            event_type=event_type,
            step_id=step_id,
            event_index=self._attempt,
            stream_name=self.stream_name,
        )

    # =====================================================================
    # think 阶段 — 问题拆解
    # =====================================================================

    def intent_started(self, query: str = "") -> None:
        self._send("思考过程", step_type="think", event_type="title", step_id="think")
        self._send("解析用户意图...", step_type="think", event_type="line", step_id="think")

    def original_query_received(self, request_id: str, query: str) -> None:
        self._send(f"原始问题：{query}", step_type="think", event_type="line", step_id="think")

    def decomposition_step(self, step_id: str, description: str) -> None:
        self._send(
            f"匹配步骤：{step_id} - {description}",
            step_type="think",
            event_type="line",
            step_id="think",
        )

    def think_done(self) -> None:
        self._send("", step_type="think", event_type="done", step_id="think")

    # =====================================================================
    # 步骤生命周期 — title / done / error
    # =====================================================================

    # =====================================================================
    # 拆解阶段 — 模型每次尝试的原始输出流式推送
    # =====================================================================

    @staticmethod
    def raw_attempt_step_id(attempt: int) -> str:
        return f"decompose-attempt-{attempt}"

    def attempt_started(self, attempt: int, reason: str = "") -> None:
        self._attempt = attempt
        if attempt > 1 and reason:
            # 重试开始时向 think 容器先发 title 再发 line，message 为上次失败的具体原因，
            # 让用户知道为什么在重新拆解
            self._send(
                "思考过程",
                step_type="think",
                event_type="title",
                step_id="think",
            )
            self._send(
                f"第 {attempt - 1} 次模型输出校验未通过：{reason[:60]}。正在自动重新拆解，请稍候…",
                step_type="think",
                event_type="line",
                step_id="think",
            )
        label = f"模型第 {attempt} 次尝试"
        if reason:
            label += f"（{reason[:60]}）"
        self._send(label, step_type="decompose", event_type="title",
                   step_id=self.raw_attempt_step_id(attempt))

    def notify_raw_stream(self, attempt: int, delta: str) -> None:
        self._send(delta, step_type="decompose", event_type="token",
                   step_id=self.raw_attempt_step_id(attempt))

    def attempt_failed(self, attempt: int, error: str) -> None:
        self._send(f"第 {attempt} 次尝试失败：{error}", step_type="decompose",
                   event_type="error", step_id=self.raw_attempt_step_id(attempt))

    def step_started(self, step_id: str, step_type: str, description: str = "") -> None:
        title = description or _TITLE_MAP.get(step_type, step_type)
        self._send(title, step_type=step_type, event_type="title", step_id=step_id)

    def step_succeeded(self, step_id: str, step_type: str = "") -> None:
        self._send("", step_type=step_type, event_type="done", step_id=step_id)

    def step_failed(self, step_id: str, step_type: str = "", error: Optional[str] = None) -> None:
        self._send(error or "执行失败", step_type=step_type, event_type="error", step_id=step_id)

    # =====================================================================
    # analyze 流式 token
    # =====================================================================

    def notify_analysis_stream(self, step_id: str, delta: str) -> None:
        self._send(delta, step_type="analyze", event_type="token", step_id=step_id)

    def analysis_done(self, step_id: str) -> None:
        self._send("", step_type="analyze", event_type="done", step_id=step_id)

    # =====================================================================
    # summarize 流式 token
    # =====================================================================

    def notify_summary_stream(self, step_id: str, delta: str) -> None:
        self._send(delta, step_type="summarize", event_type="token", step_id=step_id)

    def summary_done(self, step_id: str) -> None:
        self._send("", step_type="summarize", event_type="done", step_id=step_id)

    # =====================================================================
    # compute step result notification
    # =====================================================================

    def notify_step_result(self, step_id: str, result, step_type: str = "compute") -> None:
        """Push step result to frontend as a line message."""
        try:
            text = result.to_display_text() if hasattr(result, "to_display_text") else str(result)
        except Exception:
            text = str(result)
        self._send(text, step_type=step_type, event_type="line", step_id=step_id)


    # =====================================================================
    # 全局结束
    # =====================================================================

    def query_close(self) -> None:
        self._send("", step_type="close", event_type="done")


StepResultNotifier = AnalysisProgressReporter
