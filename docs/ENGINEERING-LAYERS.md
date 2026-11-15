# 接口自动化工程分层与平台共享

## 目标

接口自动化工程不是只会写 YAML，而是承载企业定制代码：

- 被测应用 facade / RPC 接口封装
- 特定场景请求报文模板
- 造数封装（DB / 接口）
- 不同应用的 MySQL/Kafka/MQTT/Redis 连接方式
- 共享的 action / atom

这些代码只维护一套，平台和测试工程共同使用。

## 分层

| 层 | 作用 | 示例 |
| --- | --- | --- |
| Flow | 业务语义用例 | 登录契约、端到端设备控制 |
| Action | 可复用组合操作 | 登录、设备接入、订单创建 |
| Atom | 固定业务操作 | HTTP 登录、SQL 查询、Kafka 发送、MQTT 下发、RPC 查询 |

代码通过 `@EasyContract` 暴露：

```java
@EasyContract(
    id = "iot.atom.http.login",
    name = "网关登录",
    layer = ContractLayer.ATOM,
    protocols = {"HTTP"},
    inputs = {"username", "password"},
    outputs = {"token"}
)
public ContractResult login(ContractContext context) { ... }
```

`layer` 与 `protocols` 会进入：

- 工程方法 manifest
- API Agent 方法目录
- 平台「工程方法」页面
- API 场景 component 步骤的 methodPath 提示

Action/Atom 在平台 API 场景里和 Flow 一样，直接作为 `component` 步骤执行。

方法可声明输入/输出 schema：

```java
@EasyContract(..., inputSchema = "{\"type\":\"object\",\"required\":[\"username\"]}")
```

平台方法目录会显示 `schema 已声明`，API 场景选择方法时带有层级/协议提示。

## 共享 SDK

`engineering-sdk` 与 `contract-api` 一起组成测试工程依赖：

```xml
<dependency>
  <groupId>com.easytest</groupId>
  <artifactId>easytest-contract-api</artifactId>
</dependency>
<dependency>
  <groupId>com.easytest</groupId>
  <artifactId>easytest-engineering-sdk</artifactId>
</dependency>
```

提供的原子能力：

| 能力 | 类 | 说明 |
| --- | --- | --- |
| HTTP | `HttpAtom` | JDK HttpClient，网关/客户端调用 |
| SQL | `JdbcAtom` | JDBC URL，按应用配置连接 |
| Kafka | `KafkaAtom` | produce/consume |
| MQTT | `MqttAtom` | MQTT 3.1.1 QoS0 publish（可替换企业客户端） |
| RPC | `RpcAtom` | SPI，绑定 Dubbo/Feign/gRPC |
| Redis | `RedisAtom` | RESP 简单命令，缓存/Token 断言 |

RPC 通过 `RpcAtom.register(...)` 注入企业实现；示例工程提供反射式 Dubbo `IotDubboRpcInvoker`。

## 平台使用

1. Git 工程仓库扫描 `easytest.api.yaml`
2. 方法目录按 `layer` / `protocols` 展示
3. API 场景新增 `component` 步骤，选择 ATOM/ACTION 方法
4. 保存时校验 methodPath 与 inputs/outputs
5. 执行时由 API Agent 加载工程 JAR 调用

## IoT 智能家居示例

目录：[examples/iot-api-engineering-repo](examples/iot-api-engineering-repo)

假设业务系统（不需要平台实现）：

- Spring Boot + Kafka + Redis + MySQL 微服务
- 外部客户端 -> gateway HTTP
- gateway -> 微服务内部 RPC
- 微服务异步链路 Kafka
- IoT 设备通过 MQTT 与 device 微服务通信

工程分层：

```text
flow/    IotFlows
action/  DeviceOnboardingAction
atom/    HttpGatewayAtom / MysqlSeedAtom / KafkaEventAtom / MqttCommandAtom / RpcDeviceAtom
```

用例策略：

1. **单接口**：网关登录、RPC 设备查询、Kafka/MQTT 原子操作；DB 造数用 `MysqlSeedAtom`
2. **端到端**：接口造数，从 gateway 登录开始，HTTP 绑定 -> MQTT 控制 -> Kafka 事件验证
3. 平台场景复用 action/atom，无需在平台重复实现 HTTP/SQL/MQTT/Kafka/RPC 封装

## 本地 IoT Mock

在没有真实业务系统时，可用本地 mock 跑通 flow：

```bash
./examples/iot-mock/start.sh
docker exec -i easytest-mysql mysql -uroot -peasytest < examples/iot-mock/schema.sql
docker exec easytest-mysql mysql -uroot -peasytest -e "
  CREATE USER IF NOT EXISTS 'iot'@'%' IDENTIFIED BY 'iot';
  GRANT ALL PRIVILEGES ON iot_home.* TO 'iot'@'%'; FLUSH PRIVILEGES;"
```

已验证：

```text
IoT single login  SUCCESS 1/1 steps passed
IoT mysql seed    SUCCESS 1/1 steps passed
IoT e2e           SUCCESS 1/1 steps passed
```

## 构建

```bash
./scripts/mvn.sh -q -pl contract-api,engineering-sdk -DskipTests install
cd examples/iot-api-engineering-repo
mvn -q -DskipTests package
# 产出 fat jar：target/iot-api-engineering.jar
```

将 JAR 放入 API Agent `data/engineering/`，或通过 Git 工程扫描 `artifact` 自动部署。

## 限制

- 本示例不包含真实 IoT 业务服务；端到端执行依赖实际 gateway/Kafka/MQTT/MySQL/RPC 环境
- MQTT 内置客户端只实现 QoS0 publish，生产建议替换为企业 MQTT SDK
- Dubbo 适配器通过反射加载，需在工程 JAR/Agent classpath 中提供 Dubbo runtime
- 平台场景直接选择 action/atom 依赖 Git 工程扫描 + JAR 部署
