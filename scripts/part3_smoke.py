#!/usr/bin/env python3
"""Part 3 acceptance: environments/auth presets, data-driven iterations, shared queue."""
import json
import os
import pathlib
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CORE = "http://127.0.0.1:8080"
EXEC = os.environ.get("TESTHUB_APIEXEC_URL", "http://127.0.0.1:8084")
EXEC2 = os.environ.get("TESTHUB_APIEXEC_SECOND_URL", "")
STUB_PORT = 18098
ROOT = pathlib.Path(__file__).resolve().parent.parent
PASSED, FAILED = [], []
LOGIN_COUNT = 0
SEEN = []


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
        raise AssertionError(f"{method} {path} -> {status}: {payload[:500]!r}")
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

    def _body(self):
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length).decode("utf-8", "replace") if length else ""

    def do_GET(self):
        if self.path.startswith("/secure"):
            expected = "Bearer tok-abc"
            if self.headers.get("Authorization") != expected:
                self._send(401, {"ok": False, "auth": self.headers.get("Authorization")})
            else:
                self._send(200, {"ok": True, "auth": self.headers.get("Authorization")})
        elif self.path.startswith("/slow"):
            time.sleep(1.2)
            self._send(200, {"ok": True})
        else:
            self._send(404, {"message": "not found"})

    def do_POST(self):
        global LOGIN_COUNT
        body = self._body()
        if self.path.startswith("/login"):
            LOGIN_COUNT += 1
            if "tester" in body and "secret" in body:
                self._send(200, {"data": {"token": "tok-abc"}})
            else:
                self._send(400, {"message": "bad credentials"})
        elif self.path.startswith("/echo"):
            from urllib.parse import parse_qs, urlparse
            parsed = urlparse(self.path)
            query = {k: v[0] for k, v in parse_qs(parsed.query).items()}
            try:
                payload = json.loads(body) if body else {}
            except Exception:
                payload = {"raw": body}
            self._send(200, {
                "id": query.get("id"),
                "name": query.get("name"),
                "body": payload,
                "header": self.headers.get("X-Row"),
            })
        else:
            self._send(404, {"message": "not found"})


def login():
    return call(CORE, "POST", "/api/v1/auth/login",
                {"username": "tester", "password": "test123"})["token"]


def wait_run(token, run_id, timeout=90):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = call(CORE, "GET", f"/api/v1/plan-runs/{run_id}", token=token)
        if all(i["status"] in ("SUCCESS", "FAILED", "TIMEOUT", "CANCELLED", "SKIPPED")
               for i in last["items"]):
            return last
        time.sleep(1)
    raise AssertionError(f"run not finished: {last}")


def trigger(token, plan_id, timeout_sec=60):
    return call(CORE, "POST", f"/api/v1/plans/{plan_id}/trigger",
                {"timeoutSec": timeout_sec, "stepTimeoutSec": 30}, token=token)["runId"]


def create_plan(token, name, env_id=None):
    body = {"name": name, "description": "part3"}
    if env_id:
        body["environmentId"] = env_id
    return call(CORE, "POST", "/api/v1/plans", body, token=token)["id"]


def mount_api(token, plan_id, case_id):
    call(CORE, "POST", f"/api/v1/plans/{plan_id}/cases", {"apiCaseIds": [case_id]}, token=token)


def task_detail(token, task_id):
    return call(EXEC, "GET", f"/api/v1/api-execution/tasks/{task_id}", token=token)


print("== Part 3 API automation enhancements ==")
server = ThreadingHTTPServer(("127.0.0.1", STUB_PORT), Stub)
threading.Thread(target=server.serve_forever, daemon=True).start()
call(CORE, "GET", "/actuator/health")
token = login()

print("\n[1] environment with login auth")
env = call(CORE, "POST", "/api/v1/api-environments", {
    "name": "Part3 登录环境",
    "baseUrl": "${host}",
    "headers": {"X-Env": "part3-${user}"},
    "variables": {"host": f"http://127.0.0.1:{STUB_PORT}", "user": "tester", "pass": "secret"},
    "auth": {
        "type": "LOGIN", "loginMethod": "POST", "loginPath": "/login",
        "loginHeaders": {"Content-Type": "application/json"},
        "loginBody": '{"username":"${user}","password":"${pass}"}',
        "tokenPath": "$.data.token", "tokenHeader": "Authorization", "tokenPrefix": "Bearer ",
    },
    "enabled": True}, token=token)
check(env.get("auth", {}).get("type") == "LOGIN", "environment created")
secure_case = call(CORE, "POST", "/api/v1/api-cases", {
    "name": "Part3 鉴权接口", "method": "GET", "path": "/secure",
    "headers": {"X-Case": "secure"}, "query": {}, "assertions": [
        {"type": "STATUS_CODE", "operator": "EQ", "expected": "200"},
        {"type": "JSON_PATH", "path": "$.ok", "operator": "EQ", "expected": "true"}],
    "timeoutSec": 30, "enabled": True}, token=token)["id"]
