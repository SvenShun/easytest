# 本地 IoT 被测系统

真实本地链路（不含物理设备）：

```text
HTTP client
  -> iot-gateway :18080
  -> Dubbo RPC :20880
  -> iot-service
       -> MySQL iot_local
       -> Kafka iot-device-events
       -> embedded Moquette MQTT :1883
       -> Paho MQTT subscriber (模拟 device 微服务接收命令)
```

## 组件

- `iot-gateway`：Spring Boot HTTP gateway + Dubbo consumer
- `iot-service`：Dubbo provider、MySQL、Kafka producer、嵌入式 MQTT broker、MQTT subscriber
- `iot-api`：Dubbo facade/DTO
- MySQL / Kafka 复用本地 Easytest Docker

## 启动

```bash
# 先确保 MySQL/Kafka 容器在运行
./scripts/dev-up.sh

./scripts/mvn.sh -q -f examples/iot-local-system/pom.xml -DskipTests package
./examples/iot-local-system/start-local.sh
```

验证：

```bash
curl -s -X POST http://127.0.0.1:18080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"tester","password":"secret"}'
```

## 测试工程

`examples/iot-api-engineering-repo` 默认配置：

```text
IOT_GATEWAY_URL=http://127.0.0.1:18080
IOT_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/iot_local
IOT_KAFKA_BOOTSTRAP=127.0.0.1:9092
IOT_MQTT_HOST=127.0.0.1
IOT_MQTT_PORT=1883
```

平台 API 场景已可运行：

- 单接口：gateway 登录
- MySQL：造数 + 查询
- 端到端：HTTP 绑定 -> MQTT 命令 -> Kafka 事件

## MQTT 说明

不需要物理设备；`DeviceMqttListener` 作为 device 微服务订阅 `iot/device/+/command`，
收到命令后写 `iot_local.device_command`、更新设备状态并发送 Kafka 事件。

## 端口

- 18080 gateway
- 20880 Dubbo
- 1883 MQTT
- 18081 service management
