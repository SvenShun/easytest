#!/usr/bin/env bash
# Show local service health + a small runtime snapshot.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for pair in "core:8080:/actuator/health" "ui-execution:8081:/actuator/health" \
            "device-agent:8090:/agent/health" "ai-runtime:8095:/runtime/health" \
            "web:5173:/"; do
  name="${pair%%:*}"; rest="${pair#*:}"; port="${rest%%:*}"; path="${rest#*:}"
  if curl -fsS --max-time 2 "http://127.0.0.1:$port$path" >/dev/null 2>&1; then
    echo "  UP    $name (:$port)"
  else
    echo "  DOWN  $name (:$port)"
  fi
done
