#!/usr/bin/env bash
# Start a second api-execution instance on :8085 sharing the Redis queue.
# Prerequisite: the main stack is running with EASYTEST_INFRA=docker (Redis queue).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if lsof -nP -iTCP:8085 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 8085 is already in use." >&2; exit 1
fi
if ! docker exec easytest-redis redis-cli ping >/dev/null 2>&1; then
  echo "Redis (easytest-redis) is required for the shared queue; run make infra." >&2; exit 1
fi
if ! curl -fsS --max-time 3 http://127.0.0.1:8084/actuator/health >/dev/null 2>&1; then
  echo "api-execution :8084 is not running; run make up first." >&2; exit 1
fi
JAR="$(ls api-execution/target/api-execution-*.jar 2>/dev/null | grep -v original | head -1 || true)"
if [ -z "$JAR" ]; then
  ./scripts/mvn.sh -q -pl api-execution -am -DskipTests package
  JAR="$(ls api-execution/target/api-execution-*.jar | grep -v original | head -1)"
fi
mkdir -p logs data/pids
SERVER_PORT=8085 \
SPRING_PROFILES_ACTIVE=mysql \
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/easytest_apiexec?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8' \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=easytest \
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=6379 \
EASYTEST_JWT_SECRET="${EASYTEST_JWT_SECRET:-easytest-dev-jwt-secret-change-me-0123456789abcdef}" \
EASYTEST_SERVICE_TOKEN="${EASYTEST_SERVICE_TOKEN:-easytest-dev-service-token}" \
EASYTEST_AGENT_TOKEN="${EASYTEST_AGENT_TOKEN:-easytest-dev-agent-token}" \
EASYTEST_ENCRYPTION_KEY="${EASYTEST_ENCRYPTION_KEY:-easytest-dev-encryption-key-change-me}" \
EASYTEST_CORE_URL='http://127.0.0.1:8080' \
EASYTEST_APIEXEC_PUBLIC_URL='http://127.0.0.1:8085' \
EASYTEST_APIEXEC_QUEUE=redis \
EASYTEST_APIEXEC_INSTANCE_ID=apiexec-local-2 \
EASYTEST_APIEXEC_WORKERS="${EASYTEST_APIEXEC_WORKERS:-2}" \
nohup java -jar "$JAR" > logs/apiexec-2.log 2>&1 &
echo $! > data/pids/apiexec-2.pid
for _ in $(seq 1 40); do
  if curl -fsS --max-time 2 http://127.0.0.1:8085/actuator/health >/dev/null 2>&1; then
    echo "api-execution #2 is up: http://127.0.0.1:8085 (pid $(cat data/pids/apiexec-2.pid))"
    exit 0
  fi
  sleep 1
done
echo "api-execution #2 failed; see logs/apiexec-2.log" >&2
tail -20 logs/apiexec-2.log >&2
exit 1
