#!/usr/bin/env bash
# Stop all locally started TestHub services.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [ -d data/pids ]; then
  for pidfile in data/pids/*.pid; do
    [ -e "$pidfile" ] || continue
    name="$(basename "$pidfile" .pid)"
    # Only manage TestHub's own services; MeterSphere has its own script.
    case "$name" in
      core|uiexec|apiexec|apiexec-2|agent|ai-runtime|web|scrcpy-web) ;;
      *) continue ;;
    esac
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "[stop] $name (pid $pid)"
      kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 15); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  done
fi
# spring-boot:run forks a JVM child; make sure none of ours survive.
pkill -f 'testhub-server.*spring-boot:run' 2>/dev/null || true
pkill -f 'ui-execution.*spring-boot:run' 2>/dev/null || true
pkill -f 'api-execution.*spring-boot:run' 2>/dev/null || true
pkill -f 'api-execution-1.0.0.jar' 2>/dev/null || true
pkill -f 'device-agent.*spring-boot:run' 2>/dev/null || true
pkill -f 'uvicorn app.main:app' 2>/dev/null || true
echo "stopped"
