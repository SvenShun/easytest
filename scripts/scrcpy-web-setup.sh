#!/usr/bin/env bash
# Clone/build ws-scrcpy (MIT) into tools/ws-scrcpy. Requires Node 18+.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/tools/ws-scrcpy"
mkdir -p "$ROOT/tools"
if [ ! -d "$DIR/.git" ]; then
  git clone --depth 1 https://github.com/NetrisTV/ws-scrcpy.git "$DIR"
fi
export npm_config_cache="$ROOT/tools/npm-cache"
cd "$DIR"
npm ci --ignore-scripts --no-audit --no-fund
# node-pty prebuilt is required by the server module; 1.1.0 ships darwin-arm64.
npm install node-pty@1.1.0 --no-save --no-audit --no-fund
npm run dist:prod
mkdir -p "$DIR/dist"
cat > "$DIR/dist/config.yaml" <<'YAML'
runGoogTracker: true
runApplTracker: false
server:
  - secure: false
    port: 8000
YAML
echo "ws-scrcpy built at $DIR/dist"
