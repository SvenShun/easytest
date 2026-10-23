"""Skills injection (contract section 4 / V1 scope).

The device-agent resolves the Skills checkout and passes its absolute path as
``skills.dir``.  The runtime only reads a text overview from that directory:

* prefer ``README.md`` (the "latter" file in the contract wording), then
  ``SKILLS.md``;
* truncate to 8000 characters;
* missing / empty directory is not an error (INFO log, no injection);
* read errors only produce a warning.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from .models import SkillsRef

SKILLS_MAX_CHARS = 8000
SKILLS_FILE_ORDER = ("README.md", "SKILLS.md")

logger = logging.getLogger("ai_runtime.skills")


def load_skills_text(
    skills: Optional[SkillsRef], log: Optional[logging.Logger] = None
) -> str:
    """Return the truncated Skills text, or ``""`` when nothing is available."""
    active_logger = log or logger
    if skills is None:
        return ""
    directory = (skills.dir or "").strip()
    if not directory:
        return ""

    base = Path(directory).expanduser()
    if not base.exists():
        active_logger.info("skills dir not found, skipping injection: %s", base)
        return ""
    if not base.is_dir():
        active_logger.info("skills dir is not a directory, skipping injection: %s", base)
        return ""

    for name in SKILLS_FILE_ORDER:
        candidate = base / name
        if not candidate.is_file():
            continue
        try:
            text = candidate.read_text(encoding="utf-8", errors="replace")
        except (OSError, ValueError) as exc:
            active_logger.warning("failed to read skills file %s: %s", candidate, exc)
            return ""
        if not text.strip():
            active_logger.info("skills file %s is empty, skipping", candidate)
            continue
        truncated = text[:SKILLS_MAX_CHARS]
        active_logger.info(
            "skills loaded: file=%s chars=%d truncated=%s",
            candidate,
            len(truncated),
            len(text) > SKILLS_MAX_CHARS,
        )
        return truncated

    active_logger.info(
        "skills dir has no README.md/SKILLS.md, skipping injection: %s", base
    )
    return ""


def build_system_prompt(skills_text: str) -> str:
    """Prefix the Skills text with ``## Skills`` (empty string when absent)."""
    text = (skills_text or "").strip()
    if not text:
        return ""
    return "## Skills\n" + text
