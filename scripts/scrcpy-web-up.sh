#!/usr/bin/env bash
# Start ws-scrcpy (browser scrcpy stream) on :8000 using the workspace adb.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/tools/ws-scrcpy"
if [ ! -f "$DIR/dist/index.js" ]; then
  echo "ws-scrcpy not built; run make scrcpy-setup" >&2
  exit 1
fi
if lsof -nP -iTCP:8000 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ws-scrcpy already listening on :8000"
  exit 0
fi
mkdir -p "$DIR/dist" "$ROOT/data/pids"
cat > "$DIR/dist/config.yaml" <<'YAML'
runGoogTracker: true
runApplTracker: false
server:
  - secure: false
    port: 8000
YAML
export PATH="$ROOT/tools/platform-tools:$PATH"
export NODE_PATH="$DIR/node_modules"
cd "$DIR/dist"
nohup node index.js > "$ROOT/logs/scrcpy-web.log" 2>&1 &
echo $! > "$ROOT/data/pids/scrcpy-web.pid"
for _ in $(seq 1 20); do
  if curl -fsS --max-time 2 http://127.0.0.1:8000/ >/dev/null 2>&1; then
    echo "ws-scrcpy is up: http://127.0.0.1:8000 (pid $(cat "$ROOT/data/pids/scrcpy-web.pid"))"
    exit 0
  fi
  sleep 1
done
echo "ws-scrcpy failed; see logs/scrcpy-web.log" >&2
tail -20 "$ROOT/logs/scrcpy-web.log" >&2
exit 1
