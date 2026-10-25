#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [ -f data/pids/metersphere.pid ]; then
  pid="$(cat data/pids/metersphere.pid)"
  kill "$pid" 2>/dev/null || true
  sleep 2
  kill -9 "$pid" 2>/dev/null || true
  rm -f data/pids/metersphere.pid
fi
echo "metersphere stopped"
