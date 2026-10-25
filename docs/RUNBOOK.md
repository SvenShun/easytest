# TestHub V1 运维手册（RUNBOOK）

## 1. 本地开发

```bash
make setup   # 安装 Java/Python/Web 依赖（Maven 使用工作区 .m2/repository）
make up      # 启动 4 个后端服务，日志在 logs/，PID 在 data/pids/
make e2e     # 端到端验收
make down    # 停止服务
```

服务地址：core `:8080`、ui-execution `:8081`、api-execution `:8084`、device-agent `:8090`、ai-runtime `:8095`、web `:5173`。

> Maven Central 在本机不可达，`scripts/mvn.sh` 固定使用工作区本地仓库 `.m2/repository` 与 `~/.m2/settings.xml` 中的阿里云镜像。
> 不要把 `.m2/` 提交到版本库（已 gitignore）。

## 1.0 主服务部署（第一部分验收）

```bash
make main-up                       # 构建 core jar + 主服务模式前端，启动 MySQL/MinIO + core + 静态站点
python3 scripts/part1_smoke.py     # 21 项主服务验收
make main-down                     # 只停 core 与静态站点，保留中间件
```

- 页面：http://127.0.0.1:8080 ；API：http://127.0.0.1:8080。
- 主服务模式隐藏总览/设备池/执行任务，并隐藏计划"触发"入口（执行属于第二、三部分）。
- 前置：Docker 中 MySQL/MinIO（`make infra`），脚本会自动检查并启动。

## 1.1 真实中间件模式（推荐用于联调）

```bash
make infra                                  # Docker: MySQL 8.0 / Redis 7 / MinIO
TESTHUB_INFRA=docker ./scripts/dev-up.sh    # 应用切到 MySQL + Redis + MinIO
make e2e
make down && make infra-down
```

中间件端口：MySQL `3306`（root/testhub）、Redis `6379`、MinIO `9000`（控制台 `9001`，testhub/testhub123）、
Kafka `9092`（KRaft 单节点）。

Kafka 主题（`TESTHUB_EVENT_BUS=kafka` 时启用）：

```bash
docker exec testhub-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list
docker exec testhub-kafka /opt/kafka/bin/kafka-console-consumer.sh   --bootstrap-server 127.0.0.1:9092 --topic ui-execution.task.result --from-beginning
```

事件体见 `docs/API-CONTRACT.md` 附录 A。当前 Kafka 只承载任务事件（下发/结果），任务下发仍走 HTTP。

> 该网络无法访问 Docker Hub 与 dl.min.io，`deploy/docker-compose.infra.yml` 使用 AWS ECR Public 的
> MySQL/Redis 官方镜像与 Chainguard 的上游 MinIO 二进制镜像。Kafka 镜像未能在可接受时间内拉取，暂不可用。

## 1.2 MeterSphere（源码构建，阶段 C）

```bash
cp -R /Users/wen/Workspace/TestPlatform/metersphere/metersphere-3.x upstream/metersphere
make ms-install-module            # 注入 ui-execution 模块与补丁
make ms-build                     # JDK 21 + 工作区 Maven 仓库
make ms-up                        # :8082 / 管理 :7171，日志 logs/metersphere.log
eval "$(make -s ms-token)"        # X-AUTH-TOKEN / CSRF-TOKEN / PROJECT
TESTHUB_INFRA=docker TESTHUB_CASE_SOURCE=metersphere ./scripts/dev-up.sh
make ms-down
```

- 账号 `admin/metersphere`；默认项目 `100001100001`。
- 计划结果回写：`TESTHUB_METERSPHERE_WRITEBACK=true` + 映射接口（见 METERSPHERE-INTEGRATION §4.1）。
- 推荐配置 `TESTHUB_METERSHPHERE_USERNAME/PASSWORD` 自动登录；仅配 token 时 12h 后需刷新。
- MeterSphere 会话默认 12h，过期后重新执行 `make ms-token` 并重启 ui-execution。
- 数据库必须使用 `utf8mb4_general_ci`；日志已用 `deploy/metersphere/logback-spring.xml` 覆盖。
- 详见 `docs/METERSPHERE-INTEGRATION.md`。

## 2. 默认存储与运行模式

