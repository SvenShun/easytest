# TestHub V1 接口契约（实现基准）

> 本文件是 V1 前后端、微服务、Agent、Runtime 之间的**唯一事实来源**。字段名、枚举、路径、状态机以本文为准。
> 基线：`docs/V1-SCOPE.md`。所有服务默认 JSON（UTF-8），时间为 ISO-8601 UTC，ID 为 UUID 字符串。

## 0. 通用约定

| 项 | 约定 |
|---|---|
| 鉴权（用户） | `Authorization: Bearer <JWT>`，HS256，载荷 `sub/username/name/role/iat/exp` |
| 鉴权（服务间） | `X-Service-Token: <TESTHUB_SERVICE_TOKEN>`，仅 `/internal/**` |
| 鉴权（Agent） | `X-Agent-Token: <TESTHUB_AGENT_TOKEN>`，Agent 注册/心跳/回调与下发 |
| 默认拒绝 | `/api/**` 未标注公开即需登录；`/internal/**` 必须是服务令牌 |
| 错误体 | `{timestamp,status,code,message,path,details[]}`，HTTP 状态与 `code` 一致 |
| 分页 | `?page=1&size=20`，响应 `{items,total,page,size}` |
| 角色 | `ADMIN` `TESTER` `BUSINESS` `OPS` |
| 评审状态 | `DRAFT` `PENDING` `APPROVED` `REJECTED` |
| 用例来源 | `MANUAL` `AI` |
| 脚本类型 | `TESTNG` `UIAUTOMATOR2` `PYTEST` `CUSTOM` |
| 执行器 | `SCRIPT` `AI_RUNTIME`（报告模型另预留 `API`，V1 不产生） |
| 任务状态 | `PENDING` `DISPATCHED` `RUNNING` `SUCCESS` `FAILED` `TIMEOUT` `CANCELLED` |
| 设备状态 | `IDLE` `QUEUED` `SCRIPT_RUNNING` `AI_RUNNING` `OFFLINE` `MAINTENANCE` |
| 失败分类 | `MODEL_DECISION_FAILED` `ACTION_FAILED` `ELEMENT_NOT_FOUND` `TIMEOUT` `MANUAL_INTERRUPT` `EXECUTOR_UNAVAILABLE` `SKILLS_UNAVAILABLE` `SCRIPT_FAILED` |

服务地址（默认）：core `http://127.0.0.1:8080`，ui-execution `http://127.0.0.1:8081`，agent `http://127.0.0.1:8090`，ai-runtime `http://127.0.0.1:8095`。

---

## 1. testhub-server（core，8080）

### 1.1 认证

`POST /api/v1/auth/login`（公开）
```json
{"username":"tester","password":"test123"}
→ 200 {"token":"...","expiresInSec":43200,
        "user":{"id":"u-tester","username":"tester","displayName":"测试工程师","role":"TESTER"}}
```
失败：401 `INVALID_CREDENTIALS`。`GET /api/v1/auth/me` → user。
`GET /api/v1/system/users`（ADMIN）→ `{items:[user],total,page,size}`。

### 1.2 系统设置 / 模型配置（ADMIN）

`GET /api/v1/system/model-configs`
```json
{"items":[
  {"configType":"text","provider":"mock","endpoint":"","modelId":"mock-text","apiKeyMasked":"","configured":true,"updatedBy":"system","updatedAt":"..."},
  {"configType":"vision","provider":"mock","endpoint":"","modelId":"mock-vision","apiKeyMasked":"","configured":true,"updatedBy":"system","updatedAt":"..."}
]}
```
`PUT /api/v1/system/model-configs/{configType}`，`configType ∈ text|vision`
```json
{"provider":"openai|mock|deepseek","endpoint":"https://api.deepseek.com","apiKey":"sk-...","modelId":"deepseek-chat"}
→ 200 配置项（apiKey 仅返回掩码）
```
`apiKey` 为空字符串表示保留原密钥；`provider=mock` 时忽略 endpint/key。

`GET /api/v1/system/audit-logs?page=&size=&action=` → 分页，项 `{id,actor,action,target,detail,createdAt}`。

### 1.3 需求

`POST /api/v1/requirements` `{title,description}` → `{id,title,description,createdBy,createdAt}`
`GET /api/v1/requirements?keyword=&page=&size=` / `GET|PUT|DELETE /api/v1/requirements/{id}`

