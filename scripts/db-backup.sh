#!/usr/bin/env bash
# Dump the main-service and UI-execution databases to a local .sql file.
#   ./scripts/db-backup.sh [output.sql]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="${1:-backup-$(date +%Y%m%d-%H%M%S).sql}"
mkdir -p backups
case "$OUT" in /*) TARGET="$OUT" ;; *) TARGET="$ROOT/backups/$OUT" ;; esac
docker exec testhub-mysql mysqldump -uroot -ptesthub --databases testhub_core testhub_uiexec > "$TARGET"
echo "backup written to $TARGET ($(du -h "$TARGET" | cut -f1))"
