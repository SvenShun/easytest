#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [ -f data/pids/scrcpy-web.pid ]; then
  pid="$(cat data/pids/scrcpy-web.pid)"
  kill "$pid" 2>/dev/null || true
  sleep 1
  kill -9 "$pid" 2>/dev/null || true
  rm -f data/pids/scrcpy-web.pid
  echo "stopped ws-scrcpy"
else
  echo "ws-scrcpy not managed by this repo"
fi
