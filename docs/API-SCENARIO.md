# 接口自动化场景（YAML 驱动）

## 背景与 MeterSphere 对照

MeterSphere 的场景能力基于 JMeter：

- 场景（`ApiScenario`）+ 有序步骤（`ApiScenarioStep`/`StepBlob`）
- 步骤类型：接口/接口用例/自定义请求/子场景、循环控制器、条件控制器、一次控制器、等待、脚本、JMeter 组件
- 前后置处理器：SQL、BeanShell/Groovy/JSR223 脚本
- 变量/CSV、断言、报告/通知
- 执行引擎是 JMeter，TestHub 不适合直接嵌入这套引擎。

TestHub 采用 **YAML 作为用例载体 + 自研轻量驱动器**，复用现有 `api-execution` 的队列、HTTP 客户端、断言引擎和计划回调；支持同样的核心能力（HTTP/SQL/脚本/变量/断言），但更易读、易 diff、易生成。

## YAML 格式

```yaml
name: login-and-query
timeoutSec: 120
continueOnFailure: false
variables:
  username: tester
  password: test123
environment:
  baseUrl: http://127.0.0.1:8080
  headers: {}
  variables: {}
  datasources:
    main:
      url: jdbc:mysql://127.0.0.1:3306/demo?useSSL=false
      username: root
      password: testhub
steps:
  - name: 登录
    type: http
    method: POST
    path: /api/v1/auth/login
    headers: { Content-Type: application/json }
    body: { username: "${username}", password: "${password}" }
    extract:
      token: $.token
      userId: $.user.id
    assertions:
      - { type: STATUS_CODE, operator: EQ, expected: 200 }
      - { type: JSON_PATH, path: $.user.username, operator: EQ, expected: tester }

  - name: 查询数据库
    type: sql
    datasource: main
    query: SELECT id, name FROM users WHERE name = ?
    params: ["${username}"]
    extract: { dbId: "rows[0].id" }
    assertions:
      - { type: SQL_ROW_COUNT, operator: GT, expected: 0 }
      - { type: SQL_VALUE, path: rows[0].name, operator: EQ, expected: tester }

  - name: 自定义脚本
    type: groovy
    script: |
      vars.put("combined", vars.get("token") + "-" + vars.get("dbId"))
      log.info("combined=" + vars.get("combined"))

  - name: 校验变量
    type: assert
    assertions:
      - { type: VARIABLE, path: combined, operator: CONTAINS, expected: "-" }

  - name: 调用业务接口
    type: http
    method: GET
    path: /api/v1/profile
    headers: { Authorization: "Bearer ${token}" }
    assertions:
      - { type: STATUS_CODE, operator: EQ, expected: 200 }
```

### 步骤类型

| type | 能力 |
|---|---|
| `http` | method/path/url、headers、query、body、timeout、extract、assertions |
| `sql` | datasource、query、params、extract（rows/rowCount/updateCount）、SQL 断言 |
| `groovy` | `vars`、`env`、`log`、`json` 绑定；可读写变量 |
| `set` | 设置变量（支持模板） |
| `assert` | 变量/值比较：EQ/NE/CONTAINS/NOT_CONTAINS/GT/GE/LT/LE |
| `wait` | 等待毫秒 |
| `if` | 条件分支：`condition: "${flag} == yes"`，`steps` / `elseSteps` |
| `loop` | 循环：`times` / `items` / `dataset`，`indexVar` / `itemVar`，子 `steps` |
| `once` | 只执行一次的子步骤组 |
| `while` | 每轮后重新计算条件；`maxIterations` 默认 20 |

### 数据驱动示例

```yaml
datasets:
  users:
    - { username: tester, password: test123 }
    - { username: admin, password: admin123 }
steps:
  - name: iterate users
    type: loop
    dataset: users
    indexVar: idx
    itemVar: row
    steps:
      - name: login
        type: http
        method: POST
        path: /api/v1/auth/login
        body: { username: "${username}", password: "${password}" }
        extract: { token: $.token }
```

控制器在运行时展开为普通步骤执行，报告会保留每次迭代的步骤明细（包括合成的 `loop N` 变量设置步骤）。

### 前置/后置处理器

任意步骤可带 `before` / `after` 子步骤，按顺序在主体步骤前/后执行，共享变量：

```yaml
- name: login
  type: http
  method: POST
  path: /api/v1/auth/login
  before:
    - {name: prepare, type: set, variables: {marker: before-ok}}
  after:
    - name: verify
      type: assert
      assertions:
        - {type: VARIABLE, path: marker, operator: EQ, expected: before-ok}
```

### CSV 导入

API 场景编辑器提供「导入 CSV 数据集」：选择 CSV 后自动写入 YAML 的 `datasets:` 段，loop 用 `dataset: 名称` 引用。支持带引号的 CSV 单元格。

### 数据传递

