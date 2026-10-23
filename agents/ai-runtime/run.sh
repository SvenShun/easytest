#!/usr/bin/env bash
# Start the TestHub AI Runtime on port 8095 (override with PORT).
set -euo pipefail
cd "$(dirname "$0")"
exec .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8095}"
