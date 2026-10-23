"""MockProvider login strategy: HOME -> LOGIN -> username -> password -> click -> done."""

from app.providers.mock import MockProvider, extract_credentials


def element(
    text="",
    element_id="",
    element_type="TextView",
    clickable=False,
    editable=False,
    value=None,
):
    data = {
        "id": element_id,
        "text": text,
        "type": element_type,
        "clickable": clickable,
        "editable": editable,
        "bounds": {"x": 0, "y": 0, "width": 100, "height": 40},
    }
    if value is not None:
        data["value"] = value
    return data


def tree(title, texts=None, elements=None):
    return {
        "width": 1080,
        "height": 1920,
        "screen": {"app": "com.testhub.demo", "title": title, "texts": texts or []},
        "elements": elements or [],
    }


HOME = tree(
    "HOME",
    ["首页", "欢迎使用 TestHub"],
    [
        element("登录", "btn_login", "Button", clickable=True),
        element("注册", "btn_register", "Button", clickable=True),
    ],
)

LOGIN_EMPTY = tree(
    "登录",
    ["用户名", "密码"],
    [
        element("用户名", "et_username", "EditText", editable=True),
        element("密码", "et_password", "EditText", editable=True),
        element("登录", "btn_login", "Button", clickable=True),
    ],
)

LOGIN_USER_FILLED = tree(
    "登录",
    ["密码"],
    [
        element("tester", "et_username", "EditText", editable=True),
        element("密码", "et_password", "EditText", editable=True),
        element("登录", "btn_login", "Button", clickable=True),
    ],
)

LOGIN_BOTH_FILLED = tree(
    "登录",
    ["用户名已填写", "密码已填写"],
    [
        element("tester", "et_username", "EditText", editable=True),
        element("••••••", "et_password", "EditText", editable=True),
        element("登录", "btn_login", "Button", clickable=True),
    ],
)

SUCCESS = tree(
    "首页",
    ["登录成功", "欢迎回来"],
    [element("登录成功", "tv_welcome", "TextView")],
)

BDD = {
    "given": "已打开 App 且处于未登录状态",
    "when": "输入用户名 tester 和密码 secret 并点击登录",
    "then": "进入首页并显示 登录成功",
}


def decide(provider, ui_tree, step_no, history=None):
    return provider.decide(
        system_prompt="",
        bdd=BDD,
        ui_tree=ui_tree,
        screenshot_png=b"\x89PNG\r\n\x1a\nfake",
        step_no=step_no,
        history=history or [],
    )


def test_login_strategy_sequence():
    provider = MockProvider()
    history = []

    first = decide(provider, HOME, 1)
    assert first.done is False
    assert first.action == {"type": "click", "target": {"text": "登录"}}
    assert "首页" in first.analysis
    assert first.raw == first.analysis

    second = decide(provider, LOGIN_EMPTY, 2, history)
    assert second.action == {
        "type": "input",
        "target": {"text": "用户名"},
        "text": "tester",
    }
    assert "用户名" in second.analysis
    history.append({"seq": 2, "action": second.action, "status": "OK"})

    third = decide(provider, LOGIN_USER_FILLED, 3, history)
    assert third.action == {
        "type": "input",
        "target": {"text": "密码"},
        "text": "secret",
    }
    assert "密码" in third.analysis
    history.append({"seq": 3, "action": third.action, "status": "OK"})

    fourth = decide(provider, LOGIN_BOTH_FILLED, 4, history)
    assert fourth.action == {"type": "click", "target": {"text": "登录"}}
    assert "提交" in fourth.analysis

    fifth = decide(provider, SUCCESS, 5, history)
    assert fifth.done is True
    assert fifth.action == {"type": "done"}
    assert fifth.summary == "已完成：" + BDD["then"]


def test_home_without_login_button_falls_back_to_first_clickable():
    provider = MockProvider()
    page = tree("HOME", ["首页"], [element("设置", "btn_settings", "Button", clickable=True)])
    decision = decide(provider, page, 1)
    assert decision.action == {"type": "click", "target": {"text": "设置"}}


