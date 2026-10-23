"""Thread-safe in-flight task / cancellation registry.

A task is registered when ``POST /runtime/tasks`` starts executing and is
removed when it reaches a terminal state.  ``POST /runtime/tasks/{id}/cancel``
only succeeds for *in-flight* tasks; unknown or already finished task ids
return ``{"cancelled": false}`` (HTTP 200), per contract.
"""

from __future__ import annotations

import threading
from typing import Dict, List, Optional


class TaskAlreadyRunning(Exception):
    """Raised when the same taskId is submitted while it is in flight."""

    def __init__(self, task_id: str) -> None:
        super().__init__(task_id)
        self.task_id = task_id


class CancelToken:
    """A one-way cancellation flag shared with the engine loop."""

    def __init__(self, task_id: str) -> None:
        self.task_id = task_id
        self._event = threading.Event()

    def cancel(self) -> None:
        self._event.set()

    def is_cancelled(self) -> bool:
        return self._event.is_set()

    @property
    def cancelled(self) -> bool:
        return self._event.is_set()

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return "CancelToken(task_id=%r, cancelled=%s)" % (self.task_id, self.cancelled)


class CancelRegistry:
    """Thread-safe map of ``taskId -> CancelToken`` for in-flight tasks."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._tokens: Dict[str, CancelToken] = {}
        self._finished: Dict[str, str] = {}

    def register(self, task_id: str) -> CancelToken:
        """Register a task, raising ``TaskAlreadyRunning`` on concurrent submit."""
        with self._lock:
            if task_id in self._tokens:
                raise TaskAlreadyRunning(task_id)
            token = CancelToken(task_id)
            self._tokens[task_id] = token
            self._finished.pop(task_id, None)
            return token

    def unregister(self, task_id: str, status: Optional[str] = None) -> None:
        """Remove an in-flight task (terminal state reached)."""
        with self._lock:
            self._tokens.pop(task_id, None)
            if status is not None:
                self._finished[task_id] = status

    def cancel(self, task_id: str) -> bool:
        """Set the cancellation flag.  Returns False if the task is not in flight."""
        with self._lock:
            token = self._tokens.get(task_id)
        if token is None:
            return False
        token.cancel()
        return True

    def get(self, task_id: str) -> Optional[CancelToken]:
        with self._lock:
            return self._tokens.get(task_id)

    def is_cancelled(self, task_id: str) -> bool:
        token = self.get(task_id)
        return bool(token and token.is_cancelled())

    def active_task_ids(self) -> List[str]:
        with self._lock:
            return list(self._tokens.keys())

    def finished_task_ids(self) -> List[str]:
        with self._lock:
            return list(self._finished.keys())

    def __contains__(self, task_id: str) -> bool:
        with self._lock:
            return task_id in self._tokens

    def __len__(self) -> int:
        with self._lock:
            return len(self._tokens)