### 1.4 脚本工程

`POST /api/v1/scripts`（multipart/form-data）字段：`file`(zip)、`name`、`scriptType`、`entryCommand`（默认 `sh run.sh`）
→ `{id,name,scriptType,entryCommand,sizeBytes,createdBy,createdAt}`
`GET /api/v1/scripts` / `GET /api/v1/scripts/{id}` / `DELETE /api/v1/scripts/{id}`

### 1.5 用例（客户端前端用例）

`POST /api/v1/cases`（TESTER/ADMIN）
```json
{"title":"用户使用正确凭据登录",
 "given":"已打开 App 且处于未登录状态",
 "when":"输入用户名 tester 和密码 secret 并点击登录",
 "then":"进入首页并显示 登录成功",
 "requirementId":"req-1",
 "scriptId":null,
 "source":"MANUAL"}
```
`scriptId` 可空；非空时 `scriptType/scriptName` 由脚本工程带出。source 由服务端决定（人工=`MANUAL`）。

`GET /api/v1/cases?reviewStatus=&keyword=&requirementId=&page=&size=`
`GET /api/v1/cases/{id}` → 用例 + 能力：
```json
{"id":"case-1","title":"...","given":"...","when":"...","then":"...",
 "requirementId":"req-1","reviewStatus":"APPROVED","source":"MANUAL","modelId":null,
 "script":{"id":"s1","name":"login-testng.zip","scriptType":"TESTNG","entryCommand":"sh run.sh"},"scriptRef":null,
 "failureAnalysis":null,"failureReportUrl":null,
 "capability":{"availableExecutors":["AI_RUNTIME"],"defaultExecutor":"AI_RUNTIME"},
 "reviews":[{"id":"r1","action":"APPROVE","actor":"tester","comment":"ok","createdAt":"..."}],
 "createdBy":"tester","createdAt":"...","updatedAt":"..."}
```
`PUT /api/v1/cases/{id}` 同创建字段（评审中/已通过时允许编辑，V1 不锁定）。`DELETE /api/v1/cases/{id}`（有未完成计划引用时 409 `CASE_IN_USE`）。

`POST /api/v1/cases/{id}/review` `{action:"SUBMIT|APPROVE|REJECT","comment":"..."}`
- 合法流转：`DRAFT→PENDING`、`REJECTED→PENDING`（SUBMIT）；`PENDING→APPROVED|REJECTED`（APPROVE/REJECT）
- 非法流转 409 `INVALID_REVIEW_TRANSITION`；角色 TESTER/ADMIN。
`GET /api/v1/cases/{id}/reviews` → 评审记录数组。

### 1.6 AI 分析

`POST /api/v1/ai/bdd-drafts`（TESTER/ADMIN/BUSINESS）
```json
{"requirementId":"req-1","requirementText":"用户可以使用用户名密码登录","sampleCount":3}
→ 200 {"jobId":"job-1","status":"SUCCEEDED","modelId":"mock-text",
        "drafts":[{"caseId":"case-9","title":"用户使用正确凭据登录","given":"...","when":"...","then":"...","reviewStatus":"DRAFT"}]}
```
- 同步执行并把草稿写入用例库：`source=AI`、`reviewStatus=DRAFT`、`modelId` 记录。
- 无文本模型配置 → 409 `MODEL_NOT_CONFIGURED`。
- 记录 `ai_analysis_job`：`{id,requirementId,modelId,promptVersion,status,outputCaseIds,error,createdAt,finishedAt}`。
`GET /api/v1/ai/jobs?page=&size=` / `GET /api/v1/ai/jobs/{jobId}`。

### 1.7 测试计划

`POST /api/v1/plans` `{name,description}` → `{id,name,description,status,createdBy,createdAt}`
`GET /api/v1/plans?keyword=&page=&size=` / `GET|PUT|DELETE /api/v1/plans/{id}`
`POST /api/v1/plans/{id}/cases` `{caseIds:["c1"],executorTypeOverride:"AI_RUNTIME"}` → 挂载列表；非 APPROVED 用例 409 `CASE_NOT_APPROVED`。
`DELETE /api/v1/plans/{id}/cases/{caseId}`
`GET /api/v1/plans/{id}` → `{plan, cases:[planCase], runs:[run]}`，`planCase={caseId,title,reviewStatus,hasScript,scriptType,executorTypeOverride,defaultExecutor,availableExecutors}`

