# MeterSphere 接入说明（阶段 C）

> 上游：`metersphere/metersphere`，分支 `v3.x`，本文件基于 commit
> `53df526e2fb08bdf933662acf7eb5ae0f1bd5e3a`（Spring Boot 3.5.7）。
> 许可证：**GPLv3 + 附加条款**（见上游 `LICENSE`），分发衍生作品前必须评估合规性。

## 1. 现状：接入了什么，没接入什么

| 项 | 状态 | 说明 |
|---|---|---|
| 上游源码 | **未 vendor**（`upstream/` 已 gitignore） | `scripts/fetch-upstream.sh` 可拉取元数据/稀疏检出；完整仓库 427MB，本网络无法在可接受时间内完成 |
| 用例源适配 | **已实现** | `testhub.uiexec.case-source=metersphere` 时，ui-execution 通过 MeterSphere API 读取功能用例并映射为 BDD 上下文 |
| 失败分析回写 | **已实现** | 以“用例评论”形式写入 MeterSphere，不覆盖人工备注 |
| 执行计划/结果 | **未实现** | 尚未把 TestHub 计划运行映射为 MeterSphere 测试计划，也未调用 `/test-plan/functional/case/run` |
| 运行 MeterSphere | **未验证** | 需要其完整部署（MySQL + Flyway 迁移 + Redis + 其他组件），当前网络下未构建运行 |
| Sonic-Agent 源码 | **未接入** | `sonic-server` / `sonic-agent` 已归档（AGPL-3.0），设备层仍是等价实现 |

结论：**这是 API 级适配，不是源码级 fork/内嵌**。它符合架构文档“ui-execution 对上游 case-management/test-plan
暴露/调用接口”的服务边界，但不满足“直接复用 MeterSphere 代码”的更强诉求。

## 2. 上游接口与源码位置

| 用途 | MeterSphere 接口 | 源码（相对上游根目录） |
|---|---|---|
| 读取功能用例 | `GET /functional/case/detail/{id}` | `backend/services/case-management/src/main/java/io/metersphere/functional/controller/FunctionalCaseController.java` |
| 用例 DTO | — | `backend/services/case-management/.../functional/dto/FunctionalCaseDetailDTO.java` |
| 失败分析回写 | `POST /functional/case/comment/save` | `backend/services/case-management/.../functional/controller/FunctionalCaseCommentController.java` + `.../request/FunctionalCaseCommentRequest.java` |
| 测试计划执行结果 | `POST /test-plan/functional/case/run` | `backend/services/test-plan/.../plan/controller/TestPlanFunctionalCaseController.java` + `.../request/TestPlanCaseRunRequest.java` |
| 用例 AI 生成（参考） | `/functional/case/ai/chat`、`/transform`、`/batch/save` | `backend/services/case-management/.../controller/FunctionalCaseAIController.java` |
| 认证头 | `X-AUTH-TOKEN`（另有 `accessKey` + `signature`） | `backend/framework/sdk/.../constants/SessionConstants.java` |
| 默认端口 | `8081`（**与 TestHub ui-execution 冲突**） | `backend/app/src/main/resources/commons.properties` |

### 字段映射（无 BDD 原生模型，采用显式映射）

| BDD | MeterSphere 字段 | 兜底 |
|---|---|---|
| title | `name` | — |
| given | `prerequisite` | 已打开待测应用 |
| when | `textDescription` → `steps` | 执行用例 |
| then | `expectedResult` | 用例执行成功 |
| reviewStatus | 默认 `APPROVED`；开启 `require-review` 后仅白名单状态通过 | `COMPLETED` |

评审状态枚举来自 `CaseReviewStatus`：`PREPARED / UNDERWAY / COMPLETED / ARCHIVED`。

## 3. 配置

ui-execution：

| 变量 | 默认 | 说明 |
|---|---|---|
| `TESTHUB_CASE_SOURCE` | `testhub` | 改为 `metersphere` 启用适配 |
| `TESTHUB_METERSHPHERE_BASE_URL` | `http://127.0.0.1:8081` | MeterSphere 地址（注意端口冲突） |
| `TESTHUB_METERSHPHERE_TOKEN` | — | 登录后的 `X-AUTH-TOKEN`（或改用 accessKey/signature 的网关） |
| `TESTHUB_METERSHPHERE_PROJECT_ID` | — | 写评论必填 |
| `TESTHUB_METERSHPHERE_REQUIRE_REVIEW` | `false` | 是否按 MeterSphere 评审状态做执行门禁 |
| `TESTHUB_METERSHPHERE_APPROVED_STATUSES` | `COMPLETED` | 允许执行的状态（逗号分隔） |

TestHub 侧的模型配置、计划与报告仍由 `testhub-server` 提供；MeterSphere 模式下只替换“用例源 + 失败回写”。

## 4. 如何验证

自动化（无需 MeterSphere）：

```bash
./scripts/mvn.sh -pl ui-execution -Dtest=MeterSphereAdapterTest test
```

该测试用 JDK `HttpServer` 模拟上游两个接口，校验字段映射、评审门禁、404 与评论写回请求体。

真实联调（需要 MeterSphere 实例）：

```bash
scripts/fetch-upstream.sh --sparse     # 拉取上游源码用于对照/构建
# 按 MeterSphere 官方文档部署实例（MySQL + Redis + ...），拿到 projectId 和 X-AUTH-TOKEN
TESTHUB_CASE_SOURCE=metersphere \
TESTHUB_METERSHPHERE_BASE_URL=http://127.0.0.1:8082 \
TESTHUB_METERSHPHERE_TOKEN=... \
TESTHUB_METERSHPHERE_PROJECT_ID=... \
TESTHUB_INFRA=docker ./scripts/dev-up.sh
```

## 5. 未完成项与建议路径

1. **计划/结果映射**：需要一张 `plan_case` ↔ MeterSphere `testPlanCaseId` 的映射表，并调用
   `POST /test-plan/functional/case/run` 回写执行结果；这要求 MeterSphere 实例可运行。
2. **认证**：目前手工注入 `X-AUTH-TOKEN`；生产建议接入 MeterSphere 的 accessKey/signature，
   或在 `testhub-server` 做一次登录换取 token 并缓存。
3. **用例能力差异**：MeterSphere 功能用例的 `steps` 是 JSON 结构；如需完整步骤级执行，
   应按其步骤模型解析并映射为 AI Runtime 的 BDD 步骤，而不是拼接文本。
4. **源码级内嵌**：若必须把 ui-execution 做成 MeterSphere 的 Maven 模块，需要：
   - 完整检出 427MB 源码并在其 `backend/services` 下新增模块；
   - 复用其 `framework/sdk`、`domain`、鉴权与 Flyway 迁移；
   - 接受 GPLv3 附加条款；
   - 在可访问 Docker Hub 的环境跑通其部署栈。
   当前网络下无法完成，建议在具备带宽与容器仓库访问的环境执行。
5. **Sonic**：`sonic-server`/`sonic-agent` 已归档且为 AGPL-3.0，建议只借鉴设备层设计或使用其
   稳定版本二进制，而不是长期维护 fork。

## 6. 重要风险

- **许可证**：GPLv3 + 附加条款对闭源分发有强约束，必须由法务确认。
- **版本漂移**：适配器依赖字段名与路径；升级 MeterSphere 需回归 `MeterSphereAdapterTest` 并补充真实联调。
- **端口冲突**：两者默认都是 8081，必须改其一。
- **双事实源**：若同时使用 TestHub 与 MeterSphere 的用例库，需要明确哪边是权威源，避免 ID 漂移。
