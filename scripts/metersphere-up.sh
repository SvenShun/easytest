#!/usr/bin/env bash
# Run the locally built MeterSphere backend (source at upstream/metersphere)
# against the TestHub Docker middleware (MySQL/Redis/Kafka).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MS_SRC="${METERSPHERE_SRC:-$ROOT/upstream/metersphere}"
MS_PORT="${METERSPHERE_PORT:-8082}"
MS_MGMT_PORT="${METERSPHERE_MGMT_PORT:-7171}"
JDK="${JAVA_21_HOME:-/Users/wen/.workbuddy/binaries/java/jdk-21.jdk/Contents/Home}"
JAR="$(ls "$MS_SRC"/backend/app/target/app-*.jar 2>/dev/null | grep -v original | head -1 || true)"

if [ ! -x "$JDK/bin/java" ]; then
  echo "JDK 21 not found at $JDK; set JAVA_21_HOME" >&2; exit 1
fi
if [ -z "$JAR" ]; then
  echo "MeterSphere jar not found. Build it first:" >&2
  echo "  cd $MS_SRC && JAVA_HOME=$JDK mvn -Dmaven.repo.local=$ROOT/.m2/repository -f backend/pom.xml -DskipTests package" >&2
  exit 1
fi
if ! docker exec testhub-mysql mysqladmin ping -h127.0.0.1 -ptesthub >/dev/null 2>&1; then
  echo "MySQL is not running. Start it with ./scripts/infra-up.sh" >&2; exit 1
fi
docker exec testhub-mysql mysql -uroot -ptesthub -e \
  "CREATE DATABASE IF NOT EXISTS metersphere CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" 2>/dev/null

mkdir -p logs data/pids data/metersphere-logs
if [ -f data/pids/metersphere.pid ] && kill -0 "$(cat data/pids/metersphere.pid)" 2>/dev/null; then
  echo "MeterSphere already running (pid $(cat data/pids/metersphere.pid))"; exit 0
fi

echo "[start] MeterSphere $JAR on :$MS_PORT (management :$MS_MGMT_PORT)"
export LOG_DIR="$ROOT/data/metersphere-logs"
nohup "$JDK/bin/java" -Xms512m -Xmx2g -jar "$JAR" \
  --logging.config="file:$ROOT/deploy/metersphere/logback-spring.xml" \
  --server.port="$MS_PORT" \
  --management.server.port="$MS_MGMT_PORT" \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/metersphere?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectionCollation=utf8mb4_general_ci" \
  --spring.datasource.username=root \
  --spring.datasource.password=testhub \
  --spring.kafka.bootstrap-servers=127.0.0.1:9092 \
  --spring.data.redis.host=127.0.0.1 \
  --spring.data.redis.port=6379 \
  --spring.redis.redisson.file="file:$ROOT/deploy/metersphere/redisson.yml" \
  --logging.file.path="$ROOT/data/metersphere-logs" \
  --jmeter.home="$MS_SRC/backend/app/src/main/resources/jmeter" \
  --minio.endpoint=http://127.0.0.1:9000 \
  --minio.access-key=testhub \
  --minio.secret-key=testhub123 \
  > logs/metersphere.log 2>&1 &
echo $! > data/pids/metersphere.pid

echo -n "waiting for MeterSphere"
for _ in $(seq 1 120); do
  if nc -z 127.0.0.1 "$MS_PORT" >/dev/null 2>&1; then echo " ok"; break; fi
  echo -n "."; sleep 2
done
if ! nc -z 127.0.0.1 "$MS_PORT" >/dev/null 2>&1; then
  echo; echo "MeterSphere failed to start; see logs/metersphere.log" >&2; tail -30 logs/metersphere.log >&2; exit 1
fi
echo
echo "MeterSphere API: http://127.0.0.1:$MS_PORT"
echo "Management:     http://127.0.0.1:$MS_MGMT_PORT"
echo "Logs:           logs/metersphere.log"
