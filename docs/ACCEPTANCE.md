# TestHub V1 验收报告

| 项 | 内容 |
|---|---|
| 验收日期 | 2026-10-23 |
| 版本 | V1（首个可运行版本） |
| 验收环境 | macOS（Apple Silicon），Java 17.0.2，Maven 3.9.9，Node 22.20.0，Python 3.9.15，无 Docker daemon、无真机、无大模型 Key |
| 运行模式 | H2 文件库 + 内存 FIFO 队列 + 本地产物 + `mock` 模型 + 模拟设备（完全离线） |
| 结论 | **通过**：全量 145 个自动化测试通过，端到端验收 47 项检查全部通过 |

## 1. 构建与自动化测试

| 模块 | 测试数 | 结果 | 命令 |
|---|---|---|---|
| common | 4 | 通过 | `./scripts/mvn.sh -pl common test` |
| testhub-server | 27 | 通过 | `./scripts/mvn.sh -pl testhub-server test` |
| ui-execution | 31 | 通过 | `./scripts/mvn.sh -pl ui-execution test` |
| device-agent | 35 | 通过 | `./scripts/mvn.sh -pl device-agent test` |
| ai-runtime (Python) | 48 | 通过 | `cd agents/ai-runtime && .venv/bin/pytest -q` |
| **合计** | **145** | **0 失败 / 0 错误 / 0 跳过** | `make check` |
| web | — | `tsc --noEmit` + Vite build 成功 | `cd web && npm run build` |

覆盖重点：执行器路由、FIFO 队列位置、任务状态机、失败分类、评审流转、执行能力计算、
计划触发与失败继续、AI mock Provider、脚本票据、模拟设备登录流程、zip slip 防护、JUnit 解析、
动作白名单、超时/取消、Skills 缓存与降级、步骤/日志/结果回调、CORS 与默认拒绝鉴权。

## 2. 端到端验收（`make e2e`）

```
RESULT: 47 checks passed, 0 failed
```

| 组 | 检查项 | 结果 |
|---|---|---|
| 0 健康 | 四个服务健康、登录角色正确 | 5/5 |
| 1 AI 分析 | 需求 → BDD 草稿、状态 DRAFT、source=AI、无脚本仅 AI_RUNTIME | 4/4 |
| 2 评审与脚本 | 人工评审通过、BDD 持久化、脚本用例能力/默认执行器 | 4/4 |
| 3 Skills | 本地 Git 仓库同步解析 HEAD commit | 1/1 |
| 4 设备池 | Agent 注册模拟设备、设备在线 | 1/1 |
| 5 计划与 FIFO | 用例挂载、逐用例建任务、队列非空、位置有序、严格按计划顺序 | 5/5 |
| 6 混合执行 | AI 成功、慢速脚本成功、通过脚本成功、失败脚本失败、失败分类、运行状态 PARTIAL | 6/6 |
| 7 AI 轨迹 | 多步骤、每步截图、动作命中登录流程、Skills commit 固化、截图是真实 PNG | 5/5 |
| 8 脚本与失败回写 | JUNIT+SCREENSHOT 产物、失败报告 URL、分类、timeline、设备环境、用例回写 | 7/7 |
| 9 统一报告 | 汇总计数、按执行器分组、失败分类聚合、按 planRunId 过滤任务 | 5/5 |
| 10 队列取消 | 排队任务取消、运行含 CANCELLED、其余任务继续执行 | 3/3 |
| 11 统计 | 设备/任务计数 | 2/2 |

### 本次验收产生的真实数据

- 计划运行：`run_a40c477475ac4fe79177`（运行 `GET /api/v1/plan-runs/{id}/report`）
- 任务：
  - AI Runtime 成功：`task_a2fc1f8cb54c45f4895f`（多步截图 + 决策原文）
  - 慢速脚本成功：`task_0d62f26016e544a1ae2f`
  - 脚本通过：`task_63b0e62942a6434eb970`
  - 脚本失败：`task_c37ac602297749aa91f9`
- 汇总：`total=4, success=3, failed=1`；`SCRIPT: 3/2/1`、`AI_RUNTIME: 1/1/0`；失败分类 `SCRIPT_FAILED:1`
- 失败轨迹报告：`http://127.0.0.1:8081/artifacts/task_c37ac602297749aa91f9/failure-report.json`
  - `reasonCategory=SCRIPT_FAILED`，`timeline` 含失败用例，`environment.deviceSerial=emulator-5554`
- 用例回写：失败分析字段写入 `script failed: 1 of 2 test case(s) failed: com.testhub.demo.LoginErrorTest.loginShowsErrorToast`
- 取消验证运行：`run_5aef853b5d994f789c52`（1 个排队任务取消，其余 2 个成功）
- Skills 固化 commit：`33f2217714c9c95e641d7a0fe116c63873485fe0`

## 3. 验收中发现并修复的问题

| 问题 | 修复 |
|---|---|
| AI Runtime 复用了上一任务残留的设备界面，导致新任务在第 1 步就判定“已完成”（1 步轨迹） | Runtime 在每次任务开始时调用 `POST /device/{serial}/reset` 做测试隔离；回归后 AI 任务稳定产生 5 步轨迹（点登录 → 输入用户名 → 输入密码 → 点登录 → done）。48 个 Python 测试仍全绿。 |
| 快速任务使 FIFO 队列在断言前已排空，队列可观测性不足 | 验收脚本把 6 秒慢速脚本用例放在计划首位，稳定观测到 3 个排队任务及其位置顺序。 |
| Web 将 SUCCESS 任务的 `failureSummary` 误显示为失败卡 | Web 按状态/失败原因/报告链接判断失败卡片显示（由 Web 工作流修复）。 |

## 4. 手工验证

- Web 控制台 `http://127.0.0.1:5173`：登录、总览、用例、AI 分析、计划、运行报告、任务轨迹、设备池、系统设置各页面在真实后端数据下可用（由 Web 工作流以真实后端只读遍历验证，无运行时错误）。
- 服务端点抽检：未认证访问返回 401；OPS/Agent 令牌越权返回 403；Skills 同步失败返回 409；重复终止回调返回 409。

## 5. 未覆盖项（V1 已知边界）

- 真机 ADB：仅最小实现，未在真实设备上验证；投屏/scrcpy 未实现。
- 真实大模型：Provider 代码路径已实现，但未用真实 Key 做联网验收（默认 mock）。
- Redis 队列 / MinIO 产物：实现存在且按条件装配，本次未在真实 Redis/MinIO 上验收（Docker daemon 未运行）。
- Docker Compose：已提供，本次未在容器环境实测。
- 接口测试（JMeter）：不在 V1 范围，仅报告模型预留 `executorType=API`。
- 多实例/集群、Kafka、Flyway、SSE 压测、轨迹生命周期归档未覆盖。

## 6. 复现命令

```bash
make setup
make up
make e2e          # 期望：RESULT: 47 checks passed, 0 failed
make check        # Java 97 + Python 48 测试 + Web 构建
make down
```

验收证据文件：`data/e2e-report.json`（每次运行覆盖）。