`POST /api/v1/plans/{id}/trigger`
```json
{"targetDeviceId":null,"targetTags":["android"],"timeoutSec":120,"stepTimeoutSec":30}
→ 200 {"runId":"run-1","items":[
   {"caseId":"c1","caseTitle":"...","taskId":"t1","taskStatus":"PENDING","executorType":"AI_RUNTIME","error":null}]}
```
- 触发时对每个已挂载用例调用 ui-execution `POST /api/v1/ui-execution/tasks`（服务令牌）。
- ui-execution 调用失败：item 记 `taskStatus=FAILED`、`error=...`，计划继续。
- 计划无已通过用例 → 409 `NO_EXECUTABLE_CASE`。

`GET /api/v1/plans/{id}/runs?page=&size=` → 分页 `[{id,planId,status,triggeredBy,startedAt,finishedAt,total,success,failed,timeout,cancelled,running,pending}]`

**MeterSphere 结果回写映射**（可选，TESTER/ADMIN）：

`PUT /api/v1/plans/{id}/metersphere` `{testPlanId}` → `PlanMappingView`
`PUT /api/v1/plans/{id}/cases/{caseId}/metersphere` `{metersphereCaseId, metersphereTestPlanCaseId}` → `PlanMappingView`
`GET /api/v1/plans/{id}/metersphere` → `{planId, metersphereTestPlanId, cases:[{caseId, metersphereCaseId, metersphereTestPlanCaseId}]}`

配置 `TESTHUB_METERSPHERE_WRITEBACK=true` 后，`POST /internal/v1/plan-runs/items/result` 处理完成时，
按映射调用 MeterSphere `POST /test-plan/functional/case/run`：

| TestHub item 状态 | MeterSphere `lastExecResult` |
|---|---|
| SUCCESS | SUCCESS |
| FAILED | ERROR |
| TIMEOUT / CANCELLED / SKIPPED | BLOCKED |
| 其他 | PENDING |

写回为 best-effort：MeterSphere 不可达只记录 WARN，不影响 TestHub 结果。请求需带 `X-AUTH-TOKEN`、`CSRF-TOKEN`、`PROJECT`。
`GET /api/v1/plan-runs/{runId}` → `{run, items:[{caseId,caseTitle,taskId,executorType,status,failureReason,failureReportUrl,summary,deviceId,updatedAt}]}`
`GET /api/v1/plan-runs/{runId}/report`
```json
{"run":{...},
 "summary":{"total":2,"success":1,"failed":1,"timeout":0,"cancelled":0,"running":0,"pending":0,"skipped":0},
 "executors":[{"executorType":"SCRIPT","total":1,"success":1,"failed":0},
              {"executorType":"AI_RUNTIME","total":1,"success":0,"failed":1}],
 "failureCategories":[{"category":"MODEL_DECISION_FAILED","count":1}],
 "items":[{"caseId":"c1","caseTitle":"...","taskId":"t1","executorType":"AI_RUNTIME","status":"FAILED",
           "failureReason":"MODEL_DECISION_FAILED","failureReportUrl":"http://.../artifacts/...","summary":"..."}]}
```
读取 `GET plan-runs/{runId}` 时，对未终止 item 主动向 ui-execution 对账（`GET tasks?planRunId=`）。

### 1.8 内部接口（`X-Service-Token`）

`GET /internal/v1/cases/{caseId}/execution-context`
```json
{"caseId":"c1","title":"...","reviewStatus":"APPROVED",
 "bdd":{"given":"...","when":"...","then":"..."},
 "script":{"id":"s1","scriptType":"TESTNG","name":"login.zip","entryCommand":"sh run.sh",
           "bundleUrl":"http://127.0.0.1:8080/internal/v1/scripts/s1/bundle?ticket=..."},
 "failureAnalysis":"..."}
```
无脚本时 `script=null`。`bundleUrl` 已含 15 分钟票据，Agent 可无鉴权下载。

`GET /internal/v1/scripts/{scriptId}/bundle?ticket=...` → zip 字节流（无服务令牌，票据校验；过期 401 `TICKET_EXPIRED`）。

