"""Provider factory + configuration validation."""

from __future__ import annotations

from typing import List

from ..models import ModelSpec
from .base import (
    PLATFORM_CONSTRAINTS,
    Decision,
    DecisionParseError,
    ProviderError,
    VisionProvider,
)
from .mock import MockProvider
from .openai import OpenAICompatibleProvider

SUPPORTED_PROVIDERS: List[str] = ["mock", "openai"]
# ``deepseek`` is an alias of the generic OpenAI-compatible provider; health
# still advertises only the two canonical provider names per contract.
PROVIDER_ALIASES = {"deepseek": "openai"}


def normalize_provider(provider: str) -> str:
    name = (provider or "mock").strip().lower()
    return PROVIDER_ALIASES.get(name, name)


def validate_model_config(model: ModelSpec) -> None:
    """Validate provider selection before a task is registered (HTTP 400)."""
    name = normalize_provider(model.provider)
    if name == "mock":
        return
    if name == "openai":
        if not (model.endpoint or "").strip():
            raise ValueError("provider=openai/deepseek 需要非空 endpoint")
        return
    raise ValueError("不支持的 provider: %s" % (model.provider,))


def build_provider(model: ModelSpec, step_timeout_sec: float = 30.0) -> VisionProvider:
    """Create the provider described by the dispatch payload."""
    name = normalize_provider(model.provider)
    if name == "mock":
        return MockProvider()
    if name == "openai":
        return OpenAICompatibleProvider(
            endpoint=model.endpoint,
            api_key=model.apiKey,
            model_id=model.modelId,
            step_timeout_sec=step_timeout_sec,
        )
    raise ValueError("不支持的 provider: %s" % (model.provider,))


__all__ = [
    "Decision",
    "DecisionParseError",
    "ProviderError",
    "VisionProvider",
    "MockProvider",
    "OpenAICompatibleProvider",
    "PLATFORM_CONSTRAINTS",
    "SUPPORTED_PROVIDERS",
    "build_provider",
    "normalize_provider",
    "validate_model_config",
]