plan = create_plan(token, "Part3 鉴权计划", env["id"])
mount_api(token, plan, secure_case)
login_before = LOGIN_COUNT
run = wait_run(token, trigger(token, plan))
item = run["items"][0]
check(item["status"] == "SUCCESS", "login environment executes API case", str(item))
check(LOGIN_COUNT - login_before == 1, "login called once per plan run", f"logins={LOGIN_COUNT - login_before}")
detail = task_detail(token, item["taskId"])
check(detail["request"]["headers"].get("Authorization") == "Bearer tok-abc",
      "token injected into request", str(detail["request"]["headers"]))
check(detail["request"]["headers"].get("X-Env") == "part3-tester", "environment variable header resolved")
check(detail["request"]["url"].startswith(f"http://127.0.0.1:{STUB_PORT}/secure"),
      "environment baseUrl variable resolved", detail["request"]["url"])

print("\n[2] data-driven iterations")
data_case = call(CORE, "POST", "/api/v1/api-cases", {
    "name": "Part3 数据驱动", "method": "POST", "path": "/echo",
    "headers": {"X-Row": "${id}"}, "query": {"id": "${id}", "name": "${name}"},
    "body": '{"user":"${name}"}',
    "assertions": [
        {"type": "STATUS_CODE", "operator": "EQ", "expected": "200"},
        {"type": "JSON_PATH", "path": "$.id", "operator": "EQ", "expected": "${id}"},
        {"type": "JSON_PATH", "path": "$.body.user", "operator": "EQ", "expected": "${name}"}],
    "dataset": [
        {"id": "1", "name": "alice"},
        {"id": "2", "name": "bob"},
        {"id": "3", "name": "carol"}],
    "timeoutSec": 30, "enabled": True}, token=token)["id"]
data_plan = create_plan(token, "Part3 数据驱动计划", env["id"])
mount_api(token, data_plan, data_case)
login_before = LOGIN_COUNT
data_run = wait_run(token, trigger(token, data_plan))
check(len(data_run["items"]) == 3, "one run item per dataset row", str(len(data_run["items"])))
check(all(i["status"] == "SUCCESS" for i in data_run["items"]), "all dataset rows pass",
      str([(i["caseTitle"], i["status"]) for i in data_run["items"]]))
check([i["iteration"] for i in data_run["items"]] == [1, 2, 3],
      "iteration numbers", str([i["iteration"] for i in data_run["items"]]))
check(data_run["items"][0]["caseTitle"].endswith("[1/3]"), "iteration title")
check(LOGIN_COUNT - login_before == 1, "login still once for all iterations")
for index, row in enumerate(data_run["items"]):
    detail = task_detail(token, row["taskId"])
    assert detail["request"]["url"].endswith(f"path=/echo&id={index + 1}&name={['alice','bob','carol'][index]}") or \
        (f"id={index + 1}" in detail["request"]["url"] and f"name={['alice','bob','carol'][index]}" in detail["request"]["url"]), detail["request"]["url"]
check(True, "dataset row values resolved into URL")
check(detail["request"]["headers"].get("X-Row") == "3", "dataset header resolved for last row")

print("\n[3] environment in-use protection")
try:
    call(CORE, "DELETE", f"/api/v1/api-environments/{env['id']}", token=token, expect=(409,))
    check(True, "environment referenced by plan cannot be deleted")
except AssertionError as e:
    check(False, "environment referenced by plan cannot be deleted", str(e))

print("\n[4] shared queue (optional)")
shared_run_id = None
worker_ids = []
if not EXEC2:
    print("  SKIP  TESTHUB_APIEXEC_SECOND_URL not set")
else:
    slow_case = call(CORE, "POST", "/api/v1/api-cases", {
        "name": "Part3 共享队列", "method": "GET", "path": "/slow",
        "headers": {}, "query": {}, "assertions": [
            {"type": "STATUS_CODE", "operator": "EQ", "expected": "200"}],
        "dataset": [{"i": str(i)} for i in range(1, 9)],
        "timeoutSec": 30, "enabled": True}, token=token)["id"]
    queue_plan = create_plan(token, "Part3 共享队列计划", env["id"])
    mount_api(token, queue_plan, slow_case)
    queue_run = wait_run(token, trigger(token, queue_plan), timeout=120)
    shared_run_id = queue_run["run"]["id"]
    check(all(i["status"] == "SUCCESS" for i in queue_run["items"]), "shared queue tasks all pass")
    workers = set()
    for item in queue_run["items"]:
        d = task_detail(token, item["taskId"])
        if d.get("workerId"):
            workers.add(d["workerId"].rsplit("-", 1)[0])
    for item in queue_run["items"]:
        d = task_detail(token, item["taskId"])
        if d.get("workerId"):
            worker_ids.append(d["workerId"])
    check(len(workers) >= 2, "tasks consumed by both instances", str(workers))

server.shutdown()
print("\n" + "=" * 60)
print(f"PART 3 RESULT: {len(PASSED)} checks passed, 0 failed")
print("=" * 60)
report_path = ROOT / "data" / "part3-report.json"
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps({"passed": PASSED, "failed": FAILED,
                                   "envId": env["id"], "authRunId": run["run"]["id"],
                                   "datasetRunId": data_run["run"]["id"],
                                   "sharedRunId": shared_run_id, "workers": sorted(set(worker_ids))},
                                  ensure_ascii=False, indent=2), encoding="utf-8")
print("report:", report_path)
