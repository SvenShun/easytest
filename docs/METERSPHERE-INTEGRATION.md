# MeterSphere 接入说明与联调记录

> 上游源码：`/Users/wen/Workspace/TestPlatform/metersphere/metersphere-3.x`（构建副本：`upstream/metersphere/`，已 gitignore）
> 版本：`metersphere-3.x`，Spring Boot **3.5.7**，Java **21**
> 许可证：**GPLv3 + 附加条款**（见上游 `LICENSE`），分发衍生作品前必须评估合规性。

## 1. 当前状态

| 项 | 状态 | 说明 |
|---|---|---|
| 源码获取 | 完成 | 使用你提供的本地源码包（158MB），已复制到工作区（沙箱只允许写工作区） |
| 后端构建 | 完成 | `app-3.x.jar`（293MB），JDK 21 + 阿里云 Maven 镜像 |
| 真实运行 | 完成 | MeterSphere 运行在 `:8082`（默认 8081 与 ui-execution 冲突，已改端口） |
| 用例源适配 | **已真实验证** | ui-execution 从 MeterSphere 读取功能用例，映射为 BDD 并驱动 AI Runtime 执行 |
| 失败回写 | **已真实验证** | 失败时以“用例评论”写回 MeterSphere，追加式，不覆盖人工备注 |
| 计划/结果回写 | **已真实验证** | TestHub 计划运行结果通过映射写回 MeterSphere 测试计划（SUCCESS→SUCCESS、FAILED→ERROR） |
| 源码级内嵌 | 未实现 | 目前是 API 级适配；把 ui-execution 做成 MeterSphere Maven 模块仍需合并构建与依赖治理 |

**结论**：MeterSphere 已真实构建、启动并完成“用例源 + 失败回写”的端到端联调；距离文档中“紧耦合融合/源码内嵌”仍有计划映射和模块内嵌两步。

## 2. 构建与运行（可复现）

### 2.1 前置

- JDK 21（本机：`/Users/wen/.workbuddy/binaries/java/jdk-21.jdk/Contents/Home`）
- MySQL / Redis / Kafka / MinIO：`make infra`（已有）
- Maven 镜像：`~/.m2/settings.xml` 的阿里云配置；工作区本地仓库 `.m2/repository`

### 2.2 构建

```bash
cp -R /Users/wen/Workspace/TestPlatform/metersphere/metersphere-3.x upstream/metersphere
make ms-build
```

构建中的两个必要处理（已在脚本中固化）：

1. `frontend/dist` 必须存在：后端 `system-setting` 的 antrun 会把它复制到测试资源，缺失即 `BUILD FAILURE`。
2. 使用 JDK 21；JDK 17 无法满足 MeterSphere 的 `release=21`。

### 2.3 启动

```bash
make ms-up          # 默认 :8082，管理端口 :7171
make ms-down
```

启动参数（见 `scripts/metersphere-up.sh`）：

| 配置 | 值 |
|---|---|
| DB | `jdbc:mysql://127.0.0.1:3306/metersphere`，root/testhub，**库 collation 必须 `utf8mb4_general_ci`** |
| Redis | Redisson 配置 `deploy/metersphere/redisson.yml`（single server, db 0） |
| Kafka | `127.0.0.1:9092` |
| MinIO | `http://127.0.0.1:9000`，testhub/testhub123 |
| 日志 | 覆盖 `logback-spring.xml`（上游默认写 `/opt/metersphere/logs`，本地不可写） |

> 首次启动 Flyway 会自动建表；数据库 collation 不一致会报
> `Illegal mix of collations (utf8mb4_unicode_ci) and (utf8mb4_general_ci)`。

### 2.4 默认账号与数据

- 账号：`admin / metersphere`（Flyway 初始化，密码为 `MD5('metersphere')`）
- 默认组织/项目：`100001` / `100001100001`（示例项目）
- 登录接口返回 `sessionId` 与 `csrfToken`；后续请求必须同时带：

```
X-AUTH-TOKEN: <sessionId>
CSRF-TOKEN:   <csrfToken>
PROJECT:      100001100001
```

`make ms-token` 可直接输出 ui-execution 需要的环境变量。

## 3. 适配层实现

| 用途 | MeterSphere 接口 | 源码位置（上游） |
|---|---|---|
| 读取功能用例 | `GET /functional/case/detail/{id}` | `backend/services/case-management/.../FunctionalCaseController.java` |
| 失败回写 | `POST /functional/case/comment/save` | `.../FunctionalCaseCommentController.java` + `FunctionalCaseCommentRequest` |
| 计划结果（待接） | `POST /test-plan/functional/case/run` | `backend/services/test-plan/.../TestPlanFunctionalCaseController.java` |
| 认证 | `X-AUTH-TOKEN` + `CSRF-TOKEN` | `SessionConstants`、`SessionConfig`、`CsrfFilter` |

映射（MeterSphere 无原生 BDD 模型）：

| BDD | MeterSphere 字段 | 兜底 |
|---|---|---|
| title | `name` | — |
| given | `prerequisite` | 已打开待测应用 |
| when | `textDescription` → `steps` | 执行用例 |
| then | `expectedResult` | 用例执行成功 |
| reviewStatus | 默认 APPROVED；开启 `require-review` 后按白名单 | `COMPLETED` |

