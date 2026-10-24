# TestHub V1 运维手册（RUNBOOK）

## 1. 本地开发

```bash
make setup   # 安装 Java/Python/Web 依赖（Maven 使用工作区 .m2/repository）
make up      # 启动 4 个后端服务，日志在 logs/，PID 在 data/pids/
make e2e     # 端到端验收
make down    # 停止服务
```

服务地址：core `:8080`、ui-execution `:8081`、device-agent `:8090`、ai-runtime `:8095`、web `:5173`。

> Maven Central 在本机不可达，`scripts/mvn.sh` 固定使用工作区本地仓库 `.m2/repository` 与 `~/.m2/settings.xml` 中的阿里云镜像。
> 不要把 `.m2/` 提交到版本库（已 gitignore）。

## 2. 默认存储与运行模式

| 能力 | 默认（dev） | 切换方式 |
|---|---|---|
| 元数据 | H2 文件库 `data/db/{core,uiexec}` | `SPRING_PROFILES_ACTIVE=mysql` + `SPRING_DATASOURCE_*` |
| 设备 FIFO 队列 | 进程内内存队列 | `TESTHUB_QUEUE_STORE=redis` + `SPRING_DATA_REDIS_HOST` |
| 产物（截图/报告/脚本包） | 本地文件 `data/artifacts`、`data/core-artifacts` | `TESTHUB_ARTIFACT_STORE=minio` + `MINIO_*` |
| AI 模型 | `mock`（离线确定性） | 系统设置里改为 `openai`/`deepseek` 并填 endpoint/key/model |
| 设备 | 模拟设备（2 台） | `TESTHUB_AGENT_MODE=adb`（需 adb 与真机） |

## 3. 环境变量

### 共享安全

| 变量 | 默认（仅 dev） | 说明 |
|---|---|---|
| `TESTHUB_JWT_SECRET` | `testhub-dev-jwt-secret-...` | HS256 密钥，所有服务必须一致 |
| `TESTHUB_SERVICE_TOKEN` | `testhub-dev-service-token` | 服务间 `/internal/**` 令牌 |
| `TESTHUB_AGENT_TOKEN` | `testhub-dev-agent-token` | Agent 注册/心跳/回调令牌 |
| `TESTHUB_ENCRYPTION_KEY` | `testhub-dev-encryption-key-change-me` | 模型 API Key 落库加密密钥；变更后旧密钥无法解密 |

### testhub-server（core）

| 变量 | 默认 | 说明 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | H2 文件库 | 推荐 `jdbc:mysql://host:3306/testhub_core?...` |
| `TESTHUB_PUBLIC_BASE_URL` | `http://127.0.0.1:8080` | 对外访问地址 |
| `TESTHUB_INTERNAL_BASE_URL` | 同 public | Agent 下载脚本包用的内部地址（容器内填 `http://core:8080`） |
| `TESTHUB_UIEXEC_BASE_URL` | `http://127.0.0.1:8081` | 触发计划时调用 ui-execution |
| `TESTHUB_ARTIFACT_DIR` | `./data/core-artifacts` | 脚本包存储目录 |
| `TESTHUB_ARTIFACT_STORE` | `local` | `minio` 启用对象存储 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | — | MinIO 配置 |

### ui-execution

| 变量 | 默认 | 说明 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | H2 文件库 | 推荐 `jdbc:mysql://host:3306/testhub_uiexec?...` |
| `TESTHUB_CORE_URL` | `http://127.0.0.1:8080` | 内部回调/用例上下文 |
| `TESTHUB_UIEXEC_PUBLIC_URL` | `http://127.0.0.1:8081` | 产物/报告对外 URL |
| `TESTHUB_UIEXEC_CALLBACK_URL` | 同 public | Agent 回调地址（容器内填 `http://ui-execution:8081`） |
| `TESTHUB_QUEUE_STORE` | `memory` | `redis` |
| `SPRING_DATA_REDIS_HOST` / `PORT` | `127.0.0.1` / `6379` | Redis |
| `TESTHUB_ARTIFACT_DIR` | `./data/artifacts` | 本地产物目录 |
| `TESTHUB_ARTIFACT_STORE` | `local` | `minio` |
| `TESTHUB_AGENT_OFFLINE_SECONDS` | `90` | 心跳超时判定离线 |

### device-agent

