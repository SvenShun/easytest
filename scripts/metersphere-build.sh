#!/usr/bin/env bash
# Build the provided MeterSphere source (upstream/metersphere) with JDK 21.
# The source is copied from /Users/wen/Workspace/TestPlatform/metersphere/metersphere-3.x.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MS_SRC="${METERSPHERE_SRC:-$ROOT/upstream/metersphere}"
JDK="${JAVA_21_HOME:-/Users/wen/.workbuddy/binaries/java/jdk-21.jdk/Contents/Home}"

if [ ! -f "$MS_SRC/pom.xml" ]; then
  echo "MeterSphere source not found at $MS_SRC" >&2
  echo "Copy it from /Users/wen/Workspace/TestPlatform/metersphere/metersphere-3.x" >&2
  exit 1
fi
if [ ! -x "$JDK/bin/java" ]; then
  echo "JDK 21 not found at $JDK; set JAVA_21_HOME" >&2; exit 1
fi

cd "$MS_SRC"
# The antrun step copies frontend/dist into test resources; the directory must
# exist even when only the backend is built.
mkdir -p frontend/dist
export JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH"

echo "==> MeterSphere parent pom"
mvn -B -Dmaven.repo.local="$ROOT/.m2/repository" -N install
echo "==> MeterSphere backend (skip tests)"
mvn -B -Dmaven.repo.local="$ROOT/.m2/repository" -f backend/pom.xml -DskipTests install

echo
echo "built: $MS_SRC/backend/app/target/app-3.x.jar"
