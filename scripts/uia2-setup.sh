#!/usr/bin/env bash
# Copy Sonic's Appium UIA2 server APKs into tools/uia2 for device-agent.
# Usage: SONIC_HOME=/path/to/sonic ./scripts/uia2-setup.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SONIC_HOME="${SONIC_HOME:-/Users/wen/Workspace/TestPlatform/sonic}"
PLUGINS="${SONIC_UA2_PLUGINS:-$SONIC_HOME/sonic-agent-main/plugins}"
mkdir -p "$ROOT/tools/uia2"
for name in sonic-appium-uiautomator2-server.apk sonic-appium-uiautomator2-server-test.apk; do
  src="$PLUGINS/$name"
  if [ ! -f "$src" ]; then
    echo "missing $src" >&2
    echo "Set SONIC_HOME/SONIC_UA2_PLUGINS to a Sonic checkout with agent plugins/." >&2
    exit 1
  fi
  cp "$src" "$ROOT/tools/uia2/$name"
done
echo "UIA2 APKs copied to tools/uia2"
