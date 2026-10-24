# TestHub V1 范围与架构落地说明

> 依据：《TestHub 产品需求文档》《TestHub 架构设计文档》（定稿）
> 本文记录 V1 的交付边界、与原始架构的偏差，以及每个偏差的后续演进路径。

## 1. V1 目标

交付一个**可离线运行、端到端闭环**的 TestHub V1：

```
需求 → AI 生成 BDD 草稿 → 人工评审通过
     → 测试计划挂载 → 触发
     → ui-execution 执行器路由 + 设备 FIFO 队列
     → 设备子服务器执行（传统脚本 / AI Runtime）
     → 逐步轨迹（截图 + 模型决策 + 动作）
     → 失败轨迹报告 → 用例失败分析回写
     → 计划统一报告
```

V1 面向单机 Docker Compose / 本地开发环境，硬性要求：**没有大模型 Key、没有真机、没有 Docker 时也能跑通全链路**（mock 模型 + 模拟设备 + H2 + 内存队列 + 本地文件产物）。

## 2. V1 交付清单

| 域 | V1 交付 | 说明 |
|---|---|---|
| 身份与权限 | JWT 登录、ADMIN/TESTER/BUSINESS/OPS 四角色、默认拒绝 | 服务间 `X-Service-Token`，Agent `X-Agent-Token` |
| 用例管理 | 客户端前端用例 CRUD、BDD 结构、脚本引用（0/1）、评审流转、失败分析字段 | `case-management` 模块 |
| 脚本工程 | zip 包上传、类型/入口命令、短时票据下载 | 替代 MinIO 预签名 URL，语义一致 |
| AI 分析 Agent | 需求 → BDD 草稿、生成任务记录、模型标识落库 | 默认 `mock`，可配 OpenAI 兼容端点 |
| 测试计划 | 混合计划骨架、用例挂载、执行器覆盖、触发、运行报告 | 接口执行域由 MeterSphere 承载，V1 报告模型预留 |
| ui-execution | 执行器路由、设备 FIFO 队列、状态机、取消、轨迹、日志、失败报告、Skills 指针、统计、审计 | 独立服务，端口 8081 |
| 设备子服务器 | 注册/心跳、设备同步、模拟设备、传统脚本执行器、AI Runtime 桥接、Skills 本地缓存 | 独立服务，端口 8090 |
| AI Runtime | 观察→决策→动作闭环、动作白名单、超时/取消、轨迹、Skills 注入 | Python FastAPI，端口 8095 |
| Web 控制台 | 登录、总览、设备池/队列、用例与评审、计划与报告、任务轨迹、系统设置 | React + Vite |
| 部署 | Docker Compose（MySQL/Redis/MinIO）、Dockerfile、一键脚本 | 无 Docker 时走 dev profile |

## 3. 与架构文档的偏差（重要）

| 文档设计 | V1 实现 | 原因与演进 |
|---|---|---|
| 6 个独立微服务 + Eureka + Gateway | `testhub-server` 单服务内含 `case-management / test-plan / system-setting / ai-analysis` 四个**模块包**；`ui-execution` 独立服务 | MeterSphere 源码不在本仓库，V1 需可独立运行；包边界与接口契约按可拆分设计，后续按模块拆出即可 |
| Kafka 承载下发/日志/结果 | **V1 未实现 Kafka，也未抽象 EventBus 端口**；跨服务全部走 HTTP 回调 | V1 单机不引入 broker；后续需新增事件总线端口 + Kafka 适配器 |
| MinIO 存储产物 | `ArtifactStore` 端口：默认本地文件系统，`TESTHUB_ARTIFACT_STORE=minio` 切 MinIO | MinIO 实现已编写，但**未在真实 MinIO 上验收** |
| Redis 设备 FIFO | `DeviceQueueStore` 端口：默认内存，`TESTHUB_QUEUE_STORE=redis` 切 Redis | Redis 实现已编写，但**未在真实 Redis 上验收** |
| 设备投屏（scrcpy） | 不做投屏流，设备池展示状态/队列；模拟设备提供截图 | 复用 Sonic 设备通道，需真机环境；V1 预留 WebSocket 边界 |
| 传统脚本执行器 | 解压工程包 + 执行入口命令，解析 JUnit XML 与截图 | 真实 TestNG/uiautomator2 依赖外部 Maven/ADB，V1 提供同构执行链路 |
| AI Runtime 内核（成熟框架二次开发） | 自实现最小闭环内核（Python） | V1 验证决策-动作-轨迹链路，后续可替换内核 |
| 接口测试执行 | 不实现（归属 MeterSphere Task Runner） | 计划/报告模型已预留 `executorType=API` |
| 数据库迁移 | JPA `ddl-auto=update` + 启动种子数据 | V1 快速迭代；上线前替换 Flyway（TODO 已记录） |
| 产物访问控制 | `/artifacts/**` 无鉴权但路径含随机 ID | V1 内网工具；后续改为签名 URL |

