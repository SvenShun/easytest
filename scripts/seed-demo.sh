#!/usr/bin/env bash
# Seed the Docker demo: sync engineering methods and create IoT API scenarios.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${EASYTEST_BASE_URL:-http://127.0.0.1:8080}"
python3 - "$BASE" <<'PY'
import json, sys, urllib.request, urllib.error, urllib.parse
base = sys.argv[1].rstrip('/')
def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'} if body is not None else {}
    if token: headers['Authorization'] = 'Bearer ' + token
    try:
        with urllib.request.urlopen(urllib.request.Request(base + path, data=data, headers=headers, method=method), timeout=30) as r:
            raw = r.read(); return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b'null')
status, login = call('POST', '/api/v1/auth/login', {'username': 'tester', 'password': 'test123'})
if status != 200: raise SystemExit('login failed: ' + str(login))
token = login['token']
status, sync = call('POST', '/api/v1/engineering-methods/sync', {}, token)
print('engineering sync', status, sync)
scenarios = {
    'IoT Docker single login': ('com.easytest.iot.flow.IotFlows#loginContract', {'username': 'tester', 'password': 'secret'}),
    'IoT Docker mysql seed': ('com.easytest.iot.flow.IotFlows#mysqlSeed', {'homeId': 'h-docker', 'ownerId': 'u-local'}),
    'IoT Docker e2e': ('com.easytest.iot.flow.IotFlows#bindAndControl', {'username': 'tester', 'password': 'secret', 'homeId': 'h-docker', 'deviceId': 'dev-docker'}),
}
for name, (path, inputs) in scenarios.items():
    status, existing = call('GET', '/api/v1/api-scenarios?keyword=' + urllib.parse.quote(name), token=token)
    if status == 200 and existing.get('items'):
        print('scenario exists', name); continue
    step = {'name': name, 'type': 'component', 'methodPath': path, 'inputs': inputs}
    yaml = 'name: ' + name + '\nsteps:\n  - name: ' + name + '\n    type: component\n    methodPath: ' + path + '\n    inputs:\n'
    for key, value in inputs.items():
        yaml += '      ' + key + ': ' + str(value) + '\n'
    status, created = call('POST', '/api/v1/api-scenarios', {'name': name, 'yaml': yaml, 'enabled': True}, token)
    print('scenario', name, status)
PY
