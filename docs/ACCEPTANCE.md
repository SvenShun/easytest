# TestHub V1 验收报告

| 项 | 内容 |
|---|---|
| 验收日期 | 2026-10-23 |
| 版本 | V1（首个可运行版本） |
| 验收环境 | macOS（Apple Silicon），Java 17.0.2，Maven 3.9.9，Node 22.20.0，Python 3.9.15，无 Docker daemon、无真机、无大模型 Key |
| 运行模式 | H2 文件库 + 内存 FIFO 队列 + 本地产物 + `mock` 模型 + 模拟设备（完全离线） |
| 结论 | **通过**：全量 154 个自动化测试通过，端到端验收 47 项检查全部通过 |

## 1. 构建与自动化测试

| 模块 | 测试数 | 结果 | 命令 |
|---|---|---|---|
| common | 4 | 通过 | `./scripts/mvn.sh -pl common test` |
| testhub-server | 30 | 通过 | `./scripts/mvn.sh -pl testhub-server test` |
| ui-execution | 36 | 通过 | `./scripts/mvn.sh -pl ui-execution test` |
| device-agent | 36 | 通过 | `./scripts/mvn.sh -pl device-agent test` |
| ai-runtime (Python) | 48 | 通过 | `cd agents/ai-runtime && .venv/bin/pytest -q` |
| **合计** | **154** | **0 失败 / 0 错误 / 0 跳过** | `make check` |
| web | — | `tsc --noEmit` + Vite build 成功 | `cd web && npm run build` |

覆盖重点：执行器路由、FIFO 队列位置、任务状态机、失败分类、评审流转、执行能力计算、
计划触发与失败继续、AI mock Provider、脚本票据、模拟设备登录流程、zip slip 防护、JUnit 解析、
动作白名单、超时/取消、Skills 缓存与降级、步骤/日志/结果回调、CORS 与默认拒绝鉴权。

## 2. 端到端验收（`make e2e`）

```
RESULT: 47 checks passed, 0 failed
```

| 组 | 检查项 | 结果 |
|---|---|---|
| 0 健康 | 四个服务健康、登录角色正确 | 5/5 |
| 1 AI 分析 | 需求 → BDD 草稿、状态 DRAFT、source=AI、无脚本仅 AI_RUNTIME | 4/4 |
| 2 评审与脚本 | 人工评审通过、BDD 持久化、脚本用例能力/默认执行器 | 4/4 |
| 3 Skills | 本地 Git 仓库同步解析 HEAD commit | 1/1 |
| 4 设备池 | Agent 注册模拟设备、设备在线 | 1/1 |
| 5 计划与 FIFO | 用例挂载、逐用例建任务、队列非空、位置有序、严格按计划顺序 | 5/5 |
| 6 混合执行 | AI 成功、慢速脚本成功、通过脚本成功、失败脚本失败、失败分类、运行状态 PARTIAL | 6/6 |
| 7 AI 轨迹 | 多步骤、每步截图、动作命中登录流程、Skills commit 固化、截图是真实 PNG | 5/5 |
| 8 脚本与失败回写 | JUNIT+SCREENSHOT 产物、失败报告 URL、分类、timeline、设备环境、用例回写 | 7/7 |
| 9 统一报告 | 汇总计数、按执行器分组、失败分类聚合、按 planRunId 过滤任务 | 5/5 |
| 10 队列取消 | 排队任务取消、运行含 CANCELLED、其余任务继续执行 | 3/3 |
| 11 统计 | 设备/任务计数 | 2/2 |

### 本次验收产生的真实数据

- 计划运行：`run_a40c477475ac4fe79177`（运行 `GET /api/v1/plan-runs/{id}/report`）
- 任务：
  - AI Runtime 成功：`task_a2fc1f8cb54c45f4895f`（多步截图 + 决策原文）
  - 慢速脚本成功：`task_0d62f26016e544a1ae2f`
  - 脚本通过：`task_63b0e62942a6434eb970`
  - 脚本失败：`task_c37ac602297749aa91f9`
- 汇总：`total=4, success=3, failed=1`；`SCRIPT: 3/2/1`、`AI_RUNTIME: 1/1/0`；失败分类 `SCRIPT_FAILED:1`
- 失败轨迹报告：`http://127.0.0.1:8081/artifacts/task_c37ac602297749aa91f9/failure-report.json`
  - `reasonCategory=SCRIPT_FAILED`，`timeline` 含失败用例，`environment.deviceSerial=emulator-5554`
- 用例回写：失败分析字段写入 `script failed: 1 of 2 test case(s) failed: com.testhub.demo.LoginErrorTest.loginShowsErrorToast`
- 取消验证运行：`run_5aef853b5d994f789c52`（1 个排队任务取消，其余 2 个成功）
- Skills 固化 commit：`33f2217714c9c95e641d7a0fe116c63873485fe0`

