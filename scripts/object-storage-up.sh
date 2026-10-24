#!/usr/bin/env bash
# Start the MinIO object store used by TESTHUB_ARTIFACT_STORE=minio.
# It is part of deploy/docker-compose.infra.yml.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose -f deploy/docker-compose.infra.yml up -d minio
for _ in $(seq 1 40); do
  if curl -fsS --max-time 2 http://127.0.0.1:9000/minio/health/live >/dev/null 2>&1; then
    echo "minio is up on http://127.0.0.1:9000 (console :9001)"; exit 0
  fi
  sleep 2
done
echo "minio did not become healthy" >&2; exit 1
