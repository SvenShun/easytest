"""Engine loop tests using injected FakeDevice / FakeProvider / FakeCallback."""

import base64
import threading
import time

from app.cancel import CancelToken
from app.engine import Engine
from app.models import (
    BDD,
    CallbackSpec,
    DeviceSpec,
    ModelSpec,
    RuntimeTaskRequest,
    SkillsRef,
    Timeouts,
)
from app.providers.base import (
    Decision,
    DecisionParseError,
    VisionProvider,
)
from app.skills import load_skills_text

PNG = b"\x89PNG\r\n\x1a\nfake-png-bytes"
BDD_DATA = BDD(
    given="已打开 App 且处于未登录状态",
    when="输入用户名 tester 和密码 secret 并点击登录",
    then="进入首页并显示 登录成功",
)


# ---------------------------------------------------------------------------
# Fakes
# ---------------------------------------------------------------------------
class FakeDevice:
    def __init__(self, trees=None, action_results=None):
        self.trees = list(
            trees
            or [
                {
                    "width": 1080,
                    "height": 1920,
                    "screen": {"app": "com.testhub.demo", "title": "HOME", "texts": []},
                    "elements": [],
                }
            ]
        )
        self.action_results = list(action_results or [])
        self.actions = []
        self.screenshots = 0

    def screenshot(self, timeout=None):
        self.screenshots += 1
        return PNG

    def ui_tree(self, timeout=None):
        if len(self.trees) > 1:
            return self.trees.pop(0)
        return self.trees[0]

    def action(self, action, timeout=None):
        self.actions.append(action)
        if self.action_results:
            return self.action_results.pop(0)
        return {"ok": True, "message": "ok"}


class ScriptedProvider(VisionProvider):
    name = "scripted"

    def __init__(self, decisions):
        self.decisions = list(decisions)
        self.calls = []

    def decide(
        self,
        system_prompt,
        bdd,
        ui_tree,
        screenshot_png,
        step_no,
        history,
    ):
        self.calls.append(
            {
                "system_prompt": system_prompt,
                "bdd": bdd,
                "ui_tree": ui_tree,
                "screenshot_png": screenshot_png,
                "step_no": step_no,
                "history": list(history),
            }
        )
        if not self.decisions:
            raise AssertionError("ScriptedProvider ran out of decisions")
        item = self.decisions.pop(0)
        if isinstance(item, BaseException):
            raise item
        return item


class SlowProvider(VisionProvider):
    name = "slow"

    def __init__(self, sleep_sec):
        self.sleep_sec = sleep_sec

    def decide(self, system_prompt, bdd, ui_tree, screenshot_png, step_no, history):
        time.sleep(self.sleep_sec)
        return Decision(action={"type": "click", "target": {"text": "登录"}}, analysis="慢")


class BlockingProvider(VisionProvider):
    name = "blocking"

    def __init__(self):
        self.started = threading.Event()
        self.release = threading.Event()

    def decide(self, system_prompt, bdd, ui_tree, screenshot_png, step_no, history):
        self.started.set()
        self.release.wait(5)
        return Decision(
            action={"type": "click", "target": {"text": "登录"}}, analysis="点击登录"
        )


class FakeCallback:
    def __init__(self):
        self.steps = []
        self.logs = []

    def send_step(self, payload):
        self.steps.append(payload)
        return True

    def send_log(self, lines):
        self.logs.extend(lines)
        return True


class ExplodingCallback:
    def send_step(self, payload):
        raise RuntimeError("callback down")

    def send_log(self, lines):
        raise RuntimeError("callback down")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def make_task(**overrides):
    data = {
        "taskId": "t-engine",
        "bdd": BDD_DATA,
        "skills": SkillsRef(repo="file:///tmp/skills", commit="abc123"),
        "timeouts": Timeouts(totalSec=5, stepSec=2),
        "maxSteps": 5,
        "model": ModelSpec(provider="mock", modelId="mock-vision"),
        "device": DeviceSpec(baseUrl="http://127.0.0.1:8090", serial="emulator-5554"),
        "callbacks": CallbackSpec(
            stepUrl="http://127.0.0.1:8081/api/v1/ui-execution/tasks/t/steps",
            logUrl="http://127.0.0.1:8081/api/v1/ui-execution/tasks/t/logs",
            token="agent-token",
        ),
    }
    data.update(overrides)
    return RuntimeTaskRequest(**data)


