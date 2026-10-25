#!/usr/bin/env python3
"""Part 3 device acceptance (hermetic): ADB discovery/hotplug/actions/install via a fake adb.

This verifies the device-agent protocol without a physical device. Replace the fake
adb with the real adb and attach a device to run the same flow on hardware.
"""
import json
import os
import pathlib
import stat
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from urllib.parse import urlencode

ROOT = pathlib.Path(__file__).resolve().parent.parent
AGENT = "http://127.0.0.1:8091"
TOKEN = "part4-agent-token"
PASSED, FAILED = [], []


def check(cond, label, detail=""):
    if cond:
        PASSED.append(label)
        print(f"  PASS  {label}")
    else:
        FAILED.append((label, detail))
        print(f"  FAIL  {label} {detail}")
        raise SystemExit(1)


def request(method, path, body=None, headers=None, expect=(200, 201, 204)):
    h = dict(headers or {})
    h.setdefault("X-Agent-Token", TOKEN)
    data = body
    req = urllib.request.Request(AGENT + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            payload = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        payload = e.read()
        status = e.code
    if expect and status not in expect:
        raise AssertionError(f"{method} {path} -> {status}: {payload[:400]!r}")
    return status, payload


def multipart_apk(path, content):
    boundary = "----part4" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
            f"filename=\"demo.apk\"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n").encode()
    body += content + f"\r\n--{boundary}--\r\n".encode()
    return body, {"Content-Type": f"multipart/form-data; boundary={boundary}"}


def make_fake_adb(dir_path: pathlib.Path):
    devices = dir_path / "devices.txt"
    xml = dir_path / "window.xml"
    log = dir_path / "adb.log"
    devices.write_text("List of devices attached\nfake-1 device product:test model:Pixel_7 device:test transport_id:1\n")
    xml.write_text("""<hierarchy rotation="0">
  <node class="android.widget.FrameLayout" package="com.demo" bounds="[0,0][200,400]">
    <node class="android.widget.Button" text="登录" resource-id="com.demo:id/login"
          clickable="true" bounds="[10,20][110,80]"/>
  </node>
</hierarchy>""")
    script = dir_path / "adb"
    script.write_text(f"""#!/usr/bin/env python3
import sys
args = sys.argv[1:]
with open({str(log)!r}, "a") as f:
    f.write(" ".join(args) + "\\n")
if args and args[0] == "devices":
    sys.stdout.write(open({str(devices)!r}).read())
    sys.exit(0)
if len(args) >= 2 and args[0] == "-s":
    args = args[2:]
if args[:3] == ["exec-out", "screencap", "-p"]:
    sys.stdout.buffer.write(b"\\x89PNG\\r\\n\\x1a\\n")
    sys.exit(0)
if args[:3] == ["shell", "uiautomator", "dump"]:
    sys.exit(0)
if args[:2] == ["exec-out", "cat"]:
    sys.stdout.write(open({str(xml)!r}).read())
    sys.exit(0)
sys.exit(0)
""")
    script.chmod(script.stat().st_mode | stat.S_IEXEC)
    return script, devices, log


print("== Part 3 device acceptance (fake adb) ==")
work = pathlib.Path(tempfile.mkdtemp(prefix="testhub-adb-"))
adb, devices_file, adb_log = make_fake_adb(work)
jar = ROOT / "device-agent" / "target" / "device-agent-1.0.0.jar"
if not jar.exists():
    subprocess.run([str(ROOT / "scripts" / "mvn.sh"), "-q", "-pl", "device-agent", "-am", "-DskipTests", "package"],
                   cwd=ROOT, check=True)
env = os.environ.copy()
env.update({
    "SERVER_PORT": "8091",
    "TESTHUB_AGENT_MODE": "adb",
    "TESTHUB_AGENT_ADB": str(adb),
    "TESTHUB_AGENT_REGISTRATION_ENABLED": "false",
    "TESTHUB_AGENT_TOKEN": TOKEN,
    "TESTHUB_AGENT_DATA_DIR": str(work / "agent-data"),
    "TESTHUB_AGENT_RUNTIME_URL": "http://127.0.0.1:1",
    "TESTHUB_AGENT_DEVICE_REFRESH_MS": "2000",
})
log_file = open(work / "agent.log", "w")
proc = subprocess.Popen(["java", "-jar", str(jar)], cwd=ROOT, env=env,
                        stdout=log_file, stderr=subprocess.STDOUT)
try:
    for _ in range(90):
        try:
            status, _ = request("GET", "/agent/health", headers={})
            if status == 200:
                break
        except Exception:
            pass
        time.sleep(1)
    else:
        raise AssertionError("device-agent did not start: " + (work / "agent.log").read_text()[-2000:])

    print("\n[1] discovery & hotplug")
    _, raw = request("GET", "/agent/devices")
    data = json.loads(raw)
    check(data["total"] == 1 and data["items"][0]["serial"] == "fake-1", "adb device discovered")
    devices_file.write_text("List of devices attached\nfake-1 device product:test model:Pixel_7 device:test\n"
                            "fake-2 device product:test model:Pixel_8 device:test\n")
    _, raw = request("POST", "/agent/devices/refresh", body=b"")
    data = json.loads(raw)
    check(data["total"] == 2, "hotplug adds device")
    devices_file.write_text("List of devices attached\nfake-2 device product:test model:Pixel_8 device:test\n")
    _, raw = request("POST", "/agent/devices/refresh", body=b"")
    data = json.loads(raw)
    check(data["total"] == 1 and data["items"][0]["serial"] == "fake-2", "hotplug removes device")

    print("\n[2] screenshot / UI tree")
    _, raw = request("GET", "/device/fake-2/screenshot")
    check(raw.startswith(b"\x89PNG"), "screenshot PNG")
    _, raw = request("GET", "/device/fake-2/ui-tree")
    tree = json.loads(raw)
    check(tree["width"] == 200 and tree["height"] == 400, "ui tree bounds")
    check(any(e.get("text") == "登录" for e in tree["screen"]["elements"]), "ui tree element")

    print("\n[3] action & install")
    _, raw = request("POST", "/device/fake-2/action",
                     body=json.dumps({"action": "click", "target": {"text": "登录"}}).encode(),
                     headers={"Content-Type": "application/json"})
    check(json.loads(raw)["ok"] is True, "click action")
    check("shell input tap 60 50" in adb_log.read_text(), "tap command sent")
    body, headers = multipart_apk(work, b"fake-apk-bytes")
    _, raw = request("POST", "/device/fake-2/install?replace=true", body=body, headers=headers)
    check(json.loads(raw)["ok"] is True, "apk install accepted")
    check("install -r" in adb_log.read_text(), "install command sent")
finally:
    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()
    log_file.close()

print("\n" + "=" * 60)
print(f"PART 3 DEVICE RESULT: {len(PASSED)} checks passed, 0 failed")
print("=" * 60)
report = ROOT / "data" / "part4-report.json"
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({"passed": PASSED, "failed": FAILED, "fakeAdb": str(adb)},
                             ensure_ascii=False, indent=2), encoding="utf-8")
print("report:", report)
