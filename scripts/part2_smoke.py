#!/usr/bin/env python3
"""Part 2 acceptance: API test cases + independent api-execution service."""
import json
import pathlib
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CORE = "http://127.0.0.1:8080"
EXEC = "http://127.0.0.1:8084"
ROOT = pathlib.Path(__file__).resolve().parent.parent
PASSED, FAILED = [], []
STUB_PORT = 18099


def check(cond, label, detail=""):
    if cond:
        PASSED.append(label)
        print(f"  PASS  {label}")
    else:
        FAILED.append((label, detail))
        print(f"  FAIL  {label} {detail}")
        raise SystemExit(1)


def call(base, method, path, body=None, token=None, expect=(200, 201, 204), service_token=False):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    headers = {}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if service_token:
        headers["X-Service-Token"] = "testhub-dev-service-token"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            payload = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        payload = e.read()
        status = e.code
    if expect and status not in expect:
        raise AssertionError(f"{method} {path} -> {status}: {payload[:400]!r}")
    return json.loads(payload) if payload else None


class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, status, body):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path.startswith("/health"):
            self._send(200, {"status": "UP", "code": 0})
        elif self.path.startswith("/slow"):
            time.sleep(6)
            self._send(200, {"status": "SLOW", "code": 0})
        elif self.path.startswith("/fail"):
            self._send(500, {"code": 500, "message": "boom"})
        else:
            self._send(404, {"message": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8", "replace") if length else ""
        if self.path.startswith("/login"):
            if "tester" in body and "secret" in body:
                self._send(200, {"code": 0, "message": "success", "token": "stub-token"})
            else:
                self._send(400, {"code": 400, "message": "bad credentials"})
        else:
            self._send(404, {"message": "not found"})


def login(user, password):
    return call(CORE, "POST", "/api/v1/auth/login", {"username": user, "password": password})["token"]


def wait_run(token, run_id, timeout=60):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = call(CORE, "GET", f"/api/v1/plan-runs/{run_id}", token=token)
        if all(i["status"] in ("SUCCESS", "FAILED", "TIMEOUT", "CANCELLED", "SKIPPED") for i in last["items"]):
            return last
        time.sleep(1)
    raise AssertionError(f"run not finished: {last}")


def create_plan_with_case(token, name, case_id):
    plan = call(CORE, "POST", "/api/v1/plans", {"name": name, "description": "part2"}, token=token)
    call(CORE, "POST", f"/api/v1/plans/{plan['id']}/cases", {"apiCaseIds": [case_id]}, token=token)
    return plan["id"]


def create_api_case(token, name, path, assertions, timeout=30):
    return call(CORE, "POST", "/api/v1/api-cases", {
        "name": name, "method": "GET", "baseUrl": f"http://127.0.0.1:{STUB_PORT}", "path": path,
        "headers": {"Accept": "application/json"}, "query": {}, "body": None,
        "assertions": assertions, "timeoutSec": timeout, "enabled": True}, token=token)["id"]


print("== Part 2 API execution acceptance ==")
server = ThreadingHTTPServer(("127.0.0.1", STUB_PORT), Stub)
threading.Thread(target=server.serve_forever, daemon=True).start()
call(CORE, "GET", "/actuator/health")
exec_health = call(EXEC, "GET", "/actuator/health")
check(exec_health.get("status") == "UP", "api-execution health")
token = login("tester", "test123")

print("\n[1] API case CRUD")
case = call(CORE, "GET", f"/api/v1/api-cases?keyword=Part2&size=20", token=token)
check("items" in case, "list API cases")
health_case = create_api_case(token, "Part2 健康检查", "/health", [
    {"type": "STATUS_CODE", "operator": "EQ", "expected": "200"},
    {"type": "JSON_PATH", "path": "$.status", "operator": "EQ", "expected": "UP"}])
listed = call(CORE, "GET", "/api/v1/api-cases?keyword=Part2&size=20", token=token)
check(any(i["id"] == health_case for i in listed["items"]), "created API case is listed")

print("\n[2] API-only plan execution")
plan_id = create_plan_with_case(token, "Part2 健康检查计划", health_case)
trigger = call(CORE, "POST", f"/api/v1/plans/{plan_id}/trigger",
               {"timeoutSec": 60, "stepTimeoutSec": 30}, token=token)
run_id = trigger["runId"]
run = wait_run(token, run_id)
item = run["items"][0]
check(item["status"] == "SUCCESS", "plan item SUCCESS", str(item))
check(item["executorType"] == "API", "plan item executor API")
task = call(EXEC, "GET", f"/api/v1/api-execution/tasks/{item['taskId']}", token=token)
check(task["status"] == "SUCCESS", "api-execution task SUCCESS")
check(task.get("httpStatus") == 200, "HTTP 200 captured")
assertions = task.get("assertions") or task.get("assertionResults") or []
check(assertions and all(a["passed"] for a in assertions), "assertions passed")
report = call(EXEC, "GET", f"/api/v1/api-execution/tasks/{item['taskId']}/report", token=token)
check(report.get("status") == "SUCCESS", "report available")

print("\n[3] assertion failure path")
fail_case = create_api_case(token, "Part2 失败断言", "/fail", [
    {"type": "STATUS_CODE", "operator": "EQ", "expected": "500"},
    {"type": "JSON_PATH", "path": "$.code", "operator": "EQ", "expected": "0"}])
fail_plan = create_plan_with_case(token, "Part2 断言失败计划", fail_case)
fail_run = wait_run(token, call(CORE, "POST", f"/api/v1/plans/{fail_plan}/trigger",
                               {"timeoutSec": 60}, token=token)["runId"])
fail_item = fail_run["items"][0]
check(fail_item["status"] == "FAILED", "failed assertion -> FAILED", str(fail_item))
check(fail_item["failureReason"] == "ASSERTION_FAILED", "failure reason ASSERTION_FAILED")
check(bool(fail_item.get("failureReportUrl")), "failure report URL")
fail_report = urllib.request.urlopen(urllib.request.Request(
    fail_item["failureReportUrl"], headers={"Authorization": "Bearer " + token}), timeout=10)
check(fail_report.status == 200, "failure report reachable")

print("\n[4] timeout path")
slow_case = create_api_case(token, "Part2 超时", "/slow", [
    {"type": "STATUS_CODE", "operator": "EQ", "expected": "200"}], timeout=1)
slow_plan = create_plan_with_case(token, "Part2 超时计划", slow_case)
slow_run = wait_run(token, call(CORE, "POST", f"/api/v1/plans/{slow_plan}/trigger",
                               {"timeoutSec": 5}, token=token)["runId"], timeout=30)
slow_item = slow_run["items"][0]
check(slow_item["status"] == "TIMEOUT", "slow API -> TIMEOUT", str(slow_item))
check(slow_item["failureReason"] == "TIMEOUT", "timeout reason")

print("\n[5] mixed UI + API plan (optional, requires ui-execution)")
try:
    ui_health = call(EXEC.replace("8084", "8081"), "GET", "/actuator/health")
    ui_up = ui_health.get("status") == "UP"
except Exception:
    ui_up = False
if not ui_up:
    print("  SKIP  ui-execution is not running")
else:
    ui_case = call(CORE, "POST", "/api/v1/cases", {
        "title": "Part2 混合 UI 用例", "given": "已打开 App 且未登录",
        "when": "输入用户名 tester 和密码 secret 并点击登录", "then": "显示 登录成功",
        "source": "MANUAL"}, token=token)["id"]
    call(CORE, "POST", f"/api/v1/cases/{ui_case}/review", {"action": "SUBMIT", "comment": "part2"}, token=token)
    call(CORE, "POST", f"/api/v1/cases/{ui_case}/review", {"action": "APPROVE", "comment": "part2"}, token=token)
    mixed_plan = call(CORE, "POST", "/api/v1/plans", {"name": "Part2 混合计划", "description": "ui+api"}, token=token)["id"]
    call(CORE, "POST", f"/api/v1/plans/{mixed_plan}/cases",
         {"caseIds": [ui_case], "apiCaseIds": [health_case]}, token=token)
    mixed_run = wait_run(token, call(CORE, "POST", f"/api/v1/plans/{mixed_plan}/trigger",
                                     {"timeoutSec": 120, "stepTimeoutSec": 30}, token=token)["runId"], timeout=120)
    check(all(i["status"] == "SUCCESS" for i in mixed_run["items"]), "mixed plan all SUCCESS",
          str([(i["executorType"], i["status"]) for i in mixed_run["items"]]))
    kinds = {i["executorType"] for i in mixed_run["items"]}
    check(kinds == {"AI_RUNTIME", "API"}, "mixed plan routed to both executors", str(kinds))
    mixed_report = call(CORE, "GET", f"/api/v1/plan-runs/{mixed_run['run']['id']}/report", token=token)
    check({e["executorType"] for e in mixed_report["executors"]} == {"AI_RUNTIME", "API"},
          "report aggregates both executors")

server.shutdown()
print("\n" + "=" * 60)
print(f"PART 2 RESULT: {len(PASSED)} checks passed, 0 failed")
print("=" * 60)
report_path = ROOT / "data" / "part2-report.json"
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps({"passed": PASSED, "failed": FAILED, "runId": run_id, "mixedRunId": (mixed_run["run"]["id"] if ui_up else None),
                                   "planId": plan_id, "caseId": health_case},
                                  ensure_ascii=False, indent=2), encoding="utf-8")
print("report:", report_path)
