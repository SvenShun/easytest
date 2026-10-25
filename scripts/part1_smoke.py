#!/usr/bin/env python3
"""Part 1 acceptance smoke test: main service only (no execution services).

Covers: auth, user management, requirements, scripts, cases/review, plans/mount,
AI BDD drafts, model config, audit log and the built web console.
"""
import json
import pathlib
import time
import urllib.error
import urllib.request
import uuid

CORE = "http://127.0.0.1:8080"
WEB = "http://127.0.0.1:5173"
ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "examples" / "scripts" / "login-pass.zip"
PASSED, FAILED = [], []


def check(cond, label, detail=""):
    if cond:
        PASSED.append(label)
        print(f"  PASS  {label}")
    else:
        FAILED.append((label, detail))
        print(f"  FAIL  {label} {detail}")
        raise SystemExit(1)


def call(method, path, body=None, token=None, expect=(200, 201, 204), raw=False, headers=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    h = dict(headers or {})
    if body is not None:
        h["Content-Type"] = "application/json"
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(CORE + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            payload = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        payload = e.read()
        status = e.code
    if expect and status not in expect:
        raise AssertionError(f"{method} {path} -> {status}: {payload[:300]!r}")
    if raw:
        return status, payload
    return json.loads(payload) if payload else None


def upload_script(token, name):
    boundary = "----part1" + uuid.uuid4().hex
    body = bytearray()
    for key, value in {"name": name, "scriptType": "TESTNG", "entryCommand": "sh run.sh"}.items():
        body += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n{value}\r\n".encode()
    body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
             f"filename=\"{SCRIPT.name}\"\r\nContent-Type: application/zip\r\n\r\n").encode()
    body += SCRIPT.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(CORE + "/api/v1/scripts", data=bytes(body), method="POST",
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                                          "Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


def login(username, password):
    data = call("POST", "/api/v1/auth/login", {"username": username, "password": password}, expect=(200,))
    return data["token"], data["user"]


print("== Part 1 main-service acceptance ==")
print("\n[0] health & web")
status, body = call("GET", "/actuator/health", raw=True)
check(status == 200, "core health")
req = urllib.request.Request(WEB + "/")
with urllib.request.urlopen(req, timeout=10) as r:
    html = r.read().decode("utf-8", "replace")
check(r.status == 200 and "<div id=\"root\">" in html, "web console is served")

print("\n[1] auth & roles")
admin, admin_user = login("admin", "admin123")
tester, _ = login("tester", "test123")
business, _ = login("business", "biz123")
check(admin_user["role"] == "ADMIN", "admin role")
sb, _ = call("GET", "/api/v1/system/users", token=business, raw=True, expect=(403,))
check(sb == 403, "business cannot list users")

print("\n[2] user management")
suffix = time.strftime("%H%M%S")
new_user = call("POST", "/api/v1/system/users", {
    "username": f"part1_{suffix}", "password": "init-123", "displayName": "Part1 User",
    "role": "TESTER", "enabled": True}, token=admin)
uid = new_user["id"]
check(new_user["username"] == f"part1_{suffix}" and new_user["enabled"], "create user")
updated = call("PUT", f"/api/v1/system/users/{uid}", {
    "displayName": "Part1 Business", "role": "BUSINESS", "enabled": True}, token=admin)
check(updated["role"] == "BUSINESS", "update user role")
call("POST", f"/api/v1/system/users/{uid}/reset-password", {"password": "reset-456"}, token=admin)
login(f"part1_{suffix}", "reset-456")
check(True, "reset password and login")
disabled = call("PUT", f"/api/v1/system/users/{uid}", {"enabled": False}, token=admin)
check(disabled["enabled"] is False, "disable user")
sbad, _ = call("POST", "/api/v1/auth/login",
               {"username": f"part1_{suffix}", "password": "reset-456"}, raw=True, expect=(401,))
check(sbad == 401, "disabled user cannot login")
call("PUT", f"/api/v1/system/users/{uid}", {"enabled": True}, token=admin)
sbad2, _ = call("PUT", f"/api/v1/system/users/{admin_user['id']}", {"enabled": False},
                token=admin, raw=True, expect=(409,))
check(sbad2 == 409, "last admin cannot be disabled")

print("\n[3] requirements")
requirement = call("POST", "/api/v1/requirements", {
    "title": f"Part1 需求 {suffix}",
    "description": "用户可以使用用户名 tester 和密码 secret 登录，成功后显示 登录成功"}, token=tester)
updated_req = call("PUT", f"/api/v1/requirements/{requirement['id']}", {
    "title": requirement["title"] + "（更新）", "description": requirement["description"]}, token=tester)
check("更新" in updated_req["title"], "update requirement")

print("\n[4] scripts & cases")
script = upload_script(tester, f"part1-script-{suffix}")
check(script.get("id") and script.get("scriptType") == "TESTNG", "upload script package")
case = call("POST", "/api/v1/cases", {
    "title": f"Part1 用例 {suffix}", "given": "已打开 App 且未登录",
    "when": "输入用户名 tester 和密码 secret 并点击登录", "then": "显示 登录成功",
    "requirementId": requirement["id"], "scriptId": script["id"], "source": "MANUAL"}, token=tester)
detail = call("GET", f"/api/v1/cases/{case['id']}", token=tester)
check(sorted(detail["capability"]["availableExecutors"]) == ["AI_RUNTIME", "SCRIPT"],
      "case capability computed", str(detail["capability"]))
call("POST", f"/api/v1/cases/{case['id']}/review", {"action": "SUBMIT", "comment": "part1"}, token=tester)
approved = call("POST", f"/api/v1/cases/{case['id']}/review", {"action": "APPROVE", "comment": "part1"}, token=tester)
check(approved["reviewStatus"] == "APPROVED", "case review flow")
listed = call("GET", f"/api/v1/cases?reviewStatus=APPROVED&keyword=Part1&size=20", token=tester)
check(any(item["id"] == case["id"] for item in listed["items"]), "case list filter")

print("\n[5] test plan (management only, no trigger)")
plan = call("POST", "/api/v1/plans", {"name": f"Part1 计划 {suffix}", "description": "part1"}, token=tester)
mounted = call("POST", f"/api/v1/plans/{plan['id']}/cases", {"caseIds": [case["id"]]}, token=tester)
check(len(mounted) == 1, "mount case")
plan_detail = call("GET", f"/api/v1/plans/{plan['id']}", token=tester)
check(len(plan_detail["cases"]) == 1, "plan detail")
runs = call("GET", f"/api/v1/plans/{plan['id']}/runs", token=tester)
check(runs["total"] == 0, "plan has no execution runs in Part 1")

print("\n[6] AI analysis & settings")
draft = call("POST", "/api/v1/ai/bdd-drafts", {
    "requirementId": requirement["id"], "requirementText": requirement["description"], "sampleCount": 2},
    token=tester)
check(draft.get("drafts"), "AI draft generated")
configs = call("GET", "/api/v1/system/model-configs", token=admin)
check(len(configs["items"]) == 2, "model configs")
call("PUT", "/api/v1/system/model-configs/text", {
    "provider": "mock", "endpoint": "", "apiKey": "", "modelId": "mock-text"}, token=admin)
audit = call("GET", "/api/v1/system/audit-logs?page=1&size=50", token=admin)
actions = {item["action"] for item in audit["items"]}
check("USER_CREATE" in actions and "CASE_REVIEW" in actions, "audit log records admin actions")

print("\n" + "=" * 60)
print(f"PART 1 RESULT: {len(PASSED)} checks passed, 0 failed")
print("=" * 60)
report = ROOT / "data" / "part1-report.json"
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({"passed": PASSED, "failed": FAILED, "user": new_user["username"],
                              "caseId": case["id"], "planId": plan["id"]},
                             ensure_ascii=False, indent=2), encoding="utf-8")
print("report:", report)
