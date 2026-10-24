#!/usr/bin/env bash
# Fetch pinned upstream sources for the MeterSphere / Sonic integration work.
#
#   scripts/fetch-upstream.sh --metadata   # commit metadata only (fast, ~300KB)
#   scripts/fetch-upstream.sh --sparse     # backend modules + poms (recommended)
#   scripts/fetch-upstream.sh --full       # entire checkout (large; needs bandwidth)
#
# upstream/ is gitignored: we reference upstream source, we do not vendor it.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---metadata}"
DEST="$ROOT/upstream/metersphere"
COMMIT="${METERSPHERE_COMMIT:-53df526e2fb08bdf933662acf7eb5ae0f1bd5e3a}"
REPO="${METERSPHERE_REPO:-https://github.com/metersphere/metersphere.git}"

mkdir -p "$ROOT/upstream"
if [ ! -d "$DEST/.git" ]; then
  echo "==> initialising blobless clone at $DEST"
  git init -q "$DEST"
  git -C "$DEST" remote add origin "$REPO"
fi
if ! git -C "$DEST" cat-file -e "$COMMIT^{commit}" 2>/dev/null; then
  echo "==> fetching $COMMIT (metadata only)"
  git -C "$DEST" fetch --depth 1 --filter=blob:none origin "$COMMIT"
fi
git -C "$DEST" checkout -q --detach "$COMMIT" 2>/dev/null || true

case "$MODE" in
  --metadata)
    echo "metadata ready; read files with: git -C upstream/metersphere show HEAD:<path>"
    ;;
  --sparse)
    echo "==> sparse checkout (backend/services + framework)"
    git -C "$DEST" sparse-checkout init --cone
    git -C "$DEST" sparse-checkout set \
      backend/pom.xml backend/app backend/framework backend/services/case-management \
      backend/services/test-plan backend/services/api-test backend/services/system-setting
    git -C "$DEST" checkout -q "$COMMIT"
    echo "sparse checkout ready (may take a while on slow networks)"
    ;;
  --full)
    echo "==> full checkout (large, this can take a long time)"
    git -C "$DEST" sparse-checkout disable 2>/dev/null || true
    git -C "$DEST" checkout -q "$COMMIT"
    ;;
  *)
    echo "unknown option: $MODE" >&2
    exit 2
    ;;
esac

echo
echo "commit : $(git -C "$DEST" rev-parse HEAD)"
echo "license:"
git -C "$DEST" show HEAD:LICENSE 2>/dev/null | head -6
echo
echo "NOTE: MeterSphere is GPLv3 with additional terms; review before distributing derived work."
