"""Best-effort callbacks to ui-execution (contract section 2.3).

Step / log callbacks must never break the engine: any HTTP or serialization
failure is logged and swallowed.  Screenshots are forwarded verbatim (no
resizing / re-encoding) by the engine.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

import httpx

from .models import CallbackSpec

logger = logging.getLogger("ai_runtime.callbacks")


class CallbackClient:
    """Posts step and log callbacks with ``X-Agent-Token`` authentication."""

    def __init__(
        self,
        spec: CallbackSpec,
        timeout: float = 10.0,
        client: Optional[httpx.Client] = None,
    ) -> None:
        self.step_url = (spec.stepUrl or "").strip()
        self.log_url = (spec.logUrl or "").strip()
        self.token = spec.token or ""
        self.timeout = float(timeout)
        self._owns_client = client is None
        self._client = client or httpx.Client(timeout=self.timeout)

    def _headers(self) -> Dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["X-Agent-Token"] = self.token
        return headers

    def _post(self, url: str, body: Dict[str, Any], kind: str) -> bool:
        if not url:
            logger.debug("no %s callback url configured; skipping", kind)
            return False
        try:
            response = self._client.post(
                url, json=body, headers=self._headers(), timeout=self.timeout
            )
            if response.status_code >= 400:
                logger.warning(
                    "%s callback rejected: url=%s status=%s body=%s",
                    kind,
                    url,
                    response.status_code,
                    response.text[:300],
                )
                return False
            return True
        except Exception as exc:  # callback failure must not block the task
            logger.warning("%s callback failed: url=%s error=%s", kind, url, exc)
            return False

    def send_step(self, payload: Dict[str, Any]) -> bool:
        return self._post(self.step_url, payload, "step")

    def send_log(self, lines: List[Dict[str, Any]]) -> bool:
        return self._post(self.log_url, {"lines": lines}, "log")

    def close(self) -> None:
        if self._owns_client:
            try:
                self._client.close()
            except Exception:  # pragma: no cover - defensive
                logger.debug("failed to close callback http client", exc_info=True)

    def __enter__(self) -> "CallbackClient":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()
