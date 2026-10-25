#!/usr/bin/env bash
# Install the source-level ui-execution module into a MeterSphere source tree.
#
#   scripts/install-metersphere-module.sh [metersphere-source-root]
#
# Default root: upstream/metersphere (gitignored). The script is idempotent:
# it copies integration/metersphere/ui-execution and patches the reactor POMs
# plus FilterChainUtils (agent callback paths).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MS_SRC="${1:-$ROOT/upstream/metersphere}"

if [ ! -f "$MS_SRC/backend/pom.xml" ]; then
  echo "MeterSphere source not found at $MS_SRC" >&2
  exit 1
fi

echo "==> installing module into $MS_SRC"
rm -rf "$MS_SRC/backend/services/ui-execution"
cp -R "$ROOT/integration/metersphere/ui-execution" "$MS_SRC/backend/services/ui-execution"

python3 - "$MS_SRC" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])

# 1. reactor module
p = root / "backend/services/pom.xml"
s = p.read_text()
if "<module>ui-execution</module>" not in s:
    s = s.replace("        <module>dashboard</module>",
                  "        <module>dashboard</module>\n        <module>ui-execution</module>")
    p.write_text(s)
    print("patched backend/services/pom.xml")
else:
    print("backend/services/pom.xml already patched")

# 2. app dependency
p = root / "backend/app/pom.xml"
s = p.read_text()
if "metersphere-ui-execution" not in s:
    s = s.replace("""        <dependency>
            <groupId>io.metersphere</groupId>
            <artifactId>metersphere-dashboard</artifactId>""",
"""        <dependency>
            <groupId>io.metersphere</groupId>
            <artifactId>metersphere-ui-execution</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>io.metersphere</groupId>
            <artifactId>metersphere-dashboard</artifactId>""")
    p.write_text(s)
    print("patched backend/app/pom.xml")
else:
    print("backend/app/pom.xml already patched")

# 3. anonymous agent callback paths
p = root / "backend/framework/sdk/src/main/java/io/metersphere/sdk/util/FilterChainUtils.java"
s = p.read_text()
marker = 'filterChainDefinitionMap.put("/anonymous/**", "anon");'
if "/test-plan/ui-execution/tasks/*/steps" not in s:
    if marker not in s:
        raise SystemExit("cannot find /anonymous/** in FilterChainUtils.java")
    s = s.replace(marker, marker + """

        // UI execution: device-agent callbacks authenticate with X-Agent-Token
        filterChainDefinitionMap.put("/test-plan/ui-execution/tasks/*/steps", "anon");
        filterChainDefinitionMap.put("/test-plan/ui-execution/tasks/*/result", "anon");
        filterChainDefinitionMap.put("/test-plan/ui-execution/tasks/*/logs", "anon");""")
    p.write_text(s)
    print("patched FilterChainUtils.java")
else:
    print("FilterChainUtils.java already patched")
PY

echo
echo "Installed. Build with:"
echo "  JAVA_HOME=<jdk21> mvn -Dmaven.repo.local=$ROOT/.m2/repository -f $MS_SRC/backend/pom.xml -DskipTests install"
