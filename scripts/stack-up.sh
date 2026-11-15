#!/usr/bin/env bash
# One-command full Docker stack: platform + local IoT server + middleware.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if ! docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
  echo "Docker daemon is not running." >&2
  exit 1
fi
# Build the demo engineering JAR if missing (compose mounts it into API agent).
if [ ! -f data/engineering/iot-api-engineering.jar ]; then
  echo "[build] demo IoT engineering JAR"
  mkdir -p data/engineering
  ./scripts/mvn.sh -q -pl contract-api,engineering-sdk -DskipTests install
  ./scripts/mvn.sh -q -f examples/iot-api-engineering-repo/pom.xml -DskipTests package
  cp examples/iot-api-engineering-repo/target/iot-api-engineering-1.0.0.jar data/engineering/iot-api-engineering.jar
fi
if [ ! -f deploy/.env ]; then
  cp deploy/.env.example deploy/.env
fi
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
echo "EasyTest stack is starting..."
echo "  platform web: http://127.0.0.1:5173"
echo "  core:         http://127.0.0.1:8080"
echo "  IoT gateway:  http://127.0.0.1:18080"
echo "  MQTT:         127.0.0.1:1883"
echo
echo "Wait for health: ./scripts/stack-status.sh"
echo "Stop: ./scripts/stack-down.sh"
