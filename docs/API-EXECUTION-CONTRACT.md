# 第二部分：接口自动化执行契约（V1）

> 覆盖主服务接口用例模型、独立 api-execution 执行服务、计划混合挂载与统一报告。

## 1. 部署形态

- `api-execution` 为独立 Spring Boot 服务，默认端口 **8084**，库 `testhub_apiexec`。
- 可水平扩容：主服务配置多个 base URL（`TESTHUB_API_EXECUTION_URLS`，逗号分隔），按轮询分发任务。
- 执行器无状态于业务库之外；任务落自己的库，重启后未完成任务标记 FAILED（V1）。
- 主服务与执行服务之间使用 `X-Service-Token`；回调走 `/internal/v1/plan-runs/items/result`。

## 2. 接口用例（主服务）

```http
POST /api/v1/api-cases
{
  "name": "登录接口",
  "method": "POST",
  "baseUrl": "http://127.0.0.1:9099",
  "path": "/login",
  "headers": {"Content-Type": "application/json"},
  "query": {"lang": "zh"},
  "body": "{\"username\":\"tester\",\"password\":\"secret\"}",
  "assertions": [
    {"type":"STATUS_CODE","operator":"EQ","expected":"200"},
    {"type":"JSON_PATH","path":"$.code","operator":"EQ","expected":"0"},
    {"type":"BODY_CONTAINS","operator":"CONTAINS","expected":"success"},
    {"type":"RESPONSE_TIME_MS","operator":"LT","expected":"2000"}
  ],
  "timeoutSec": 30,
  "enabled": true
}
→ {id, name, method, baseUrl, path, ..., createdAt, updatedAt}
```

- `GET /api/v1/api-cases?keyword=&page=&size=`
- `GET|PUT|DELETE /api/v1/api-cases/{id}`
- 写操作 TESTER/ADMIN；读所有登录角色。
- headers/query 为字符串 map；body 为原始文本；assertions 为数组（见 §4）。

## 3. api-execution 接口

### 3.1 创建任务

`POST /api/v1/api-execution/tasks`（服务令牌或 JWT）

```json
{
  "planId": "p1", "planRunId": "run-1",
  "caseId": "api-case-1", "caseName": "登录接口",
  "request": {
    "method": "POST",
    "baseUrl": "http://127.0.0.1:9099",
    "path": "/login",
    "headers": {"Content-Type": "application/json"},
    "query": {"lang": "zh"},
    "body": "{\"username\":\"tester\"}",
    "timeoutSec": 30
  },
  "assertions": [{"type":"STATUS_CODE","operator":"EQ","expected":"200"}],
  "createdBy": "tester"
}
→ 201 {"taskId":"api-task-1","status":"PENDING"}
```

### 3.2 查询/取消

- `GET /api/v1/api-execution/tasks/{id}` → 任务详情 + 请求/响应 + `assertionResults` + 状态。
- `GET /api/v1/api-execution/tasks?planRunId=&status=&page=&size=`
- `POST /api/v1/api-execution/tasks/{id}/cancel` → `{taskId,status}`
- `GET /api/v1/api-execution/tasks/{id}/report` → 失败/成功报告 JSON（需要 JWT 或服务令牌）
- `GET /api/v1/api-execution/stats` → `{pending,running,success,failed,timeout,cancelled,workers}`

状态：`PENDING` `RUNNING` `SUCCESS` `FAILED` `TIMEOUT` `CANCELLED`。

### 3.3 回调主服务

```
POST {core}/internal/v1/plan-runs/items/result
X-Service-Token: ...
{
  "planRunId": "run-1", "caseId": "api-case-1", "taskId": "api-task-1",
  "status": "SUCCESS|FAILED|TIMEOUT|CANCELLED", "executorType": "API",
  "failureReason": "ASSERTION_FAILED|HTTP_ERROR|TIMEOUT|CANCELLED|null",
  "failureReportUrl": "http://127.0.0.1:8084/api/v1/api-execution/tasks/api-task-1/report",
  "summary": "3/3 assertions passed, HTTP 200, 123ms"
}
```

回调失败只记录 WARN，任务状态仍保留在 api-execution。

## 4. 断言

| type | 字段 | 说明 |
|---|---|---|
| `STATUS_CODE` | `operator`(EQ/NE/LT/GT), `expected` | HTTP 状态码 |
| `JSON_PATH` | `path`(如 `$.code`), `operator`, `expected` | 响应 JSON 路径 |
| `BODY_CONTAINS` | `operator`(CONTAINS/NOT_CONTAINS), `expected` | 响应文本包含 |
| `RESPONSE_TIME_MS` | `operator`(LT/GT), `expected` | 耗时毫秒 |
| `HEADER_CONTAINS` | `path`(header 名), `operator`, `expected` | 响应头 |

全部断言通过 → SUCCESS；任一失败 → FAILED（`ASSERTION_FAILED`）。
请求异常/非 2xx 且没有显式状态断言 → FAILED（`HTTP_ERROR`）；超时 → TIMEOUT。

## 5. 报告

```json
{
  "taskId":"api-task-1","caseId":"api-case-1","caseName":"登录接口",
  "status":"FAILED","failureReason":"ASSERTION_FAILED",
  "request":{"method":"POST","url":"http://.../login","headers":{},"body":"..."},
  "response":{"status":500,"headers":{},"body":"...","durationMs":123},
  "assertions":[{"type":"STATUS_CODE","expected":"200","actual":"500","passed":false}],
  "summary":"1/1 assertions failed, HTTP 500, 123ms",
  "createdAt":..., "startedAt":..., "finishedAt":...
}
```

## 6. 计划混合编排

- `plan_case.case_type`：`UI`（默认）或 `API`。
- `POST /api/v1/plans/{id}/cases` 支持 `{caseIds:[...], apiCaseIds:[...], executorTypeOverride?}`。
- 触发时：UI 用例 → `ui-execution`；API 用例 → `api-execution`（按配置轮询）。
- 计划报告 `executors` 中新增 `API`；运行项 `executorType=API`。
- API 用例要求 `enabled=true`，否则 item 记 SKIPPED。
