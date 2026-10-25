# MeterSphere 源码级内嵌（ui-execution 模块）

> 目标：把 UI 执行编排从"外挂服务"变成 MeterSphere 源码的一部分：同一构建、同一进程、
> 复用 MeterSphere 的会话/权限、用例服务、计划执行服务与数据库。
>
> 上游：`metersphere-3.x`（Spring Boot 3.5.7 / Java 21）。源码补丁位于
> `integration/metersphere/ui-execution/`，不会污染本仓库的 Java 构建。

## 1. 模块位置与接线

```
upstream/metersphere/backend/services/ui-execution/     # 由安装脚本复制
├── pom.xml                                             # 依赖 test-plan / case-management / system-setting
├── src/main/java/io/metersphere/uiexec/
│   ├── UiExecutionProperties.java                      # metersphere.uiexec.*
│   ├── UiExecutionController.java                      # /test-plan/ui-execution/tasks
│   ├── UiExecutionService.java                         # 用例读取、下发、回调、结果回写
│   ├── DeviceAgentClient.java                          # 调 TestHub device-agent
│   └── UiExecutionDtos.java
└── src/main/resources/migration/3.7.0/ddl/V3.7.0_1__ui_execution.sql
```

安装（幂等）：

```bash
scripts/install-metersphere-module.sh upstream/metersphere
```

脚本做三件事：

1. 复制模块到 `backend/services/ui-execution`；
2. 在 `backend/services/pom.xml` 注册模块、在 `backend/app/pom.xml` 增加依赖；
3. 在 `FilterChainUtils` 放行 Agent 回调路径（`tasks/*/steps|result|logs`）。

构建（与 MeterSphere 一起）：

```bash
JAVA_HOME=<jdk21> mvn -Dmaven.repo.local=.m2/repository \
  -f upstream/metersphere/backend/pom.xml -DskipTests install
./scripts/metersphere-up.sh
```

## 2. 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/test-plan/ui-execution/tasks` | MS 会话 + `PROJECT_TEST_PLAN:READ+EXECUTE` | 入参 `{caseId, testPlanId, testPlanCaseId, projectId?, deviceSerial?, timeoutSec?, stepTimeoutSec?}` |
| GET | `/test-plan/ui-execution/tasks/{id}` | MS 会话 + `PROJECT_TEST_PLAN:READ` | 任务与步骤 |
| POST | `/test-plan/ui-execution/tasks/{id}/cancel` | MS 会话 + `PROJECT_TEST_PLAN:READ+EXECUTE` | 取消排队中/执行中的任务 |
| GET | `/test-plan/ui-execution/tasks/{id}/report` | MS 会话 + `PROJECT_TEST_PLAN:READ` | 失败轨迹报告 JSON |
| POST | `/test-plan/ui-execution/associate` | MS 会话 + `PROJECT_TEST_PLAN:READ+ASSOCIATION` | 关联功能用例到测试计划，返回 `testPlanCaseId` |
| POST | `/test-plan/ui-execution/tasks/{id}/steps` | `X-Agent-Token` | Agent 步骤回调 |
| POST | `/test-plan/ui-execution/tasks/{id}/result` | `X-Agent-Token` | Agent 结果回调 |
| POST | `/test-plan/ui-execution/tasks/{id}/logs` | `X-Agent-Token` | 日志（预留） |

用户接口走 Shiro 会话 + CSRF；Agent 回调在 Shiro 层匿名，但控制器做常量时间 token 校验。

## 3. 执行链路（同进程）

