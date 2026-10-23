"""Deterministic offline provider used for demos and tests.

Strategy (contract section 4 / task spec):

1. If the page shows ``登录成功`` -> ``done``.
2. On a login page -> fill username (when empty), then password, then click 登录.
3. On HOME -> click 登录.
4. Otherwise -> click the first clickable element, or ``done`` with
   ``未找到可执行动作`` when nothing is clickable.

The provider is a pure function of (UI tree, BDD, history), so it can be
unit tested without any network or emulator.
"""

from __future__ import annotations

import re
from typing import Any, Dict, Iterable, List, Optional

from .base import Decision, VisionProvider

_SUCCESS_TEXTS = ("登录成功",)
_USERNAME_HINTS = ("用户名", "账号", "账户", "username", "user name", "user_name", "et_user")
_PASSWORD_HINTS = ("密码", "password", "passwd", "pwd", "et_pwd")
_LOGIN_KEYWORDS = ("登录", "login", "sign in", "signin")
_HOME_TITLES = {"home", "homepage", "main", "主界面", "首页", "主页"}

_USERNAME_RE = re.compile(
    r"(?:用户名|账号|账户|username|user)\s*(?:为|是|[:：=])?\s*([^\s，,。；;]+)",
    re.IGNORECASE,
)
_PASSWORD_RE = re.compile(
    r"(?:密码|password|passwd|pwd)\s*(?:为|是|[:：=])?\s*([^\s，,。；;]+)",
    re.IGNORECASE,
)


