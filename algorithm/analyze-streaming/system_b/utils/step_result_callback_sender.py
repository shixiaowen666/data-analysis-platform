import logging
from typing import Any, Optional

from system_b.communication.system_a_client import SystemAClient

logger = logging.getLogger(__name__)


class StepResultCallbackSender:
    """Send finalized step results back to System A through one shared entry."""

    def __init__(self, request_id: str, system_a_client: Optional[SystemAClient] = None):
        self.request_id = request_id
        self.system_a_client = system_a_client or SystemAClient()

    @staticmethod
    def serialize_payload(payload: Any) -> Any:
        if payload is None:
            return None
        if hasattr(payload, "to_dict") and callable(payload.to_dict):
            return payload.to_dict()
        return payload

    def send_result(
        self,
        step_id: str,
        step_type: str,
        description: str,
        payload: Any,
    ) -> None:
        if payload is None:
            return

        result_data = self.serialize_payload(payload)

        try:
            logger.info(f"发送步骤类型={step_type}-步骤id={step_id}的中间结果")
            self.system_a_client.send_compute_result(
                request_id=self.request_id,
                step_id=step_id,
                step_type=step_type,
                description=description,
                result_data=result_data,
            )
        except Exception as e:
            logger.warning(f"[StepResultCallbackSender] Failed to sync result for {step_id}: {e}")

    def send_step_result(self, step: dict, payload: Any) -> None:
        self.send_result(
            step_id=step.get("step_id", ""),
            step_type=step.get("step_type", ""),
            description=step.get("description", ""),
            payload=payload,
        )