```
POST /test-plan/ui-execution/tasks
  → FunctionalCaseService.getFunctionalCaseDetail(caseId)      # MeterSphere 自己的用例服务
  → 映射 BDD（prerequisite / textDescription|steps / expectedResult）
  → 写 ui_execution_task（JdbcTemplate）
  → 设备空闲：立即 DISPATCHED；设备忙：PENDING + 排队位次（FIFO）
  → POST device-agent /agent/tasks（AI_RUNTIME，回调指向 MS）
  → Agent/Runtime 逐步 POST .../steps                         # 落 ui_execution_step
  → Agent POST .../result                                     # 更新任务 + 生成失败报告
  → dispatchNext(device)                                      # 串行执行下一个
  → TestPlanFunctionalCaseService.run(...)                    # 复用 MS 计划执行服务
  → MeterSphere 计划用例 lastExecResult + 执行历史
```

- **设备 FIFO**：同一 serial 上同时只有一个 `DISPATCHED/RUNNING`；其余 `PENDING` 按创建时间排序。
- **看门狗**：每 10s 扫描，`dispatched_at + timeout_sec + 15s` 仍未终止 → `TIMEOUT`，生成报告并推进队列。
- **失败轨迹报告**：非 SUCCESS 终止时生成结构化 JSON（环境、时间线、原因），存 `failure_report` 并提供 GET 接口。
- **取消**：排队中直接 `CANCELLED`；执行中向 Agent 发 `DELETE /agent/tasks/{id}` 后终止并推进队列。
- **计划关联**：`/associate` 复用 MS 的计划-用例关系（插入默认功能测试集），返回 `testPlanCaseId`。

状态映射：`SUCCESS→SUCCESS`、`FAILED→ERROR`、`TIMEOUT/CANCELLED→BLOCKED`。

## 4. 真实验证（2026-10-25）

Flyway 依次执行 `3.7.0.1 - ui execution`、`3.7.0.2 - ui execution queue`，表与队列字段成功创建。

| 场景 | 证据 |
|---|---|
| 基础执行 | 任务 `1707866188744261632` SUCCESS，5 步；计划用例 `1707773434630599999` = SUCCESS |
| 计划关联 | `/associate` 为用例 `1707933035615264768` 创建计划用例 `1707933035615264769` |
| 设备 FIFO | 同设备连发 3 个任务：`DISPATCHED` + `PENDING(1)` + `PENDING(2)`；最终 SUCCESS / CANCELLED / SUCCESS（串行） |
| 取消 | 第二个排队任务取消 → `CANCELLED`，生成报告链接，后续任务继续执行 |
| 失败报告 | 停止 Runtime 后任务 `1707933241773694976` FAILED，`GET .../report` 返回 JSON（reason=EXECUTOR_UNAVAILABLE），计划用例 = ERROR |
| 看门狗 | 任务 `1707933430752256000`（timeoutSec=2）在 Agent 停止后 17s 被标记 `TIMEOUT`，生成报告 |
| 会话自动登录 | core 仅配置 `TESTHUB_METERSHPHERE_USERNAME/PASSWORD`，运行 `run_95cd3b2578d14ec8b4f2` 自动创建会话并回写，计划用例 = SUCCESS |

> `ui-execution` 的 `GET /tasks/{id}` 会返回实时排队位次（`queuePosition`），`reportUrl` 指向失败报告。

## 5. 当前边界

- 只支持 AI_RUNTIME（传统脚本执行器尚未迁入）。
- 回调仅保存步骤摘要，**不保存截图/产物**；失败报告为 JSON 文本（不含截图）。
- 设备选择使用请求中的 serial（尚无自动选设备/标签匹配）。
- 未写模块级自动化测试；本次为真实进程联调验证。
- 外挂 ui-execution 的 MeterSphere 用例源仍需手工 token；core 的计划回写已支持自动登录。

## 6. 演进建议

1. 用 MS 的 MinIO 存储截图/产物，复用其文件服务；
2. 迁移脚本执行通道（传统 TestNG/uiautomator2）；
3. 增加设备自动选择（标签/空闲优先）；
4. 前端在 MeterSphere 计划详情增加"UI 执行"入口；
5. 移除对外挂 ui-execution 的依赖，最终实现文档中的"紧耦合"。
