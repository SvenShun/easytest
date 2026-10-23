"""End-to-end offline test: FastAPI -> Engine -> DeviceClient / CallbackClient.

A tiny stdlib HTTP server simulates the device-agent device endpoints and the
ui-execution callbacks, so the whole synchronous HTTP path (including the
mock login strategy) is exercised without any real model or emulator.
"""

import base64
import json
import logging
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest
from fastapi.testclient import TestClient

from app.main import app

PNG = b"\x89PNG\r\n\x1a\nintegration-png"
BDD_THEN = "进入首页并显示 登录成功"


def element(text="", element_id="", element_type="TextView", clickable=False, editable=False):
    return {
        "id": element_id,
        "text": text,
        "type": element_type,
        "clickable": clickable,
        "editable": editable,
        "bounds": {"x": 0, "y": 0, "width": 100, "height": 40},
    }


def tree(title, texts, elements):
    return {
        "width": 1080,
        "height": 1920,
        "screen": {"app": "com.testhub.demo", "title": title, "texts": texts},
        "elements": elements,
    }


TREES = [
    tree("HOME", ["首页", "欢迎"], [element("登录", "btn_login", "Button", clickable=True)]),
    tree(
        "登录",
        ["用户名", "密码"],
        [
            element("用户名", "et_username", "EditText", editable=True),
            element("密码", "et_password", "EditText", editable=True),
            element("登录", "btn_login", "Button", clickable=True),
        ],
    ),
    tree(
        "登录",
        ["密码"],
        [
            element("tester", "et_username", "EditText", editable=True),
            element("密码", "et_password", "EditText", editable=True),
            element("登录", "btn_login", "Button", clickable=True),
        ],
    ),
    tree(
        "登录",
        ["用户名已填写", "密码已填写"],
        [
            element("tester", "et_username", "EditText", editable=True),
            element("••••••", "et_password", "EditText", editable=True),
            element("登录", "btn_login", "Button", clickable=True),
        ],
    ),
    tree("首页", ["登录成功", "欢迎回来"], [element("登录成功", "tv_welcome")]),
]


class FakeAgent:
    """Device endpoints + ui-execution callback endpoints."""

    def __init__(self):
        self.tree_index = 0
        self.actions = []
        self.steps = []
        self.logs = []
        self.step_tokens = []
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), self._make_handler())
        self.port = self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)

    @property
    def base_url(self):
        return "http://127.0.0.1:%d" % self.port

    def start(self):
        self.thread.start()
        return self

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(5)

    def current_tree(self):
        return TREES[min(self.tree_index, len(TREES) - 1)]

    def _make_handler(self):
        agent = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, fmt, *args):  # silence test output
                pass

            def _send_json(self, status, payload):
                body = json.dumps(payload).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def _send_bytes(self, status, payload, content_type):
                self.send_response(status)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def do_GET(self):
                if self.path == "/device/emulator-5554/screenshot":
                    self._send_bytes(200, PNG, "image/png")
                elif self.path == "/device/emulator-5554/ui-tree":
                    self._send_json(200, agent.current_tree())
                else:
                    self._send_json(404, {"error": "not found", "path": self.path})

            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                raw = self.rfile.read(length)
                payload = json.loads(raw) if raw else {}
                if self.path == "/device/emulator-5554/action":
                    agent.actions.append(payload)
                    agent.tree_index += 1
                    self._send_json(
                        200,
                        {"ok": True, "message": "performed", "screen": "next"},
                    )
                elif self.path.endswith("/steps"):
                    agent.steps.append(payload)
                    agent.step_tokens.append(self.headers.get("X-Agent-Token"))
                    self._send_json(201, {"stepId": "s%d" % payload.get("seq", 0)})
                elif self.path.endswith("/logs"):
                    agent.logs.extend(payload.get("lines") or [])
                    self.send_response(204)
                    self.end_headers()
                else:
                    self._send_json(404, {"error": "not found", "path": self.path})

        return Handler


@pytest.fixture()
def fake_agent():
    agent = FakeAgent().start()
    try:
        yield agent
    finally:
        agent.stop()


def test_full_login_flow_over_http(fake_agent, caplog):
    caplog.set_level(logging.INFO)
    client = TestClient(app)
    body = {
        "taskId": "t-integration",
        "bdd": {
            "given": "已打开 App 且处于未登录状态",
            "when": "输入用户名 tester 和密码 secret 并点击登录",
            "then": BDD_THEN,
        },
        "skills": {"repo": "file:///tmp/skills", "commit": "abc123", "dir": None},
        "timeouts": {"totalSec": 30, "stepSec": 10},
        "maxSteps": 10,
        "model": {
            "provider": "mock",
            "endpoint": "",
            "apiKey": "sk-never-logged",
            "modelId": "mock-vision",
        },
        "device": {
            "baseUrl": fake_agent.base_url,
            "serial": "emulator-5554",
        },
        "callbacks": {
            "stepUrl": fake_agent.base_url + "/api/v1/ui-execution/tasks/t/steps",
            "logUrl": fake_agent.base_url + "/api/v1/ui-execution/tasks/t/logs",
            "token": "agent-token-integration",
        },
    }

    response = client.post("/runtime/tasks", json=body)
    assert response.status_code == 200, response.text
    result = response.json()
    assert result["taskId"] == "t-integration"
    assert result["status"] == "SUCCESS"
    assert result["reasonCategory"] is None
    assert result["summary"] == BDD_THEN
    assert result["totalSteps"] == 5
    assert result["elapsedMs"] >= 0

    # Device action sequence produced by MockProvider (wire format uses
    # contract §3 ``action`` key, not the provider's ``type``).
    assert fake_agent.actions == [
        {"action": "click", "target": {"text": "登录"}},
        {"action": "input", "target": {"text": "用户名"}, "text": "tester"},
        {"action": "input", "target": {"text": "密码"}, "text": "secret"},
        {"action": "click", "target": {"text": "登录"}},
    ]

    # Step callbacks: 5 steps, screenshot forwarded verbatim, token attached.
    assert [step["seq"] for step in fake_agent.steps] == [1, 2, 3, 4, 5]
    assert [step["action"] for step in fake_agent.steps] == [
        "click(登录)",
        "input(用户名)=tester",
        "input(密码)=secret",
        "click(登录)",
        "done",
    ]
    assert all(step["status"] == "OK" for step in fake_agent.steps)
    assert fake_agent.steps[0]["screenshotBase64"] == base64.b64encode(PNG).decode("ascii")
    assert fake_agent.steps[0]["screenshotContentType"] == "image/png"
    assert fake_agent.step_tokens == ["agent-token-integration"] * 5

    # Logs: one line per step plus lifecycle lines.
    messages = [line["message"] for line in fake_agent.logs]
    assert any(message.startswith("step 1 OK") for message in messages)
    assert any(message.startswith("step 5 OK") for message in messages)
    assert any("finished status=SUCCESS" in message for message in messages)
    assert all(line["level"] in ("INFO", "ERROR") for line in fake_agent.logs)

    # The API key must never be logged.
    assert "sk-never-logged" not in caplog.text