## 2.1 真实中间件复验（MySQL 8.0 / Redis 7 / MinIO）

在 Docker 中启动真实中间件后，把 `testhub-server` 与 `ui-execution` 切到 MySQL profile、Redis FIFO、MinIO 产物，
`device-agent`/`ai-runtime` 不变，**端到端 47 项检查再次全部通过**（运行 `run_e7a48159beec4c0d82e9`，事件总线为 Kafka）。

| 中间件 | 版本 | 实际使用证据 |
|---|---|---|
| MySQL | 8.0.46 | `testhub_core` 12 张表、`testhub_uiexec` 9 张表；复验后累计 15 用例 / 5 计划运行 / 16 任务 / 28 步骤 / 4 失败报告 |
| Redis | 7.4.11 | 运行期捕获 `testhub:uiexec:queue:device_*` 列表中有排队任务，执行完自动清空；持久索引键 `testhub:uiexec:queues` 保留设备维度 |
| MinIO | RELEASE.2026-10-23T20-06-51Z | 桶 `testhub-core`（脚本包）、`testhub-ui`（截图/失败报告/产物）；`/artifacts/**` 由 ArtifactStore 流式读取，e2e 校验了 PNG 与失败报告 JSON |
| Kafka | 3.8.0（KRaft 单节点） | 主题 `ui-execution.task.dispatch` / `ui-execution.task.result` 自动创建；抓取到 `TASK_DISPATCHED` 与 `TASK_FINISHED`（含 SUCCESS、FAILED、CANCELLED）事件，字段与任务/报告一致 |

**复验中发现并修复的真实兼容问题**：`ue_task` 的 `bdd_given/bdd_when/bdd_then/failure_summary` 原为
`varchar(4000)`，在 MySQL utf8mb4 下四个字段合计超过 65535 字节行上限，建表失败（H2 不会暴露）。
已改为 `TEXT`，MySQL 建表成功。

Kafka 事件示例（截断）：

```json
{"eventId":"evt_305bfb89c1ee4900898b","type":"TASK_DISPATCHED","taskId":"task_1595e603b10441acaa81",
 "planRunId":"run_54cbabf41f8d4f05b316","executorType":"SCRIPT","status":"DISPATCHED",
 "deviceSerial":"simulator-0001","occurredAt":"2026-10-24T17:14:10.690512Z"}
{"eventId":"evt_1bbd738b807f4474a575","type":"TASK_FINISHED","taskId":"task_8b14f11c3bec44b2ac3a",
 "status":"CANCELLED","failureReason":"MANUAL_INTERRUPT",
 "failureReportUrl":"http://127.0.0.1:8081/artifacts/task_8b14f11c3bec44b2ac3a/failure-report.json"}
```

> 网络限制说明：Docker Hub 与 `dl.min.io` 在该网络不可达。MySQL/Redis 使用 AWS ECR Public 的官方镜像；
> MinIO 使用 Chainguard 构建的**上游 MinIO 二进制**（`cgr.dev/chainguard/minio`）；Kafka 使用 Strimzi 镜像中的
> Apache Kafka 3.8.0，以单节点 KRaft 模式运行。

## 2.2 MeterSphere 真实源码联调（阶段 C）

| 项 | 结果 |
|---|---|
| 源码 | 你提供的 `metersphere-3.x`（158MB），复制到 `upstream/metersphere`，Java 21 构建 |
| 构建产物 | `backend/app/target/app-3.x.jar`（293MB） |
| 运行 | MeterSphere `:8082`（避开 8081），MySQL/Redis/Kafka/MinIO 全部走真实中间件 |
| 用例源适配 | 真实用例 `1707709216279502848` 被 ui-execution 读取，BDD 映射正确 |
| AI 执行 | `task_c40a656d96a34726a330` SUCCESS，5 步轨迹（登录 → 输入 → 输入 → 登录 → done） |
| 失败回写 | `task_311b90a11dac4b3eb25e` FAILED，MeterSphere 评论 `1707710899906691072` 写入分类/摘要/报告 URL |
| 计划结果回写 | 成功：`run_bd27529cc54048ae89ca` → MS plan-case `1707773434630599999` = **SUCCESS**；失败：`task_8929962a82c7404ca949` → MS plan-case `17077734346124044` = **ERROR** |
| 回归 | 恢复 `case-source=testhub` 后 E2E 47/47 |

构建/运行中解决的真实问题：JDK 21 要求、`frontend/dist` 缺失导致 antrun 失败、
MySQL 库 collation 必须 `utf8mb4_general_ci`、上游 logback 硬编码 `/opt/metersphere` 日志路径、
MeterSphere API 的 `{code,message,data}` 包装与 `CSRF-TOKEN`/`PROJECT` 请求头。

