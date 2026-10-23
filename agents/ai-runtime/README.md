# TestHub AI Runtime (ai-runtime)

设备侧 AI 执行期决策器：`观察（截图 + UI 树）→ 模型决策 → 白名单校验 → 设备动作 → 轨迹/日志回调`。
Python 3.9 + FastAPI，默认端口 **8095**。契约唯一事实来源：`docs/API-CONTRACT.md` §4（回调格式见 §2.3）。

## 快速开始

```bash
cd agents/ai-runtime
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
./run.sh                      # 等价于 .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8095
curl -s http://127.0.0.1:8095/runtime/health
```

> 全部测试与 mock provider 均离线运行，不需要真实大模型或真机。

## 目录

```
agents/ai-runtime/
├── app/
│   ├── main.py            # FastAPI 入口：/runtime/tasks、/cancel、/health
│   ├── models.py          # Pydantic v2 请求/响应模型（camelCase 契约字段）
│   ├── device.py          # 设备客户端（screenshot / ui-tree / action）
│   ├── callbacks.py       # ui-execution step / log 回调（失败不阻断）
│   ├── engine.py          # 观察-决策-动作循环、超时/取消/白名单/失败分类
│   ├── cancel.py          # 线程安全取消注册表 + 409 并发保护
│   ├── skills.py          # Skills 文本注入（README.md/SKILLS.md，截断 8KB）
│   └── providers/
│       ├── base.py        # Decision / VisionProvider / 错误类型 / 平台约束
│       ├── mock.py        # 确定性 MockProvider（登录流程演示）
│       └── openai.py      # OpenAI 兼容 Provider（含 JSON 解析/纠错重试）
├── tests/                 # pytest（mock / engine / openai parse / api）
├── conftest.py            # 确保 app 包可导入
├── pytest.ini
├── requirements.txt
├── run.sh
└── README.md
```

## HTTP 接口

### `POST /runtime/tasks`

同步执行，请求体见 `docs/API-CONTRACT.md` §4；成功后返回终止状态：

```json
{"taskId":"t1","status":"SUCCESS","reasonCategory":null,
 "summary":"进入首页并显示 登录成功","totalSteps":7,"elapsedMs":12345}
```

- 同一 `taskId` 并发请求 → **HTTP 409**（`TASK_ALREADY_RUNNING`）。
- `provider` 非 `mock/openai/deepseek` → 400；`openai/deepseek` 缺 `endpoint` → 400。
- 请求体校验失败 → FastAPI 标准 422。

### `POST /runtime/tasks/{taskId}/cancel`

```json
{"cancelled": true}
```

未知或已结束的任务返回 HTTP 200 + `{"cancelled": false}`。取消置位后引擎在**下一步开头**检测并返回 `CANCELLED/MANUAL_INTERRUPT`。

### `GET /runtime/health`

```json
{"status":"UP","version":"1.0.0","providers":["mock","openai"],
 "skillsCacheDir":"/.../agents/ai-runtime/data/skills"}
```

`skillsCacheDir` 可由环境变量 `AI_RUNTIME_SKILLS_CACHE_DIR` 覆盖；日志级别由 `AI_RUNTIME_LOG_LEVEL` 控制。

## 引擎语义

每步：

1. 检查取消标志；检查总超时 deadline（单调时钟，不 sleep 等待）；检查 `maxSteps`。
2. `GET {device.baseUrl}/device/{serial}/screenshot`（PNG 原样保留）与 `/ui-tree`。
3. 调用 Provider 决策；解析动作并做白名单校验（`click,input,swipe,back,home,launch,wait,done`）。
4. `POST /device/{serial}/action` 执行动作（`DeviceClient` 在传输层把 Provider 的 `type` 归一化为契约 §3 的 `action` 字段）。
5. `POST callbacks.stepUrl`（`X-Agent-Token`）上报 step，`POST callbacks.logUrl` 上报日志。

回调体：

```json
{"seq":1,"status":"OK","decision":"模型原始输出（≤4000 字符）",
 "action":"click(登录)","elapsedMs":1200,
 "screenshotBase64":"...","screenshotContentType":"image/png"}
```

日志体 `{"lines":[{"level":"INFO|ERROR","message":"step 1 ..."}]}`。每步至少一条日志，任务终止追加一条汇总日志；所有回调失败只告警、不影响执行。

### 状态 / 失败分类

| 场景 | `status` | `reasonCategory` |
|---|---|---|
| Provider `done` / `done` 动作（不触达设备） | `SUCCESS` | `null`（`summary` 写 BDD `then`） |
| 动作 `ok=false` 且 message 含 `not found`/`未找到` | `FAILED` | `ELEMENT_NOT_FOUND` |
| 其他动作失败 / 设备交互异常 | `FAILED` | `ACTION_FAILED` |
| 模型输出无法解析（Provider 内部已重试 1 次） | `FAILED` | `MODEL_DECISION_FAILED` |
| 动作不在白名单（不执行） | `FAILED` | `MODEL_DECISION_FAILED` |
| 连续 2 次非法动作参数（如 click 缺 target） | `FAILED` | `MODEL_DECISION_FAILED` |
| 单步 / 总超时 | `TIMEOUT` | `TIMEOUT` |
| 收到取消 | `CANCELLED` | `MANUAL_INTERRUPT` |
| 达到 `maxSteps` 仍未 done | `FAILED` | `MODEL_DECISION_FAILED` |