## 3.1 交付边界更正（重要）

1. **未引入 MeterSphere / Sonic 源码**：本仓库是按其能力模型实现的等价新服务，不是对两个项目的 fork 或紧耦合改造；`device-agent` 也不是 Sonic-Agent 改造版。
2. **数据库**：使用 JPA 持久化；默认 H2 文件库，已提供 MySQL profile，但验收只在 H2 上完成。
3. **中间件**：Redis 队列与 MinIO 产物实现已编写且按配置装配，但验收只在内存队列 + 本地文件系统上完成；Kafka 未实现。
4. 原文档中的「紧耦合融合」在 V1 中体现为**接口与数据模型对齐**，不体现为代码级复用。

## 4. 执行器路由规则（实现为准）

1. 读取用例执行上下文（内部接口），`reviewStatus != APPROVED` → 拒绝创建任务（409）。
2. 计划层指定 `executorType` 且该执行器对用例可用 → 采用。
3. 未指定：有脚本 → `SCRIPT`；无脚本 → `AI_RUNTIME`。
4. 指定 `SCRIPT` 但无脚本 → 409 `EXECUTOR_UNAVAILABLE`。
5. 指定/需用 `AI_RUNTIME` 但视觉模型未配置 → 有脚本则回退 `SCRIPT`，否则任务置 `FAILED`（`EXECUTOR_UNAVAILABLE`）。
6. 无可用设备 → 任务置 `FAILED`（`EXECUTOR_UNAVAILABLE`），仍返回 taskId 便于报告。

## 5. 设备 FIFO 队列语义

- 每设备一条 FIFO 队列；入队即 `PENDING`，`queuePosition` 从 1 开始。
- 设备空闲（无 currentTaskId）时立刻出队并下发 → `DISPATCHED`。
- 设备任务进入终止态（SUCCESS/FAILED/TIMEOUT/CANCELLED）后自动出队下一个。
- 取消：`PENDING` 直接出队并置 `CANCELLED`；`DISPATCHED/RUNNING` 向 Agent 发取消信号，Agent 回传 `CANCELLED` 结果（保留已有轨迹）。
- 服务重启：内存实现丢失队列（V1 限制，Redis 实现可重建，见偏差表）。

## 6. 失败原因分类（枚举）

`MODEL_DECISION_FAILED` 模型决策失败 / `ACTION_FAILED` 动作执行失败 / `ELEMENT_NOT_FOUND` 元素未找到 /
`TIMEOUT` 超时 / `MANUAL_INTERRUPT` 人工中断 / `EXECUTOR_UNAVAILABLE` 执行器不可用 /
`SKILLS_UNAVAILABLE` Skills 不可用 / `SCRIPT_FAILED` 脚本执行失败。

判定顺序：Agent/Runtime 上报 `reasonCategory` → 状态推导（TIMEOUT→超时、CANCELLED→人工中断）→ 按执行器兜底（SCRIPT→SCRIPT_FAILED、AI_RUNTIME→ACTION_FAILED）。

## 7. 端口与默认配置

| 组件 | 端口 | 默认存储 | 说明 |
|---|---|---|---|
| testhub-server | 8080 | H2 文件 `./data/db/core` | dev profile；`SPRING_PROFILES_ACTIVE=mysql` 切 MySQL |
| ui-execution | 8081 | H2 文件 `./data/db/uiexec` + 内存队列 + 本地产物 | 同上 |
| device-agent | 8090 | 无 | 默认 `simulated` 驱动 |
| ai-runtime | 8095 | 本地 Skills 缓存 | Python |
| web | 5173 (dev) | — | `/api` 直连 8080/8081 |

默认账号：`admin/admin123`、`tester/test123`、`business/biz123`、`ops/ops123`（V1 种子数据，生产必须修改）。

## 8. 明确不在 V1 范围

投屏/远程控制、真机 ADB 全量动作、JMeter 接口执行、Kafka、集群化、Flyway、Skills 用户级界面、AI 参与评审决策、Windows 设备子服务器。