| 能力 | 默认（dev） | 切换方式 |
|---|---|---|
| 元数据 | H2 文件库 `data/db/{core,uiexec}` | `SPRING_PROFILES_ACTIVE=mysql` + `SPRING_DATASOURCE_*` |
| 设备 FIFO 队列 | 进程内内存队列 | `TESTHUB_QUEUE_STORE=redis` + `SPRING_DATA_REDIS_HOST` |
| 产物（截图/报告/脚本包） | 本地文件 `data/artifacts`、`data/core-artifacts` | `TESTHUB_ARTIFACT_STORE=minio` + `TESTHUB_MINIO_*`（或用 `TESTHUB_INFRA=docker`） |
| AI 模型 | `mock`（离线确定性） | 系统设置里改为 `openai`/`deepseek` 并填 endpoint/key/model |
| 用例源 | TestHub case-management | `TESTHUB_CASE_SOURCE=metersphere` + MeterSphere 连接配置（见 METERSPHERE-INTEGRATION.md） |
| 设备 | 模拟设备（2 台） | `TESTHUB_AGENT_MODE=adb`（需 adb 与真机） |
| 任务事件 | 本地结构化日志 | `TESTHUB_EVENT_BUS=kafka` + `TESTHUB_KAFKA_BOOTSTRAP`（`TESTHUB_INFRA=docker` 自动开启） |

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
| `TESTHUB_EVENT_BUS` | `local` | `kafka` 发布任务事件 |
| `TESTHUB_KAFKA_BOOTSTRAP` | `127.0.0.1:9092` | Kafka broker |

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

> 说明：`docker-compose.infra.yml`（MySQL/Redis/MinIO/Kafka）已实测；完整 `docker-compose.yml`（含应用容器）
> 仍建议在目标环境验证。该网络无法访问 Docker Hub，compose 中的镜像已替换为可达的 ECR Public / Chainguard / Strimzi 源。

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
- [ ] 将任务下发迁移到 Kafka（当前仅事件流）、多副本与队列重建
- [ ] 配置模型 API Key 轮换策略与出网代理
- [ ] 配置审计日志保留、执行轨迹生命周期归档
- [ ] 设备子服务器仅 Linux；ADB/scrcpy 与 USB 直通按部署文档配置

## 9. 数据库访问与备份

### 9.1 部署位置

- 容器：`testhub-mysql`（镜像 `public.ecr.aws/docker/library/mysql:8.0`）
- 宿主机端口：`3306`
- 数据卷：`deploy_testhub-mysql-data`，容器内路径 `/var/lib/mysql`
- 账号（开发默认）：`root / testhub`

库：

| 库 | 用途 |
|---|---|
| `testhub_core` | 主服务：用户、需求、用例、脚本、计划、AI、配置、审计 |
| `testhub_uiexec` | ui-execution：设备、Agent、任务、步骤、队列、失败报告、Skills 指针 |
| `testhub_apiexec` | api-execution：接口任务、请求/响应、断言结果、报告 |
| `metersphere` | MeterSphere 源码实例（含内嵌 ui-execution 的 `ui_execution_*` 表） |

主服务核心表：`app_user`、`req_item`、`client_case`、`case_review`、`script_project`、
`test_plan`、`plan_case`、`plan_run`、`plan_run_item`、`ai_job`、`model_config`、`audit_log`。

### 9.2 访问方式

```bash
# 项目内快捷入口（进入 testhub_core）
make db

# 指定库
./scripts/db.sh uiexec
./scripts/db.sh metersphere

# 直接用本机 mysql 客户端
mysql -h127.0.0.1 -P3306 -uroot -ptesthub testhub_core

# 不装客户端也可以
docker exec -it testhub-mysql mysql -uroot -ptesthub testhub_core
```

GUI（DBeaver / Navicat / TablePlus）：Host `127.0.0.1`，Port `3306`，User `root`，Password `testhub`，Database `testhub_core`。

### 9.3 常用操作

```sql
SHOW TABLES;
SELECT id, username, display_name, role, enabled FROM app_user;
SELECT id, title, review_status, source FROM client_case ORDER BY created_at DESC LIMIT 20;
SELECT id, name, status FROM test_plan ORDER BY created_at DESC;
SELECT action, actor, target, created_at FROM audit_log ORDER BY created_at DESC LIMIT 20;
```

### 9.4 备份与恢复

```bash
make db-backup                       # 输出到 backups/backup-时间.sql
make db-backup my.sql

# 恢复（会覆盖同名库中的对象，先停主服务更稳妥）
docker exec -i testhub-mysql mysql -uroot -ptesthub < backups/my.sql
```

### 9.5 注意

- `3306` 目前映射到 `0.0.0.0`，同一局域网可访问；生产必须改密、限制来源、加 TLS，不要直接暴露公网。
- 数据在 Docker named volume 中，删除 volume（`docker compose -f deploy/docker-compose.infra.yml down -v`）会清空全部数据。
- 接口执行：`./scripts/dev-up.sh` 会启动 api-execution；主服务用 `TESTHUB_API_EXECUTION_URLS`（逗号分隔）配置多个实例。
- 其他中间件：Redis `127.0.0.1:6379`；MinIO API `9000`、控制台 `http://127.0.0.1:9001`（`testhub/testhub123`）；Kafka `127.0.0.1:9092`。
