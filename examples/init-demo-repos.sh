#!/usr/bin/env bash
# Re-create the two independent demo engineering Git repositories from the
# tracked source trees (useful after cloning easytest from GitHub).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for name in engineering-api-repo engineering-ui-repo; do
  dir="$ROOT/examples/$name"
  if [ -d "$dir/.git" ]; then
    echo "$name already initialized"
    continue
  fi
  (cd "$dir"
   git init -q -b main
   git config user.email "easytest-demo@local"
   git config user.name "EasyTest Demo"
   git add .
   git commit -qm "demo: $name")
  echo "$name initialized at $dir"
done
echo "Platform URL: file://$ROOT/examples/engineering-api-repo"
echo "Platform URL: file://$ROOT/examples/engineering-ui-repo"
