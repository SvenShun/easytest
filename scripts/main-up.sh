#!/usr/bin/env bash
# Deploy only the main service (Part 1): MySQL + core + built web console.
# Execution services (ui-execution/device-agent/ai-runtime) are intentionally not started.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 8080 is busy; run scripts/dev-down.sh first." >&2; exit 1
fi

if ! docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
  echo "Docker is required for MySQL/MinIO (make infra)." >&2; exit 1
fi
if ! docker exec testhub-mysql mysqladmin ping -h127.0.0.1 -ptesthub >/dev/null 2>&1; then
  echo "==> starting middleware"
  ./scripts/infra-up.sh
fi

echo "==> building main service"
./scripts/mvn.sh -q -pl testhub-server -am -DskipTests package
echo "==> building web console (main-service mode)"
(cd web && VITE_MAIN_ONLY=true npm run build >/dev/null)

mkdir -p logs data/pids
JAR="$(ls testhub-server/target/testhub-server-*.jar | grep -v original | head -1)"
if [ -z "$JAR" ]; then echo "core jar not found" >&2; exit 1; fi

echo "==> starting core with MySQL + MinIO"
TESTHUB_METERSPHERE_WRITEBACK=false \
SPRING_PROFILES_ACTIVE=mysql \
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/testhub_core?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8" \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD=testhub \
TESTHUB_PUBLIC_BASE_URL="http://127.0.0.1:8080" \
TESTHUB_INTERNAL_BASE_URL="http://127.0.0.1:8080" \
TESTHUB_ARTIFACT_STORE=minio \
TESTHUB_ARTIFACT_DIR="$ROOT/data/core-artifacts" \
TESTHUB_MINIO_ENDPOINT="http://127.0.0.1:9000" \
TESTHUB_MINIO_ACCESS_KEY="testhub" \
TESTHUB_MINIO_SECRET_KEY="testhub123" \
TESTHUB_MINIO_BUCKET="testhub-core" \
TESTHUB_WEB_STATIC_DIR="$ROOT/web/dist" \
nohup java -jar "$JAR" > logs/main-core.log 2>&1 &
echo $! > data/pids/main-core.pid

echo -n "waiting for core"
for _ in $(seq 1 90); do
  if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then echo " ok"; break; fi
  echo -n "."; sleep 1
done
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  echo; echo "core failed to start; see logs/main-core.log" >&2; tail -20 logs/main-core.log >&2; exit 1
fi
if ! curl -fsS --max-time 3 http://127.0.0.1:8080/ | grep -q '<div id="root">'; then
  echo "web console is not served by core; check TESTHUB_WEB_STATIC_DIR / logs/main-core.log" >&2; exit 1
fi

echo
echo "Main service is up (page and API share one origin):"
echo "  console  http://127.0.0.1:8080"
echo "  LAN      http://<this-host-ip>:8080"
echo "  accounts admin/admin123 · tester/test123 · business/biz123 · ops/ops123"
echo "  execution pages are hidden in main-service mode"
