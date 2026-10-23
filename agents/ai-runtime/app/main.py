"""FastAPI entrypoint for the TestHub AI Runtime (port 8095).

Endpoints (contract section 4)::

    POST /runtime/tasks
    POST /runtime/tasks/{taskId}/cancel
    GET  /runtime/health

``POST /runtime/tasks`` executes synchronously: the response carries the
terminal status.  Concurrent submissions of the same taskId get HTTP 409.
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException

from . import __version__
from .callbacks import CallbackClient
from .cancel import CancelRegistry, CancelToken, TaskAlreadyRunning
from .device import DeviceClient
from .engine import Engine
from .models import (
    CancelResponse,
    HealthResponse,
    RuntimeTaskRequest,
    RuntimeTaskResult,
)
from .providers import SUPPORTED_PROVIDERS, build_provider, validate_model_config

LOG_LEVEL = os.environ.get("AI_RUNTIME_LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
)
logger = logging.getLogger("ai_runtime.main")


def _default_skills_cache_dir() -> str:
    configured = os.environ.get("AI_RUNTIME_SKILLS_CACHE_DIR")
    if configured:
        return str(Path(configured).expanduser().resolve())
    return str((Path(__file__).resolve().parent.parent / "data" / "skills").resolve())


SKILLS_CACHE_DIR = _default_skills_cache_dir()

app = FastAPI(
    title="TestHub AI Runtime",
    version=__version__,
    description="设备侧 AI 执行期决策器（观察 -> 决策 -> 动作）",
)
registry = CancelRegistry()
app.state.registry = registry


def execute_task(task: RuntimeTaskRequest, token: CancelToken) -> RuntimeTaskResult:
    """Build the real device / provider / callback clients and run the engine.

    Kept as a module level function so tests can monkeypatch it without
    touching the HTTP layer.
    """
    step_timeout = float(task.timeouts.stepSec)
    provider = build_provider(task.model, step_timeout_sec=step_timeout)
    callbacks = CallbackClient(task.callbacks)
    device: Optional[DeviceClient] = None
    try:
        device = DeviceClient(
            task.device.baseUrl, task.device.serial, timeout=step_timeout
        )
        engine = Engine(device=device, provider=provider, callbacks=callbacks)
        return engine.run(task, token)
    finally:
        if device is not None:
            device.close()
        provider.close()
        callbacks.close()


@app.get("/runtime/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        version=__version__,
        providers=list(SUPPORTED_PROVIDERS),
        skillsCacheDir=SKILLS_CACHE_DIR,
    )


@app.post("/runtime/tasks", response_model=RuntimeTaskResult)
def create_runtime_task(task: RuntimeTaskRequest) -> RuntimeTaskResult:
    # Validate model configuration before registering, so a bad request does
    # not occupy a taskId.
    try:
        validate_model_config(task.model)
    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail={"code": "MODEL_NOT_CONFIGURED", "message": str(exc)},
        )

    try:
        token = registry.register(task.taskId)
    except TaskAlreadyRunning:
        raise HTTPException(
            status_code=409,
            detail={
                "code": "TASK_ALREADY_RUNNING",
                "message": "task %s is already running" % task.taskId,
            },
        )

    logger.info(
        "task=%s accepted provider=%s model=%s device=%s/%s",
        task.taskId,
        task.model.provider,
        task.model.modelId,
        task.device.baseUrl,
        task.device.serial,
    )
    try:
        return execute_task(task, token)
    finally:
        registry.unregister(task.taskId)


@app.post("/runtime/tasks/{taskId}/cancel", response_model=CancelResponse)
def cancel_runtime_task(taskId: str) -> CancelResponse:
    cancelled = registry.cancel(taskId)
    logger.info("task=%s cancel requested -> cancelled=%s", taskId, cancelled)
    return CancelResponse(cancelled=cancelled)
