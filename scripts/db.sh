#!/usr/bin/env bash
# Open a MySQL shell inside the TestHub MySQL container.
#   ./scripts/db.sh            -> testhub_core (main service)
#   ./scripts/db.sh uiexec     -> testhub_uiexec
#   ./scripts/db.sh metersphere
#   ./scripts/db.sh <dbname>
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DB="${1:-testhub_core}"
case "$DB" in
  core) DB=testhub_core ;;
  uiexec|ui) DB=testhub_uiexec ;;
  ms) DB=metersphere ;;
esac

if ! docker exec testhub-mysql mysqladmin ping -h127.0.0.1 -ptesthub >/dev/null 2>&1; then
  echo "MySQL container testhub-mysql is not running. Start it with: make infra" >&2
  exit 1
fi
exec docker exec -it testhub-mysql mysql -uroot -ptesthub "$DB"
