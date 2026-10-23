#!/usr/bin/env bash
# Install all dependencies: Maven modules (workspace-local repo) + Python venv + web node_modules.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Maven modules"
./scripts/mvn.sh -q -DskipTests install

echo "==> Python AI Runtime"
python3 -m venv agents/ai-runtime/.venv
agents/ai-runtime/.venv/bin/pip install -q --upgrade pip
agents/ai-runtime/.venv/bin/pip install -q -r agents/ai-runtime/requirements.txt

echo "==> Web console"
export npm_config_cache="$ROOT/.npm-cache"
(cd web && npm install --no-fund --no-audit)

echo "done"
