#!/usr/bin/env bash
# Run every automated check: Java tests, Python tests, web build.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Java tests"
./scripts/mvn.sh -q test

if [ -x agents/ai-runtime/.venv/bin/pytest ]; then
  echo "==> Python tests"
  (cd agents/ai-runtime && .venv/bin/pytest -q)
else
  echo "!! ai-runtime venv missing, skipping python tests"
fi

echo "==> Web build"
(cd web && npm run build)

echo "all checks passed"