def run_engine(provider, device=None, callbacks=None, task=None, token=None):
    task = task or make_task()
    engine = Engine(
        device=device or FakeDevice(),
        provider=provider,
        callbacks=callbacks if callbacks is not None else FakeCallback(),
    )
    return engine.run(task, token or CancelToken(task.taskId))


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------
def test_success_path_emits_step_callbacks():
    provider = ScriptedProvider(
        [
            Decision(
                action={"type": "click", "target": {"text": "登录"}},
                analysis="点击登录按钮",
            ),
            Decision(
                action={"type": "done"},
                analysis="已看到登录成功",
                done=True,
                summary="已完成：进入首页并显示 登录成功",
            ),
        ]
    )
    device = FakeDevice()
    callbacks = FakeCallback()

    result = run_engine(provider, device=device, callbacks=callbacks)

    assert result.status == "SUCCESS"
    assert result.reasonCategory is None
    assert result.summary == "进入首页并显示 登录成功"
    assert result.totalSteps == 2
    assert result.elapsedMs >= 0
    assert device.actions == [{"type": "click", "target": {"text": "登录"}}]
    assert len(callbacks.steps) == 2

    first = callbacks.steps[0]
    assert first["seq"] == 1
    assert first["status"] == "OK"
    assert first["action"] == "click(登录)"
    assert first["decision"] == "点击登录按钮"
    assert first["elapsedMs"] >= 0
    assert first["screenshotBase64"] == base64.b64encode(PNG).decode("ascii")
    assert first["screenshotContentType"] == "image/png"

    second = callbacks.steps[1]
    assert second["seq"] == 2
    assert second["status"] == "OK"
    assert second["action"] == "done"

    messages = [line["message"] for line in callbacks.logs]
    assert any(message.startswith("step 1 OK") for message in messages)
    assert any(message.startswith("step 2 OK") for message in messages)
    assert any("finished status=SUCCESS" in message for message in messages)


def test_done_action_does_not_touch_device():
    provider = ScriptedProvider(
        [Decision(action={"type": "done"}, analysis="完成", done=True)]
    )
    device = FakeDevice()
    result = run_engine(provider, device=device)
    assert result.status == "SUCCESS"
    assert result.totalSteps == 1
    assert device.actions == []


def test_action_failure_not_found_maps_to_element_not_found():
    provider = ScriptedProvider(
        [Decision(action={"type": "click", "target": {"text": "登录"}}, analysis="点击")]
    )
    device = FakeDevice(
        action_results=[{"ok": False, "message": "element not found: 登录"}]
    )
    callbacks = FakeCallback()
    result = run_engine(provider, device=device, callbacks=callbacks)

    assert result.status == "FAILED"
    assert result.reasonCategory == "ELEMENT_NOT_FOUND"
    assert result.totalSteps == 1
    assert callbacks.steps[0]["status"] == "FAIL"
    assert any(line["level"] == "ERROR" for line in callbacks.logs)


def test_action_failure_generic_maps_to_action_failed():
    provider = ScriptedProvider(
        [Decision(action={"type": "back"}, analysis="返回")]
    )
    device = FakeDevice(action_results=[{"ok": False, "message": "driver crashed"}])
    result = run_engine(provider, device=device)
    assert result.status == "FAILED"
    assert result.reasonCategory == "ACTION_FAILED"


def test_illegal_action_is_blocked_by_whitelist():
    provider = ScriptedProvider(
        [
            Decision(
                action={"type": "uninstall", "package": "com.testhub.demo"},
                analysis="卸载应用",
            )
        ]
    )
    device = FakeDevice()
    callbacks = FakeCallback()

    result = run_engine(provider, device=device, callbacks=callbacks)

    assert result.status == "FAILED"
    assert result.reasonCategory == "MODEL_DECISION_FAILED"
    assert device.actions == []
    assert callbacks.steps[0]["status"] == "FAIL"
    assert callbacks.steps[0]["action"].startswith("invalid")


def test_step_timeout_returns_timeout():
    task = make_task(timeouts=Timeouts(totalSec=5, stepSec=0.05))
    provider = SlowProvider(sleep_sec=0.3)
    result = run_engine(provider, task=task)
    assert result.status == "TIMEOUT"
    assert result.reasonCategory == "TIMEOUT"


def test_total_timeout_returns_timeout():
    task = make_task(timeouts=Timeouts(totalSec=0.05, stepSec=30))
    provider = SlowProvider(sleep_sec=0.3)
    result = run_engine(provider, task=task)
    assert result.status == "TIMEOUT"
    assert result.reasonCategory == "TIMEOUT"


def test_cancel_returns_cancelled():
    task = make_task(timeouts=Timeouts(totalSec=10, stepSec=5))
    provider = BlockingProvider()
    token = CancelToken(task.taskId)
    holder = {}

    def runner():
        holder["result"] = Engine(
            device=FakeDevice(), provider=provider, callbacks=FakeCallback()
        ).run(task, token)

    thread = threading.Thread(target=runner)
    thread.start()
    assert provider.started.wait(2), "provider was never called"
    token.cancel()
    provider.release.set()
    thread.join(5)

    assert not thread.is_alive()
    result = holder["result"]
    assert result.status == "CANCELLED"
    assert result.reasonCategory == "MANUAL_INTERRUPT"


