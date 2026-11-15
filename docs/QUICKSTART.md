# EasyTest Docker 快速启动

## 前置

- Docker + Docker Compose
- 首次构建需要能拉取 `public.ecr.aws` 基础镜像和 Maven 依赖

## 启动整栈

```bash
./scripts/stack-up.sh
./scripts/stack-status.sh
```

首次会构建：

- core / api-execution / ui-execution / ai-runtime
- device-agent（Android/iOS 模拟/ADB）
- web-agent（HtmlUnit）
- 本地 IoT gateway / Dubbo service / MQTT broker
- MySQL/Redis/MinIO/Kafka
- web 前端与 Web Demo

平台：

```text
http://127.0.0.1:5173
```

种子：

```bash
./scripts/seed-demo.sh
```

## Docker 内验证

### API Demo

打开 API 场景：

- `IoT Docker single login`
- `IoT Docker mysql seed`
- `IoT Docker e2e`

三者均通过：

```text
SUCCESS 1/1 steps passed
```

### Web Demo

设备池有 `web-1`（platform=web）。创建 STEP 用例：

```yaml
- stepType: LAUNCH
  value: http://web-demo/
- stepType: INPUT
  selectorType: ID
  selector: username
  value: tester
- stepType: CLICK
  selectorType: TEXT
  selector: 登录
- stepType: ASSERT_TEXT
  selectorType: TEXT
  selector: 登录成功
```

计划触发时选择 **设备 ID**（不是 serial），可在设备池/API 获取：

```bash
curl -s http://127.0.0.1:8081/api/v1/ui-execution/devices
```

## 停止

```bash
./scripts/stack-down.sh
```

## 多机

中心机运行完整栈；工作机使用：

```bash
docker compose -f deploy/docker-compose.agent-node.yml up -d --build
```

配置 `CORE_URL`、`UIEXEC_URL`、共享 MySQL/Redis/Kafka/MinIO 即可。
