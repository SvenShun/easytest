"""Pydantic (v2) models for the AI Runtime HTTP contract.

The wire format is defined by ``docs/API-CONTRACT.md`` section 4 (and the
agent dispatch payload in section 2.6).  Field names are kept exactly as the
contract spells them (camelCase) because they are the public protocol.

Python 3.9 compatible: ``Optional[...]`` / ``List[...]`` are used instead of
PEP 604 unions.
"""

from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

TaskStatus = Literal["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"]


class _WireModel(BaseModel):
    """Base model: tolerate additive fields from newer callers."""

    model_config = ConfigDict(extra="ignore")


class BDD(_WireModel):
    given: str = ""
    when: str = ""
    then: str = ""


class SkillsRef(_WireModel):
    """Pointer to the Skills checkout prepared by the device-agent.

    ``dir`` is the only field the runtime actually reads (V1); ``repo`` and
    ``commit`` are carried for traceability / future cache management.
    """

    repo: Optional[str] = None
    commit: Optional[str] = None
    dir: Optional[str] = None


class Timeouts(_WireModel):
    totalSec: float = Field(default=120.0, gt=0)
    stepSec: float = Field(default=30.0, gt=0)


class ModelSpec(_WireModel):
    provider: str = "mock"
    endpoint: str = ""
    apiKey: str = ""
    modelId: str = "mock-vision"


class DeviceSpec(_WireModel):
    baseUrl: str = Field(min_length=1)
    serial: str = Field(min_length=1)


class CallbackSpec(_WireModel):
    stepUrl: Optional[str] = None
    logUrl: Optional[str] = None
    token: Optional[str] = None


class RuntimeTaskRequest(_WireModel):
    taskId: str = Field(min_length=1)
    bdd: BDD
    skills: Optional[SkillsRef] = None
    timeouts: Timeouts = Field(default_factory=Timeouts)
    maxSteps: int = Field(default=20, ge=1, le=200)
    model: ModelSpec = Field(default_factory=ModelSpec)
    device: DeviceSpec
    callbacks: CallbackSpec = Field(default_factory=CallbackSpec)


class RuntimeTaskResult(_WireModel):
    taskId: str
    status: TaskStatus
    reasonCategory: Optional[str] = None
    summary: str = ""
    totalSteps: int = 0
    elapsedMs: int = 0


class CancelResponse(_WireModel):
    cancelled: bool


class HealthResponse(_WireModel):
    status: str = "UP"
    version: str
    providers: List[str]
    skillsCacheDir: str


# Convenience aliases used by tests / callers that want the raw dict shape.
JsonDict = Dict[str, Any]
