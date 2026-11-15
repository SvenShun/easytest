# TestHub 完整部署手册

本文档面向**另一台电脑从 GitHub 克隆后部署**，覆盖本地演示、推荐的主机部署、完整 Docker Compose、远程访问、升级、备份和排障。

- 仓库：<https://github.com/SvenShun/easytest>
- 默认分支：`main`
- 当前架构：Spring Boot 3 / Java 17 + React + MySQL/Redis/MinIO/Kafka
- 适用环境：macOS（Intel/Apple Silicon）、Linux x86_64/arm64、Windows 10/11 + WSL2

> 先读第 1 节的端口和账号，再按第 3 节选择部署模式。首次部署推荐 **模式 B：Docker 中间件 + 宿主机 Java 服务**。

---

## 1. 架构、端口和账号

### 1.1 服务

| 服务 | 目录 | 默认端口 | 用途 |
|---|---|---:|---|
| core | `testhub-server/` | 8080 | 用户、用例、计划、报告、API 场景、静态 Web |
| ui-execution | `ui-execution/` | 8081 | UI 执行编排、设备 FIFO、轨迹 |
| api-execution | `api-execution/` | 8084 | 接口/API 场景执行、断言、回调 |
| device-agent | `device-agent/` | 8090 | 设备 Agent、ADB、脚本执行 |
| ai-runtime | `agents/ai-runtime/` | 8095 | AI Runtime（Python） |
| web | `web/` | 8080（构建后由 core 提供）或 5173（Vite 开发） | React 控制台 |
| ws-scrcpy（可选） | `tools/ws-scrcpy/` | 8000 | 真机实时画面 |

中间件默认端口：

| 组件 | 端口 | 默认账号 |
|---|---:|---|
| MySQL 8 | 3306 | `root / testhub`（仅开发默认） |
| Redis 7 | 6379 | 无密码（仅开发默认） |
| MinIO | 9000 / 9001 | `testhub / testhub123` |
| Kafka 3.8 | 9092 | 无认证 |

数据库名：`testhub_core`、`testhub_uiexec`、`testhub_apiexec`。首次启动由 `deploy/mysql-init/01-databases.sql` 创建。

### 1.2 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `tester` | `test123` | TESTER |
| `business` | `biz123` | BUSINESS |
| `ops` | `ops123` | OPS |

> 这些是演示种子账号。部署到局域网或公网前必须修改密码和密钥，见第 8 节。

---

## 2. 前置条件

### 2.1 必需

| 依赖 | 版本 | 检查命令 |
|---|---|---|
| Git | 2.30+ | `git --version` |
| JDK | **17** | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20 或 22 | `node -v` |
| npm | 10+ | `npm -v` |
| Python | 3.11+ | `python3 --version` |
| curl / lsof / make | 系统自带或安装 | `curl --version` |

macOS 安装示例：

```bash
brew install openjdk@17 maven node python@3.11
```

Ubuntu/Debian 示例：

```bash
sudo apt update
sudo apt install -y git openjdk-17-jdk maven nodejs npm python3 python3-venv make curl lsof
```

### 2.2 Docker（模式 B/C 必需）

- Docker Desktop（macOS/Windows）或 Docker Engine + Compose v2（Linux）
- 检查：

```bash
docker --version
docker compose version
docker info
```

模式 A 使用 H2 + 内存队列 + 模拟设备，不需要 Docker。

### 2.3 可选

- Android `adb` + USB 真机：真实设备执行时需要；没有真机可用 `simulated` 模拟设备。
- JDK 21：只有部署 MeterSphere 对比环境才需要；TestHub 本身不需要。
- Python 虚拟环境由 `make setup` 自动创建。

### 2.4 端口和资源

默认需要以下端口空闲：`8080 8081 8084 8090 8095 3306 6379 9000 9001 9092`；用 Vite 开发时再加 `5173`，实时画面再加 `8000`。

建议至少 8 GB 内存、10 GB 可用磁盘；MySQL、镜像和构建缓存会占用较多磁盘。

