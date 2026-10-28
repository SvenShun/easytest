#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
for name in sonic-gateway sonic-folder sonic-controller sonic-eureka sonic-ui metersphere-ui; do
  if [ -f "data/pids/$name.pid" ]; then
    pid="$(cat "data/pids/$name.pid")"
    kill "$pid" 2>/dev/null || true
    sleep 0.3
    kill -9 "$pid" 2>/dev/null || true
    rm -f "data/pids/$name.pid"
    echo "stopped $name"
  fi
done
