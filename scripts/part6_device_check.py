#!/usr/bin/env python3
"""Real-device acceptance for UIA2 / Unicode / multi-touch / recording / metrics / diff.

Usage:
  python3 scripts/part6_device_check.py [--device f04af072] [--skip-uia2] [--skip-diff]
"""
from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

CORE = os.environ.get("TESTHUB_CORE_URL", "http://127.0.0.1:8080")
UEXEC = os.environ.get("TESTHUB_UIEXEC_URL", "http://127.0.0.1:8081")


class CheckError(RuntimeError):
    pass


def request(method: str, base: str, path: str, token: str | None = None,
            body: dict | None = None, timeout: float = 180.0) -> object:
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"} if body is not None else {}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read()
            if not raw:
                return None
            content_type = response.headers.get("Content-Type", "")
            if "json" in content_type:
                return json.loads(raw)
            return raw
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:800]
        raise CheckError(f"{method} {path} -> HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise CheckError(f"{method} {path} -> {exc}") from exc


def multipart(base: str, path: str, token: str, field: str, filename: str,
              content: bytes, extra: dict[str, str] | None = None) -> object:
    boundary = "----testhub" + uuid.uuid4().hex
    parts = []
    for key, value in (extra or {}).items():
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n{value}\r\n".encode())
    content_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    parts.append(
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
        f"Content-Type: {content_type}\r\n\r\n".encode() + content + b"\r\n"
    )
    parts.append(f"--{boundary}--\r\n".encode())
    body = b"".join(parts)
    req = urllib.request.Request(base + path, data=body, method="POST", headers={
        "Authorization": "Bearer " + token,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    })
    try:
        with urllib.request.urlopen(req, timeout=180) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        raise CheckError(f"POST {path} -> HTTP {exc.code}: {exc.read().decode(errors='replace')[:800]}") from exc


def check(name: str, fn):
    try:
        detail = fn()
        print(f"  PASS  {name}" + (f"  {detail}" if detail else ""))
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"  FAIL  {name}: {exc}")
        return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", default="f04af072")
    parser.add_argument("--skip-uia2", action="store_true")
    parser.add_argument("--skip-diff", action="store_true")
    args = parser.parse_args()

    token = request("POST", CORE, "/api/v1/auth/login",
                    body={"username": "tester", "password": "test123"})["token"]
    devices = request("GET", UEXEC, "/api/v1/ui-execution/devices", token)["items"]
    device = next((item for item in devices if item.get("serial") == args.device), None)
    if device is None:
        print(f"device {args.device} not found; adb devices:")
        os.system("adb devices -l")
        return 2
    device_id = device["deviceId"]
    print(f"== device {args.device} ({device_id}) status={device.get('status')} ==")

    results: dict[str, bool] = {}
    uia2 = False

    if not args.skip_uia2:
        def start_uia2():
            nonlocal uia2
            result = request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/uia2/start",
                             token, timeout=180)
            if not result.get("ok"):
                raise CheckError(result.get("message") or "UIA2 start failed")
            uia2 = True
            return result.get("uiaUrl")
        results["UIA2 start"] = check("UIA2 start (install/forward/instrument/session)", start_uia2)

    if uia2:
        def launch():
            result = request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/action",
                             token, {"action": "launch", "app": "com.testhub.demo"})
            if not result.get("ok"):
                raise CheckError(result.get("message") or "launch failed")
            time.sleep(2.0)
        results["launch demo"] = check("launch demo", launch)

        def unicode_input():
            text = "中文验收" + str(int(time.time()))[-4:]
            # focus the first editable field, then use UIA2 /keys for Unicode.
            request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/action", token,
                    {"action": "input", "target": {"index": 0}, "text": text})
            time.sleep(0.8)
            tree = request("GET", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/ui-tree", token)
            texts = []
            for element in tree.get("elements", []):
                texts.append(element.get("text") or "")
            texts.extend(tree.get("screen", {}).get("texts") or [])
            if not any(text == text_value or text in text_value for text_value in texts):
                raise CheckError(f"UI tree does not contain {text!r}: {texts[:20]}")
            return text
        results["Unicode input"] = check("UIA2 Unicode input visible in UI tree", unicode_input)

        def multitouch():
            tree = request("GET", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/ui-tree", token)
            width = int(tree.get("width") or 1080)
            height = int(tree.get("height") or 2400)
            cx, cy = width // 2, height // 2
            result = request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/action", token, {
                "action": "multitouch",
                "fingers": [
                    {"from": {"x": cx - 80, "y": cy}, "to": {"x": cx - 320, "y": cy}, "durationMs": 300},
                    {"from": {"x": cx + 80, "y": cy}, "to": {"x": cx + 320, "y": cy}, "durationMs": 300},
                ],
            })
            if not result.get("ok"):
                raise CheckError(result.get("message") or "multitouch failed")
        results["multi-touch"] = check("UIA2 two-finger pinch", multitouch)

    def metrics():
        sample = request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/performance/sample", token)
        if not any(sample.get(key) is not None for key in ("cpuPercent", "memUsedPercent", "batteryLevel")):
            raise CheckError(f"no numeric metrics: {sample}")
        history = request("GET", UEXEC,
                          f"/api/v1/ui-execution/devices/{device_id}/performance/history?minutes=60", token)
        return f"cpu={sample.get('cpuPercent')} mem={sample.get('memUsedPercent')} history={len(history)}"
    results["performance"] = check("performance sample/history", metrics)

    def recording():
        start = request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/recording/start", token)
        if start.get("ok") is False:
            raise CheckError(start.get("message") or "recording start failed")
        time.sleep(2.0)
        saved = request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/recording/stop",
                        token, timeout=180)
        url = saved.get("url")
        if not url or int(saved.get("size") or 0) <= 0:
            raise CheckError(f"empty recording: {saved}")
        req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token})
        with urllib.request.urlopen(req, timeout=60) as response:
            if response.status != 200 or not response.read(16):
                raise CheckError("recording URL not readable")
        return f"{saved.get('size')} bytes"
    results["recording"] = check("screenrecord start/stop artifact", recording)

    if not args.skip_diff:
        def diff():
            baseline = request("GET", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/screenshot", token)
            if not baseline:
                raise CheckError("empty baseline screenshot")
            # change the screen so the comparison is meaningful
            request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/action", token, {"action": "home"})
            time.sleep(1.2)
            result = multipart(UEXEC, f"/api/v1/ui-execution/devices/{device_id}/screenshot/compare",
                               token, "baseline", "baseline.png", baseline)
            for key in ("diffUrl", "baselineUrl", "currentUrl"):
                if not result.get(key):
                    raise CheckError(f"missing {key}: {result}")
            return f"mismatch={result.get('mismatchPercent')}%"
        results["screenshot diff"] = check("screenshot pixel diff", diff)

    if uia2:
        def stop_uia2():
            request("POST", UEXEC, f"/api/v1/ui-execution/devices/{device_id}/uia2/stop", token)
        results["UIA2 stop"] = check("UIA2 stop / restore ADB", stop_uia2)

    passed = sum(1 for value in results.values() if value)
    print(f"\nPART 6 DEVICE RESULT: {passed}/{len(results)} passed")
    report = {"device": args.device, "deviceId": device_id, "results": results}
    with open("data/part6-device-report.json", "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
