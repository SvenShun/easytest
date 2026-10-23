#!/usr/bin/env bash
# Start every TestHub service for local development (H2 + in-memory queue + local artifacts).
# Logs: logs/<service>.log  PIDs: data/pids/<service>.pid
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p logs data/pids data/db data/artifacts data/core-artifacts data/agent

export TESTHUB_JWT_SECRET="${TESTHUB_JWT_SECRET:-testhub-dev-jwt-secret-change-me-0123456789abcdef}"
export TESTHUB_SERVICE_TOKEN="${TESTHUB_SERVICE_TOKEN:-testhub-dev-service-token}"
export TESTHUB_AGENT_TOKEN="${TESTHUB_AGENT_TOKEN:-testhub-dev-agent-token}"
export TESTHUB_ENCRYPTION_KEY="${TESTHUB_ENCRYPTION_KEY:-testhub-dev-encryption-key-change-me}"

export TESTHUB_CORE_URL="http://127.0.0.1:8080"
export TESTHUB_UIEXEC_URL="http://127.0.0.1:8081"
export TESTHUB_UIEXEC_PUBLIC_URL="http://127.0.0.1:8081"
export TESTHUB_RUNTIME_URL="http://127.0.0.1:8095"
export TESTHUB_AGENT_BASE_URL="http://127.0.0.1:8090"
export TESTHUB_ARTIFACT_DIR="$ROOT/data/artifacts"

start() {
  local name="$1"; shift
  if [ -f "data/pids/$name.pid" ] && kill -0 "$(cat "data/pids/$name.pid")" 2>/dev/null; then
    echo "[skip] $name already running (pid $(cat "data/pids/$name.pid"))"
    return
  fi
  echo "[start] $name"
  "$@" > "logs/$name.log" 2>&1 &
  echo $! > "data/pids/$name.pid"
}

start core env \
  SPRING_DATASOURCE_URL="jdbc:h2:file:$ROOT/data/db/core;MODE=MySQL;AUTO_SERVER=TRUE" \
  TESTHUB_PUBLIC_BASE_URL="http://127.0.0.1:8080" \
  TESTHUB_UI_EXECUTION_BASE_URL="http://127.0.0.1:8081" \
  TESTHUB_ARTIFACT_DIR="$ROOT/data/core-artifacts" \
  ./scripts/mvn.sh -q -pl testhub-server spring-boot:run

start uiexec env \
  SPRING_DATASOURCE_URL="jdbc:h2:file:$ROOT/data/db/uiexec;MODE=MySQL;AUTO_SERVER=TRUE" \
  TESTHUB_QUEUE_STORE=memory \
  TESTHUB_ARTIFACT_STORE=local \
  ./scripts/mvn.sh -q -pl ui-execution spring-boot:run

start agent env \
  TESTHUB_AGENT_MODE=simulated \
  TESTHUB_AGENT_DATA_DIR="$ROOT/data/agent" \
  ./scripts/mvn.sh -q -pl device-agent spring-boot:run

if [ -x "$ROOT/agents/ai-runtime/.venv/bin/uvicorn" ]; then
  start ai-runtime env PORT=8095 PYTHONUNBUFFERED=1 bash "$ROOT/agents/ai-runtime/run.sh"
else
  echo "[warn] ai-runtime venv missing, skipping (run scripts/setup.sh)"
fi

echo
echo "Waiting for services..."
wait_for() {
  local name="$1" url="$2" tries="${3:-90}"
  for _ in $(seq 1 "$tries"); do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
      echo "  ok   $name -> $url"
      return 0
    fi
    sleep 1
  done
  echo "  FAIL $name -> $url (see logs/$name.log)"
  return 1
}

wait_for core    http://127.0.0.1:8080/actuator/health
wait_for uiexec  http://127.0.0.1:8081/actuator/health
wait_for agent   http://127.0.0.1:8090/agent/health
wait_for runtime http://127.0.0.1:8095/runtime/health 60 || true

echo
echo "TestHub is up:"
echo "  core        http://127.0.0.1:8080"
echo "  ui-execution http://127.0.0.1:8081"
echo "  device-agent http://127.0.0.1:8090"
echo "  ai-runtime  http://127.0.0.1:8095"
echo "  console     cd web && npm run dev  (http://127.0.0.1:5173)"
