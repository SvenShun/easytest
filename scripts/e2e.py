#!/usr/bin/env python3
"""TestHub V1 end-to-end acceptance script.

Covers the whole V1 loop:
  requirement -> AI BDD draft -> review -> plan -> executor routing
  -> device FIFO queue -> SCRIPT / AI_RUNTIME execution -> trace -> failure
  report -> case write-back -> unified plan report, plus queue cancel and
  Skills pointer sync (local git repo).

Requires the four services to be running (scripts/dev-up.sh) and the demo
script bundles (scripts/make-demo-scripts.py).

Exit code 0 = all checks passed.
"""
import json
import pathlib
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
CORE = "http://127.0.0.1:8080"
UEXEC = "http://127.0.0.1:8081"
AGENT = "http://127.0.0.1:8090"
RUNTIME = "http://127.0.0.1:8095"
SCRIPTS = ROOT / "examples" / "scripts"

PASSED, FAILED = [], []


class CheckFailure(Exception):
    pass


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body


def check(condition, description, detail=""):
    if condition:
        PASSED.append(description)
        print(f"  PASS  {description}")
    else:
        FAILED.append((description, detail))
        print(f"  FAIL  {description} {detail}")
        raise CheckFailure(f"{description} {detail}")


def http(method, url, body=None, token=None, headers=None, raw=False, timeout=60, expect=None):
    data = None
    hdrs = {"Accept": "application/json"}
    if headers:
        hdrs.update(headers)
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = response.read()
            status = response.status
            content_type = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        payload = error.read()
        status = error.code
        content_type = error.headers.get("Content-Type", "")
    if expect is not None and status not in expect:
        raise ApiError(status, payload.decode("utf-8", "replace")[:1200])
    if raw:
        return status, payload, content_type
    if not payload:
        return status, None
    try:
        return status, json.loads(payload.decode("utf-8"))
    except json.JSONDecodeError:
        return status, payload.decode("utf-8", "replace")


def wait_until(description, fn, timeout=180, interval=2):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            result = fn()
            if result:
                return result
        except Exception as error:  # noqa: BLE001 - polling helper
            last = error
        time.sleep(interval)
    raise CheckFailure(f"timeout waiting for {description}: {last}")


def multipart_upload(path, fields, token):
    boundary = "----testhub" + uuid.uuid4().hex
    body = bytearray()
    for key, value in fields.items():
        body += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n{value}\r\n".encode()
    body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
             f"filename=\"{path.name}\"\r\nContent-Type: application/zip\r\n\r\n").encode()
    body += path.read_bytes()
    body += f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(
        CORE + "/api/v1/scripts", data=bytes(body), method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                 "Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        raise ApiError(error.code, error.read().decode("utf-8", "replace")[:1200])


def login(username, password):
    _, data = http("POST", CORE + "/api/v1/auth/login",
                   {"username": username, "password": password}, expect=(200,))
    return data["token"], data["user"]


def review(token, case_id, approve=True):
    http("POST", f"{CORE}/api/v1/cases/{case_id}/review",
         {"action": "SUBMIT", "comment": "e2e 提交评审"}, token=token, expect=(200,))
    if approve:
        http("POST", f"{CORE}/api/v1/cases/{case_id}/review",
             {"action": "APPROVE", "comment": "e2e 评审通过"}, token=token, expect=(200,))


def create_case(token, title, given, when, then, requirement_id, script_id=None):
    _, data = http("POST", CORE + "/api/v1/cases", {
        "title": title, "given": given, "when": when, "then": then,
        "requirementId": requirement_id, "scriptId": script_id, "source": "MANUAL",
    }, token=token, expect=(200, 201))
    return data


def task_detail(token, task_id):
    _, data = http("GET", f"{UEXEC}/api/v1/ui-execution/tasks/{task_id}",
                   token=token, expect=(200,))
    return data


def run_detail(token, run_id):
    _, data = http("GET", f"{CORE}/api/v1/plan-runs/{run_id}", token=token, expect=(200,))
    return data


def prepare_skills_repo():
    repo = ROOT / "data" / "skills-demo"
    if repo.exists():
        shutil.rmtree(repo)
    repo.mkdir(parents=True)
    (repo / "SKILLS.md").write_text(
        "# TestHub Demo Skills\n\n"
        "- 使用 ui-tree 中的 text 定位元素，不要依赖坐标。\n"
        "- 每次操作后重新截屏确认界面变化。\n"
        "- 登录场景：用户名输入 tester，密码输入 secret。\n", encoding="utf-8")
    (repo / "README.md").write_text("# demo skills\n", encoding="utf-8")
    env = {"GIT_AUTHOR_NAME": "testhub", "GIT_AUTHOR_EMAIL": "testhub@local",
           "GIT_COMMITTER_NAME": "testhub", "GIT_COMMITTER_EMAIL": "testhub@local"}
    subprocess.run(["git", "init", "-q", "-b", "main"], cwd=repo, check=True)
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "demo skills"], cwd=repo, check=True,
                   env={**__import__("os").environ, **env})
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    return repo, sha


