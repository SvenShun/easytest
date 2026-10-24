#!/usr/bin/env bash
# Start the real middleware used by the "docker infra" dev mode.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
  echo "Docker daemon is not running. Start Docker Desktop first." >&2
  exit 1
fi

docker compose -f deploy/docker-compose.infra.yml up -d

echo -n "waiting for mysql"
for _ in $(seq 1 60); do
  if docker exec testhub-mysql mysqladmin ping -h127.0.0.1 -ptesthub >/dev/null 2>&1; then
    echo " ok"; break
  fi
  echo -n "."; sleep 2
done
echo -n "waiting for redis"
for _ in $(seq 1 30); do
  if docker exec testhub-redis redis-cli ping >/dev/null 2>&1; then echo " ok"; break; fi
  echo -n "."; sleep 1
done
echo -n "waiting for minio"
for _ in $(seq 1 40); do
  if curl -fsS --max-time 2 http://127.0.0.1:9000/minio/health/live >/dev/null 2>&1; then echo " ok"; break; fi
  echo -n "."; sleep 2
done

echo -n "waiting for kafka"
for _ in $(seq 1 40); do
  if docker exec testhub-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server 127.0.0.1:9092 >/dev/null 2>&1; then
    echo " ok"; break
  fi
  echo -n "."; sleep 2
done

docker ps --filter name=testhub- --format '  {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo
echo "Start the apps against this stack with:"
echo "  TESTHUB_INFRA=docker ./scripts/dev-up.sh"
echo "Stop with:"
echo "  docker compose -f deploy/docker-compose.infra.yml down"
