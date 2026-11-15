#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
docker compose -f deploy/docker-compose.yml ps
for url in http://127.0.0.1:8080/actuator/health http://127.0.0.1:8081/actuator/health \
           http://127.0.0.1:8084/actuator/health http://127.0.0.1:18080/api/auth/login; do
  printf '%-45s' "$url"
  curl -fsS --max-time 2 "$url" >/dev/null 2>&1 && echo UP || echo DOWN
done
