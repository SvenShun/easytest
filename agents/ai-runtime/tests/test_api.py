"""FastAPI surface tests (health / validation / 409 / cancel)."""

import copy
import threading

import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from app.models import RuntimeTaskResult

client = TestClient(app)

VALID_TASK = {
    "taskId": "t-api-1",
    "bdd": {
        "given": "已打开 App 且处于未登录状态",
        "when": "输入用户名 tester 和密码 secret 并点击登录",
        "then": "进入首页并显示 登录成功",
    },
    "skills": {"repo": "file:///tmp/skills", "commit": "abc123", "dir": None},
    "timeouts": {"totalSec": 120, "stepSec": 30},
    "maxSteps": 25,
    "model": {
        "provider": "mock",
        "endpoint": "",
        "apiKey": "sk-should-never-be-logged",
        "modelId": "mock-vision",
    },
    "device": {"baseUrl": "http://127.0.0.1:8090", "serial": "emulator-5554"},
    "callbacks": {
        "stepUrl": "http://127.0.0.1:8081/api/v1/ui-execution/tasks/t-api-1/steps",
        "logUrl": "http://127.0.0.1:8081/api/v1/ui-execution/tasks/t-api-1/logs",
        "token": "agent-token",
    },
}


def payload(**overrides):
    data = copy.deepcopy(VALID_TASK)
    data.update(overrides)
    return data


def test_health():
    response = client.get("/runtime/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["version"] == "1.0.0"
    assert body["providers"] == ["mock", "openai"]
    assert body["skillsCacheDir"]
    assert body["skillsCacheDir"].endswith("skills")


def test_task_requires_task_id():
    data = payload()
    data.pop("taskId")
    assert client.post("/runtime/tasks", json=data).status_code == 422


def test_task_requires_device():
    data = payload()
    data.pop("device")
    assert client.post("/runtime/tasks", json=data).status_code == 422


@pytest.mark.parametrize("max_steps", [0, -1, 201])
def test_max_steps_is_validated(max_steps):
    response = client.post("/runtime/tasks", json=payload(maxSteps=max_steps))
    assert response.status_code == 422


def test_unknown_provider_is_rejected():
    data = payload(
        model={"provider": "bogus", "endpoint": "", "apiKey": "", "modelId": "m"}
    )
    response = client.post("/runtime/tasks", json=data)
    assert response.status_code == 400
    assert response.json()["detail"]["code"] == "MODEL_NOT_CONFIGURED"


def test_openai_provider_requires_endpoint():
    data = payload(
        model={
            "provider": "openai",
            "endpoint": "",
            "apiKey": "sk-x",
            "modelId": "gpt-4o-mini",
        }
    )
    response = client.post("/runtime/tasks", json=data)
    assert response.status_code == 400


def test_cancel_unknown_task_returns_false():
    response = client.post("/runtime/tasks/never-seen/cancel")
    assert response.status_code == 200
    assert response.json() == {"cancelled": False}


def test_create_task_returns_engine_result(monkeypatch):
    captured = {}

    def fake_execute(task, token):
        captured["task"] = task
        captured["token"] = token
        return RuntimeTaskResult(
            taskId=task.taskId,
            status="SUCCESS",
            reasonCategory=None,
            summary="进入首页并显示 登录成功",
            totalSteps=2,
            elapsedMs=42,
        )

    monkeypatch.setattr(main_module, "execute_task", fake_execute)

    response = client.post("/runtime/tasks", json=payload(taskId="t-api-ok"))

    assert response.status_code == 200
    assert response.json() == {
        "taskId": "t-api-ok",
        "status": "SUCCESS",
        "reasonCategory": None,
        "summary": "进入首页并显示 登录成功",
        "totalSteps": 2,
        "elapsedMs": 42,
    }
    assert captured["task"].taskId == "t-api-ok"
    assert captured["token"].task_id == "t-api-ok"

    # After completion the taskId is released; cancel is a no-op.
    assert client.post("/runtime/tasks/t-api-ok/cancel").json() == {"cancelled": False}


def test_concurrent_same_task_id_returns_409(monkeypatch):
    entered = threading.Event()
    release = threading.Event()

    def blocking_execute(task, token):
        entered.set()
        assert release.wait(5)
        return RuntimeTaskResult(
            taskId=task.taskId,
            status="SUCCESS",
            reasonCategory=None,
            summary="done",
            totalSteps=0,
            elapsedMs=1,
        )

    monkeypatch.setattr(main_module, "execute_task", blocking_execute)
    task_id = "t-api-concurrent"
    result = {}

    def first_request():
        result["response"] = client.post("/runtime/tasks", json=payload(taskId=task_id))

    thread = threading.Thread(target=first_request)
    thread.start()
    try:
        assert entered.wait(5), "first request never entered execute_task"

        duplicate = client.post("/runtime/tasks", json=payload(taskId=task_id))
        assert duplicate.status_code == 409
        assert duplicate.json()["detail"]["code"] == "TASK_ALREADY_RUNNING"

        cancel = client.post("/runtime/tasks/%s/cancel" % task_id)
        assert cancel.status_code == 200
        assert cancel.json() == {"cancelled": True}
    finally:
        release.set()
        thread.join(5)

    assert not thread.is_alive()
    assert result["response"].status_code == 200
    assert result["response"].json()["status"] == "SUCCESS"

    # Registry is clean after the task finishes.
    assert client.post("/runtime/tasks/%s/cancel" % task_id).json() == {
        "cancelled": False
    }
