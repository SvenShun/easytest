#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
if [ -f data/pids/metersphere-ui.pid ] && kill -0 "$(cat data/pids/metersphere-ui.pid)" 2>/dev/null; then
  echo "MeterSphere UI already running: http://127.0.0.1:5100"; exit 0
fi
mkdir -p logs data/pids
nohup node deploy/compare/static-proxy.mjs 5100 \
  upstream/metersphere/frontend/dist http://127.0.0.1:8082 - all \
  > logs/metersphere-ui.log 2>&1 &
echo $! > data/pids/metersphere-ui.pid
echo "MeterSphere UI: http://127.0.0.1:5100 (admin / metersphere)"