def test_empty_page_returns_done_with_fallback_summary():
    provider = MockProvider()
    decision = decide(provider, tree("未知页", ["加载中"], []), 1)
    assert decision.done is True
    assert decision.summary == "未找到可执行动作"
    assert decision.action == {"type": "done"}


def test_login_success_takes_priority_over_clickable_elements():
    provider = MockProvider()
    decision = decide(provider, SUCCESS, 1)
    assert decision.done is True
    assert decision.summary.startswith("已完成：")


def test_credentials_fall_back_to_contract_defaults():
    assert extract_credentials("") == {"username": "tester", "password": "secret"}
    assert extract_credentials("输入用户名和密码，然后点击登录") == {
        "username": "tester",
        "password": "secret",
    }
    extracted = extract_credentials("输入用户名 alice 和密码 s3cret 并点击登录")
    assert extracted == {"username": "alice", "password": "s3cret"}


def test_input_already_typed_in_tree_is_not_repeated():
    """Even if the UI tree exposes the typed value, the provider clicks login."""
    provider = MockProvider()
    page = tree(
        "登录",
        [],
        [
            element("alice", "et_username", "EditText", editable=True),
            element("s3cret", "et_password", "EditText", editable=True),
            element("登录", "btn_login", "Button", clickable=True),
        ],
    )
    decision = decide(provider, page, 1)
    assert decision.action == {"type": "click", "target": {"text": "登录"}}


# ---------------------------------------------------------------------------
# Compatibility with the real simulated device-agent (device-agent module)
# ---------------------------------------------------------------------------
def sim_element(text, element_id, element_type, clickable, editable):
    return element(
        text=text,
        element_id=element_id,
        element_type=element_type,
        clickable=clickable,
        editable=editable,
    )


SIM_HOME = tree(
    "TestHub Demo",
    ["欢迎使用 TestHub 演示应用"],
    [
        sim_element("登录", "btn-login", "button", True, False),
        sim_element("搜索", "btn-search", "button", True, False),
    ],
)

SIM_LOGIN = tree(
    "登录",
    [],
    [
        sim_element("用户名", "input-username", "editable", True, True),
        sim_element("密码", "input-password", "editable", True, True),
        sim_element("登录", "btn-login", "button", True, False),
        sim_element("返回", "btn-back", "button", True, False),
    ],
)

SIM_LOGGED_IN = tree(
    "首页",
    ["登录成功", "欢迎, tester"],
    [
        sim_element("搜索", "btn-search", "button", True, False),
        sim_element("退出登录", "btn-logout", "button", True, False),
    ],
)


def test_simulated_device_login_flow_without_value_in_ui_tree():
    """The simulated device never echoes input values; history must cover it."""
    provider = MockProvider()
    history = []

    step1 = decide(provider, SIM_HOME, 1)
    assert step1.action == {"type": "click", "target": {"text": "登录"}}
    history.append({"seq": 1, "action": step1.action, "status": "OK"})

    step2 = decide(provider, SIM_LOGIN, 2, history)
    assert step2.action == {
        "type": "input",
        "target": {"text": "用户名"},
        "text": "tester",
    }
    history.append({"seq": 2, "action": step2.action, "status": "OK"})

    step3 = decide(provider, SIM_LOGIN, 3, history)
    assert step3.action == {
        "type": "input",
        "target": {"text": "密码"},
        "text": "secret",
    }
    history.append({"seq": 3, "action": step3.action, "status": "OK"})

    step4 = decide(provider, SIM_LOGIN, 4, history)
    assert step4.action == {"type": "click", "target": {"text": "登录"}}

    step5 = decide(provider, SIM_LOGGED_IN, 5, history)
    assert step5.done is True
    assert step5.summary == "已完成：" + BDD["then"]


def test_elements_nested_under_screen_are_supported():
    """Contract §3 nests elements under ``screen``."""
    provider = MockProvider()
    nested = {
        "width": 720,
        "height": 1280,
        "screen": {
            "app": "com.testhub.demo",
            "title": "首页",
            "texts": ["登录成功"],
            "elements": [element("登录成功", "tv", "text", False, False)],
        },
    }
    decision = decide(provider, nested, 1)
    assert decision.done is True
