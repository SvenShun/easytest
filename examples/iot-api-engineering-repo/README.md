# IoT API Engineering Example

分层接口自动化测试工程示例：Spring Boot + Kafka + Redis + MySQL 微服务、gateway HTTP、内部 RPC、MQTT 设备链路。

## 分层

```text
flow/    IotFlows                 业务语义：单接口 & 端到端
action/  DeviceOnboardingAction   组合操作：HTTP + MQTT + Kafka
atom/    HttpGatewayAtom          HTTP 原子
         MysqlSeedAtom            MySQL 原子
         KafkaEventAtom           Kafka 原子
         MqttCommandAtom          MQTT 原子
         RpcDeviceAtom            RPC 原子（SPI）
config/  IotConfig                各应用连接配置
```

## 运行

```bash
# 安装 EasyTest contract-api 和 engineering-sdk 到本地 Maven
./scripts/mvn.sh -q -pl contract-api,engineering-sdk -DskipTests install

cd examples/iot-api-engineering-repo
mvn -q -DskipTests package
cp target/iot-api-engineering-1.0.0.jar target/iot-api-engineering.jar
```

把 JAR 放到 API Agent 的 `data/engineering/`，或通过平台 Git 工程扫描自动部署。

## 配置

```text
IOT_GATEWAY_URL=http://gateway:8000
IOT_MYSQL_URL=jdbc:mysql://mysql:3306/iot_home
IOT_KAFKA_BOOTSTRAP=kafka:9092
IOT_MQTT_HOST=mqtt
IOT_DUBBO_REGISTRY=zookeeper://zk:2181
```

RPC 通过 `RpcAtom` SPI 绑定；生产接入 `IotDubboRpcInvoker`（需要 Dubbo runtime）。

## 用例策略

- 单接口：网关登录/设备查询等，DB 造数通过 `MysqlSeedAtom`
- 端到端：从 gateway 登录开始，经 HTTP -> MQTT -> Kafka 验证异步链路
- action/atom 与平台共享：平台 API 场景可直接用 component 步骤调用