def main():
    print("== TestHub V1 acceptance ==")
    print("\n[0] health")
    for name, url, path in (("core", CORE, "/actuator/health"),
                            ("ui-execution", UEXEC, "/actuator/health"),
                            ("device-agent", AGENT, "/agent/health"),
                            ("ai-runtime", RUNTIME, "/runtime/health")):
        status, payload, _ = http("GET", url + path, raw=True, expect=(200,))
        check(status == 200, f"{name} is up", str(payload)[:200])

    tester, user = login("tester", "test123")
    admin, _ = login("admin", "admin123")
    check(user["role"] == "TESTER", "login returns role TESTER")

    print("\n[1] requirement + AI BDD draft")
    suffix = time.strftime("%H%M%S")
    _, requirement = http("POST", CORE + "/api/v1/requirements", {
        "title": f"用户登录 {suffix}",
        "description": "用户可以使用用户名 tester 和密码 secret 登录，登录成功后进入首页并显示 登录成功",
    }, token=tester, expect=(200, 201))
    requirement_id = requirement["id"]

    _, draft = http("POST", CORE + "/api/v1/ai/bdd-drafts", {
        "requirementId": requirement_id, "sampleCount": 3,
        "requirementText": requirement["description"],
    }, token=tester, expect=(200,))
    check(draft.get("drafts"), "AI analysis produced BDD drafts", str(draft)[:300])
    ai_case = draft["drafts"][0]
    ai_case_id = ai_case["caseId"]
    _, ai_detail = http("GET", f"{CORE}/api/v1/cases/{ai_case_id}", token=tester, expect=(200,))
    check(ai_detail["reviewStatus"] == "DRAFT", "AI draft starts as DRAFT")
    check(ai_detail["source"] == "AI", "AI draft records source=AI")
    check(ai_detail["capability"]["availableExecutors"] == ["AI_RUNTIME"],
          "scriptless case is AI_RUNTIME only", str(ai_detail["capability"]))

    print("\n[2] review + script cases")
    review(tester, ai_case_id)
    _, ai_detail = http("GET", f"{CORE}/api/v1/cases/{ai_case_id}", token=tester, expect=(200,))
    check(ai_detail["reviewStatus"] == "APPROVED", "AI case approved by human review")
    bdd = ai_detail.get("bdd") or ai_detail
    check(bdd.get("given") and bdd.get("when") and bdd.get("then"),
          "BDD given/when/then persisted")

    pass_script = multipart_upload(SCRIPTS / "login-pass.zip",
                                     {"name": "login-pass", "scriptType": "TESTNG",
                                      "entryCommand": "sh run.sh"}, tester)
    fail_script = multipart_upload(SCRIPTS / "login-fail.zip",
                                     {"name": "login-fail", "scriptType": "TESTNG",
                                      "entryCommand": "sh run.sh"}, tester)
    slow_script = multipart_upload(SCRIPTS / "login-slow.zip",
                                     {"name": "login-slow", "scriptType": "TESTNG",
                                      "entryCommand": "sh run.sh"}, tester)

    ok_case = create_case(tester, f"脚本回归-通过 {suffix}", "已打开 App", "执行登录脚本", "脚本通过",
                          requirement_id, pass_script["id"])
    bad_case = create_case(tester, f"脚本回归-失败 {suffix}", "已打开 App", "执行错误凭据脚本",
                           "应提示用户名或密码错误", requirement_id, fail_script["id"])
    slow_a = create_case(tester, f"慢速回归-A {suffix}", "已打开 App", "执行慢速脚本", "脚本通过",
                         requirement_id, slow_script["id"])
    slow_b = create_case(tester, f"慢速回归-B {suffix}", "已打开 App", "执行慢速脚本", "脚本通过",
                         requirement_id, slow_script["id"])
    for case in (ok_case, bad_case, slow_a, slow_b):
        review(tester, case["id"])

    _, bad_detail = http("GET", f"{CORE}/api/v1/cases/{bad_case['id']}", token=tester, expect=(200,))
    check(sorted(bad_detail["capability"]["availableExecutors"]) == ["AI_RUNTIME", "SCRIPT"],
          "script case offers SCRIPT and AI_RUNTIME")
    check(bad_detail["capability"]["defaultExecutor"] == "SCRIPT", "script case defaults to SCRIPT")

    print("\n[3] skills pointer (local git repo)")
    repo, sha = prepare_skills_repo()
    http("PUT", UEXEC + "/api/v1/ui-execution/skills/pointer",
         {"gitUrl": "file://" + str(repo), "branch": "main"}, token=admin, expect=(200,))
    _, synced = http("POST", UEXEC + "/api/v1/ui-execution/skills/pointer/sync",
                     {}, token=admin, expect=(200,))
    check(synced.get("activeCommit") == sha, "skills sync resolves HEAD commit",
          f"want={sha} got={synced.get('activeCommit')}")

    print("\n[4] device pool")
    devices = wait_until("device registration", lambda: (
        lambda d: d["items"] if d.get("items") else None)(
        http("GET", UEXEC + "/api/v1/ui-execution/devices", token=tester, expect=(200,))[1]),
        timeout=90)
    check(len(devices) >= 1, "device agent registered simulated devices", str(devices)[:300])
    device = next((d for d in devices if d.get("online")), devices[0])
    device_id = device["deviceId"]
    print(f"  device: {device['serial']} ({device['status']})")

    print("\n[5] plan + trigger (single device => FIFO observable)")
    _, plan = http("POST", CORE + "/api/v1/plans", {
        "name": f"混合回归 {suffix}", "description": "E2E：AI Runtime + 传统脚本"},
        token=tester, expect=(200, 201))
    plan_id = plan["id"]
    # Slow case first so the FIFO queue is observable while it runs.
    order = [slow_a["id"], ai_case_id, bad_case["id"], ok_case["id"]]
    http("POST", f"{CORE}/api/v1/plans/{plan_id}/cases",
         {"caseIds": order}, token=tester, expect=(200, 201))
    _, triggered = http("POST", f"{CORE}/api/v1/plans/{plan_id}/trigger",
                        {"targetDeviceId": device_id, "timeoutSec": 180, "stepTimeoutSec": 45},
                        token=tester, expect=(200,))
    run_id = triggered["runId"]
    items = triggered["items"]
    check(len(items) == 4, "trigger created one item per case", str(items)[:300])
    check(all(i.get("taskId") for i in items), "every item got a task id", str(items)[:400])

    time.sleep(1.5)
    _, queue = http("GET", f"{UEXEC}/api/v1/ui-execution/devices/{device_id}/queue",
                    token=tester, expect=(200,))
    queued = [t["taskId"] for t in queue.get("tasks", [])]
    check(len(queued) >= 2, "device queue holds waiting tasks", str(queue)[:400])
    positions = [t["position"] for t in queue["tasks"]]
    check(positions == sorted(positions), "queue positions are ordered", str(positions))
    expected_tail = [i["taskId"] for i in items[1:]][:len(queued)]
    check(queued == [i["taskId"] for i in items if i["taskId"] in queued],
          "queued tasks preserve plan order (FIFO)", f"queued={queued}")

    print("\n[6] wait for run completion")
    detail = wait_until("plan run terminal", lambda: (
        lambda d: d if all(i["status"] in ("SUCCESS", "FAILED", "TIMEOUT", "CANCELLED", "SKIPPED")
                           for i in d["items"]) else None)(run_detail(tester, run_id)),
        timeout=300)
    by_case = {i["caseId"]: i for i in detail["items"]}
    ai_item, bad_item = by_case[ai_case_id], by_case[bad_case["id"]]
    ok_item, slow_item = by_case[ok_case["id"]], by_case[slow_a["id"]]
    check(ai_item["status"] == "SUCCESS", "AI Runtime case succeeded", str(ai_item)[:400])
    check(slow_item["status"] == "SUCCESS", "slow script case succeeded", str(slow_item)[:400])
    check(ok_item["status"] == "SUCCESS", "passing script case succeeded", str(ok_item)[:400])
    check(bad_item["status"] == "FAILED", "failing script case failed", str(bad_item)[:400])
    check(bad_item.get("failureReason") == "SCRIPT_FAILED",
          "failure category is SCRIPT_FAILED", str(bad_item)[:400])
    check(detail["run"]["status"] == "PARTIAL", "mixed run is PARTIAL", detail["run"]["status"])

    print("\n[7] AI trace")
    ai_task = task_detail(tester, ai_item["taskId"])
    check(len(ai_task["steps"]) >= 3, "AI runtime recorded multiple steps",
          f"steps={len(ai_task['steps'])}")
    check(all(s.get("screenshotUrl") for s in ai_task["steps"]),
          "every AI step has a screenshot")
    actions = " ".join(s.get("action") or "" for s in ai_task["steps"])
    check("登录" in actions, "AI actions target the login flow", actions[:300])
    check(ai_task.get("skills", {}).get("commit") == sha,
          "AI task pinned the skills commit", str(ai_task.get("skills")))
    status, image, content_type = http("GET", ai_task["steps"][0]["screenshotUrl"], raw=True,
                                       expect=(200,))
    check(image[:8] == b"\x89PNG\r\n\x1a\n", "step screenshot is a real PNG",
          f"content-type={content_type}")

    print("\n[8] script artifacts + failure report + write-back")
    ok_task = task_detail(tester, ok_item["taskId"])
    artifact_types = {a.get("type") for a in ok_task.get("artifacts", [])}
    check("JUNIT" in artifact_types and "SCREENSHOT" in artifact_types,
          "script task uploaded JUNIT and SCREENSHOT artifacts", str(artifact_types))
    bad_task = task_detail(tester, bad_item["taskId"])
    check(bad_task.get("failureReportUrl"), "failed task has a failure report URL",
          str(bad_task)[:400])
    report_url = bad_task["failureReportUrl"]
    _, report = http("GET", report_url, raw=False, expect=(200,))
    check(report.get("reasonCategory") == "SCRIPT_FAILED", "report reasonCategory",
          str(report)[:400])
    check(report.get("timeline"), "report contains a timeline", str(report)[:300])
    check(report.get("environment", {}).get("deviceSerial") == device["serial"],
          "report records device environment")

    _, bad_case_detail = http("GET", f"{CORE}/api/v1/cases/{bad_case['id']}", token=tester,
                              expect=(200,))
    check(bad_case_detail.get("failureAnalysis"),
          "failure analysis written back to the case",
          str(bad_case_detail.get("failureAnalysis"))[:400])

    print("\n[9] unified report")
    _, report = http("GET", f"{CORE}/api/v1/plan-runs/{run_id}/report", token=tester, expect=(200,))
    summary = report["summary"]
    check(summary["total"] == 4 and summary["success"] == 3 and summary["failed"] == 1,
          "report summary counts", str(summary))
    executors = {e["executorType"]: e for e in report["executors"]}
    check(executors.get("SCRIPT", {}).get("total") == 3, "report groups SCRIPT results",
          str(executors))
    check(executors.get("AI_RUNTIME", {}).get("total") == 1, "report groups AI_RUNTIME results",
          str(executors))
    check(any(c["category"] == "SCRIPT_FAILED" and c["count"] == 1
              for c in report["failureCategories"]),
          "report aggregates failure categories", str(report["failureCategories"]))

    tasks = wait_until("task list by planRunId", lambda: (
        lambda d: d["items"] if d.get("total") == 4 else None)(
        http("GET", f"{UEXEC}/api/v1/ui-execution/tasks?planRunId={run_id}&size=50",
             token=tester, expect=(200,))[1]), timeout=30)
    check({t["caseId"] for t in tasks} == set(order), "task list filters by planRunId")

    print("\n[10] queue cancel")
    _, plan2 = http("POST", CORE + "/api/v1/plans", {
        "name": f"取消验证 {suffix}", "description": "E2E：FIFO 取消"},
        token=tester, expect=(200, 201))
    http("POST", f"{CORE}/api/v1/plans/{plan2['id']}/cases",
         {"caseIds": [slow_a["id"], slow_b["id"], ok_case["id"]]},
         token=tester, expect=(200, 201))
    _, triggered2 = http("POST", f"{CORE}/api/v1/plans/{plan2['id']}/trigger",
                         {"targetDeviceId": device_id, "timeoutSec": 180, "stepTimeoutSec": 45},
                         token=tester, expect=(200,))
    run2 = triggered2["runId"]
    pending = wait_until("a pending task in queue", lambda: next(
        (i for i in run_detail(tester, run2)["items"] if i["status"] == "PENDING"), None),
        timeout=30, interval=0.5)
    _, cancelled = http("POST", f"{UEXEC}/api/v1/ui-execution/tasks/{pending['taskId']}/cancel",
                        {}, token=tester, expect=(200,))
    check(cancelled.get("status") == "CANCELLED", "queued task cancelled", str(cancelled))
    detail2 = wait_until("second run terminal", lambda: (
        lambda d: d if all(i["status"] in ("SUCCESS", "FAILED", "TIMEOUT", "CANCELLED", "SKIPPED")
                           for i in d["items"]) else None)(run_detail(tester, run2)),
        timeout=300)
    statuses = [i["status"] for i in detail2["items"]]
    check("CANCELLED" in statuses, "run contains the cancelled item", str(statuses))
    check(statuses.count("SUCCESS") == 2, "remaining queued tasks still ran", str(statuses))

    print("\n[11] stats")
    _, stats = http("GET", UEXEC + "/api/v1/ui-execution/stats", token=tester, expect=(200,))
    check(stats["devices"]["total"] >= 1, "stats exposes device counters", str(stats)[:400])
    check(stats["tasks"]["success"] >= 5, "stats exposes task counters", str(stats)[:400])

    print("\n" + "=" * 72)
    print(f"RESULT: {len(PASSED)} checks passed, {len(FAILED)} failed")
    print("=" * 72)
    report_path = ROOT / "data" / "e2e-report.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps({
        "passed": PASSED, "failed": FAILED, "runId": run_id, "cancelRunId": run2,
        "planId": plan_id, "skillsCommit": sha, "device": device,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print("report written to", report_path)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except CheckFailure as failure:
        print(f"\nE2E FAILED: {failure}")
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
