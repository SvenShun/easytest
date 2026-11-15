#!/usr/bin/env bash
# EasyTest 一期冒烟：只读检查 + 一个工程契约场景真实执行。
set -euo pipefail
BASE="${EASYTEST_BASE_URL:-http://127.0.0.1:8080}"
python3 - "$BASE" <<'PY'
import json, sys, time, urllib.request, urllib.error

base = sys.argv[1].rstrip('/')
checks = []

def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'} if body is not None else {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b'null')

def check(name, ok, detail=''):
    checks.append((name, ok, detail))
    print(('PASS' if ok else 'FAIL'), name, detail)

status, _ = call('GET', '/actuator/health')
check('health', status == 200, str(status))

status, login = call('POST', '/api/v1/auth/login', {'username': 'tester', 'password': 'test123'})
check('login tester', status == 200 and bool(login and login.get('token')))
token = login['token'] if isinstance(login, dict) else None
if not token:
    print(f'\n{sum(1 for _, ok, _ in checks if ok)}/{len(checks)} checks passed')
    sys.exit(1)

status, projects = call('GET', '/api/v1/projects', token=token)
check('projects', status == 200 and any(p.get('id') == 'default' for p in (projects or [])))

status, cases = call('GET', '/api/v1/cases?page=1&size=5', token=token)
check('cases', status == 200 and 'items' in (cases or {}))

status, methods = call('GET', '/api/v1/engineering-methods', token=token)
check('engineering methods', status == 200 and isinstance(methods, list))

status, agents = call('GET', '/api/v1/api-agents', token=token)
check('api agents', status == 200 and isinstance(agents, list))

status, scripts = call('GET', '/api/v1/scripts?page=1&size=5', token=token)
check('scripts', status == 200 and 'items' in (scripts or {}))

status, repos = call('GET', '/api/v1/engineering-repos', token=token)
check('git engineering repos', status == 200 and isinstance(repos, list))

status, scenarios = call('GET', '/api/v1/api-scenarios?page=1&size=50', token=token)
check('api scenarios', status == 200 and 'items' in (scenarios or {}))
items = (scenarios or {}).get('items', [])
target = next((s for s in items if '工程' in (s.get('name') or '') or '契约' in (s.get('name') or '')), None)
if target:
    status, run = call('POST', f"/api/v1/api-scenarios/{target['id']}/run", {'timeoutSec': 60}, token)
    if status == 200:
        detail = None
        for _ in range(20):
            status, detail = call('GET', f"/api/v1/api-scenarios/runs/{run['id']}", token=token)
            if detail and detail.get('status') in ('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED'):
                break
            time.sleep(1)
        check('contract scenario', detail and detail.get('status') == 'SUCCESS',
              (detail or {}).get('summary') or str(status))
    else:
        check('contract scenario', False, str(run)[:200])
else:
    check('contract scenario', False, 'no engineering scenario found')

passed = sum(1 for _, ok, _ in checks if ok)
print(f'\n{passed}/{len(checks)} checks passed')
sys.exit(0 if passed == len(checks) else 1)
PY