检查端口：

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:3306 -sTCP:LISTEN
```

---

## 3. 获取代码

SSH（仓库已配置 SSH remote）：

```bash
git clone git@github.com:SvenShun/easytest.git
cd easytest
git checkout main
git pull --ff-only
```

没有配置 SSH 时用 HTTPS：

```bash
git clone https://github.com/SvenShun/easytest.git
cd easytest
```

确认版本：

```bash
git log -1 --oneline
```

### 3.1 安装依赖

```bash
make setup
```

`make setup` 做三件事：

1. 用工作区本地 Maven 仓库 `.m2/repository` 安装所有 Java 模块；
2. 创建 `agents/ai-runtime/.venv` 并安装 Python 依赖；
3. 安装 Web 依赖并执行一次生产构建，生成 `web/dist`。

Maven 使用 `./scripts/mvn.sh`，不需要改动全局 `~/.m2`。首次运行需要访问 Maven/ npm 仓库，耗时取决于网络。

如果只想启动主服务（用户/用例/计划，不启动执行服务），可以用：

```bash
make main-up
```

主服务页面：`http://127.0.0.1:8080`。

---

## 4. 选择部署模式

### 模式 A：纯本地演示（H2 + 内存队列，无需 Docker）

适合第一次体验、离线演示和开发。

```bash
make setup
make up
```

访问：

- 控制台：<http://127.0.0.1:8080>（`make setup` 已构建 `web/dist`）
- 可选 Vite 开发服务器：`cd web && npm run dev` → <http://127.0.0.1:5173>
- 健康检查：`./scripts/status.sh`

停止：

```bash
make down
```

特性：

- H2 文件库：`data/db/core`、`data/db/uiexec`、`data/db/apiexec`
- 本地文件产物：`data/artifacts`、`data/core-artifacts`
- 内存 FIFO / 内存 API 队列
- 模拟设备 / mock AI（无需真机和模型 Key）
- 所有服务由 `scripts/dev-up.sh` 启动，日志在 `logs/`，PID 在 `data/pids/`

### 模式 B：Docker 中间件 + 宿主机应用（推荐）

适合作为团队测试环境和“另一台电脑”的正式体验环境。

```bash
# 1. 启动 MySQL/Redis/MinIO/Kafka
make infra

# 2. 构建 Java + Web
make build

# 3. 以真实中间件启动全部服务
TESTHUB_INFRA=docker ./scripts/dev-up.sh
```

也可以分步执行：

```bash
./scripts/infra-up.sh
./scripts/mvn.sh -q -DskipTests package
(cd web && npm run build)
TESTHUB_INFRA=docker ./scripts/dev-up.sh
```

访问：

- 控制台：<http://127.0.0.1:8080>
- core API：<http://127.0.0.1:8080>
- ui-execution：<http://127.0.0.1:8081>
- api-execution：<http://127.0.0.1:8084>
- 设备 Agent：<http://127.0.0.1:8090>
- AI Runtime：<http://127.0.0.1:8095>
- MinIO 控制台：<http://127.0.0.1:9001>

停止：

```bash
make down          # 停 TestHub 应用
make infra-down    # 停中间件（默认保留数据卷）
```

> 中间件容器名为 `testhub-mysql`、`testhub-redis`、`testhub-minio`、`testhub-kafka`。
> 如果端口被其他 MySQL/Redis 占用，先停止占用进程，或修改 `deploy/docker-compose.infra.yml` 的宿主机端口并同步修改环境变量。

### 模式 C：完整 Docker Compose

适合已经熟悉 Docker、希望应用也容器化的场景。该模式已提供 compose 和 Dockerfile；不同网络环境下载基础镜像可能较慢，**首次使用请先按第 7 节做健康检查**。

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env：修改数据库/MinIO 密码、四个 TESTHUB_* secret、对外 URL
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps
```

访问：

- Web（nginx 静态站点）：`http://127.0.0.1:${TESTHUB_WEB_PORT:-5173}`
- core API：`http://127.0.0.1:8080`
- 其他服务端口见 `deploy/docker-compose.yml`

> Compose 的 Web 容器只提供静态文件，浏览器会直接访问 `:8080` 的 core API，因此 **8080 也必须对浏览器开放**。如果只允许暴露一个端口，用模式 B 并在 8080 后面统一反向代理，不建议只暴露 5173。

停止：

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down
```

数据卷默认保留。要清空数据：

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v
```

