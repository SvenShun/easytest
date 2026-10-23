"""HTTP client for the device-agent device endpoints (contract section 3).

Endpoints used by the runtime (all served by the local device sub-server):

``GET  {baseUrl}/device/{serial}/screenshot`` -> PNG bytes
``GET  {baseUrl}/device/{serial}/ui-tree``    -> UI tree JSON
``POST {baseUrl}/device/{serial}/action``     -> ``{"ok": bool, "message": str, "screen": str}``

The client is deliberately tiny and duck-typed so the engine can be unit
tested with a fake device.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional
from urllib.parse import quote

import httpx

logger = logging.getLogger("ai_runtime.device")


class DeviceClient:
    """Synchronous client for one device on a device-agent."""

    def __init__(
        self,
        base_url: str,
        serial: str,
        timeout: float = 30.0,
        client: Optional[httpx.Client] = None,
    ) -> None:
        if not base_url:
            raise ValueError("device.baseUrl is required")
        if not serial:
            raise ValueError("device.serial is required")
        self.base_url = base_url.rstrip("/")
        self.serial = serial
        self.timeout = float(timeout)
        self._owns_client = client is None
        self._client = client or httpx.Client(timeout=self.timeout)

    # -- helpers ---------------------------------------------------------
    @property
    def _device_prefix(self) -> str:
        return "%s/device/%s" % (self.base_url, quote(self.serial, safe=""))

    @staticmethod
    def _effective_timeout(timeout: Optional[float], default: float) -> float:
        if timeout is None:
            return default
        return max(0.001, float(timeout))

    # -- device API ------------------------------------------------------
    def screenshot(self, timeout: Optional[float] = None) -> bytes:
        url = self._device_prefix + "/screenshot"
        response = self._client.get(
            url, timeout=self._effective_timeout(timeout, self.timeout)
        )
        response.raise_for_status()
        return response.content

    def ui_tree(self, timeout: Optional[float] = None) -> Dict[str, Any]:
        url = self._device_prefix + "/ui-tree"
        response = self._client.get(
            url, timeout=self._effective_timeout(timeout, self.timeout)
        )
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, dict):
            raise ValueError("device ui-tree response is not a JSON object")
        return data

    def action(
        self, action: Dict[str, Any], timeout: Optional[float] = None
    ) -> Dict[str, Any]:
        url = self._device_prefix + "/action"
        # Provider decisions use ``type`` (``{"type":"click",...}``); the device
        # contract §3 uses ``action``.  Normalize at the transport boundary.
        payload = dict(action or {})
        if "type" in payload and "action" not in payload:
            payload["action"] = payload.pop("type")
        response = self._client.post(
            url,
            json=payload,
            timeout=self._effective_timeout(timeout, self.timeout),
        )
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, dict):
            raise ValueError("device action response is not a JSON object")
        return data

    def reset(self) -> Dict[str, Any]:
        """Return the simulated/real device to a clean app state.

        Test isolation matters for AI execution: without a reset a previous
        task may leave the app on a screen where the next scenario is already
        "satisfied", producing a one-step trace.
        """
        url = self._device_prefix + "/reset"
        response = self._client.post(url, timeout=self.timeout)
        response.raise_for_status()
        data = response.json()
        return data if isinstance(data, dict) else {"ok": True}

    # -- lifecycle -------------------------------------------------------
    def close(self) -> None:
        if self._owns_client:
            try:
                self._client.close()
            except Exception:  # pragma: no cover - defensive
                logger.debug("failed to close device http client", exc_info=True)

    def __enter__(self) -> "DeviceClient":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()
