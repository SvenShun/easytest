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
  → POST device-agent /agent/tasks（AI_RUNTIME，回调指向 MS）
  → Agent/Runtime 逐步 POST .../steps                         # 落 ui_execution_step
  → Agent POST .../result                                     # 更新任务
  → TestPlanFunctionalCaseService.run(...)                    # 复用 MS 计划执行服务
  → MeterSphere 计划用例 lastExecResult + 执行历史
```

状态映射：`SUCCESS→SUCCESS`、`FAILED→ERROR`、`TIMEOUT/CANCELLED→BLOCKED`。

## 4. 真实验证（2026-10-25）

在 MeterSphere `:8082` 上以 admin 会话直接调用新接口：

```
POST /test-plan/ui-execution/tasks
{"caseId":"1707709216279502848","testPlanId":"1707773434630520832",
 "testPlanCaseId":"1707773434630599999","projectId":"100001100001","deviceSerial":"simulator-0001"}
→ task row 1707862409173041152
→ status SUCCESS, steps:
   1 OK click(登录)
   2 OK input(用户名)=tester
   3 OK input(密码)=secret
   4 OK click(登录)
   5 OK done
→ MeterSphere plan-case 1707773434630599999: lastExecResult=SUCCESS, lastExecTime 更新
```

Flyway 迁移 `3.7.0.1 - ui execution` 自动执行，`ui_execution_task` / `ui_execution_step` 建表成功。

## 5. 当前边界

- 仍是**单任务直发**：没有设备 FIFO 队列、看门狗、失败轨迹报告；这些仍由外挂 ui-execution 服务承担。
- 只支持 AI_RUNTIME（脚本执行器尚未迁入）。
- 回调仅保存步骤摘要，不保存截图/日志（外挂服务仍可承担产物）。
- 计划-用例关联仍依赖已有映射（本次用 SQL fixture 建立）。
- 未写模块级自动化测试；本次为真实进程联调验证。

## 6. 演进建议

1. 把 FIFO 队列/看门狗/失败报告迁移为 MS 模块内的 `ui_execution_*` 表 + 定时任务；
2. 用 MS 的 MinIO 存储截图与报告，复用其文件服务；
3. 迁移脚本执行通道（传统 TestNG/uiautomator2）；
4. 前端在 MeterSphere 计划详情增加"UI 执行"入口；
5. 移除对外挂 ui-execution 的依赖，最终实现文档中的"紧耦合"。
