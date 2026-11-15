#!/usr/bin/env bash
# Start every EasyTest service for local development.
#
#   ./scripts/dev-up.sh                  H2 + in-memory queue + local artifacts
#   EASYTEST_INFRA=docker ./scripts/dev-up.sh
#                                        MySQL + Redis + MinIO (scripts/infra-up.sh first)
#
# Logs: logs/<service>.log  PIDs: data/pids/<service>.pid
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p logs data/pids data/db data/artifacts data/core-artifacts data/agent

export EASYTEST_JWT_SECRET="${EASYTEST_JWT_SECRET:-easytest-dev-jwt-secret-change-me-0123456789abcdef}"
export EASYTEST_SERVICE_TOKEN="${EASYTEST_SERVICE_TOKEN:-easytest-dev-service-token}"
export EASYTEST_AGENT_TOKEN="${EASYTEST_AGENT_TOKEN:-easytest-dev-agent-token}"
export EASYTEST_ENCRYPTION_KEY="${EASYTEST_ENCRYPTION_KEY:-easytest-dev-encryption-key-change-me}"

export EASYTEST_CORE_URL="${EASYTEST_CORE_URL:-http://127.0.0.1:8080}"
export EASYTEST_UIEXEC_URL="${EASYTEST_UIEXEC_URL:-http://127.0.0.1:8081}"
export EASYTEST_UIEXEC_PUBLIC_URL="${EASYTEST_UIEXEC_PUBLIC_URL:-http://127.0.0.1:8081}"
export EASYTEST_RUNTIME_URL="${EASYTEST_RUNTIME_URL:-http://127.0.0.1:8095}"
export EASYTEST_AGENT_BASE_URL="${EASYTEST_AGENT_BASE_URL:-http://127.0.0.1:8090}"
PUBLIC_BASE_URL="${EASYTEST_PUBLIC_BASE_URL:-http://127.0.0.1:8080}"
INTERNAL_BASE_URL="${EASYTEST_INTERNAL_BASE_URL:-http://127.0.0.1:8080}"
UIEXEC_BASE_URL="${EASYTEST_UI_EXECUTION_BASE_URL:-http://127.0.0.1:8081}"
APIEXEC_PUBLIC_URL="${EASYTEST_APIEXEC_PUBLIC_URL:-http://127.0.0.1:8084}"
APIEXEC_CALLBACK_URL="${EASYTEST_APIEXEC_CALLBACK_URL:-http://127.0.0.1:8080}"
UIEXEC_CALLBACK_URL="${EASYTEST_UIEXEC_CALLBACK_URL:-http://127.0.0.1:8081}"
export EASYTEST_ARTIFACT_DIR="$ROOT/data/artifacts"

INFRA="${EASYTEST_INFRA:-local}"
CORE_DB_URL="jdbc:h2:file:$ROOT/data/db/core;MODE=MySQL;AUTO_SERVER=TRUE"
UEXEC_DB_URL="jdbc:h2:file:$ROOT/data/db/uiexec;MODE=MySQL;AUTO_SERVER=TRUE"
APIEXEC_DB_URL="jdbc:h2:file:$ROOT/data/db/apiexec;MODE=MySQL;AUTO_SERVER=TRUE"
QUEUE_STORE="${EASYTEST_QUEUE_STORE:-memory}"
APIEXEC_QUEUE="${EASYTEST_APIEXEC_QUEUE:-memory}"
APIEXEC_INSTANCE_ID="${EASYTEST_APIEXEC_INSTANCE_ID:-apiexec-local-1}"
ARTIFACT_STORE="${EASYTEST_ARTIFACT_STORE:-local}"

if [ "$INFRA" = "docker" ]; then
  if ! docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
    echo "Docker daemon is not running. Start Docker Desktop and run scripts/infra-up.sh." >&2
    exit 1
  fi
  echo "[infra] docker: MySQL / Redis / MinIO"
  export SPRING_PROFILES_ACTIVE=mysql
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-root}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-easytest}"
  export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
  export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"
  export EASYTEST_MINIO_ENDPOINT="${EASYTEST_MINIO_ENDPOINT:-http://127.0.0.1:9000}"
  export EASYTEST_MINIO_ACCESS_KEY="${EASYTEST_MINIO_ACCESS_KEY:-easytest}"
  export EASYTEST_MINIO_SECRET_KEY="${EASYTEST_MINIO_SECRET_KEY:-easytest123}"
  export EASYTEST_KAFKA_BOOTSTRAP="${EASYTEST_KAFKA_BOOTSTRAP:-127.0.0.1:9092}"
  export EASYTEST_EVENT_BUS="${EASYTEST_EVENT_BUS:-kafka}"
  CORE_DB_URL="jdbc:mysql://127.0.0.1:3306/easytest_core?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
  UEXEC_DB_URL="jdbc:mysql://127.0.0.1:3306/easytest_uiexec?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
  APIEXEC_DB_URL="jdbc:mysql://127.0.0.1:3306/easytest_apiexec?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
  QUEUE_STORE="${EASYTEST_QUEUE_STORE:-redis}"
  APIEXEC_QUEUE="${EASYTEST_APIEXEC_QUEUE:-redis}"
  ARTIFACT_STORE="${EASYTEST_ARTIFACT_STORE:-minio}"