# ---------------------------------------------------------------------------
# UI tree helpers
# ---------------------------------------------------------------------------
def _text(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def _elements(ui_tree: Dict[str, Any]) -> List[Dict[str, Any]]:
    raw = (ui_tree or {}).get("elements")
    if not raw:
        # Contract §3 nests elements under ``screen``; the device-agent also
        # exposes a top-level alias.  Support both shapes.
        screen = (ui_tree or {}).get("screen")
        if isinstance(screen, dict):
            raw = screen.get("elements")
    return [item for item in (raw or []) if isinstance(item, dict)]


def _screen(ui_tree: Dict[str, Any]) -> Dict[str, Any]:
    raw = (ui_tree or {}).get("screen")
    return raw if isinstance(raw, dict) else {}


def _identity(element: Dict[str, Any]) -> str:
    return " ".join(
        [_text(element.get("id")), _text(element.get("text")), _text(element.get("type"))]
    ).lower()


def _is_clickable(element: Dict[str, Any]) -> bool:
    if bool(element.get("clickable")):
        return True
    return _text(element.get("type")).lower() in {"button", "imagebutton", "clickable"}


def _is_editable(element: Dict[str, Any]) -> bool:
    if bool(element.get("editable")):
        return True
    return _text(element.get("type")).lower() in {"edittext", "textfield", "input"}


def _looks_username(element: Dict[str, Any]) -> bool:
    identity = _identity(element)
    return any(hint in identity for hint in _USERNAME_HINTS)


def _looks_password(element: Dict[str, Any]) -> bool:
    identity = _identity(element)
    return any(hint in identity for hint in _PASSWORD_HINTS)


def _target(element: Dict[str, Any]) -> Dict[str, str]:
    text = _text(element.get("text")).strip()
    if text:
        return {"text": text}
    element_id = _text(element.get("id")).strip()
    if element_id:
        return {"id": element_id}
    return {"text": ""}


def _target_label(element: Dict[str, Any]) -> str:
    target = _target(element)
    return target.get("text") or target.get("id") or "?"


def _has_value(element: Dict[str, Any]) -> bool:
    """Heuristic: does an editable element already contain user input?"""
    value = element.get("value")
    if value is not None and _text(value).strip():
        return True
    text = _text(element.get("text")).strip()
    if not text:
        return False
    lowered = text.lower()
    placeholder_hints = (
        "用户名",
        "账号",
        "密码",
        "请输入",
        "input",
        "username",
        "password",
        "手机号",
    )
    return not any(hint in lowered for hint in placeholder_hints)


def _history_filled(history: List[Dict[str, Any]], element: Dict[str, Any]) -> bool:
    """Fallback when the UI tree does not expose the typed value."""
    target = _target(element)
    for item in history or []:
        if not isinstance(item, dict):
            continue
        action = item.get("action")
        if not isinstance(action, dict) or action.get("type") != "input":
            continue
        if action.get("target") == target and _text(action.get("text")).strip():
            return True
    return False


def _field_filled(element: Dict[str, Any], history: List[Dict[str, Any]]) -> bool:
    return _has_value(element) or _history_filled(history, element)


def _find_username(editable: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    for element in editable:
        if _looks_username(element):
            return element
    if editable and not _looks_password(editable[0]):
        return editable[0]
    return None


def _find_password(editable: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    for element in editable:
        if _looks_password(element):
            return element
    if len(editable) >= 2 and not _looks_username(editable[1]):
        return editable[1]
    return None


def _find_clickable(
    elements: Iterable[Dict[str, Any]], keywords: Iterable[str]
) -> Optional[Dict[str, Any]]:
    candidates = [element for element in elements if _is_clickable(element)]
    keyword_list = [keyword.lower() for keyword in keywords]
    for keyword in keyword_list:  # exact match first
        for element in candidates:
            if _target_label(element).strip().lower() == keyword:
                return element
    for keyword in keyword_list:  # then substring match
        for element in candidates:
            if keyword in _target_label(element).strip().lower():
                return element
    return None


def _clean_credential(value: Optional[str], other_hint: str) -> Optional[str]:
    if not value:
        return None
    value = value.strip().strip("\"'“”‘’")
    if not value:
        return None
    lowered = value.lower()
    if other_hint in lowered or "点击" in value or value in {"和", "and", "然后"}:
        return None
    return value


def extract_credentials(when: str) -> Dict[str, str]:
    """Best-effort credential extraction from the BDD ``when`` text.

    Falls back to the contract example values (``tester`` / ``secret``).
    """
    when = _text(when)
    username_match = _USERNAME_RE.search(when)
    password_match = _PASSWORD_RE.search(when)
    username = _clean_credential(
        username_match.group(1) if username_match else None, other_hint="密码"
    )
    password = _clean_credential(
        password_match.group(1) if password_match else None, other_hint="用户名"
    )
    return {"username": username or "tester", "password": password or "secret"}


# ---------------------------------------------------------------------------
# Provider
# ---------------------------------------------------------------------------
class MockProvider(VisionProvider):
    """Rule based, fully offline vision provider."""

    name = "mock"

    def decide(
        self,
        system_prompt: str,
        bdd: Dict[str, Any],
        ui_tree: Dict[str, Any],
        screenshot_png: bytes,
        step_no: int,
        history: List[Dict[str, Any]],
    ) -> Decision:
        if hasattr(bdd, "model_dump"):
            bdd = bdd.model_dump()
        bdd = bdd if isinstance(bdd, dict) else {}
        ui_tree = ui_tree if isinstance(ui_tree, dict) else {}
        history = history or []

        elements = _elements(ui_tree)
        screen = _screen(ui_tree)
        title = _text(screen.get("title"))
        texts = [_text(item) for item in (screen.get("texts") or []) if item is not None]
        element_texts = [_text(item.get("text")) for item in elements if item.get("text")]
        all_texts = texts + element_texts
        then = _text(bdd.get("then"))
        when = _text(bdd.get("when"))

        # Rule 1: goal reached.
        if any(_SUCCESS_TEXTS[0] in text for text in all_texts):
            analysis = "页面出现“登录成功”，用例目标已达成，结束执行。"
            return Decision(
                action={"type": "done"},
                analysis=analysis,
                done=True,
                summary="已完成：" + then,
                raw=analysis,
            )

        credentials = extract_credentials(when)
        editable = [element for element in elements if _is_editable(element)]
        username_element = _find_username(editable)
        password_element = _find_password(editable)
        has_username_hint = any(
            any(hint in text.lower() for hint in ("用户名", "账号", "username"))
            for text in all_texts
        )

        # Rule 2: login page.
        is_login_page = (
            "登录" in title
            or title.strip().lower() in {"login", "sign in", "signin"}
            or has_username_hint
            or username_element is not None
        )
        if is_login_page:
            if username_element is not None and not _field_filled(
                username_element, history
            ):
                analysis = "检测到登录页，用户名输入框为空，先输入用户名 %s。" % credentials[
                    "username"
                ]
                return Decision(
                    action={
                        "type": "input",
                        "target": _target(username_element),
                        "text": credentials["username"],
                    },
                    analysis=analysis,
                    raw=analysis,
                )
            if password_element is not None and not _field_filled(
                password_element, history
            ):
                analysis = "用户名已填写，继续输入密码。"
                return Decision(
                    action={
                        "type": "input",
                        "target": _target(password_element),
                        "text": credentials["password"],
                    },
                    analysis=analysis,
                    raw=analysis,
                )
            login_button = _find_clickable(elements, _LOGIN_KEYWORDS)
            if login_button is not None:
                analysis = "用户名和密码已填写，点击“%s”提交登录。" % _target_label(
                    login_button
                )
                return Decision(
                    action={"type": "click", "target": _target(login_button)},
                    analysis=analysis,
                    raw=analysis,
                )

        # Rule 3: HOME without success -> open login.
        is_home = (
            title.strip().lower() in _HOME_TITLES
            or any("首页" in text or text.strip().lower() == "home" for text in texts)
        )
        if is_home:
            login_button = _find_clickable(elements, _LOGIN_KEYWORDS)
            if login_button is not None:
                analysis = "当前在首页且未检测到“登录成功”，点击“%s”进入登录页。" % (
                    _target_label(login_button)
                )
                return Decision(
                    action={"type": "click", "target": _target(login_button)},
                    analysis=analysis,
                    raw=analysis,
                )

        # Rule 4: generic fallback.
        clickable = [element for element in elements if _is_clickable(element)]
        if clickable:
            element = clickable[0]
            analysis = "未匹配到已知登录流程，点击第一个可点击元素“%s”推进页面。" % (
                _target_label(element)
            )
            return Decision(
                action={"type": "click", "target": _target(element)},
                analysis=analysis,
                raw=analysis,
            )

        analysis = "当前页面没有可点击元素，也没有可识别的登录入口，结束执行。"
        return Decision(
            action={"type": "done"},
            analysis=analysis,
            done=True,
            summary="未找到可执行动作",
            raw=analysis,
        )