---

## 5. 局域网 / 远程服务器访问

默认配置假定浏览器访问 `127.0.0.1`。局域网或服务器部署时，必须把对外 URL 改成浏览器实际访问的地址。

以服务器 IP `192.168.1.50` 为例，在启动前：

```bash
export TESTHUB_PUBLIC_BASE_URL="http://192.168.1.50:8080"
export TESTHUB_UIEXEC_PUBLIC_URL="http://192.168.1.50:8081"
export TESTHUB_APIEXEC_PUBLIC_URL="http://192.168.1.50:8084"
export TESTHUB_INTERNAL_BASE_URL="http://192.168.1.50:8080"
TESTHUB_INFRA=docker ./scripts/dev-up.sh
```

然后访问：

```text
http://192.168.1.50:8080
```

防火墙至少放行 `8080`、`8081`、`8084`；如果使用设备实时画面，再放行 `8000`。**不要**把 MySQL `3306`、Redis `6379`、MinIO `9000` 暴露到公网。

### 5.1 反向代理和 HTTPS

建议用 Nginx/Caddy 在 8080 前面终止 TLS，并把 core 作为唯一入口：

```nginx
server {
    listen 443 ssl;
    server_name testhub.example.com;

    ssl_certificate     /etc/ssl/testhub/fullchain.pem;
    ssl_certificate_key /etc/ssl/testhub/privkey.pem;

    client_max_body_size 1024m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

同时设置：

```bash
export TESTHUB_PUBLIC_BASE_URL="https://testhub.example.com"
export TESTHUB_UIEXEC_PUBLIC_URL="https://testhub.example.com:8081" # 若单独暴露 8081
export TESTHUB_APIEXEC_PUBLIC_URL="https://testhub.example.com:8084"
```

core 的 CORS 允许跨域；生产仍建议只通过网关访问，内网服务不直接暴露。

### 5.2 浏览器端地址

Web 默认使用“当前页面主机名 + 8080/8081”。如果保存过错误的本地地址，进入页面右上角 **系统设置** 修改 Core/UI Execution 地址，或清空浏览器 localStorage 中的 `testhub.config.*` 后刷新。

---

## 6. 环境变量

### 6.1 必改安全变量

| 变量 | 用途 |
|---|---|
| `TESTHUB_JWT_SECRET` | JWT HS256 密钥，所有服务一致 |
| `TESTHUB_SERVICE_TOKEN` | 服务间 `/internal/**` 令牌 |
| `TESTHUB_AGENT_TOKEN` | Agent 注册/心跳/回调令牌 |
| `TESTHUB_ENCRYPTION_KEY` | 模型 API Key 落库加密密钥 |

`TESTHUB_ENCRYPTION_KEY` 一旦更换，旧密钥加密的数据无法解密；升级时保持稳定。

生成随机值：

```bash
openssl rand -hex 32
```

### 6.2 常用变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `TESTHUB_INFRA` | `local` | `docker` 使用 MySQL/Redis/MinIO/Kafka |
| `TESTHUB_PUBLIC_BASE_URL` | `http://127.0.0.1:8080` | 浏览器/报告使用的外部 core 地址 |
| `TESTHUB_UI_EXECUTION_BASE_URL` | `http://127.0.0.1:8081` | core 调 ui-execution |
| `TESTHUB_API_EXECUTION_URLS` | `http://127.0.0.1:8084` | core 调 api-execution，可逗号分隔多实例 |
| `TESTHUB_UIEXEC_PUBLIC_URL` | `http://127.0.0.1:8081` | UI 报告/产物外部地址 |
| `TESTHUB_APIEXEC_PUBLIC_URL` | `http://127.0.0.1:8084` | API 报告外部地址 |
| `TESTHUB_QUEUE_STORE` | `memory` / Docker 模式 `redis` | 设备 FIFO 队列 |
| `TESTHUB_APIEXEC_QUEUE` | `memory` / Docker 模式 `redis` | API 任务队列 |
| `TESTHUB_ARTIFACT_STORE` | `local` / Docker 模式 `minio` | 产物存储 |
| `TESTHUB_EVENT_BUS` | `local` / Docker 模式 `kafka` | 任务事件流 |
| `TESTHUB_AGENT_MODE` | `simulated` | `simulated` / `adb` / `auto` |
| `TESTHUB_AGENT_DATA_DIR` | `./data/agent` | Agent 缓存/脚本目录 |
| `TESTHUB_SCRCPY_WEB` | `auto` | 设为 `0` 可禁用 ws-scrcpy 自动启动 |
| `TESTHUB_AGENT_ADB` | `adb` | adb 可执行文件路径 |
| `SPRING_DATASOURCE_*` | 由脚本设置 | MySQL 连接 |
| `SPRING_DATA_REDIS_HOST/PORT` | 由脚本设置 | Redis |
| `TESTHUB_MINIO_*` | 由脚本设置 | MinIO |
| `TESTHUB_KAFKA_BOOTSTRAP` | 由脚本设置 | Kafka |

AI Runtime 使用 `PORT`（默认 8095）。模型 Key 在页面 **系统设置 → 模型配置** 中维护，不放在仓库环境文件里。

### 6.3 存储位置

| 内容 | 模式 A/B 路径 | Compose 卷 |
|---|---|---|
| H2 数据 | `data/db/` | — |
| 本地产物 | `data/artifacts/`、`data/core-artifacts/` | `core-artifacts`、`ui-artifacts` |
| Agent 缓存 | `data/agent/` | `agent-data` |
| MySQL | Docker 卷 | `mysql-data` |
| Redis | Docker 卷 | `redis-data` |
| MinIO | Docker 卷 | `minio-data` |
| Kafka | Docker 卷 | `kafka-data` |

`data/`、`logs/`、`.m2/`、`upstream/` 都在 `.gitignore` 中，不会提交到 GitHub。

---

## 7. 验证部署

### 7.1 健康检查

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8084/actuator/health
curl -fsS http://127.0.0.1:8090/agent/health
curl -fsS http://127.0.0.1:8095/runtime/health
```

或：

```bash
./scripts/status.sh
```

全部应为 UP。注意 `scripts/status.sh` 检查的是 8080 静态站点；Vite 开发服务器 5173 不是必需服务。

### 7.2 登录

浏览器打开 `http://127.0.0.1:8080`，使用 `admin/admin123` 或 `tester/test123`。

API 登录：

```bash
curl -fsS -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"tester","password":"test123"}'
```

### 7.3 自动化验收

```bash
make test          # Java 单测 + Python 单测
make e2e           # 端到端
python3 scripts/part1_smoke.py   # 主服务
python3 scripts/part2_smoke.py   # 接口自动化
```

`make test` 不启动浏览器；`make e2e` 需要服务处于启动状态。真机相关检查（`part6`、`device-check`）在没有设备时会跳过/失败，不能用它判断部署是否成功。

### 7.4 首次业务烟测

1. 登录 → 用例管理，确认演示数据。
2. 打开 **API 场景**，新建/编辑场景，切换 **表单编辑 / YAML 源码**，点击 **校验 YAML** 和 **流程预览**。
3. 打开 **接口用例**，确认可以调试单接口。
4. 打开 **测试计划**，挂载用例并触发。
5. 打开 **设备池**：模式 A/B 默认应有模拟设备；真实设备需切换 `TESTHUB_AGENT_MODE=adb`。

---

## 8. 生产安全清单

部署到共享网络/公网前逐项确认：

- [ ] 修改 `TESTHUB_JWT_SECRET`、`TESTHUB_SERVICE_TOKEN`、`TESTHUB_AGENT_TOKEN`、`TESTHUB_ENCRYPTION_KEY`
- [ ] 修改 MySQL root 密码、MinIO 凭据，并同步 `deploy/.env` / 启动环境
- [ ] 修改/删除演示账号，至少修改 `admin` 密码
- [ ] MySQL/Redis/MinIO 只绑定内网，不映射到公网端口
- [ ] TLS 放在 Nginx/Caddy，core 只监听内网
- [ ] 限制 `/internal/**` 仅服务网段可达；`/artifacts/**` 不要公开暴露敏感产物
- [ ] 配置备份和日志保留；确认 `data/`、`logs/` 磁盘容量
- [ ] 生产关闭 JPA `ddl-auto=update` 前先补 Flyway/手工 schema；当前版本仍使用自动建表
- [ ] 配置出网代理/DNS，确保 Maven、npm、Docker 镜像和模型 API 可访问
- [ ] 真机 Agent 只部署在 Linux + USB 设备节点，不要把设备节点暴露到公网

---

## 9. 升级、启停和日志

### 9.1 升级

```bash
cd easytest
git pull --ff-only
make setup       # 重新装依赖并构建 Web（首次升级建议执行）
make build       # Java + Web
make down
TESTHUB_INFRA=docker ./scripts/dev-up.sh
```

只升级主服务时：

```bash
make main-down
make main-up
```

### 9.2 启停命令

| 操作 | 模式 A/B | 完整 Compose |
|---|---|---|
| 启动 | `make up` / `TESTHUB_INFRA=docker ./scripts/dev-up.sh` | `docker compose -f deploy/docker-compose.yml up -d` |
| 停止应用 | `make down` | `docker compose ... down` |
| 停止中间件 | `make infra-down` | 同上 |
| 状态 | `./scripts/status.sh` | `docker compose ... ps` |
| 日志 | `tail -f logs/core.log`、`logs/apiexec.log`、`logs/ui-exec.log` | `docker compose ... logs -f core` |
| 主服务 | `make main-up` / `make main-down` | `web`/`core` 容器 |

### 9.3 日志位置

- `logs/core.log`
- `logs/uiexec.log`
- `logs/apiexec.log`
- `logs/agent.log`
- `logs/ai-runtime.log`
- `logs/metersphere.log`（可选 MeterSphere 对比环境）

启动失败时先看 core 日志；服务启动顺序为 MySQL/Redis → core → ui-execution/api-execution → agent/runtime。

---

## 10. 备份、恢复和重置

### 10.1 MySQL 备份

模式 B 使用 Docker MySQL 时：

```bash
make db-backup
# 等价于
docker exec testhub-mysql mysqldump -uroot -ptesthub \
  --databases testhub_core testhub_uiexec testhub_apiexec > backup.sql
```

`make db-backup` 会把文件写入 `backups/`。生产环境请改用独立备份账号，不要使用 root。

恢复：

```bash
docker exec -i testhub-mysql mysql -uroot -ptesthub < backup.sql
```

H2 模式直接备份 `data/db/` 和 `data/core-artifacts/`。

### 10.2 重置本地环境

```bash
make down
rm -rf data logs backups
make setup
make up
```

这会删除 H2 数据、运行产物、PID 和日志，不会删除源码、`.m2/`、`node_modules`。

Compose 重置：

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v
```

---

## 11. 常见问题

| 现象 | 原因/处理 |
|---|---|
| `mvn: command not found` | 安装 Maven 3.9+；或设置 `PATH` |
| `Permission denied: ~/.m2/repository` | 始终用 `./scripts/mvn.sh`；它把仓库放到工作区 `.m2/repository` |
| `EACCES` / npm 日志目录不可写 | 设置 `npm_config_cache=$PWD/.npm-cache` 后重试 `make setup` |
| 8080 已被占用 | `lsof -nP -iTCP:8080 -sTCP:LISTEN`；停止旧进程或改端口 |
| 打开页面空白 | 先 `make build` 确认 `web/dist/index.html`；确认 core 用 `TESTHUB_WEB_STATIC_DIR=web/dist` 启动 |
| core 起不来 | `tail -100 logs/core.log`；检查 MySQL 连接、数据库是否存在、密钥长度 |
| MySQL 认证失败 | 检查 `SPRING_DATASOURCE_PASSWORD` 是否与 `deploy/.env`/容器一致；旧数据卷可能保留了旧密码 |
| MySQL 数据库不存在 | `docker exec testhub-mysql mysql -uroot -p... -e "CREATE DATABASE ..."`；或删除数据卷重新初始化 |
| MinIO 启动失败/产物 404 | 检查 `TESTHUB_MINIO_ENDPOINT`、bucket、access/secret；模式 A 不需要 MinIO |
| 页面能登录但没有执行服务 | 使用模式 A/B 的 `make up`，不要只运行 `main-up`；`main-up` 隐藏执行入口 |
| 远程页面请求 `127.0.0.1` | 设置 `TESTHUB_PUBLIC_BASE_URL` 等对外 URL，或在系统设置中修改 Core/UI Execution 地址 |
| Docker 镜像拉取超时 | 当前网络可能无法访问 Docker Hub；使用 `deploy/docker-compose.infra.yml` 中的 ECR Public/Chainguard/Strimzi 镜像，或改用模式 A/B |
| H2 文件锁 | `make down` 后删除 `data/db/*.lock.db` 再启动 |
| 设备池为空 | 检查 `logs/agent.log` 与 `TESTHUB_AGENT_MODE`；真机需要 adb、USB 权限和 Android 驱动 |
| API 任务一直 PENDING | 检查 api-execution 健康、Redis/内存队列、worker 日志 `logs/apiexec.log` |
| 计划触发失败 | 确认 core → ui-execution/api-execution 的 URL 可达；检查 `TESTHUB_UI_EXECUTION_BASE_URL`、`TESTHUB_API_EXECUTION_URLS` |

---

## 12. Git 场景源同步（可选）

在「API 场景 → Git 同步」配置仓库后，core 会用服务端 `git` 命令拉取 YAML：

```bash
# 运行 core 的用户必须能执行 git 并访问仓库
git --version
ssh -T git@github.com   # 私有 SSH 仓库
```

- Git 克隆目录：`data/git-scenarios/<hash>`（忽略提交）。
- Webhook 地址：`http://<host>:8080/api/v1/api-scenarios/git/webhook`
- 密钥：配置页填写的值作为 `X-Webhook-Token`，或配置 GitHub `X-Hub-Signature-256` HMAC。
- 同一个 `sourcePath` 对应一个场景；同步不会自动删除 Git 中已移除的场景。
- 公网部署时把 webhook 限制在可信来源，并使用 HTTPS。

## 13. 目录和关键文件

```text
common/            共享 JWT/角色/异常/CORS
testhub-server/    core：用户、需求、用例、计划、API 场景、报告
ui-execution/      UI 执行编排
api-execution/     接口/API 场景执行
device-agent/      设备 Agent
agents/ai-runtime/ Python AI Runtime
web/               React 控制台
deploy/            Compose、Dockerfile、nginx、MySQL 初始化
scripts/           构建/启动/验收/备份脚本
docs/              部署、契约、验收、产品文档
examples/          演示脚本工程
```

关键文件：

- [README.md](../README.md) — 项目概览
- [docs/RUNBOOK.md](RUNBOOK.md) — 运维补充
- [docs/API-CONTRACT.md](API-CONTRACT.md) — 接口契约
- [docs/API-SCENARIO.md](API-SCENARIO.md) — YAML 场景
- `deploy/docker-compose.yml` — 完整容器编排
- `deploy/docker-compose.infra.yml` — 仅中间件
- `deploy/.env.example` — Compose 配置模板
- `scripts/dev-up.sh` / `scripts/dev-down.sh`
- `scripts/main-up.sh` / `scripts/main-down.sh`

---

## 14. 可选：MeterSphere / Sonic 对比环境

本仓库的 TestHub 部署**不依赖** MeterSphere 或 Sonic。若要部署对比环境，参见 [COMPARE-DEPLOY.md](COMPARE-DEPLOY.md)；它使用 `upstream/` 下的源码，`upstream/` 被 `.gitignore` 忽略，需要在目标机器重新拉取源码并单独构建，不属于 TestHub 主部署流程。

---

## 15. GitHub 提交和更新

代码通过 GitHub `main` 分支交付：

```bash
git status
git add ...
git commit -m "..."
git push origin main
```

在新电脑上：

```bash
git clone git@github.com:SvenShun/easytest.git
cd easytest
git pull --ff-only
make setup
```

不要提交：

- `.env`、`deploy/.env`、任何真实密钥
- `data/`、`logs/`、`backups/`
- `.m2/`、`node_modules/`、`web/dist/`
- `upstream/`（MeterSphere/Sonic 源码）
- `tools/`（本地 adb/ws-scrcpy）

`.gitignore` 已覆盖这些路径。生产密钥通过环境变量或部署平台的 Secret 管理。
