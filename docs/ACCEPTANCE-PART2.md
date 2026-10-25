# 第二部分（接口自动化执行）验收报告

> 验收对象：接口用例模型与管理、独立部署的 api-execution 执行服务、测试计划混合编排、统一报告。
> 验收方式：`scripts/part2_smoke.py`（真实 HTTP 请求 + 真实执行服务）18 项检查。

## 1. 交付范围

| 能力 | 实现 |
|---|---|
| 接口用例 | `api_case` 表 + `/api/v1/api-cases` CRUD；方法、baseUrl/path、Headers、Query、Body、断言、超时、启停 |
| 独立执行服务 | `api-execution`，端口 8084，独立库 `testhub_apiexec`，可水平扩容 |
| 执行引擎 | Java HttpClient，异步 worker 池（默认 4），请求/响应/耗时/断言结果全量落库 |
| 断言 | STATUS_CODE、JSON_PATH、BODY_CONTAINS、RESPONSE_TIME_MS、HEADER_CONTAINS；EQ/NE/LT/GT/CONTAINS/NOT_CONTAINS |
| 生命周期 | PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED；排队取消、运行中取消、启动恢复 |
| 计划编排 | `plan_case.case_type=UI/API`；UI → ui-execution，API → api-execution；按配置 URL 轮询分发 |
| 统一报告 | 计划运行项 `executorType=API`，报告按执行器聚合；失败报告 URL 指回 api-execution |
| 回调 | 终止后回调 core `/internal/v1/plan-runs/items/result`（服务令牌，失败仅 WARN） |
| 前端 | 「接口用例」页面：列表/搜索/新建/编辑/删除/启停；计划「添加用例」支持选择接口用例 |

## 2. 部署

全栈（UI + API）：

```bash
make infra
make up            # core / ui-execution / api-execution / device-agent / ai-runtime
cd web && npm run dev
```

只启动接口执行服务：

```bash
./scripts/mvn.sh -q -pl api-execution -am -DskipTests package
SPRING_PROFILES_ACTIVE=mysql \
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/testhub_apiexec?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=testhub \
TESTHUB_CORE_URL=http://127.0.0.1:8080 \
TESTHUB_APIEXEC_PUBLIC_URL=http://127.0.0.1:8084 \
java -jar api-execution/target/api-execution-1.0.0.jar
```

主服务通过 `TESTHUB_API_EXECUTION_URLS`（逗号分隔）配置多个实例，轮询下发；对账会合并所有实例结果。

## 3. 验收结果

`python3 scripts/part2_smoke.py`：

```
PART 2 RESULT: 18 checks passed, 0 failed
```

| 组 | 检查项 | 结果 |
|---|---|---|
| 健康 | api-execution `/actuator/health` | 1/1 |
| 用例 | 列表、创建后可查询 | 2/2 |
| API-only 计划 | 计划项 SUCCESS、执行器 API、任务 SUCCESS、HTTP 200、断言通过、报告可读 | 6/6 |
| 失败路径 | 断言失败 → FAILED/ASSERTION_FAILED、失败报告 URL 可访问 | 4/4 |
| 超时路径 | `/slow` + timeout=1s → TIMEOUT | 2/2 |
| 混合计划 | UI + API 均 SUCCESS、路由到 AI_RUNTIME + API、报告聚合两类执行器 | 3/3 |

证据：

- API-only 运行：`run_1ae7e0cbca6e45359296`，计划 `plan_424bf63f469842b6a391`，用例 `ac_8e6f2329ca8149259e1c`
- 混合运行：`run_26375833571f4413bcd4`（UI=SUCCESS/AI_RUNTIME，API=SUCCESS/API）
- 失败报告：`failureReportUrl` 指向 api-execution `/report`；断言明细包含 expected/actual/passed

自动化测试：**189 个全绿**（Java 141 + Python 48），其中 api-execution 26 个、testhub-server 39 个。
`RestApiExecutionClientTest` 覆盖多 worker 对账 URL（修复了此前 `uriBuilder.path(绝对URL)` 导致的 `http:/...` 问题）。

## 4. 数据模型

```text
core: api_case(id, name, method, base_url, path, headers_json, query_json, body,
               assertions_json, timeout_sec, enabled, created_by, created_at, updated_at)
      plan_case(+ case_type UI/API)
      plan_run_item(executor_type 扩展 API)
apiexec: api_task(... request/response/assertions/status/report...)
```

`plan_run_item.executor_type` 与 `plan_case.executor_type_override` 旧版是 MySQL ENUM；
启动时 `SchemaCompatibility` 自动转 VARCHAR，兼容已存在的库。

## 5. 已知边界

- 无环境/变量集（baseUrl 直接写在用例里）、无鉴权预设/Token 自动获取。
- Body 仅文本（不支持 multipart/文件上传）；断言不支持 XML/XPath、JSON Schema。
- 无前置/后置脚本、无用例级重试、无数据驱动。
- 任务队列在实例内存 + DB，重启会把 RUNNING 标记 FAILED；跨实例队列依赖主服务轮询分发，不是 Redis 共享队列。
- 计划级并发是逐条创建任务（未做批量提交），大规模场景需要任务分片和限流。
- 接口用例无评审流（启用即可挂载），与 UI 用例的评审模型不同。
- Docker Compose 已加入 `api-execution` 服务，但未在容器环境实测。

## 6. 下一步

1. 环境变量与鉴权预设（登录接口提取 token，供后续接口引用）。
2. 数据驱动/CSV、前后置脚本、重试。
3. Redis 共享任务队列 + 多实例 worker 横向扩展。
4. XML/XPath、JSON Schema、契约校验。
5. 接口用例评审与版本管理。
