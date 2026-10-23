"""Provider abstraction for the AI Runtime.

A provider turns one observation (UI tree + screenshot + BDD) into one
``Decision``.  Providers must be deterministic given their inputs when
``provider == "mock"`` (offline demos / tests), and may call an OpenAI
compatible endpoint otherwise.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

# Platform constraints injected into every vision-model system prompt.  The
# whitelist is enforced a second time by the engine (never trust the model).
PLATFORM_CONSTRAINTS = """你是 TestHub 的 Android 设备侧 UI 自动化执行器。你根据 BDD 用例、当前 UI 树和截图，逐步决定下一个设备动作。

规则：
1. 只允许以下动作（白名单）：click, input, swipe, back, home, launch, wait, done。
2. 禁止卸载应用、清除数据、修改系统设置、关机、重启、拨号、发短信等危险动作。
3. 每次只输出一个动作；动作执行后会重新观察设备再决定下一步。
4. 目标达成时输出 done=true（action 可为 {"type":"done"}）。
5. 若页面出现与 BDD then 一致的文本（例如“登录成功”），应立即结束。
6. 不要重复执行已经成功且无进展的动作；必要时使用 back/home 返回。
"""


@dataclass
class Decision:
    """One model decision.

    ``action`` uses the device-agent action schema, e.g.
    ``{"type": "click", "target": {"text": "登录"}}``.
    ``raw`` keeps the model's original output for the step callback.
    """

    action: Dict[str, Any]
    analysis: str = ""
    done: bool = False
    summary: Optional[str] = None
    raw: Optional[str] = None
    extra: Dict[str, Any] = field(default_factory=dict)


class ProviderError(Exception):
    """Transport / server failure while talking to a model provider."""


class DecisionParseError(ProviderError):
    """Model output could not be parsed into a valid decision (after retries)."""

    def __init__(self, message: str, raw: Optional[str] = None, attempts: int = 1) -> None:
        super().__init__(message)
        self.raw = raw or ""
        self.attempts = attempts


class VisionProvider(ABC):
    """Interface implemented by mock / OpenAI-compatible providers."""

    name: str = "base"

    @abstractmethod
    def decide(
        self,
        system_prompt: str,
        bdd: Dict[str, Any],
        ui_tree: Dict[str, Any],
        screenshot_png: bytes,
        step_no: int,
        history: List[Dict[str, Any]],
    ) -> Decision:
        """Return the next action for the current observation."""

    def close(self) -> None:
        """Release provider resources (HTTP clients)."""