超时实现：总超时用 deadline；单步用短生命周期守护线程 + Provider/设备 HTTP 请求的 `httpx` timeout 双重约束。取消注册表为线程安全 dict（`threading.Lock`），`/cancel` 只对进行中的任务生效。

## Provider

### MockProvider（`provider=mock`）

确定性规则（便于无模型演示与测试）：

1. 页面文本含「登录成功」→ `done`，summary=`已完成：<then>`。
2. 登录页（title 含「登录」或存在用户名输入）→ 依次：用户名（空时）→ 密码（空时）→ 点击「登录」；已填则直接点「登录」。
3. HOME 且无登录成功 → 点击「登录」。
4. 其他：点击第一个 clickable；没有则 `done` + `未找到可执行动作`。

每步 `analysis` 为一句中文策略说明，同时作为 step 回调的 `decision`。用户名/密码优先从 BDD `when` 文本提取，缺省 `tester`/`secret`。UI 树未暴露输入值时用历史动作兜底判断“已填写”。

### OpenAICompatibleProvider（`provider=openai|deepseek`）

- `POST {endpoint}/chat/completions`，`Bearer apiKey`，`model=modelId`，`temperature=0`。
- system = 平台约束 + `## Skills` 片段 + JSON 输出格式说明；user = BDD + UI 树 JSON + data URL 截图。
- 解析容忍 ```json 围栏与前后文本；解析失败追加纠正提示重试 1 次，仍失败抛 `DecisionParseError` → `MODEL_DECISION_FAILED`。
- 请求超时使用 `timeouts.stepSec`。不发送 `response_format` 以兼容更多端点。

## Skills

`skills.dir` 非空时读取目录下 `README.md`（优先）或 `SKILLS.md`，截断 8000 字符，作为 system prompt 前缀：

```
## Skills
<文本>
```

目录为空/不存在 → 跳过并记 INFO；读取失败只告警。`repo`/`commit` 仅随请求传递用于追踪，缓存由 device-agent 负责（契约 §5）。

## 测试

```bash
cd agents/ai-runtime
.venv/bin/pytest -q
```

- `tests/test_mock_provider.py`：HOME → LOGIN → 用户名 → 密码 → 点击 → done 序列（含真实模拟设备的 UI 树形状）。
- `tests/test_engine.py`：成功轨迹、`ELEMENT_NOT_FOUND`、超时、取消、白名单拦截、连续无效、maxSteps、回调失败降级、Skills 注入。
- `tests/test_openai_parse.py`：围栏 JSON 解析、请求格式、解析失败重试 1 次、二次失败、HTTP 错误与超时。
- `tests/test_api.py`：health、参数校验、400、409 并发、取消未知任务、monkeypatch 引擎。
- `tests/test_integration.py`：本地假设备/回调服务 + 真实 `DeviceClient`/`CallbackClient` 的端到端登录轨迹（校验 PNG 转发与 token）。

另外已与 `device-agent` 的真实 `SimulatedDeviceDriver`（Java，8090）联调：HOME → 登录页 → 输入用户名/密码 → 点击登录 → `登录成功`，5 步 `SUCCESS`，截图 26KB 真实 PNG。

冒烟：

```bash
timeout 15s .venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8095 &
curl -s http://127.0.0.1:8095/runtime/health
```

## 与契约的偏差 / 设计选择

1. `provider=deepseek` 作为 `openai` 的别名；`/health` 仍只列出 `mock/openai`（按契约示例）。
2. `done` 成功时 `summary` 固定取 BDD `then`（`then` 为空才回退 Provider summary）。
3. 契约未定义“达到 maxSteps”的失败分类，这里归为 `MODEL_DECISION_FAILED`。
4. 白名单外的动作立即失败；字段不完整的白名单动作容忍 1 次（第 2 次连续无效才失败），对应“连续 2 次无效”。
5. 截图按原字节转发（不缩放/不重编码）；2MB 上限由 ui-execution 侧执行。
6. 回调为 best-effort：step/log 上报失败只打 WARNING，不改变任务结果。
7. 单步“硬超时”通过守护线程 + `httpx` timeout 实现；任意阻塞的第三方同步代码无法被强制杀死（Python 限制）。
8. Runtime 不调用 `resultUrl`：结果由 device-agent 收到同步响应后按契约上报。
9. 未实现任务查询接口（契约未要求）；任务状态只通过同步响应/回调传播。
10. `skillsCacheDir` 仅用于健康检查展示，Runtime 不做 git 缓存（归属 device-agent）。
11. V1 将所有设备动作 `ok=false` 视为不可恢复（立即终止）；若后续需要重试语义，可在 `Engine` 按分类扩展。
12. 超时/异常发生在取得截图之前时，仍会上报一个 FAIL step（`screenshotBase64` 为空），保证轨迹连续。

## TODO

- 多任务并发上限 / 背压（当前每个请求占一个线程池 worker，靠 `maxSteps` 与超时收敛）。
- Provider 失败重试退避、可配置的截图缩放/压缩。
- 结构化日志与 Prometheus 指标（步数、耗时、分类计数）。
- Skills 语义检索/多文件组合；缓存目录自管理（若 device-agent 不再负责）。
- 更细的设备动作语义校验（点击区域越界、坐标归一化）。