`GET /internal/v1/model-configs/{configType}/credential`
```json
{"configType":"vision","provider":"mock","endpoint":"","apiKey":"","modelId":"mock-vision",
 "expiresAt":"2025-01-01T00:15:00Z"}
```
未配置 → 409 `MODEL_NOT_CONFIGURED`。

`PUT /internal/v1/cases/{caseId}/failure-analysis`
```json
{"taskId":"t1","reportUrl":"http://.../failure-report.json",
 "summary":"模型在第 4 步无法解析动作","reasonCategory":"MODEL_DECISION_FAILED"}
```
→ 204。写入独立字段，不覆盖人工备注。

`POST /internal/v1/plan-runs/items/result`
```json
{"planRunId":"run-1","caseId":"c1","taskId":"t1","status":"FAILED",
 "executorType":"AI_RUNTIME","failureReason":"MODEL_DECISION_FAILED",
 "failureReportUrl":"http://...","summary":"..."}
```
→ 204。幂等（同 taskId 覆盖为最新）。计划运行状态按 item 汇总。

`GET /internal/v1/health` → `{"status":"UP"}`

---

## 2. ui-execution（8081）

### 2.1 创建任务

`POST /api/v1/ui-execution/tasks`（JWT 或服务令牌）
```json
{"planId":"p1","planRunId":"run-1","caseId":"c1",
 "executorType":null,
 "targetDeviceSelector":{"deviceId":null,"agentId":null,"tags":["android"]},
 "timeoutSec":120,"stepTimeoutSec":30,"createdBy":"tester"}
→ 201 {"taskId":"t1","status":"PENDING","executorType":"AI_RUNTIME",
       "deviceId":"d1","deviceSerial":"emulator-5554","queuePosition":1,"createdAt":"..."}
```
路由规则见 `docs/V1-SCOPE.md` §4。`executorType` 非法组合 409 `EXECUTOR_UNAVAILABLE`；用例未通过 409 `CASE_NOT_APPROVED`。

### 2.2 查询

`GET /api/v1/ui-execution/tasks?planRunId=&planId=&status=&deviceId=&caseId=&page=&size=` → 分页 task 摘要。

`GET /api/v1/ui-execution/tasks/{taskId}`
```json
{"taskId":"t1","planId":"p1","planRunId":"run-1","caseId":"c1","caseTitle":"...",
 "bdd":{"given":"...","when":"...","then":"..."},
 "executorType":"AI_RUNTIME","status":"FAILED","failureReason":"MODEL_DECISION_FAILED",
 "failureSummary":"...","failureReportUrl":"http://...","queuePosition":0,
 "deviceId":"d1","deviceSerial":"emulator-5554","agentId":"a1",
 "skills":{"repo":"file:///...","commit":"abc123"},
 "timeoutSec":120,"stepTimeoutSec":30,
 "createdAt":"...","dispatchedAt":"...","startedAt":"...","finishedAt":"...",
 "steps":[{"stepId":"s1","seq":1,"status":"OK|FAIL","decision":"...","action":"click(登录)",
           "elapsedMs":1200,"screenshotUrl":"http://.../1.png","createdAt":"..."}],
 "logs":[{"seq":1,"level":"INFO","message":"...","createdAt":"..."}],
 "artifacts":[{"name":"junit.xml","type":"JUNIT","url":"http://..."}],
 "failureReport":{"reportUrl":"...","reasonCategory":"...","summary":"..."}}
```
`steps`、`logs` 默认各返回最多 500 条；另有：
`GET /tasks/{taskId}/steps`、`GET /tasks/{taskId}/logs?afterSeq=`、`GET /tasks/{taskId}/stream`（SSE，需 JWT；事件 `status`/`step`/`log`，JSON data）。
`POST /tasks/{taskId}/cancel`（JWT TESTER/ADMIN，或 Agent 令牌）→ `{taskId,status}`。语义见 SCOPE §5。

### 2.3 Agent 回调（`X-Agent-Token`）

`POST /tasks/{taskId}/steps`
```json
{"seq":1,"status":"OK","decision":"模型原文或脚本行","action":"click(登录)",
 "elapsedMs":1200,"screenshotBase64":"iVBOR...","screenshotContentType":"image/png"}
→ 201 {"stepId":"s1","screenshotUrl":"http://.../artifacts/t1/steps/1.png"}
```
截图上限 2MB（解码后），超限忽略并仍返回 step。seq 幂等：同 seq 覆盖。

