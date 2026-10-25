#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [ -f data/pids/apiexec-2.pid ]; then
  pid="$(cat data/pids/apiexec-2.pid)"
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 10); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill -9 "$pid" 2>/dev/null || true
  rm -f data/pids/apiexec-2.pid
  echo "stopped api-execution #2"
else
  echo "api-execution #2 is not running"
fi