- `${var}`、`{{ var }}` 在请求、SQL、断言、脚本前后文生效。
- HTTP `extract` 支持 JSONPath、`header:Name`、`status`、`body`。
- SQL `extract` 支持 `rows[0].id`、`rowCount`、`updateCount`。
- 提取结果写入变量，后续步骤直接引用。
- `report` 中会输出 `extracted` 和步骤级变量；`env` 不写入报告，避免泄漏 datasource 密码。

### 断言

HTTP 复用现有断言：`STATUS_CODE / JSON_PATH / XML_PATH / JSON_SCHEMA / BODY_CONTAINS / RESPONSE_TIME_MS / HEADER_CONTAINS`。
XML_PATH 的 `path` 是 XPath，`JSON_SCHEMA` 的 `expected` 是 JSON Schema 文本、`operator` 用 `VALID`/`INVALID`。
SQL：`SQL_ROW_COUNT`、`SQL_VALUE`。变量：`VARIABLE`。任一断言失败默认终止场景；可设置 `continueOnFailure: true` 或步骤级覆盖。

## 执行链路

```
POST /api/v1/api-scenarios/{id}/run
  → core 解析环境/baseUrl/headers/variables
  → api-execution POST /scenarios
  → Redis/内存队列 scenario:<taskId>
  → worker 认领 → YAML 解析 → 顺序执行 → 报告
  → core 轮询 /api-scenarios/runs/{runId}
```

API：

```
POST /api/v1/api-scenarios/validate
POST /api/v1/api-scenarios
GET  /api/v1/api-scenarios
POST /api/v1/api-scenarios/{id}/run
GET  /api/v1/api-scenarios/runs/{runId}
GET  /api/v1/api-scenarios/{id}/runs
```

## 批量运行与导入导出

- `POST /api/v1/api-scenarios/batch-run`：按场景 ID 批量启动，返回每个场景的 runId/状态。
- 前端「批量运行」弹窗支持多选、环境覆盖、超时。
- `GET /api/v1/api-scenarios/{id}/export`：导出 YAML；前端「导出」直接下载 `.yaml`。
- `POST /api/v1/api-scenarios/import`：导入 YAML 场景；前端「导入」支持文件或粘贴。

## 场景通知

独立场景/定时场景运行会在 core 后台轮询并发送通知机器人消息（event `RUN_COMPLETED`, `kind=SCENARIO`），无需打开页面。计划中的场景仍由计划运行通知。

## 直接定时

「定时任务」页新增 **API 场景** 目标选择，可直接按 Cron 运行 YAML 场景，无需先挂计划；运行 ID 指向场景运行历史。

## 与 JMeter 的差异

- 没有 JMeter 线程组/监听器/控制器树；当前是顺序步骤 + 步骤级失败策略。
- 无 CSV 数据集、循环/条件控制器、分布式压测；后续按需增加。
- Groovy 为本地脚本执行，非沙箱；仅 ADMIN/TESTER 可运行。
- 已支持挂载到测试计划：计划用例类型新增 `SCENARIO`，执行项由 api-execution 场景任务回调更新，计划报告可汇总。

## 安全

- 场景编辑器仅 ADMIN/TESTER 可写/运行。
- SQL 使用 `PreparedStatement` 参数绑定；`query` 仍由作者提供，平台按测试代码信任。
- 报告脱敏环境 datasource，不返回 `env`。

## 结构化编辑

API 场景编辑器提供两个视图：

- **表单编辑**：从 YAML 解析步骤树，支持添加/删除/上移下移、HTTP/SQL/Groovy/set/assert/wait/if/loop/once/while 参数表单、子步骤与 before/after 钩子编辑；场景变量、环境、数据集也可用 JSON 表单维护。
- **YAML 源码**：原始文本，始终是保存与执行的唯一事实来源。

保存时表单会先序列化为规范化 YAML，再走原有 `PUT /api/v1/api-scenarios/{id}`；平台不保存独立的步骤数据库模型。未知字段会随解析保留，YAML 注释会在表单序列化时丢失。

## 流程视图与静态检查

「API 场景」列表提供 **流程** 只读视图，编辑器提供 **流程预览**：

- 按 YAML 解析出步骤树，控制器/数据集/前置后置以嵌套节点展示；
- 节点显示读取变量（`${var}`）与产出变量（`extract`/`set`/`groovy vars.put`）；
- 展示变量生产者—消费者表和未定义变量的静态提示；
- 流程视图只读，YAML 仍是唯一编辑源，避免双向同步产生第二套数据模型。

对应接口：

- `POST /api/v1/api-scenarios/analyze`：分析编辑器中的 YAML；
- `GET /api/v1/api-scenarios/{id}/graph`：分析已保存场景。

## 页面入口

左侧「测试资产」→「API 场景」（`/#/api-scenarios`，需 ADMIN/TESTER）。

页面顶部：批量运行、导入 YAML、新建/编辑（YAML 编辑器）。
页面「YAML 能力入口」卡片可直接插入 HTTP/SQL/Groovy/set/assert/wait/if/loop/once/while/before-after/datasets 模板。
编辑器内：校验 YAML、CSV 数据集导入。
