#!/usr/bin/env bash
# Log in to MeterSphere and print the headers needed by ui-execution.
# MeterSphere requires X-AUTH-TOKEN + CSRF-TOKEN on authenticated calls.
set -euo pipefail
BASE="${METERSPHERE_URL:-http://127.0.0.1:8082}"
USER="${METERSPHERE_USER:-admin}"
PASS="${METERSPHERE_PASSWORD:-metersphere}"

RESP="$(curl -fsS -X POST "$BASE/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")"

python3 - "$RESP" <<'PY'
import json, shlex, sys
resp = json.loads(sys.argv[1])
if resp.get("code") != 100200:
    print("login failed:", resp, file=sys.stderr)
    sys.exit(1)
data = resp["data"]
sid, csrf = data["sessionId"], data["csrfToken"]
print("# MeterSphere session (valid for the session timeout, default 12h)")
print("export EASYTEST_CASE_SOURCE=metersphere")
print("export EASYTEST_METERSHPHERE_BASE_URL=http://127.0.0.1:8082")
print("export EASYTEST_METERSHPHERE_TOKEN=%s" % shlex.quote(sid))
print("export EASYTEST_METERSHPHERE_CSRF_TOKEN=%s" % shlex.quote(csrf))
print("export EASYTEST_METERSHPHERE_PROJECT_ID=100001100001")
print("# sessionId=%s" % sid)
PY
