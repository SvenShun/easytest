#!/usr/bin/env bash
# End-to-end acceptance run. Assumes services are up (scripts/dev-up.sh).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 scripts/make-demo-scripts.py
exec python3 scripts/e2e.py