`POST /tasks/{taskId}/logs` `{"lines":[{"level":"INFO","message":"..."}]}` → 204。

`POST /tasks/{taskId}/result`
```json
{"status":"FAILED","failureReason":"MODEL_DECISION_FAILED",
 "summary":"第 4 步模型输出无法解析","reasonCategory":"MODEL_DECISION_FAILED",
 "artifacts":[{"name":"junit.xml","type":"JUNIT","contentBase64":"..."}]}
→ 200 {"taskId":"t1","status":"FAILED","failureReportUrl":"http://..."}
```
仅接受终止态；重复回调 409 `TASK_ALREADY_FINISHED`。非 SUCCESS 触发失败报告生成 + 用例回写 + 计划回调。

### 2.4 Agent / 设备

`POST /api/v1/ui-execution/agents/register`（Agent 令牌）
```json
{"name":"device-agent-1","host":"mac.local","baseUrl":"http://127.0.0.1:8090",
 "devices":[{"serial":"emulator-5554","name":"Pixel 7","model":"sdk_gphone64","platform":"android",
             "tags":["android","pixel"],"online":true}]}
→ 200 {"agentId":"a1","devices":[{"deviceId":"d1","serial":"emulator-5554"}]}
```
同名幂等（更新 baseUrl 与设备）。`POST /agents/{agentId}/heartbeat` 同 `devices` → `{agentId,status,devices:[{deviceId,serial,online}]}`。

`GET /api/v1/ui-execution/agents` → `[{agentId,name,host,baseUrl,status,lastHeartbeat,devices:[device]}]`（JWT）
`GET /api/v1/ui-execution/devices?status=&keyword=`
```json
{"items":[{"deviceId":"d1","serial":"emulator-5554","name":"Pixel 7","model":"sdk_gphone64",
 "platform":"android","tags":["android"],"status":"AI_RUNNING","online":true,
 "currentTaskId":"t1","queueLength":0,"agentId":"a1","agentName":"device-agent-1","lastSeenAt":"..."}],
 "total":1,"page":1,"size":20}
```
`GET /api/v1/ui-execution/devices/{deviceId}/queue`
```json
{"deviceId":"d1","serial":"emulator-5554","queueLength":2,
 "tasks":[{"taskId":"t2","position":1,"caseTitle":"...","status":"PENDING","executorType":"SCRIPT","createdAt":"..."}]}
```

### 2.5 Skills 指针

`GET /api/v1/ui-execution/skills/pointer` → `{gitUrl,branch,activeCommit,updatedBy,updatedAt,syncedAt,configured}`
`PUT /api/v1/ui-execution/skills/pointer`（ADMIN）`{gitUrl,branch}` → 指针（`activeCommit` 不变）
`POST .../pointer/sync`（ADMIN）→ `{activeCommit,syncedAt}`；Git 不可达 409 `SKILLS_SYNC_FAILED`
`POST .../pointer/rollback`（ADMIN）`{commit}` → 指针
`GET /api/v1/ui-execution/audit-logs?page=&size=`（ADMIN）
`GET /api/v1/ui-execution/stats` → `{devices:{total,online,idle,running,offline},tasks:{pending,running,success,failed,timeout,cancelled},failureCategories:[{category,count}],skills:{configured,activeCommit}}`

### 2.6 下发协议（ui-execution → Agent）

`POST {agent.baseUrl}/agent/tasks`，头 `X-Agent-Token`
```json
{"taskId":"t1","executorType":"AI_RUNTIME","caseId":"c1","caseTitle":"...",
 "bdd":{"given":"...","when":"...","then":"..."},
 "script":{"id":"s1","scriptType":"TESTNG","name":"login.zip","entryCommand":"sh run.sh",
           "bundleUrl":"http://127.0.0.1:8080/internal/v1/scripts/s1/bundle?ticket=..."},
 "device":{"serial":"emulator-5554","platform":"android","tags":["android"]},
 "skills":{"repo":"file:///tmp/skills","commit":"abc123"},
 "timeouts":{"totalSec":120,"stepSec":30},
 "model":{"provider":"mock","endpoint":"","apiKey":"","modelId":"mock-vision"},
 "callbacks":{"stepUrl":"http://127.0.0.1:8081/api/v1/ui-execution/tasks/t1/steps",
              "logUrl":"http://127.0.0.1:8081/api/v1/ui-execution/tasks/t1/logs",
              "resultUrl":"http://127.0.0.1:8081/api/v1/ui-execution/tasks/t1/result",
              "token":"<agent-token>"}}
```
→ 202 `{"accepted":true}`。取消：`DELETE {agent.baseUrl}/agent/tasks/{taskId}` → 202 `{"cancelling":true}`。
Agent 其他：`GET /agent/tasks/{taskId}`、`GET /agent/devices`、`GET /agent/health`。

