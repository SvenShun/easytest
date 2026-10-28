#!/usr/bin/env bash
# Start the unmodified Sonic server sources (built in upstream/sonic) against
# the local MySQL, and expose the built Sonic web UI on :3001.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
SRC="$ROOT/upstream/sonic/sonic-server-main"
JAVA="${JAVA_17_HOME:-/Users/wen/Library/Java/jdk-17.0.2.jdk/Contents/Home}/bin/java"
export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_DATABASE=sonic MYSQL_USERNAME=root MYSQL_PASSWORD=testhub
export SONIC_EUREKA_USERNAME=sonic SONIC_EUREKA_PASSWORD=sonic SONIC_EUREKA_PORT=8761 SONIC_EUREKA_HOST=127.0.0.1
export SONIC_SERVER_HOST=127.0.0.1 SONIC_SERVER_PORT=3000 SECRET_KEY=sonic EXPIRE_DAY=14
export REGISTER_ENABLE=true NORMAL_USER_ENABLE=true LDAP_USER_ENABLE=false PERMISSION_ENABLE=true PERMISSION_SUPER_ADMIN=sonic

mkdir -p logs data/pids
start() {
  local name="$1" jar="$2" port="$3" profiles="$4"
  if [ -f "data/pids/$name.pid" ] && kill -0 "$(cat data/pids/$name.pid)" 2>/dev/null; then
    echo "[skip] $name already running"; return
  fi
  echo "[start] $name :$port"
  nohup "$JAVA" -Xms128m -Xmx512m -jar "$jar" \
    --server.port="$port" --spring.profiles.active="$profiles" \
    > "logs/$name.log" 2>&1 &
  echo $! > "data/pids/$name.pid"
}

start sonic-eureka "$SRC/sonic-server-eureka/target/sonic-server-eureka.jar" 8761 "sonic-server-eureka,logging"
for i in $(seq 1 60); do
  if curl -s -o /dev/null "http://127.0.0.1:8761/eureka/apps" 2>/dev/null; then break; fi
  sleep 1
done
if ! curl -s -o /dev/null --max-time 2 "http://127.0.0.1:8761/eureka/apps"; then
  echo "[fail] Eureka did not start"; tail -20 logs/sonic-eureka.log >&2; exit 1
fi

start sonic-controller "$SRC/sonic-server-controller/target/sonic-server-controller.jar" 0 "sonic-server-controller,eureka,jdbc,feign,logging,user"
start sonic-folder "$SRC/sonic-server-folder/target/sonic-server-folder.jar" 0 "sonic-server-folder,eureka,feign,logging,user"
start sonic-gateway "$SRC/sonic-server-gateway/target/sonic-server-gateway.jar" 3000 "sonic-server-gateway,eureka,logging,user"

for i in $(seq 1 120); do
  if curl -s -o /dev/null "http://127.0.0.1:3000/server/users/loginConfig" 2>/dev/null; then break; fi
  sleep 1
done

if [ -f data/pids/sonic-ui.pid ] && kill -0 "$(cat data/pids/sonic-ui.pid)" 2>/dev/null; then
  echo "[skip] Sonic UI proxy already running"
else
  nohup node "$ROOT/deploy/compare/static-proxy.mjs" 3001 \
    "$ROOT/upstream/sonic/sonic-client-web-main/dist" http://127.0.0.1:3000 /server prefix \
    > logs/sonic-ui.log 2>&1 &
  echo $! > data/pids/sonic-ui.pid
fi

echo "Sonic UI:   http://127.0.0.1:3001"
echo "Sonic API:  http://127.0.0.1:3000/server"
echo "Eureka:     http://127.0.0.1:8761"
