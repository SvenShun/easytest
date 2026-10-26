#!/usr/bin/env python3
"""Preflight for a physical Android device: adb, screen, input injection, APK install gate."""
import json
import os
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
ADB = os.environ.get("ADB") or str(ROOT / "tools" / "platform-tools" / "adb")


def run(*args, timeout=30):
    try:
        p = subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)
        return p.returncode, p.stdout.strip(), p.stderr.strip()
    except FileNotFoundError:
        print(f"adb not found: {ADB}")
        sys.exit(2)


args = [a for a in sys.argv[1:] if a != "--apk"]
apk_arg = ""
if "--apk" in sys.argv:
    idx = sys.argv.index("--apk")
    apk_arg = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else ""
serial = os.environ.get("ANDROID_SERIAL") or (args[0] if args else "")
code, out, err = run("devices", "-l")
if code != 0:
    print(out, err)
    sys.exit(2)
devices = [line.split()[0] for line in out.splitlines()
           if line.strip() and not line.startswith("List of") and " device " in line]
if not devices:
    print("no adb device in 'device' state")
    sys.exit(2)
serial = serial or devices[0]

def sh(command, timeout=30):
    return run("-s", serial, "shell", command, timeout=timeout)

result = {"serial": serial, "checks": {}}
code, out, _ = sh("getprop ro.product.model")
result["model"] = out or "unknown"

code, focus, _ = sh("dumpsys window | grep -m1 mCurrentFocus")
result["currentFocus"] = focus.strip()

p = subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=30)
result["checks"]["screenshot"] = len(p.stdout) > 0
result["screenshotBytes"] = len(p.stdout)

code, tree, err = sh("uiautomator dump /sdcard/testhub-check.xml >/dev/null 2>&1; cat /sdcard/testhub-check.xml")
result["checks"]["uiTree"] = "<hierarchy" in tree
result["uiTreeBytes"] = len(tree)
sh("rm -f /sdcard/testhub-check.xml")

code, _, inerr = sh("input keyevent 0")
blocked = "INJECT_EVENTS" in inerr or "SecurityException" in inerr
result["checks"]["inputInjection"] = not blocked
result["inputError"] = inerr.splitlines()[0] if inerr else ""

with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as f:
    f.write(b"not-an-apk")
    dummy = f.name
try:
    code, install_out, install_err = run("-s", serial, "install", "-r", dummy, timeout=60)
finally:
    os.unlink(dummy)
text = install_out + "\n" + install_err
result["installOutput"] = text.strip().splitlines()[-1] if text.strip() else ""
result["checks"]["installParseGate"] = "INSTALL_PARSE_FAILED_NOT_APK" in text
if apk_arg:
    code, out2, err2 = run("-s", serial, "install", "-r", apk_arg, timeout=180)
    valid = out2 + "\n" + err2
    result["validApkOutput"] = valid.strip().splitlines()[-1] if valid.strip() else ""
    result["checks"]["validNewApkInstall"] = "Success" in valid
    result["checks"]["userRestricted"] = "USER_RESTRICTED" in valid

print(json.dumps(result, ensure_ascii=False, indent=2))
print()
if result["checks"]["inputInjection"]:
    print("INPUT: OK")
else:
    print("INPUT: BLOCKED - enable Developer options -> USB debugging (Security settings) / USB调试（安全设置）")
if apk_arg and result["checks"].get("validNewApkInstall"):
    print("INSTALL: new APK install OK")
elif apk_arg and result["checks"].get("userRestricted"):
    print("INSTALL: BLOCKED - enable Developer options -> USB install / USB安装")
elif result["checks"]["installParseGate"]:
    print("INSTALL: adb install reaches parse stage; run with --apk <file> to test a valid new APK")
else:
    print("INSTALL: check install output above")
