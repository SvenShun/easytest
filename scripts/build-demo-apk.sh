#!/usr/bin/env bash
# Build the tiny TestHub Demo APK (login screen) with the Android SDK tools.
# Requires ANDROID_JAR and BUILD_TOOLS (default: tools/android/*), or pass env vars.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO="$ROOT/examples/android-demo"
ANDROID_JAR="${ANDROID_JAR:-$ROOT/tools/android/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-$ROOT/tools/android/build-tools}"
OUT="${1:-$DEMO/testhub-demo.apk}"
for tool in aapt2 d8 zipalign apksigner; do
  [ -x "$BUILD_TOOLS/$tool" ] || { echo "missing $BUILD_TOOLS/$tool (set BUILD_TOOLS)" >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "missing android.jar (set ANDROID_JAR)" >&2; exit 1; }
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/compiled" "$WORK/gen" "$WORK/classes" "$WORK/dex"
"$BUILD_TOOLS/aapt2" compile --dir "$DEMO/res" -o "$WORK/compiled" >/dev/null
"$BUILD_TOOLS/aapt2" link -o "$WORK/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$DEMO/AndroidManifest.xml" -R "$WORK/compiled"/*.flat --java "$WORK/gen" >/dev/null
javac -source 8 -target 8 -nowarn -cp "$ANDROID_JAR" -d "$WORK/classes" \
  "$DEMO/src/com/testhub/demo/LoginActivity.java" "$WORK/gen/com/testhub/demo/R.java" 2>/dev/null
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes/com/testhub/demo/"*.class >/dev/null
(cd "$WORK/dex" && zip -q "$WORK/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
KEYSTORE="${DEMO_KEYSTORE:-$WORK/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -alias androiddebugkey \
    -keypass android -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 \
    -validity 10000 >/dev/null 2>&1
fi
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT" "$WORK/aligned.apk"
echo "built $OUT"
