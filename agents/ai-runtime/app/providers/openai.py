"""Provider for any OpenAI compatible ``/chat/completions`` endpoint.

Used with ``provider=openai`` or ``provider=deepseek``.  The provider asks the
model for a strict JSON decision, tolerates Markdown fences / surrounding
text, and retries exactly once with a correction message when parsing fails.
A second parse failure raises :class:`DecisionParseError`, which the engine
maps to ``MODEL_DECISION_FAILED``.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any, Dict, List, Optional

import httpx

from .base import (
    PLATFORM_CONSTRAINTS,
    Decision,
    DecisionParseError,
    ProviderError,
    VisionProvider,
)

logger = logging.getLogger("ai_runtime.provider.openai")

OUTPUT_FORMAT_INSTRUCTIONS = """## 输出格式（必须严格遵守）
只输出一个 JSON 对象，不要输出 Markdown 代码围栏、解释或任何多余文字：
{"analysis":"一句中文决策说明","done":false,"action":{"type":"click","target":{"text":"登录"}}}

可用动作与参数（白名单）：
- click:  {"type":"click","target":{"text":"登录"}} 或 {"type":"click","target":{"id":"btn_login"}}
- input:  {"type":"input","target":{"text":"用户名"},"text":"tester"}
- swipe:  {"type":"swipe","from":{"x":500,"y":1500},"to":{"x":500,"y":400},"durationMs":300}
- back:   {"type":"back"}
- home:   {"type":"home"}
- launch: {"type":"launch","app":"com.testhub.demo"}
- wait:   {"type":"wait","ms":500}
- done:   {"type":"done"}