| 变量 | 默认 | 说明 |
|---|---|---|
| `TESTHUB_AGENT_NAME` | `device-agent-1` | 注册幂等键 |
| `TESTHUB_AGENT_BASE_URL` | `http://127.0.0.1:8090` | core（ui-execution）回调本 Agent 的地址 |
| `TESTHUB_UIEXEC_URL` | `http://127.0.0.1:8081` | 注册/心跳 |
| `TESTHUB_RUNTIME_URL` | `http://127.0.0.1:8095` | AI Runtime 地址 |
| `TESTHUB_AGENT_MODE` | `simulated` | `simulated` / `adb` / `auto` |
| `TESTHUB_AGENT_DATA_DIR` | `./data/agent` | 脚本解压、Skills 缓存 |

### ai-runtime

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | `8095` | 监听端口 |

## 4. 切换到 MySQL / Redis / MinIO

```bash
export SPRING_PROFILES_ACTIVE=mysql
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/testhub_core?useSSL=false&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=testhub

export TESTHUB_QUEUE_STORE=redis
export SPRING_DATA_REDIS_HOST=127.0.0.1

export TESTHUB_ARTIFACT_STORE=minio
export MINIO_ENDPOINT=http://127.0.0.1:9000
export MINIO_ACCESS_KEY=testhub
export MINIO_SECRET_KEY=testhub123
```

数据库需预先创建 `testhub_core`、`testhub_uiexec`（见 `deploy/mysql-init/01-databases.sql`）。
V1 使用 JPA `ddl-auto=update` 自动建表，生产环境应先引入 Flyway 再关闭自动更新（TODO）。

## 5. Docker Compose

```bash
cd deploy
docker compose up -d --build
```

包含 MySQL、Redis、MinIO、core、ui-execution、ai-runtime、device-agent（模拟设备）、web。
默认端口映射与本地一致（8080/8081/8090/8095/5173）。

> 注意：本机 Docker daemon 未运行，本次交付**未在容器环境实测**；本地 dev 模式已完整验证。
> 首次启动 MySQL 需要初始化时间，core/ui 会依赖健康检查后启动。

## 6. 常见问题

| 现象 | 排查 |
|---|---|
| 构建报 `Operation not permitted: ~/.m2` | 必须用 `./scripts/mvn.sh`，它把本地仓库指向工作区 `.m2/` |
| `Connection refused :8080` | `make up` 后看 `logs/core.log`；H2 文件锁残留可 `make down` 后删除 `data/db/*.lock.db` |
| 设备池为空 | 看 `logs/agent.log` 是否注册成功；ui-execution 未就绪时 Agent 会指数退避重试 |
| AI 任务 `EXECUTOR_UNAVAILABLE` | 系统设置里视觉模型未配置，或没有在线设备 |
| AI 任务 `SKILLS_UNAVAILABLE` | Skills 指针指向的 Git 不可达且 Agent 无本地缓存；可清空指针或修正仓库 |
| 任务一直 `PENDING` | 设备被占用/离线；看设备队列与 Agent 心跳 |
| 失败报告 404 | `TESTHUB_UIEXEC_PUBLIC_URL` 与实际访问地址不一致 |
| 中文截图乱码/空白 | 无字体环境；本地 dev 正常，容器内已安装 fontconfig/dejavu |

## 7. 重置环境

```bash
make down
rm -rf data logs
make up
```

这会清空 H2 数据、产物、Agent 缓存与 PID；不影响源码与 `.m2`。

## 8. 生产上线检查清单

- [ ] 修改全部默认密钥：`TESTHUB_JWT_SECRET`、`TESTHUB_SERVICE_TOKEN`、`TESTHUB_AGENT_TOKEN`、`TESTHUB_ENCRYPTION_KEY`
- [ ] 修改种子账号密码，删除演示账号
- [ ] 使用 MySQL + Redis + MinIO，关闭 `ddl-auto=update`，引入 Flyway
- [ ] TLS 终止在网关，限制 `/internal/**` 仅服务网段可达
- [ ] `/artifacts/**` 改为签名 URL 或鉴权（V1 为随机路径公开访问）
- [ ] 引入 Kafka（V1 未实现，需先新增事件总线端口与适配器）、多副本与队列重建
- [ ] 配置模型 API Key 轮换策略与出网代理
- [ ] 配置审计日志保留、执行轨迹生命周期归档
- [ ] 设备子服务器仅 Linux；ADB/scrcpy 与 USB 直通按部署文档配置
