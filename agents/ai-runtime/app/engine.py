"""The observe -> decide -> act loop.

Hard guarantees:

* total timeout (``timeouts.totalSec``) is checked against a monotonic
  deadline, never by sleeping;
* each step has a hard timeout (``timeouts.stepSec``) enforced with a short
  lived worker thread plus per-request ``httpx`` timeouts;
* cancellation is checked at the beginning of every step;
* only whitelisted actions ever reach the device;
* every step emits a step callback (screenshot forwarded verbatim) and a log
  callback; callback failures never abort the task.
"""

from __future__ import annotations

import base64
import json
import logging
import threading
import time
from typing import Any, Dict, List, Optional

import httpx

from .cancel import CancelToken
from .models import RuntimeTaskRequest, RuntimeTaskResult
from .providers.base import Decision, DecisionParseError, ProviderError, VisionProvider
from .skills import build_system_prompt, load_skills_text

logger = logging.getLogger("ai_runtime.engine")

#: Contract section 4 action whitelist.  Anything else is a model failure.
ACTION_WHITELIST = frozenset(
    {"click", "input", "swipe", "back", "home", "launch", "wait", "done"}
)

DECISION_MAX_CHARS = 4000
MAX_INVALID_STREAK = 2


class StepTimeout(Exception):
    """Internal: the current step exceeded its deadline."""


# ---------------------------------------------------------------------------
# Action helpers (also used by tests)
# ---------------------------------------------------------------------------
def _is_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _target_label(target: Any) -> str:
    if not isinstance(target, dict):
        return "?"
    return str(target.get("text") or target.get("id") or "?")


def validate_action(action: Dict[str, Any]) -> Optional[str]:
    """Validate the payload of a whitelisted action.

    Returns an error message when the action is malformed, else ``None``.
    """
    if not isinstance(action, dict):
        return "动作必须是 JSON 对象"
    action_type = action.get("type")

    if action_type == "click":
        target = action.get("target")
        if not isinstance(target, dict) or not (target.get("text") or target.get("id")):
            return "click 需要 target.text 或 target.id"
    elif action_type == "input":
        target = action.get("target")
        if not isinstance(target, dict) or not (target.get("text") or target.get("id")):
            return "input 需要 target.text 或 target.id"
        if not isinstance(action.get("text"), str):
            return "input 需要字符串 text"
    elif action_type == "swipe":
        for key in ("from", "to"):
            point = action.get(key)
            if (
                not isinstance(point, dict)
                or not _is_number(point.get("x"))
                or not _is_number(point.get("y"))
            ):
                return "swipe 需要 from/to 坐标 {x, y}"
    elif action_type == "launch":
        app = action.get("app")
        if not isinstance(app, str) or not app.strip():
            return "launch 需要非空 app"
    elif action_type == "wait":
        milliseconds = action.get("ms", action.get("durationMs"))
        if not _is_number(milliseconds) or milliseconds < 0:
            return "wait 需要非负 ms"
    return None


def format_action(action: Dict[str, Any]) -> str:
    """Human readable action string for callbacks/logs, e.g. ``click(登录)``."""
    if not isinstance(action, dict):
        return str(action)
    action_type = action.get("type")
    if action_type == "click":
        return "click(%s)" % _target_label(action.get("target"))
    if action_type == "input":
        return "input(%s)=%s" % (
            _target_label(action.get("target")),
            action.get("text", ""),
        )
    if action_type == "swipe":
        frm = action.get("from") if isinstance(action.get("from"), dict) else {}
        to = action.get("to") if isinstance(action.get("to"), dict) else {}
        return "swipe(%s,%s)->(%s,%s)" % (
            frm.get("x", "?"),
            frm.get("y", "?"),
            to.get("x", "?"),
            to.get("y", "?"),
        )
    if action_type == "launch":
        return "launch(%s)" % action.get("app", "")
    if action_type == "wait":
        milliseconds = action.get("ms", action.get("durationMs", ""))
        return "wait(%sms)" % milliseconds
    return str(action_type)


def decision_text(decision: Decision) -> str:
    """Model raw output capped to 4000 chars for the step callback."""
    raw = (decision.raw or "").strip()
    if not raw:
        raw = decision.analysis or json.dumps(decision.action, ensure_ascii=False)
    return raw[:DECISION_MAX_CHARS]


