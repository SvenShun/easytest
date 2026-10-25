# 接口自动化增强验收：环境/鉴权、数据驱动、共享队列

> 验收对象：环境变量与鉴权预设（含登录提取 Token）、数据集多轮执行、Redis 共享任务队列。
> 验收脚本：`scripts/part3_smoke.py`，默认 14 项；启动第二个 api-execution 后 16 项。

## 1. 交付内容

| 能力 | 实现 |
|---|---|
| 环境 | `api_environment`：Base URL、公共 Headers、变量、启停 |
| 变量 | `${name}` 环境变量；数据集行 `${field}` 或 `${data.field}`；可嵌套解析 |
| 鉴权预设 | NONE / Bearer / Basic / API Key / **登录接口提取 Token** |
| 登录鉴权 | 计划触发时调用一次登录接口，按 JSONPath 提取 Token，注入本次所有接口请求 |
| 数据驱动 | `api_case.dataset_json`：一行一次执行，生成多个运行项 `[i/n]`，带 `iteration` |
| 共享队列 | api-execution 支持 `memory` / `redis`；多实例从同一 Redis 队列消费，任务记录 `workerId` |
| 计划绑定 | 计划选择环境；触发时统一解析环境/数据变量后下发 |
| 前端 | 「环境管理」页面；计划创建/编辑可选环境；接口用例编辑器支持数据集 |

## 2. 验收结果

### 2.1 单实例（环境 + 数据驱动）

```bash
make up                 # 或 TESTHUB_INFRA=docker ./scripts/dev-up.sh
make part3
```

```
PART 3 RESULT: 14 checks passed, 0 failed
```

| 组 | 检查项 |
|---|---|
| 登录环境 | 环境创建、用例 SUCCESS、登录仅调用 1 次、Token 注入、环境变量 Header、Base URL 变量 |
| 数据驱动 | 3 行生成 3 个运行项、全部 SUCCESS、iteration 1/2/3、标题 `[1/3]`、一次登录、行值进入 URL/Header |
| 环境引用保护 | 被计划引用的环境不能删除（409） |

真实证据：

- 鉴权运行：`run_931231e0e14e43e9b444`
- 数据驱动运行：`run_8ab2e031626a4a878633`
- 环境：请求头 `Authorization: Bearer tok-abc`、`X-Env: part3-tester`

### 2.2 共享队列（双实例）

```bash
make apiexec-scale      # 启动 :8085，Redis 共享队列
TESTHUB_APIEXEC_SECOND_URL=http://127.0.0.1:8085 make part3
make apiexec-scale-down
```

```
PART 3 RESULT: 16 checks passed, 0 failed
```

- 8 个慢任务全部 SUCCESS；
- 任务由两个实例共同消费，`workerId` 同时出现：
  `apiexec-local-1-1..4` 与 `apiexec-local-2-1..2`；
- 共享运行：`run_e1ed04d54ed942b994fc`。

前提：主栈以 Docker 模式启动（Redis 可用），`TESTHUB_APIEXEC_QUEUE=redis`（`dev-up` 在 `TESTHUB_INFRA=docker` 时自动设置）。

## 3. 实现位置

- 主服务：`feature/apienv`（环境 CRUD + 解析/登录）、`api_case.dataset_json`、`plan.api_environment_id`、运行项 `iteration`
- 执行服务：`api-execution/queue/TaskQueue`（MemoryTaskQueue / RedisTaskQueue）、`TaskWorkerPool`
- 前端：[Environments.tsx](web/src/pages/Environments.tsx)、ApiCases 数据集编辑、计划环境选择

## 4. 自动化测试

**200 个全绿**（Java 152 + Python 48）：

- api-execution 32（含队列/恢复测试）
- testhub-server 42（含环境解析、登录鉴权、数据驱动迭代测试）
- ui-execution 36、device-agent 38、common 4、Python 48

## 5. 已知边界

- 环境中的 Token/密码当前明文存于 DB；生产应接密钥管理或加密字段。
- 登录 Token 每次计划触发只获取一次，不做过期自动刷新；需要刷新时可拆成前置步骤。
- 数据集仅支持 JSON 数组（最多 200 行），没有 CSV 上传/文件数据源。
- 共享队列基于 Redis List；Redis 本身未做集群/高可用；运行中取消跨实例依赖取消标记，正在执行的 HTTP 请求可能仍会完成。
- **滚动启动注意**：启动恢复会把共享 DB 中所有 RUNNING 任务标记 FAILED；新增实例应在触发长任务之前启动，避免在有任务运行时重启/扩容。
- Redis 队列不可用时，创建任务返回 503 `QUEUE_UNAVAILABLE`（任务标记 FAILED）；memory 模式单实例可用。
- 数据集每一行不单独登录；登录鉴权是运行级。
- 环境变量不支持函数/加密引用（仅字符串替换）。

## 6. 复现命令

```bash
cd /Users/wen/DeepseekWorkspace/EasyTest
make infra
TESTHUB_INFRA=docker ./scripts/dev-up.sh
make part3

# 共享队列
make apiexec-scale
TESTHUB_APIEXEC_SECOND_URL=http://127.0.0.1:8085 make part3
make apiexec-scale-down
```