def test_cancel_before_start_returns_cancelled_without_device_calls():
    task = make_task()
    token = CancelToken(task.taskId)
    token.cancel()
    device = FakeDevice()
    callbacks = FakeCallback()
    result = run_engine(
        ScriptedProvider([]), device=device, callbacks=callbacks, task=task, token=token
    )
    assert result.status == "CANCELLED"
    assert result.totalSteps == 0
    assert device.actions == []
    assert callbacks.steps == []


def test_decision_parse_error_maps_to_model_decision_failed():
    provider = ScriptedProvider(
        [DecisionParseError("bad json", raw="not a json decision", attempts=2)]
    )
    callbacks = FakeCallback()
    result = run_engine(provider, callbacks=callbacks)
    assert result.status == "FAILED"
    assert result.reasonCategory == "MODEL_DECISION_FAILED"
    assert callbacks.steps[0]["status"] == "FAIL"
    assert callbacks.steps[0]["decision"] == "not a json decision"


def test_malformed_whitelisted_action_is_tolerated_once():
    provider = ScriptedProvider(
        [
            Decision(action={"type": "click"}, analysis="缺少目标"),
            Decision(action={"type": "click", "target": {"text": "登录"}}, analysis="点击"),
            Decision(action={"type": "done"}, analysis="完成", done=True),
        ]
    )
    device = FakeDevice()
    callbacks = FakeCallback()
    result = run_engine(provider, device=device, callbacks=callbacks)

    assert result.status == "SUCCESS"
    assert result.totalSteps == 3
    assert [step["status"] for step in callbacks.steps] == ["FAIL", "OK", "OK"]
    assert device.actions == [{"type": "click", "target": {"text": "登录"}}]


def test_two_consecutive_invalid_decisions_fail():
    provider = ScriptedProvider(
        [
            Decision(action={"type": "click"}, analysis="缺少目标"),
            Decision(action={"type": "click"}, analysis="仍然缺少目标"),
        ]
    )
    result = run_engine(provider, device=FakeDevice())
    assert result.status == "FAILED"
    assert result.reasonCategory == "MODEL_DECISION_FAILED"
    assert result.totalSteps == 2


def test_max_steps_terminates_with_failure():
    provider = ScriptedProvider(
        [
            Decision(action={"type": "click", "target": {"text": "下一步"}}, analysis="1"),
            Decision(action={"type": "click", "target": {"text": "下一步"}}, analysis="2"),
            Decision(action={"type": "done"}, analysis="never reached", done=True),
        ]
    )
    task = make_task(maxSteps=2)
    result = run_engine(provider, task=task)
    assert result.status == "FAILED"
    assert result.reasonCategory == "MODEL_DECISION_FAILED"
    assert result.totalSteps == 2


def test_callback_failures_do_not_block_task():
    provider = ScriptedProvider(
        [
            Decision(action={"type": "click", "target": {"text": "登录"}}, analysis="点击"),
            Decision(action={"type": "done"}, analysis="完成", done=True),
        ]
    )
    result = run_engine(provider, callbacks=ExplodingCallback())
    assert result.status == "SUCCESS"


def test_skills_readme_is_injected_into_system_prompt(tmp_path):
    (tmp_path / "README.md").write_text("登录流程说明：先输入用户名。", encoding="utf-8")
    (tmp_path / "SKILLS.md").write_text("这条不应被优先读取。", encoding="utf-8")
    task = make_task(
        skills=SkillsRef(repo="file:///tmp/skills", commit="abc", dir=str(tmp_path))
    )
    provider = ScriptedProvider(
        [Decision(action={"type": "done"}, analysis="完成", done=True)]
    )
    result = run_engine(provider, task=task)

    assert result.status == "SUCCESS"
    prompt = provider.calls[0]["system_prompt"]
    assert prompt.startswith("## Skills\n")
    assert "登录流程说明" in prompt
    assert "这条不应被优先读取" not in prompt


def test_missing_skills_dir_is_skipped(tmp_path):
    missing = tmp_path / "does-not-exist"
    task = make_task(skills=SkillsRef(repo="file:///x", commit="abc", dir=str(missing)))
    provider = ScriptedProvider(
        [Decision(action={"type": "done"}, analysis="完成", done=True)]
    )
    result = run_engine(provider, task=task)
    assert result.status == "SUCCESS"
    assert provider.calls[0]["system_prompt"] == ""


def test_skills_text_is_truncated_to_8000_chars(tmp_path):
    (tmp_path / "SKILLS.md").write_text("x" * 9000, encoding="utf-8")
    text = load_skills_text(SkillsRef(repo="file:///x", commit="abc", dir=str(tmp_path)))
    assert len(text) == 8000