### 2.7 失败轨迹报告

JSON 上传到产物存储，`reportUrl` 指向 JSON：
```json
{"reportId":"...","taskId":"t1","caseId":"c1","caseTitle":"...","status":"FAILED",
 "reasonCategory":"MODEL_DECISION_FAILED","summary":"...",
 "generatedAt":"...","generatedBy":"ui-execution",
 "environment":{"executorType":"AI_RUNTIME","deviceId":"d1","deviceSerial":"emulator-5554",
                "agentId":"a1","skillsCommit":"abc123"},
 "timeline":[{"seq":1,"status":"OK","decision":"...","action":"...","elapsedMs":1200,"screenshotUrl":"..."}],
 "artifacts":[{"name":"junit.xml","type":"JUNIT","url":"..."}]}
```

---

## 3. device-agent（8090）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/tasks` | 接收下发（Agent 令牌），异步执行，202 |
| DELETE | `/agent/tasks/{taskId}` | 取消；Runtime 任务转发取消 |
| GET | `/agent/tasks/{taskId}` | `{taskId,status,executorType}` |
| GET | `/agent/devices` | 本机设备列表 |
| GET | `/agent/health` | `{status,mode,devices,runtimeReachable}` |

设备驱动（供 AI Runtime 本地调用）：
`GET /device/{serial}/screenshot` → `image/png`
`GET /device/{serial}/ui-tree` → `{width,height,screen:{app,title,texts:[],elements:[{id,text,type,clickable,editable,bounds:{x,y,width,height}}]}}`
`POST /device/{serial}/action`
```json
{"action":"click","target":{"text":"登录"}}
{"action":"input","target":{"text":"用户名"},"text":"tester"}
{"action":"swipe","from":{"x":500,"y":1500},"to":{"x":500,"y":400},"durationMs":300}
{"action":"back"} {"action":"home"} {"action":"launch","app":"com.testhub.demo"} {"action":"wait","ms":500}
→ {"ok":true,"message":"clicked 登录","screen":"home"}
```
动作失败（元素不存在等）→ `{"ok":false,"message":"element not found: ..."}`，HTTP 200（由 Runtime 分类）。
`POST /device/{serial}/reset` → 重置模拟设备。

执行器要求：
- `SCRIPT`：下载 `bundleUrl` zip → 解压到任务目录 → 在目录内执行 `entryCommand`（`sh -c`），注入环境变量 `TESTHUB_TASK_ID`、`TESTHUB_DEVICE_SERIAL`、`TESTHUB_OUTPUT_DIR`；逐行上报日志；解析 `**/TEST-*.xml` 或 `junit.xml` 生成步骤与产物；退出码 0 且无 failure/error → `SUCCESS`，否则 `FAILED`/`SCRIPT_FAILED`。
- `AI_RUNTIME`：解析 Skills（见 §5）→ `POST {runtime}/runtime/tasks` → 等待同步返回 → 以 `resultUrl` 上报终止状态。
- 未配置 runtime 或不可达 → `FAILED` + `EXECUTOR_UNAVAILABLE`。
- 取消：设置标志；进程执行器 `destroyForcibly`；Runtime 调 `POST /runtime/tasks/{id}/cancel`，等待其返回后上报 `CANCELLED`。

## 4. ai-runtime（8095，Python）

