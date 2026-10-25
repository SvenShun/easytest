#!/usr/bin/env bash
# Start every TestHub service for local development.
#
#   ./scripts/dev-up.sh                  H2 + in-memory queue + local artifacts
#   TESTHUB_INFRA=docker ./scripts/dev-up.sh
#                                        MySQL + Redis + MinIO (scripts/infra-up.sh first)
#
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

INFRA="${TESTHUB_INFRA:-local}"
CORE_DB_URL="jdbc:h2:file:$ROOT/data/db/core;MODE=MySQL;AUTO_SERVER=TRUE"
UEXEC_DB_URL="jdbc:h2:file:$ROOT/data/db/uiexec;MODE=MySQL;AUTO_SERVER=TRUE"
APIEXEC_DB_URL="jdbc:h2:file:$ROOT/data/db/apiexec;MODE=MySQL;AUTO_SERVER=TRUE"
QUEUE_STORE="${TESTHUB_QUEUE_STORE:-memory}"
APIEXEC_QUEUE="${TESTHUB_APIEXEC_QUEUE:-memory}"
APIEXEC_INSTANCE_ID="${TESTHUB_APIEXEC_INSTANCE_ID:-apiexec-local-1}"
ARTIFACT_STORE="${TESTHUB_ARTIFACT_STORE:-local}"

if [ "$INFRA" = "docker" ]; then
  if ! docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
    echo "Docker daemon is not running. Start Docker Desktop and run scripts/infra-up.sh." >&2
    exit 1
  fi
  echo "[infra] docker: MySQL / Redis / MinIO"
  export SPRING_PROFILES_ACTIVE=mysql
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-root}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-testhub}"
  export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
  export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"
  export TESTHUB_MINIO_ENDPOINT="${TESTHUB_MINIO_ENDPOINT:-http://127.0.0.1:9000}"
  export TESTHUB_MINIO_ACCESS_KEY="${TESTHUB_MINIO_ACCESS_KEY:-testhub}"
  export TESTHUB_MINIO_SECRET_KEY="${TESTHUB_MINIO_SECRET_KEY:-testhub123}"
  export TESTHUB_KAFKA_BOOTSTRAP="${TESTHUB_KAFKA_BOOTSTRAP:-127.0.0.1:9092}"
  export TESTHUB_EVENT_BUS="${TESTHUB_EVENT_BUS:-kafka}"
  CORE_DB_URL="jdbc:mysql://127.0.0.1:3306/testhub_core?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
  UEXEC_DB_URL="jdbc:mysql://127.0.0.1:3306/testhub_uiexec?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
  APIEXEC_DB_URL="jdbc:mysql://127.0.0.1:3306/testhub_apiexec?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
  QUEUE_STORE="${TESTHUB_QUEUE_STORE:-redis}"
  APIEXEC_QUEUE="${TESTHUB_APIEXEC_QUEUE:-redis}"
  ARTIFACT_STORE="${TESTHUB_ARTIFACT_STORE:-minio}"
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
  TESTHUB_PUBLIC_BASE_URL="http://127.0.0.1:8080" \
  TESTHUB_INTERNAL_BASE_URL="http://127.0.0.1:8080" \
  TESTHUB_UI_EXECUTION_BASE_URL="http://127.0.0.1:8081" \
  TESTHUB_API_EXECUTION_URLS="${TESTHUB_API_EXECUTION_URLS:-http://127.0.0.1:8084}" \
  TESTHUB_WEB_STATIC_DIR="$ROOT/web/dist" \
  TESTHUB_ARTIFACT_DIR="$ROOT/data/core-artifacts" \
  TESTHUB_ARTIFACT_STORE="$ARTIFACT_STORE" \
  TESTHUB_MINIO_BUCKET=testhub-core \
  ./scripts/mvn.sh -q -pl testhub-server spring-boot:run

start uiexec env \
  SPRING_DATASOURCE_URL="$UEXEC_DB_URL" \
  TESTHUB_QUEUE_STORE="$QUEUE_STORE" \
  TESTHUB_ARTIFACT_STORE="$ARTIFACT_STORE" \
  TESTHUB_EVENT_BUS="$TESTHUB_EVENT_BUS" \
  TESTHUB_KAFKA_BOOTSTRAP="$TESTHUB_KAFKA_BOOTSTRAP" \
  TESTHUB_UIEXEC_CALLBACK_URL="http://127.0.0.1:8081" \
  TESTHUB_MINIO_BUCKET=testhub-ui \
  ./scripts/mvn.sh -q -pl ui-execution spring-boot:run

start apiexec env \
  SPRING_DATASOURCE_URL="$APIEXEC_DB_URL" \
  TESTHUB_CORE_URL="http://127.0.0.1:8080" \
  TESTHUB_APIEXEC_PUBLIC_URL="http://127.0.0.1:8084" \
  TESTHUB_APIEXEC_CALLBACK_URL="http://127.0.0.1:8080" \
  TESTHUB_APIEXEC_QUEUE="$APIEXEC_QUEUE" \
  TESTHUB_APIEXEC_INSTANCE_ID="$APIEXEC_INSTANCE_ID" \
  TESTHUB_APIEXEC_WORKERS="${TESTHUB_APIEXEC_WORKERS:-4}" \
  ./scripts/mvn.sh -q -pl api-execution spring-boot:run

start agent env \
  TESTHUB_AGENT_MODE="${TESTHUB_AGENT_MODE:-simulated}" \
  TESTHUB_AGENT_ADB="${TESTHUB_AGENT_ADB:-adb}" \
  TESTHUB_AGENT_DEVICE_REFRESH_MS="${TESTHUB_AGENT_DEVICE_REFRESH_MS:-15000}" \
  TESTHUB_AGENT_DATA_DIR="$ROOT/data/agent" \
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

echo
echo "TestHub is up ($INFRA):"
echo "  core         http://127.0.0.1:8080"
echo "  ui-execution http://127.0.0.1:8081
  api-execution http://127.0.0.1:8084"
echo "  device-agent http://127.0.0.1:8090"
echo "  ai-runtime   http://127.0.0.1:8095"
echo "  console      http://127.0.0.1:8080 (built web/dist) · dev server: cd web && npm run dev (5173)"
if [ "$INFRA" = "docker" ]; then
  echo "  mysql/redis/minio/kafka: Docker · object-store: ${ARTIFACT_STORE} · queue: ${QUEUE_STORE} · event-bus: ${TESTHUB_EVENT_BUS:-local}"
fi
