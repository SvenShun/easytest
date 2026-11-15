#!/usr/bin/env bash
# Starts local HTTP/MQTT mocks and initializes the IoT MySQL schema.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$ROOT/data/iot-mock"
if ! pgrep -f "examples/iot-mock/mock_gateway.py" >/dev/null; then
  nohup python3 "$ROOT/examples/iot-mock/mock_gateway.py" >"$ROOT/data/iot-mock/gateway.log" 2>&1 &
  echo $! > "$ROOT/data/iot-mock/gateway.pid"
fi
if ! pgrep -f "examples/iot-mock/mock_mqtt.py" >/dev/null; then
  nohup python3 "$ROOT/examples/iot-mock/mock_mqtt.py" >"$ROOT/data/iot-mock/mqtt.log" 2>&1 &
  echo $! > "$ROOT/data/iot-mock/mqtt.pid"
fi
echo "HTTP gateway mock: http://127.0.0.1:18080"
echo "MQTT mock: 127.0.0.1:1883"
echo "Logs: $ROOT/data/iot-mock"