未完成：计划-用例关联的 API 自动化（当前用 SQL fixture）、会话自动续期、源码级模块内嵌。

## 2.3 MeterSphere 源码级内嵌（ui-execution 模块）

| 项 | 结果 |
|---|---|
| 形态 | MeterSphere `backend/services/ui-execution` 新模块，随 MS 一起构建、同进程运行 |
| 复用 | Shiro 会话/权限、`FunctionalCaseService`、`TestPlanFunctionalCaseService.run`、MS 数据库 |
| 持久化 | Flyway `3.7.0.1 - ui execution`，`ui_execution_task` / `ui_execution_step` |
| 下发 | 复用 TestHub device-agent 协议（AI_RUNTIME） |
| 实测 | MS 原生接口创建任务 → 5 步轨迹 → 任务 SUCCESS → 计划用例 `lastExecResult=SUCCESS` |
| 证据 | 任务 `1707866188744261632`（5 步，SUCCESS），计划用例 `1707773434630599999`（lastExecTime 更新） |

未迁移：FIFO 队列、看门狗、失败轨迹报告、脚本执行器、截图/报告产物、MeterSphere 前端入口。

## 3. 验收中发现并修复的问题

| 问题 | 修复 |
|---|---|
| AI Runtime 复用了上一任务残留的设备界面，导致新任务在第 1 步就判定“已完成”（1 步轨迹） | Runtime 在每次任务开始时调用 `POST /device/{serial}/reset` 做测试隔离；回归后 AI 任务稳定产生 5 步轨迹（点登录 → 输入用户名 → 输入密码 → 点登录 → done）。48 个 Python 测试仍全绿。 |
| 快速任务使 FIFO 队列在断言前已排空，队列可观测性不足 | 验收脚本把 6 秒慢速脚本用例放在计划首位，稳定观测到 3 个排队任务及其位置顺序。 |
| MySQL utf8mb4 行上限导致 `ue_task` 建表失败 | 四个长文本字段改为 `TEXT`；在真实 MySQL 8.0 上复验通过。 |
| S3 产物 URL 指向本地 `/artifacts` 静态目录 | 改为 `/artifacts/**` 控制器按 ArtifactStore 流式读取，本地/MinIO 共用同一 URL；已用 MinIO 复验截图与报告可读。 |
| 内嵌模块向 device-agent 下发时被当成 XML 解析 | `DeviceAgentClient` 显式用 Jackson 序列化并设置 `Content-Type: application/json`。 |
| Web 将 SUCCESS 任务的 `failureSummary` 误显示为失败卡 | Web 按状态/失败原因/报告链接判断失败卡片显示（由 Web 工作流修复）。 |

## 4. 手工验证

- Web 控制台 `http://127.0.0.1:5173`：登录、总览、用例、AI 分析、计划、运行报告、任务轨迹、设备池、系统设置各页面在真实后端数据下可用（由 Web 工作流以真实后端只读遍历验证，无运行时错误）。
- 服务端点抽检：未认证访问返回 401；OPS/Agent 令牌越权返回 403；Skills 同步失败返回 409；重复终止回调返回 409。

## 5. 未覆盖项（V1 已知边界）

- 真机 ADB：仅最小实现，未在真实设备上验证；投屏/scrcpy 未实现。
- 真实大模型：Provider 代码路径已实现，但未用真实 Key 做联网验收（默认 mock）。
- Redis 队列 / MinIO 产物：已在真实 Redis 7.4 / MinIO 上验收（见 §2.1）。
- Docker Compose：已提供，本次未在容器环境实测。
- 接口测试（JMeter）：不在 V1 范围，仅报告模型预留 `executorType=API`。
- Kafka 仅作为**任务生命周期事件流**（dispatch/result）接入，任务下发本身仍走 HTTP；把下发也迁到 Kafka 是后续工作。
- MeterSphere：已真实构建、启动，完成用例源、失败评论回写、计划结果回写；
  源码级内嵌完成首个垂直切片（见 §2.3），队列/报告/脚本执行器/前端尚未迁入。
- Sonic：已从提供的源码构建并引入 **driver-core（Apache-2.0）** 作为 `mode=sonic` 真机驱动；
  无真机/无 UIA2 服务，真机运行未验证，详见 `docs/SONIC-INTEGRATION.md`。
- 多实例/集群、Flyway、SSE 压测、轨迹生命周期归档未覆盖。

## 6. 复现命令

```bash
make setup
make up
make e2e          # 期望：RESULT: 47 checks passed, 0 failed
make check        # Java 106 + Python 48 测试 + Web 构建
make down

# 真实中间件模式（MySQL/Redis/MinIO/Kafka）
make infra
TESTHUB_INFRA=docker ./scripts/dev-up.sh
make e2e
make down && make infra-down
```

验收证据文件：`data/e2e-report.json`（每次运行覆盖）。