fi

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
  SPRING_DATASOURCE_URL="$CORE_DB_URL" \
  EASYTEST_PUBLIC_BASE_URL="$PUBLIC_BASE_URL" \
  EASYTEST_INTERNAL_BASE_URL="$INTERNAL_BASE_URL" \
  EASYTEST_UI_EXECUTION_BASE_URL="$UIEXEC_BASE_URL" \
  EASYTEST_API_EXECUTION_URLS="${EASYTEST_API_EXECUTION_URLS:-http://127.0.0.1:8084}" \
  EASYTEST_WEB_STATIC_DIR="$ROOT/web/dist" \
  EASYTEST_ARTIFACT_DIR="$ROOT/data/core-artifacts" \
  EASYTEST_ARTIFACT_STORE="$ARTIFACT_STORE" \
  EASYTEST_MINIO_BUCKET=easytest-core \
  ./scripts/mvn.sh -q -pl easytest-server spring-boot:run

start uiexec env \
  SPRING_DATASOURCE_URL="$UEXEC_DB_URL" \
  EASYTEST_QUEUE_STORE="$QUEUE_STORE" \
  EASYTEST_ARTIFACT_STORE="$ARTIFACT_STORE" \
  EASYTEST_EVENT_BUS="$EASYTEST_EVENT_BUS" \
  EASYTEST_KAFKA_BOOTSTRAP="$EASYTEST_KAFKA_BOOTSTRAP" \
  EASYTEST_UIEXEC_CALLBACK_URL="$UIEXEC_CALLBACK_URL" \
  EASYTEST_UIEXEC_PUBLIC_URL="$EASYTEST_UIEXEC_PUBLIC_URL" \
  EASYTEST_MINIO_BUCKET=easytest-ui \
  ./scripts/mvn.sh -q -pl ui-execution spring-boot:run

start apiexec env \
  SPRING_DATASOURCE_URL="$APIEXEC_DB_URL" \
  EASYTEST_CORE_URL="$EASYTEST_CORE_URL" \
  EASYTEST_APIEXEC_PUBLIC_URL="$APIEXEC_PUBLIC_URL" \
  EASYTEST_APIEXEC_CALLBACK_URL="$APIEXEC_CALLBACK_URL" \
  EASYTEST_APIEXEC_QUEUE="$APIEXEC_QUEUE" \
  EASYTEST_APIEXEC_INSTANCE_ID="$APIEXEC_INSTANCE_ID" \
  EASYTEST_APIEXEC_WORKERS="${EASYTEST_APIEXEC_WORKERS:-4}" \
  ./scripts/mvn.sh -q -pl api-execution spring-boot:run

start agent env \
  EASYTEST_AGENT_MODE="${EASYTEST_AGENT_MODE:-simulated}" \
  EASYTEST_AGENT_ADB="${EASYTEST_AGENT_ADB:-adb}" \
  EASYTEST_AGENT_DEVICE_REFRESH_MS="${EASYTEST_AGENT_DEVICE_REFRESH_MS:-15000}" \
  EASYTEST_AGENT_APP_PACKAGE="${EASYTEST_AGENT_APP_PACKAGE:-}" \
  EASYTEST_AGENT_DATA_DIR="$ROOT/data/agent" \
  ./scripts/mvn.sh -q -pl device-agent spring-boot:run

if [ -x "$ROOT/agents/ai-runtime/.venv/bin/uvicorn" ]; then
  start ai-runtime env PORT=8095 PYTHONUNBUFFERED=1 bash "$ROOT/agents/ai-runtime/run.sh"
else
  echo "[warn] ai-runtime venv missing, skipping (run scripts/setup.sh)"
fi

echo
echo "Waiting for services ($INFRA infra)..."
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
wait_for apiexec http://127.0.0.1:8084/actuator/health
wait_for agent   http://127.0.0.1:8090/agent/health
wait_for runtime http://127.0.0.1:8095/runtime/health 60 || true

if [ "${EASYTEST_SCRCPY_WEB:-auto}" != "0" ] && [ -f "$ROOT/tools/ws-scrcpy/dist/index.js" ]; then
  ./scripts/scrcpy-web-up.sh || echo "[warn] ws-scrcpy failed to start; run make scrcpy-setup" >&2
fi

echo
echo "EasyTest is up ($INFRA):"
echo "  core         http://127.0.0.1:8080"
echo "  ui-execution http://127.0.0.1:8081
  api-execution http://127.0.0.1:8084"
echo "  device-agent http://127.0.0.1:8090"
echo "  ai-runtime   http://127.0.0.1:8095"
echo "  console      http://127.0.0.1:8080 (built web/dist) · dev server: cd web && npm run dev (5173)"
if [ "$INFRA" = "docker" ]; then
  echo "  mysql/redis/minio/kafka: Docker · object-store: ${ARTIFACT_STORE} · queue: ${QUEUE_STORE} · event-bus: ${EASYTEST_EVENT_BUS:-local}"
fi
