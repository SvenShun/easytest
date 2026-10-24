#!/usr/bin/env bash
# Stop the middleware started by scripts/infra-up.sh (keeps the data volumes).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose -f deploy/docker-compose.infra.yml down