`POST /runtime/tasks`
```json
{"taskId":"t1","bdd":{"given":"...","when":"...","then":"..."},
 "skills":{"repo":"file:///tmp/skills","commit":"abc123","dir":"/abs/path/or/null"},
 "timeouts":{"totalSec":120,"stepSec":30},"maxSteps":20,
 "model":{"provider":"mock","endpoint":"","apiKey":"","modelId":"mock-vision"},
 "device":{"baseUrl":"http://127.0.0.1:8090","serial":"emulator-5554"},
 "callbacks":{"stepUrl":"http://127.0.0.1:8081/api/v1/ui-execution/tasks/t1/steps",
              "logUrl":"http://127.0.0.1:8081/api/v1/ui-execution/tasks/t1/logs",
              "token":"<agent-token>"}}
→ 200 {"taskId":"t1","status":"SUCCESS|FAILED|TIMEOUT|CANCELLED",
        "reasonCategory":"...|null","summary":"...","totalSteps":7,"elapsedMs":12345}
```
- 循环：截图 + UI 树 → 决策（模型）→ 解析动作 → 白名单校验 → `/device/{serial}/action` → 上报 step 回调。
- 动作白名单：`click` `input` `swipe` `back` `home` `launch` `wait` `done`；禁止卸载/清数据/设置类动作。
- 超时：总超时/单步超时；超时返回 `TIMEOUT`；取消返回 `CANCELLED`。
- `POST /runtime/tasks/{taskId}/cancel` → `{"cancelled":true}`。
- `GET /runtime/health` → `{status,version,providers,skillsCacheDir}`。
- Skills 注入：读取 `skills.dir` 下 `SKILLS.md`/`README.md` 文本（截断 8KB）作为系统提示片段；目录为空则跳过。

## 5. Skills 解析（Agent 侧）

1. 下发 payload `skills.commit` 为空 → 不加载 Skills（`skills=null`）。
2. 缓存目录 `{dataDir}/skills/{repoHash}/{commit}` 命中 → 直接使用。
3. 未命中 → `git fetch --depth 1 origin {commit}` + `git worktree`/`git archive` 导出到缓存目录。
4. Git 不可达 → 使用该 repo 最近一次成功缓存 commit，并在日志/报告中标注降级；无缓存 → `FAILED` + `SKILLS_UNAVAILABLE`。
5. 不落库 Skills 内容。

## 6. 任务状态机（ui-execution）

```
PENDING ──dispatch──> DISPATCHED ──agent ack/first step──> RUNNING
   │                        │                                 │
   │                        ├──> SUCCESS/FAILED/TIMEOUT/CANCELLED
   │                        └──> FAILED(EXECUTOR_UNAVAILABLE)
   ├──cancel──> CANCELLED
   └──watchdog(timeout)──> TIMEOUT
```
- `RUNNING` 进入条件：收到首个 step 回调或 result；V1 允许 `DISPATCHED` 直接到终止态。
- 看门狗：每 10s 扫描 `DISPATCHED/RUNNING` 且 `now > dispatchedAt + timeoutSec + 15s` → `TIMEOUT`（写失败报告、回写、计划回调）。
- 状态只允许单向流转；重复终止回调 409。

## 7. 计划运行状态

`RUNNING` → 所有 item 终止后：全 SUCCESS=`COMPLETED`，有失败/超时/取消=`PARTIAL`，全部失败=`FAILED`。
item 状态与任务状态同名；未通过评审的用例不创建任务，item=`SKIPPED`。


---

## 附录 A. Kafka 任务事件（`TESTHUB_EVENT_BUS=kafka`）

| Topic | 生产时机 | Key |
|---|---|---|
| `ui-execution.task.dispatch` | 任务成功下发到 Agent 后 | `taskId` |
| `ui-execution.task.result` | 任务进入终止态（SUCCESS/FAILED/TIMEOUT/CANCELLED） | `taskId` |

事件体（JSON，UTF-8）：

```json
{"eventId":"evt_...","type":"TASK_DISPATCHED|TASK_FINISHED",
 "taskId":"...","planId":"...","planRunId":"...","caseId":"...","caseTitle":"...",
 "executorType":"SCRIPT|AI_RUNTIME","status":"DISPATCHED|SUCCESS|FAILED|TIMEOUT|CANCELLED",
 "deviceId":"...","deviceSerial":"...","agentId":"...",
 "failureReason":"...|null","failureReportUrl":"...|null","summary":"...|null",
 "occurredAt":"2025-01-01T00:00:00Z"}
```

- 发送失败只记录 WARN，不影响任务执行。
- V1 中 Kafka 不是任务下发通道；设备下发仍是 HTTP（`POST /agent/tasks`）。