BDD 的 then 条件满足时输出 done=true，可省略 action。"""

CORRECTION_PROMPT = (
    "你上一次的输出不是合法的 JSON 决策。请只输出一个 JSON 对象，"
    "不要包含 Markdown 代码围栏或解释文字，格式为："
    '{"analysis":"...","done":false,"action":{"type":"click","target":{"text":"登录"}}}'
)


# ---------------------------------------------------------------------------
# Parsing helpers
# ---------------------------------------------------------------------------
_FENCE_RE = re.compile(r"```[ \t]*(?:json)?[ \t]*\r?\n?(.*?)```", re.IGNORECASE | re.DOTALL)


def strip_code_fences(text: str) -> str:
    """Return the content of the first Markdown fence, or the text unchanged."""
    match = _FENCE_RE.search(text or "")
    if match:
        return match.group(1).strip()
    return (text or "").strip()


def _balanced_object(text: str, start: int) -> Optional[str]:
    """Extract the JSON object beginning at ``start`` (brace matching)."""
    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start : index + 1]
    return None


def extract_json_object(text: str) -> Dict[str, Any]:
    """Find and decode the first JSON object in ``text``.

    Tolerates ```json fences and leading/trailing prose.
    """
    cleaned = strip_code_fences(text)
    try:
        data = json.loads(cleaned)
        if isinstance(data, dict):
            return data
    except (ValueError, TypeError):
        pass

    for index, char in enumerate(cleaned):
        if char != "{":
            continue
        candidate = _balanced_object(cleaned, index)
        if candidate is None:
            continue
        try:
            data = json.loads(candidate)
        except (ValueError, TypeError):
            continue
        if isinstance(data, dict):
            return data
    raise ValueError("no JSON object found in model output")


def _as_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in {"true", "1", "yes", "y"}
    if isinstance(value, (int, float)):
        return bool(value)
    return False


def parse_decision_text(content: str) -> Decision:
    """Parse a model message into a :class:`Decision` (raises ``ValueError``)."""
    data = extract_json_object(content)

    analysis_raw = data.get("analysis")
    if isinstance(analysis_raw, str):
        analysis = analysis_raw
    elif analysis_raw is None:
        analysis = ""
    else:
        analysis = json.dumps(analysis_raw, ensure_ascii=False)

    done = _as_bool(data.get("done", False))
    action = data.get("action")
    if action is None:
        if not done:
            raise ValueError("decision JSON is missing 'action'")
        action = {"type": "done"}
    if not isinstance(action, dict):
        raise ValueError("decision 'action' must be a JSON object")
    if not done and not action.get("type"):
        raise ValueError("decision 'action.type' is missing")

    summary_raw = data.get("summary")
    summary = summary_raw if isinstance(summary_raw, str) and summary_raw else None
    return Decision(
        action=action,
        analysis=analysis,
        done=done,
        summary=summary,
        raw=content,
    )


def _content_to_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
            elif isinstance(item, str):
                parts.append(item)
        return "\n".join(parts)
    return ""


# ---------------------------------------------------------------------------
# Provider
# ---------------------------------------------------------------------------
class OpenAICompatibleProvider(VisionProvider):
    """Calls ``POST {endpoint}/chat/completions`` with a base64 screenshot."""

    name = "openai"

    def __init__(
        self,
        endpoint: str,
        api_key: str = "",
        model_id: str = "",
        step_timeout_sec: float = 30.0,
        http_client: Optional[httpx.Client] = None,
    ) -> None:
        endpoint = (endpoint or "").strip()
        if not endpoint:
            raise ValueError("model.endpoint is required for openai/deepseek providers")
        self.endpoint = endpoint.rstrip("/")
        self.api_key = api_key or ""
        self.model_id = model_id or "gpt-4o-mini"
        self.step_timeout_sec = max(1.0, float(step_timeout_sec))
        self._owns_client = http_client is None
        self._client = http_client or httpx.Client(timeout=self.step_timeout_sec)

    # -- request building -------------------------------------------------
    @property
    def chat_url(self) -> str:
        if self.endpoint.endswith("/chat/completions"):
            return self.endpoint
        return self.endpoint + "/chat/completions"

    def _system_message(self, system_prompt: str) -> str:
        parts = [PLATFORM_CONSTRAINTS]
        if system_prompt and system_prompt.strip():
            parts.append(system_prompt.strip())
        parts.append(OUTPUT_FORMAT_INSTRUCTIONS)
        return "\n\n".join(parts)

    def _user_content(
        self,
        bdd: Dict[str, Any],
        ui_tree: Dict[str, Any],
        screenshot_png: bytes,
        step_no: int,
        history: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        import base64

        if hasattr(bdd, "model_dump"):
            bdd = bdd.model_dump()
        text_parts = [
            "## BDD\n" + json.dumps(bdd, ensure_ascii=False, indent=2),
            "## 当前 UI 树\n" + json.dumps(ui_tree, ensure_ascii=False, indent=2),
            "## 当前步骤\n这是第 %d 步。" % step_no,
        ]
        if history:
            recent = history[-10:]
            text_parts.append(
                "## 最近动作历史\n" + json.dumps(recent, ensure_ascii=False, indent=2)
            )
        content: List[Dict[str, Any]] = [
            {"type": "text", "text": "\n\n".join(text_parts)}
        ]
        if screenshot_png:
            encoded = base64.b64encode(screenshot_png).decode("ascii")
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": "data:image/png;base64," + encoded},
                }
            )
        return content

    def _headers(self) -> Dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = "Bearer " + self.api_key
        return headers

    def _complete(self, messages: List[Dict[str, Any]]) -> str:
        body = {
            "model": self.model_id,
            "temperature": 0,
            "messages": messages,
        }
        try:
            response = self._client.post(
                self.chat_url,
                json=body,
                headers=self._headers(),
                timeout=self.step_timeout_sec,
            )
        except httpx.TimeoutException:
            raise
        except httpx.HTTPError as exc:
            raise ProviderError("模型请求失败: %s" % exc)

        if response.status_code >= 400:
            raise ProviderError(
                "模型返回 HTTP %s: %s" % (response.status_code, response.text[:500])
            )
        try:
            data = response.json()
        except ValueError:
            raise ProviderError("模型响应不是合法 JSON")
        choices = data.get("choices") if isinstance(data, dict) else None
        if not isinstance(choices, list) or not choices:
            raise ProviderError("模型响应缺少 choices")
        message = choices[0].get("message") if isinstance(choices[0], dict) else None
        content = message.get("content") if isinstance(message, dict) else None
        text = _content_to_text(content)
        if not text.strip():
            raise ProviderError("模型响应缺少 content")
        return text

    # -- provider API -----------------------------------------------------
    def decide(
        self,
        system_prompt: str,
        bdd: Dict[str, Any],
        ui_tree: Dict[str, Any],
        screenshot_png: bytes,
        step_no: int,
        history: List[Dict[str, Any]],
    ) -> Decision:
        messages: List[Dict[str, Any]] = [
            {"role": "system", "content": self._system_message(system_prompt)},
            {
                "role": "user",
                "content": self._user_content(
                    bdd, ui_tree, screenshot_png, step_no, history
                ),
            },
        ]

        last_raw = ""
        for attempt in (1, 2):
            content = self._complete(messages)
            last_raw = content
            try:
                return parse_decision_text(content)
            except ValueError as exc:
                logger.warning(
                    "openai provider parse failure: attempt=%d error=%s", attempt, exc
                )
                if attempt == 1:
                    messages = list(messages) + [
                        {"role": "assistant", "content": content[:4000]},
                        {"role": "user", "content": CORRECTION_PROMPT},
                    ]
                    continue
                raise DecisionParseError(
                    "模型输出无法解析为决策 JSON: %s" % exc,
                    raw=last_raw,
                    attempts=2,
                )

        # Unreachable, keeps type checkers happy.
        raise DecisionParseError("模型输出无法解析", raw=last_raw, attempts=2)

    def close(self) -> None:
        if self._owns_client:
            try:
                self._client.close()
            except Exception:  # pragma: no cover - defensive
                logger.debug("failed to close openai http client", exc_info=True)
