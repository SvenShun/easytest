#!/usr/bin/env python3
"""Generate the two demo script bundles used by the E2E acceptance script.

Both bundles mimic a TestNG-style project: they write a JUnit XML report and a
screenshot into $TESTHUB_OUTPUT_DIR, which the device-agent collects as
artifacts. No Maven/JDK is required on the device, so the demo works offline.
"""
import base64
import pathlib
import struct
import zlib
import zipfile

OUT = pathlib.Path(__file__).resolve().parent.parent / "examples" / "scripts"
OUT.mkdir(parents=True, exist_ok=True)


def png(width: int, height: int, accent=(47, 107, 255)) -> bytes:
    """Tiny dependency-free PNG: dark background, accent header, text bars."""
    bg = (15, 23, 42)
    fg = (226, 232, 240)
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            color = bg
            if 20 <= x < width - 20 and 20 <= y < 46:
                color = accent
            elif 20 <= x < width - 20 and 64 <= y < 74:
                color = fg
            elif 20 <= x < int(width * 0.72) and 88 <= y < 96:
                color = (100, 116, 139)
            elif 20 <= x < int(width * 0.55) and 108 <= y < 116:
                color = (100, 116, 139)
            row += bytes(color)
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


RUN_TEMPLATE = r"""#!/bin/sh
{set_e}
OUT="${{TESTHUB_OUTPUT_DIR:-./output}}"
mkdir -p "$OUT"
echo "[demo] device=$TESTHUB_DEVICE_SERIAL task=$TESTHUB_TASK_ID"
echo "[demo] case: {case_title}"
cat > "$OUT/junit.xml" <<'XML'
{junit}
XML
printf '%s' '{png_b64}' | base64 -d > "$OUT/screenshot.png"
echo "[demo] screenshot saved: $OUT/screenshot.png"
{extra}
"""

PASS_JUNIT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.testhub.demo.LoginTest" tests="2" failures="0" errors="0" time="3.4">
  <testcase classname="com.testhub.demo.LoginTest" name="openLoginPage" time="1.1"/>
  <testcase classname="com.testhub.demo.LoginTest" name="loginWithValidCredentials" time="2.3"/>
</testsuite>"""

SLOW_JUNIT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.testhub.demo.SlowLoginTest" tests="1" failures="0" errors="0" time="6.0">
  <testcase classname="com.testhub.demo.SlowLoginTest" name="loginSlowly" time="6.0"/>
</testsuite>"""

FAIL_JUNIT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.testhub.demo.LoginErrorTest" tests="2" failures="1" errors="0" time="4.1">
  <testcase classname="com.testhub.demo.LoginErrorTest" name="openLoginPage" time="1.0"/>
  <testcase classname="com.testhub.demo.LoginErrorTest" name="loginShowsErrorToast" time="3.1">
    <failure message="expected toast 用户名或密码错误 but was 登录成功">AssertionError: expected toast</failure>
  </testcase>
</testsuite>"""


def bundle(run_sh: str) -> bytes:
    return base64.b64encode(png(360, 150)).decode("ascii")


def write_zip(name: str, run_sh: str) -> None:
    path = OUT / name
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("run.sh", run_sh)
        zf.writestr("src/test/java/DemoTest.java", "// demo placeholder\n")
    print("wrote", path, path.stat().st_size, "bytes")


if __name__ == "__main__":
    write_zip("login-pass.zip", RUN_TEMPLATE.format(
        set_e="set -e",
        case_title="用户使用正确凭据登录",
        junit=PASS_JUNIT,
        png_b64=bundle(""),
        extra='echo "[demo] done: 2 passed"; exit 0'))
    write_zip("login-fail.zip", RUN_TEMPLATE.format(
        set_e="",
        case_title="用户使用错误凭据登录应提示错误",
        junit=FAIL_JUNIT,
        png_b64=bundle(""),
        extra='echo "[demo] assertion failed: expected toast 用户名或密码错误 but was 登录成功"; exit 1'))
    write_zip("login-slow.zip", RUN_TEMPLATE.format(
        set_e="set -e",
        case_title="用户登录（慢速回归用例）",
        junit=SLOW_JUNIT,
        png_b64=bundle(""),
        extra='echo "[demo] sleeping 6s to make queue observable"; sleep 6; echo "[demo] done: 1 passed"'))