def is_element_not_found(message: str) -> bool:
    lowered = (message or "").lower()
    return "not found" in lowered or "未找到" in lowered


# ---------------------------------------------------------------------------
# Engine
# ---------------------------------------------------------------------------
class Engine:
    """Runs one AI Runtime task with an injected device + provider."""

    def __init__(
        self,
        device: Any,
        provider: VisionProvider,
        callbacks: Any = None,
    ) -> None:
        self.device = device
        self.provider = provider
        self.callbacks = callbacks

    # -- public API -------------------------------------------------------
    def run(
        self, task: RuntimeTaskRequest, token: Optional[CancelToken] = None
    ) -> RuntimeTaskResult:
        start = time.monotonic()
        try:
            return self._run(task, token, start)
        except Exception as exc:  # defensive: always return a terminal status
            logger.exception("task=%s engine crashed", task.taskId)
            self._send_log(
                [
                    {
                        "level": "ERROR",
                        "message": "task %s 执行期内部错误: %s" % (task.taskId, exc),
                    }
                ]
            )
            return self._result(
                task, "FAILED", "ACTION_FAILED", "执行期内部错误: %s" % exc, 0, start
            )

    # -- loop -------------------------------------------------------------
    def _run(
        self,
        task: RuntimeTaskRequest,
        token: Optional[CancelToken],
        start: float,
    ) -> RuntimeTaskResult:
        total_sec = max(0.001, float(task.timeouts.totalSec))
        step_sec = max(0.001, float(task.timeouts.stepSec))
        deadline = start + total_sec
        max_steps = int(task.maxSteps)
        bdd = task.bdd.model_dump()
        system_prompt = build_system_prompt(load_skills_text(task.skills, log=logger))

        history: List[Dict[str, Any]] = []
        steps = 0
        invalid_streak = 0
        provider_name = getattr(self.provider, "name", "unknown")

        logger.info(
            "task=%s started provider=%s model=%s maxSteps=%d totalSec=%s stepSec=%s",
            task.taskId,
            provider_name,
            task.model.modelId,
            max_steps,
            total_sec,
            step_sec,
        )
        self._send_log(
            [
                {
                    "level": "INFO",
                    "message": "task %s started provider=%s model=%s maxSteps=%d totalSec=%s stepSec=%s"
                    % (
                        task.taskId,
                        provider_name,
                        task.model.modelId,
                        max_steps,
                        total_sec,
                        step_sec,
                    ),
                }
            ]
        )

        # Test isolation: bring the device back to its initial screen so the
        # scenario is not accidentally satisfied by a previous task.
        reset = getattr(self.device, "reset", None)
        if callable(reset):
            try:
                reset()
                logger.info("task=%s device reset before run", task.taskId)
            except Exception:
                logger.warning(
                    "task=%s device reset failed, continuing", task.taskId, exc_info=True
                )

        while True:
            # 1. cancellation is checked at the beginning of every step.
            if token is not None and token.is_cancelled():
                return self._finish(
                    task,
                    "CANCELLED",
                    "MANUAL_INTERRUPT",
                    "任务被人工取消",
                    steps,
                    start,
                )

            # 2. total timeout is a deadline check, never a sleep.
            now = time.monotonic()
            if now >= deadline:
                return self._finish(
                    task,
                    "TIMEOUT",
                    "TIMEOUT",
                    "任务总超时（%gs）" % total_sec,
                    steps,
                    start,
                )

            # 3. maxSteps guard.
            if steps >= max_steps:
                return self._finish(
                    task,
                    "FAILED",
                    "MODEL_DECISION_FAILED",
                    "达到最大步数 %d 仍未完成任务" % max_steps,
                    steps,
                    start,
                )

            step_no = steps + 1
            step_start = time.monotonic()
            step_deadline = min(step_start + step_sec, deadline)
            logger.info("task=%s step=%d begin", task.taskId, step_no)

            # -- observe + decide ----------------------------------------
            png = b""
            try:
                png = self._timed(
                    lambda: self.device.screenshot(timeout=self._slot(step_deadline)),
                    step_deadline,
                )
                ui_tree = self._timed(
                    lambda: self.device.ui_tree(timeout=self._slot(step_deadline)),
                    step_deadline,
                )
                decision = self._timed(
                    lambda: self.provider.decide(
                        system_prompt=system_prompt,
                        bdd=bdd,
                        ui_tree=ui_tree,
                        screenshot_png=png,
                        step_no=step_no,
                        history=list(history),
                    ),
                    step_deadline,
                )
            except DecisionParseError as exc:
                self._report_step(
                    task, step_no, "FAIL", (exc.raw or "")[:DECISION_MAX_CHARS], "", step_start, png
                )
                self._log_step(
                    task, step_no, "FAIL", "", step_start, "ERROR",
                    "模型输出无法解析: %s" % exc,
                )
                return self._finish(
                    task,
                    "FAILED",
                    "MODEL_DECISION_FAILED",
                    "第 %d 步模型输出无法解析" % step_no,
                    step_no,
                    start,
                )
            except ProviderError as exc:
                self._report_step(task, step_no, "FAIL", "", "", step_start, png)
                self._log_step(
                    task, step_no, "FAIL", "", step_start, "ERROR",
                    "模型调用失败: %s" % exc,
                )
                return self._finish(
                    task,
                    "FAILED",
                    "MODEL_DECISION_FAILED",
                    "第 %d 步模型调用失败: %s" % (step_no, exc),
                    step_no,
                    start,
                )
            except StepTimeout:
                self._report_step(task, step_no, "FAIL", "", "", step_start, png)
                self._log_step(
                    task, step_no, "FAIL", "", step_start, "ERROR",
                    "单步超时（>%gs）" % step_sec,
                )
                return self._finish(
                    task,
                    "TIMEOUT",
                    "TIMEOUT",
                    "第 %d 步超时（>%gs）" % (step_no, step_sec),
                    step_no,
                    start,
                )
            except (httpx.TimeoutException, TimeoutError) as exc:
                self._report_step(task, step_no, "FAIL", "", "", step_start, png)
                self._log_step(
                    task, step_no, "FAIL", "", step_start, "ERROR",
                    "请求超时: %s" % exc,
                )
                return self._finish(
                    task,
                    "TIMEOUT",
                    "TIMEOUT",
                    "第 %d 步请求超时" % step_no,
                    step_no,
                    start,
                )
            except Exception as exc:
                self._report_step(task, step_no, "FAIL", "", "", step_start, png)
                self._log_step(
                    task, step_no, "FAIL", "", step_start, "ERROR",
                    "获取设备状态失败: %s" % exc,
                )
                return self._finish(
                    task,
                    "FAILED",
                    "ACTION_FAILED",
                    "第 %d 步获取设备状态失败: %s" % (step_no, exc),
                    step_no,
                    start,
                )

            decision = self._normalize_decision(decision)
            raw = decision_text(decision)
            action = decision.action if isinstance(decision.action, dict) else {}
            action_type = action.get("type")

            # -- done -----------------------------------------------------
            if decision.done or action_type == "done":
                self._report_step(task, step_no, "OK", raw, "done", step_start, png)
                self._log_step(task, step_no, "OK", "done", step_start, "INFO")
                summary = (task.bdd.then or decision.summary or "任务完成").strip()
                return self._finish(
                    task, "SUCCESS", None, summary, step_no, start
                )

            # -- whitelist enforcement (never execute) --------------------
            if not isinstance(action_type, str) or action_type not in ACTION_WHITELIST:
                display = "invalid(%s)" % (action_type,)
                self._report_step(task, step_no, "FAIL", raw, display, step_start, png)
                self._log_step(
                    task, step_no, "FAIL", display, step_start, "ERROR",
                    "动作不在白名单: %r" % (action_type,),
                )
                return self._finish(
                    task,
                    "FAILED",
                    "MODEL_DECISION_FAILED",
                    "第 %d 步模型给出白名单外的动作: %r" % (step_no, action_type),
                    step_no,
                    start,
                )

            problem = validate_action(action)
            if problem:
                invalid_streak += 1
                display = format_action(action)
                self._report_step(task, step_no, "FAIL", raw, display, step_start, png)
                self._log_step(
                    task, step_no, "FAIL", display, step_start, "ERROR",
                    "无效动作: %s" % problem,
                )
                history.append(
                    {
                        "seq": step_no,
                        "action": action,
                        "actionText": display,
                        "analysis": decision.analysis,
                        "status": "INVALID",
                    }
                )
                if invalid_streak >= MAX_INVALID_STREAK:
                    return self._finish(
                        task,
                        "FAILED",
                        "MODEL_DECISION_FAILED",
                        "连续 %d 次无效决策（%s）" % (invalid_streak, problem),
                        step_no,
                        start,
                    )
                logger.warning(
                    "task=%s step=%d invalid action (%s); retrying next step",
                    task.taskId,
                    step_no,
                    problem,
                )
                steps = step_no
                continue

            # -- act ------------------------------------------------------
            display = format_action(action)
            try:
                response = self._timed(
                    lambda: self.device.action(
                        action, timeout=self._slot(step_deadline)
                    ),
                    step_deadline,
                )
            except StepTimeout:
                self._report_step(task, step_no, "FAIL", raw, display, step_start, png)
                self._log_step(
                    task, step_no, "FAIL", display, step_start, "ERROR",
                    "动作执行超时（>%gs）" % step_sec,
                )
                return self._finish(
                    task,
                    "TIMEOUT",
                    "TIMEOUT",
                    "第 %d 步动作 %s 执行超时" % (step_no, display),
                    step_no,
                    start,
                )
            except (httpx.TimeoutException, TimeoutError) as exc:
                self._report_step(task, step_no, "FAIL", raw, display, step_start, png)
                self._log_step(
                    task, step_no, "FAIL", display, step_start, "ERROR",
                    "动作请求超时: %s" % exc,
                )
                return self._finish(
                    task,
                    "TIMEOUT",
                    "TIMEOUT",
                    "第 %d 步动作 %s 请求超时" % (step_no, display),
                    step_no,
                    start,
                )
            except Exception as exc:
                self._report_step(task, step_no, "FAIL", raw, display, step_start, png)
                self._log_step(
                    task, step_no, "FAIL", display, step_start, "ERROR",
                    "动作请求异常: %s" % exc,
                )
                return self._finish(
                    task,
                    "FAILED",
                    "ACTION_FAILED",
                    "第 %d 步执行动作 %s 异常: %s" % (step_no, display, exc),
                    step_no,
                    start,
                )

            elapsed_ms = self._elapsed_ms(step_start)
            response = response if isinstance(response, dict) else {}
            ok = bool(response.get("ok"))
            message = str(response.get("message") or "")

            if not ok:
                reason = (
                    "ELEMENT_NOT_FOUND" if is_element_not_found(message) else "ACTION_FAILED"
                )
                self._report_step(task, step_no, "FAIL", raw, display, step_start, png)
                self._log_step(
                    task, step_no, "FAIL", display, step_start, "ERROR",
                    "ok=false reason=%s message=%s" % (reason, message),
                )
                return self._finish(
                    task,
                    "FAILED",
                    reason,
                    "第 %d 步动作 %s 执行失败: %s"
                    % (step_no, display, message or "ok=false"),
                    step_no,
                    start,
                )

            self._report_step(task, step_no, "OK", raw, display, step_start, png)
            self._log_step(task, step_no, "OK", display, step_start, "INFO")
            history.append(
                {
                    "seq": step_no,
                    "action": action,
                    "actionText": display,
                    "analysis": decision.analysis,
                    "status": "OK",
                }
            )
            steps = step_no
            invalid_streak = 0
            logger.info(
                "task=%s step=%d action=%s elapsedMs=%d",
                task.taskId,
                step_no,
                display,
                elapsed_ms,
            )

    # -- helpers ---------------------------------------------------------
    @staticmethod
    def _normalize_decision(decision: Any) -> Decision:
        if isinstance(decision, Decision):
            return decision
        # Tolerate simple duck-typed providers.
        return Decision(
            action=getattr(decision, "action", {}) or {},
            analysis=getattr(decision, "analysis", "") or "",
            done=bool(getattr(decision, "done", False)),
            summary=getattr(decision, "summary", None),
            raw=getattr(decision, "raw", None),
        )

    @staticmethod
    def _slot(deadline: float) -> float:
        return max(0.001, deadline - time.monotonic())

    @staticmethod
    def _elapsed_ms(since: float) -> int:
        return int((time.monotonic() - since) * 1000)

    @staticmethod
    def _timed(fn: Any, deadline: float) -> Any:
        """Run ``fn`` with a hard deadline; raises :class:`StepTimeout`."""
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise StepTimeout()

        box: Dict[str, Any] = {}

        def runner() -> None:
            try:
                box["value"] = fn()
            except BaseException as exc:  # re-raised in the caller thread
                box["error"] = exc

        thread = threading.Thread(target=runner, name="ai-runtime-step", daemon=True)
        thread.start()
        thread.join(remaining)
        if thread.is_alive():
            raise StepTimeout()
        if "error" in box:
            raise box["error"]
        return box.get("value")

    def _report_step(
        self,
        task: RuntimeTaskRequest,
        seq: int,
        status: str,
        decision: str,
        action: str,
        step_start: float,
        png: bytes,
    ) -> None:
        payload = {
            "seq": seq,
            "status": status,
            "decision": (decision or "")[:DECISION_MAX_CHARS],
            "action": action,
            "elapsedMs": self._elapsed_ms(step_start),
            "screenshotBase64": base64.b64encode(png).decode("ascii") if png else "",
            "screenshotContentType": "image/png",
        }
        if self.callbacks is None:
            return
        try:
            self.callbacks.send_step(payload)
        except Exception as exc:  # callback failures never block the task
            logger.warning(
                "task=%s step=%d step callback failed: %s", task.taskId, seq, exc
            )

    def _log_step(
        self,
        task: RuntimeTaskRequest,
        seq: int,
        status: str,
        action: str,
        step_start: float,
        level: str,
        detail: str = "",
    ) -> None:
        elapsed_ms = self._elapsed_ms(step_start)
        message = "step %d %s action=%s elapsedMs=%d" % (
            seq,
            status,
            action or "-",
            elapsed_ms,
        )
        if detail:
            message += " " + detail
        self._send_log([{"level": level, "message": message}])
        log_line = "task=%s step=%d status=%s action=%s elapsedMs=%d"
        args = (task.taskId, seq, status, action or "-", elapsed_ms)
        if detail:
            log_line += " detail=%s"
            args = args + (detail,)
        if level == "ERROR":
            logger.error(log_line, *args)
        elif level == "WARN":
            logger.warning(log_line, *args)
        else:
            logger.info(log_line, *args)

    def _send_log(self, lines: List[Dict[str, Any]]) -> None:
        if self.callbacks is None:
            return
        try:
            self.callbacks.send_log(lines)
        except Exception as exc:
            logger.warning("log callback failed: %s", exc)

    def _finish(
        self,
        task: RuntimeTaskRequest,
        status: str,
        reason: Optional[str],
        summary: str,
        total_steps: int,
        start: float,
    ) -> RuntimeTaskResult:
        result = self._result(task, status, reason, summary, total_steps, start)
        level = "ERROR" if status in ("FAILED", "TIMEOUT") else "INFO"
        self._send_log(
            [
                {
                    "level": level,
                    "message": "task %s finished status=%s reason=%s steps=%d elapsedMs=%d"
                    % (
                        task.taskId,
                        status,
                        reason or "-",
                        result.totalSteps,
                        result.elapsedMs,
                    ),
                }
            ]
        )
        log_fn = logger.error if level == "ERROR" else logger.info
        log_fn(
            "task=%s finished status=%s reason=%s steps=%d elapsedMs=%d",
            task.taskId,
            status,
            reason,
            result.totalSteps,
            result.elapsedMs,
        )
        return result

    @staticmethod
    def _result(
        task: RuntimeTaskRequest,
        status: str,
        reason: Optional[str],
        summary: str,
        total_steps: int,
        start: float,
    ) -> RuntimeTaskResult:
        return RuntimeTaskResult(
            taskId=task.taskId,
            status=status,  # type: ignore[arg-type]
            reasonCategory=reason,
            summary=summary or "",
            totalSteps=total_steps,
            elapsedMs=int((time.monotonic() - start) * 1000),
        )