代码位置：

- `ui-execution/src/main/java/com/testhub/uiexec/client/MeterSphereCaseContextSource.java`
- `ui-execution/src/main/java/com/testhub/uiexec/client/MeterSphereFailureAnalysisSink.java`
- 测试：`MeterSphereAdapterTest`（stub 服务，覆盖包装解包、CSRF 头、映射、门禁、404、评论体）

## 4. 真实联调记录

| 步骤 | 结果 |
|---|---|
| MeterSphere 启动 | `:8082` 可访问，Flyway 建表成功，Kafka/MinIO/Redis 连接正常 |
| 通过 API 创建功能用例 | caseId `1707709216279502848`，`reviewStatus=UN_REVIEWED` |
| ui-execution 以 `case-source=metersphere` 创建任务 | 成功，任务标题/BDD 均来自 MeterSphere |
| AI Runtime 执行 | `task_c40a656d96a34726a330` **SUCCESS**，5 步：click(登录) → input(用户名) → input(密码) → click(登录) → done |
| 关闭 Runtime 制造失败 | `task_311b90a11dac4b3eb25e` **FAILED / EXECUTOR_UNAVAILABLE** |
| 失败回写 MeterSphere | 评论 id `1707710899906691072`，内容含失败分类、摘要、失败报告 URL、任务 ID |
| 恢复默认模式回归 | `make e2e` 47/47 通过（TestHub 用例源不受影响） |

## 4.1 计划结果回写（已实现并验证）

映射 API（TestHub 侧）：

```http
PUT /api/v1/plans/{planId}/metersphere
{"testPlanId":"1707773434630520832"}

PUT /api/v1/plans/{planId}/cases/{caseId}/metersphere
{"metersphereCaseId":"1707709216279502848","metersphereTestPlanCaseId":"1707773434630599999"}
```

服务端在 `POST /internal/v1/plan-runs/items/result` 处理后，按映射调用
`POST /test-plan/functional/case/run`：

| TestHub item | MeterSphere |
|---|---|
| SUCCESS | SUCCESS |
| FAILED | ERROR |
| TIMEOUT / CANCELLED / SKIPPED | BLOCKED |

配置：

```bash
TESTHUB_METERSPHERE_WRITEBACK=true
TESTHUB_METERSHPHERE_BASE_URL=http://127.0.0.1:8082
TESTHUB_METERSHPHERE_PROJECT_ID=100001100001
TESTHUB_METERSHPHERE_TOKEN=...      # make ms-token
TESTHUB_METERSHPHERE_CSRF_TOKEN=...
```

**实测结果**：

| 场景 | TestHub | MeterSphere |
|---|---|---|
| 成功 | 计划运行 `run_bd27529cc54048ae89ca` → SUCCESS | plan-case `1707773434630599999` → `SUCCESS`，执行历史落库 |
| 失败（脚本失败） | 任务 `task_8929962a82c7404ca949` → FAILED / SCRIPT_FAILED | plan-case `17077734346124044` → `ERROR`，执行历史落库 |

> 关联 fixture：MeterSphere v3 的计划-用例关联走 Minder 接口，参数较多；本次联调用 SQL 插入
> `test_plan_functional_case` 行建立关联（真实 API 用于读取计划用例和写回结果）。
> 生产化应改用其 Minder/关联接口或由 MeterSphere UI 完成关联，再通过映射 API 登记 ID。

## 5. 使用方式

```bash
make ms-up
eval "$(make -s ms-token)"          # 导出 TESTHUB_* 会话变量
TESTHUB_INFRA=docker TESTHUB_CASE_SOURCE=metersphere ./scripts/dev-up.sh
```

或在已运行的服务上只重启 ui-execution：

```bash
# 停止 ui-execution 后重新启动（core/agent/runtime 保留）
```

> 注意：MeterSphere 会话默认 12h 过期；当前需要手工刷新 token（`make ms-token`）。
> 生产化建议改用 accessKey + signature，或在适配层加入自动登录与 401 重试。

## 6. 未完成项

1. **计划/结果映射**：`plan_case` ↔ MeterSphere `testPlanCaseId`，调用 `/test-plan/functional/case/run` 写回执行结果。
2. **会话自动化**：登录、缓存、401 自动刷新（当前手工注入 token）。
3. **步骤模型**：MeterSphere 的 `steps` 是结构化 JSON，目前整段作为 `when` 文本；如要步骤级执行需解析为 BDD 步骤。
4. **源码级内嵌**：把 ui-execution 作为 `backend/services` 新模块，复用 MeterSphere 的 `framework/sdk`、鉴权与 Flyway；
   工作量最大，且需接受 GPLv3 附加条款。
5. **前端**：未构建/托管 MeterSphere 前端（只跑后端 API）；需要完整 UI 时执行其 `frontend` 构建。

## 7. 风险

- **许可证**：GPLv3 + 附加条款，闭源分发需法务确认。
- **版本漂移**：字段/路径/CSRF 行为变化需回归 `MeterSphereAdapterTest` 与真实联调。
- **端口冲突**：两者默认 8081，本方案将 MeterSphere 改到 8082。
- **双事实源**：同时使用 TestHub 与 MeterSphere 用例库时，需明确权威源与 ID 映射，避免漂移。
