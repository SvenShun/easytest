"""OpenAI-compatible provider parsing / retry tests (fully offline)."""

import base64
import json

import httpx
import pytest

from app.providers.base import DecisionParseError, ProviderError
from app.providers.openai import (
    CORRECTION_PROMPT,
    OpenAICompatibleProvider,
    extract_json_object,
    parse_decision_text,
)

PNG = b"\x89PNG\r\n\x1a\nfake"
BDD = {"given": "g", "when": "输入用户名 tester", "then": "登录成功"}
UI_TREE = {
    "screen": {"title": "登录", "texts": ["用户名", "密码"]},
    "elements": [{"text": "用户名", "editable": True}],
}

FENCED = (
    "```json\n"
    '{"analysis":"点击登录","done":false,'
    '"action":{"type":"click","target":{"text":"登录"}}}\n'
    "```"
)


def make_client(contents):
    """Return (httpx client, captured requests); ``contents(i)`` per attempt."""
    requests = []

    def handler(request):
        requests.append(request)
        index = len(requests) - 1
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": contents(index)}}]},
        )

    return httpx.Client(transport=httpx.MockTransport(handler), timeout=5), requests


def make_provider(client, **overrides):
    settings = {
        "endpoint": "http://model.local/v1",
        "api_key": "sk-test-secret",
        "model_id": "mock-vision",
        "step_timeout_sec": 5,
        "http_client": client,
    }
    settings.update(overrides)
    return OpenAICompatibleProvider(**settings)


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------
def test_parse_code_fenced_json():
    decision = parse_decision_text(FENCED)
    assert decision.action == {"type": "click", "target": {"text": "登录"}}
    assert decision.analysis == "点击登录"
    assert decision.done is False
    assert decision.raw == FENCED


def test_parse_json_with_surrounding_prose():
    text = (
        "首先分析页面...\n"
        '{"analysis":"已完成","done":true,"summary":"ok"}\n'
        "以上是我的结论。"
    )
    decision = parse_decision_text(text)
    assert decision.done is True
    assert decision.action == {"type": "done"}
    assert decision.summary == "ok"


def test_parse_rejects_non_json_and_missing_action():
    with pytest.raises(ValueError):
        parse_decision_text("sorry, I cannot help with that")
    with pytest.raises(ValueError):
        parse_decision_text('{"analysis":"x","done":false}')
    with pytest.raises(ValueError):
        parse_decision_text('{"analysis":"x","done":"maybe"}')
    with pytest.raises(ValueError):
        extract_json_object("no braces here")


def test_parse_tolerates_string_booleans():
    decision = parse_decision_text(
        '{"analysis":"x","done":"false","action":{"type":"back"}}'
    )
    assert decision.done is False
    assert decision.action == {"type": "back"}


# ---------------------------------------------------------------------------
# Request / response
# ---------------------------------------------------------------------------
def test_provider_sends_openai_compatible_request():
    client, requests = make_client(lambda index: FENCED)
    provider = make_provider(client)

    decision = provider.decide(
        system_prompt="## Skills\n登录流程说明",
        bdd=BDD,
        ui_tree=UI_TREE,
        screenshot_png=PNG,
        step_no=3,
        history=[{"seq": 1, "actionText": "click(登录)"}],
    )

    assert decision.action["type"] == "click"
    assert len(requests) == 1
    request = requests[0]
    assert str(request.url) == "http://model.local/v1/chat/completions"
    assert request.headers["Authorization"] == "Bearer sk-test-secret"

    body = json.loads(request.content)
    assert body["model"] == "mock-vision"
    assert body["temperature"] == 0

    system_content = body["messages"][0]["content"]
    assert body["messages"][0]["role"] == "system"
    assert "## Skills\n登录流程说明" in system_content
    assert "输出格式" in system_content
    assert "白名单" in system_content

    user_content = body["messages"][1]["content"]
    text_part = user_content[0]["text"]
    assert "BDD" in text_part
    assert "登录" in text_part
    assert "第 3 步" in text_part

    image_part = user_content[1]
    assert image_part["type"] == "image_url"
    expected = "data:image/png;base64," + base64.b64encode(PNG).decode("ascii")
    assert image_part["image_url"]["url"] == expected


def test_provider_endpoint_with_full_chat_path_is_not_doubled():
    client, requests = make_client(lambda index: FENCED)
    provider = make_provider(client, endpoint="http://model.local/v1/chat/completions")
    provider.decide("", BDD, UI_TREE, PNG, 1, [])
    assert str(requests[0].url) == "http://model.local/v1/chat/completions"


def test_provider_retries_once_after_malformed_output():
    client, requests = make_client(
        lambda index: "这不是 JSON" if index == 0 else FENCED
    )
    provider = make_provider(client)

    decision = provider.decide("", BDD, UI_TREE, PNG, 1, [])

    assert decision.action["type"] == "click"
    assert len(requests) == 2

    second_body = json.loads(requests[1].content)
    roles = [message["role"] for message in second_body["messages"]]
    assert roles == ["system", "user", "assistant", "user"]
    assert second_body["messages"][-1]["content"] == CORRECTION_PROMPT


def test_provider_malformed_twice_raises_decision_parse_error():
    client, requests = make_client(lambda index: "still not json")
    provider = make_provider(client)

    with pytest.raises(DecisionParseError) as exc_info:
        provider.decide("", BDD, UI_TREE, PNG, 1, [])

    assert len(requests) == 2
    assert exc_info.value.attempts == 2
    assert exc_info.value.raw == "still not json"


def test_provider_http_error_raises_provider_error():
    def handler(request):
        return httpx.Response(500, text="boom")

    client = httpx.Client(transport=httpx.MockTransport(handler), timeout=5)
    provider = make_provider(client)

    with pytest.raises(ProviderError):
        provider.decide("", BDD, UI_TREE, PNG, 1, [])


def test_provider_timeout_propagates_as_httpx_timeout():
    def handler(request):
        raise httpx.ReadTimeout("slow model", request=request)

    client = httpx.Client(transport=httpx.MockTransport(handler), timeout=5)
    provider = make_provider(client)

    with pytest.raises(httpx.TimeoutException):
        provider.decide("", BDD, UI_TREE, PNG, 1, [])


def test_missing_endpoint_is_rejected_at_construction():
    with pytest.raises(ValueError):
        OpenAICompatibleProvider(endpoint="", api_key="x", model_id="m")
