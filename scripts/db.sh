#!/usr/bin/env bash
# Open a MySQL shell inside the EasyTest MySQL container.
#   ./scripts/db.sh            -> easytest_core (main service)
#   ./scripts/db.sh uiexec     -> easytest_uiexec
#   ./scripts/db.sh metersphere
#   ./scripts/db.sh <dbname>
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DB="${1:-easytest_core}"
case "$DB" in
  core) DB=easytest_core ;;
  uiexec|ui) DB=easytest_uiexec ;;
  ms) DB=metersphere ;;
esac

if ! docker exec easytest-mysql mysqladmin ping -h127.0.0.1 -peasytest >/dev/null 2>&1; then
  echo "MySQL container easytest-mysql is not running. Start it with: make infra" >&2
  exit 1
fi
exec docker exec -it easytest-mysql mysql -uroot -peasytest "$DB"
