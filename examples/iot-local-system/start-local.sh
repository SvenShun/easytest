#!/usr/bin/env bash
# Start the local IoT server with real components:
# gateway (HTTP) -> Dubbo provider -> MySQL/Kafka; embedded MQTT broker + Paho subscriber.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SYSTEM="$ROOT/examples/iot-local-system"
mkdir -p "$ROOT/data/iot-local"
ROOT_PW="${EASYTEST_MYSQL_ROOT_PASSWORD:-easytest}"
docker exec easytest-mysql mysql -uroot -p"$ROOT_PW" -e "
  CREATE DATABASE IF NOT EXISTS iot_local CHARACTER SET utf8mb4;
  CREATE USER IF NOT EXISTS 'iot'@'%' IDENTIFIED BY 'iot';
  GRANT ALL PRIVILEGES ON iot_local.* TO 'iot'@'%';
  FLUSH PRIVILEGES;" >/dev/null 2>&1 || true

for name in service gateway; do
  if [ -f "$ROOT/data/iot-local/$name.pid" ]; then
    kill "$(cat "$ROOT/data/iot-local/$name.pid")" 2>/dev/null || true
  fi
done
sleep 1
nohup java -jar "$SYSTEM/iot-service/target/iot-service-1.0.0.jar" >"$ROOT/data/iot-local/service.log" 2>&1 &
echo $! > "$ROOT/data/iot-local/service.pid"
sleep 5
nohup java -jar "$SYSTEM/iot-gateway/target/iot-gateway-1.0.0.jar" >"$ROOT/data/iot-local/gateway.log" 2>&1 &
echo $! > "$ROOT/data/iot-local/gateway.pid"
sleep 5
curl -fsS -X POST http://127.0.0.1:18080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"tester","password":"secret"}' >/dev/null
echo "IoT gateway: http://127.0.0.1:18080"
echo "Dubbo provider: 127.0.0.1:20880"
echo "MQTT broker: 127.0.0.1:1883"
echo "MySQL: iot_local"
