# TestHub V1

一站式测试平台 V1：以 **MeterSphere（测试管理/接口测试）+ Sonic（云真机）** 的紧耦合融合为目标，
实现「BDD 用例 → 人工评审 → 混合测试计划 → 设备 FIFO 队列 → 传统脚本 / AI Runtime 双执行通道 →
逐步轨迹 → 失败轨迹报告 → 用例回写 → 统一报告」的完整闭环。

> 依据 `docs/V1-SCOPE.md`（范围与偏差）与 `docs/API-CONTRACT.md`（接口契约）实现。

**当前状态**：V1 已完成并通过端到端验收 —— 146 个自动化测试全绿、E2E 47 项检查全部通过（本地模式与 MySQL/Redis/MinIO/Kafka 模式各一轮），
证据见 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)。

## 组件

| 组件 | 目录 | 端口 | 说明 |
|---|---|---|---|
| testhub-server | `testhub-server/` | 8080 | 主服务器：用例/评审、测试计划、系统设置、AI 分析 Agent（模块化单体，边界可按服务拆分） |
| ui-execution | `ui-execution/` | 8081 | UI 执行编排：执行器路由、设备 FIFO 队列、状态机、轨迹、失败报告、Skills 指针 |
| device-agent | `device-agent/` | 8090 | 设备子服务器 Agent：注册心跳、模拟设备/ADB、传统脚本执行器、AI Runtime 桥接、Skills 缓存 |
| ai-runtime | `agents/ai-runtime/` | 8095 | 设备侧 AI 执行期决策器（Python）：截图+UI 树 → 模型决策 → 动作 → 轨迹 |
| web | `web/` | 5173 | React 控制台 |
| common | `common/` | — | JWT/角色/服务令牌/异常/加密等共享能力 |

中间件：MySQL 8（业务库）、Redis 7（设备 FIFO）、MinIO（产物）、Kafka 3.8（任务事件流）；
默认本地模式用 H2/内存/本地文件/日志替代，`TESTHUB_INFRA=docker` 切换真实中间件。

```
Browser ── web :5173
   │  JWT
   ├──────────────► testhub-server :8080 ──内部 HTTP──► ui-execution :8081
   │                 (用例/计划/系统/AI)                 (路由/队列/轨迹/报告)
   │                                                        │ 下发(HTTP+Agent Token)
   └────────────────────────────────────────────────────────┤
                                                            ▼
                                                  device-agent :8090
                                                   ├─ 模拟设备 / ADB
                                                   ├─ 传统脚本执行器
                                                   ├─ Skills Git 缓存
                                                   └─► ai-runtime :8095
```

## 快速开始（无需 Docker / 无需模型 Key / 无需真机）

```bash
# 1. 安装依赖（Maven 本地仓库放在工作区 .m2/，Python venv，npm）
make setup

# 2. 启动 core / ui-execution / device-agent / ai-runtime
make up

# 3. 启动控制台（另开一个终端）
cd web && npm run dev        # http://127.0.0.1:5173

# 4. 跑端到端验收（另开一个终端）
make e2e
```

需要真实中间件（MySQL 8 / Redis 7 / MinIO）时：

```bash
make infra
TESTHUB_INFRA=docker ./scripts/dev-up.sh
make e2e
```

演示账号：`admin/admin123`（管理员）、`tester/test123`（测试工程师）、
`business/biz123`（业务用户）、`ops/ops123`（设备运维）。

默认使用 H2 文件库 + 内存 FIFO 队列 + 本地文件产物 + **mock 大模型** + **模拟设备**，
因此完全离线即可演示：AI 生成的 BDD 草稿、AI Runtime 的逐步轨迹与截图、传统脚本产物与失败报告都是真实产生的。

## 5 分钟演示路径

1. 登录 `tester/test123` → **AI 分析**：选择需求，生成 BDD 草稿（默认 mock 模型，确定性输出）。
2. 到 **用例管理**：草稿状态为「草稿」→ 提交评审 → 通过；页面展示「可用执行器」。
3. **测试计划**：新建计划，挂载「AI Runtime 用例」与「传统脚本用例」，触发时指定目标设备。
4. **设备池**：观察设备 FIFO 队列（排队位置）、执行状态。
5. **执行任务**：打开任务详情，查看每步截图、模型决策原文、动作、耗时与实时日志。
6. 失败用例自动生成**失败轨迹报告**（JSON），并回写到用例的「失败分析」字段。
7. **计划报告**：三类结果（接口预留 / 传统脚本 / AI Runtime）统一汇总，失败原因分类统计。
8. **系统设置**：文本/视觉模型配置、Skills 全局指针（同步/回滚）、审计日志。

## 关键设计

- **执行器路由**：计划层可覆盖；未指定时有脚本→传统脚本，无脚本→AI Runtime；AI 模型不可用时按文档回退传统脚本。
- **设备 FIFO**：同一设备严格串行，队列位置由 ui-execution 集中维护，设备侧不自行排队。
- **Skills**：真相源是外部 Git；主服务器只存全局指针，任务下发时固化 commit，Agent 按 commit 拉取并本地缓存，Git 不可达时降级到最近缓存。
- **失败分类**：`MODEL_DECISION_FAILED` / `ACTION_FAILED` / `ELEMENT_NOT_FOUND` / `TIMEOUT` / `MANUAL_INTERRUPT` / `EXECUTOR_UNAVAILABLE` / `SKILLS_UNAVAILABLE` / `SCRIPT_FAILED`。
- **权限**：ADMIN / TESTER / BUSINESS / OPS；Skills 指针与模型配置仅 ADMIN；服务间用 `X-Service-Token`，Agent 用 `X-Agent-Token`；API 默认拒绝。

## 常用命令

```bash
make build          # Java + web 构建
make test           # Java 单测 + Python 单测
make up / make down # 启停本地服务
make e2e            # 端到端验收
tail -f logs/core.log
```

## 文档

- [docs/V1-SCOPE.md](docs/V1-SCOPE.md) — V1 边界、与架构文档的偏差、默认配置
- [docs/API-CONTRACT.md](docs/API-CONTRACT.md) — 全部接口/DTO/状态机/回调协议
- [docs/RUNBOOK.md](docs/RUNBOOK.md) — 部署、环境变量、切换到 MySQL/Redis/MinIO、排障
- [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) — E2E 验收结果与证据

## 目录结构

```
common/           共享安全与 Web 能力
testhub-server/   主服务器（用例/计划/系统/AI 分析）
ui-execution/     执行编排服务
device-agent/     设备子服务器 Agent
agents/ai-runtime/设备侧 AI Runtime（Python）
web/              React 控制台
deploy/           Docker Compose + Dockerfile
docs/             范围、契约、运维、验收
examples/scripts/ 演示用脚本工程包
scripts/          构建/启动/验收脚本
```

## V1 已知边界

接口测试执行（JMeter）、投屏/scrcpy、Kafka 异步下发、集群化、Flyway、真机全量动作、Windows 设备子服务器不在 V1 范围；
V1 **未引入 MeterSphere / Sonic 源码**（能力等价实现）；MySQL / Redis / MinIO / Kafka 已完成真实验收（Kafka 仅承载任务事件，下发仍走 HTTP），详见 V1-SCOPE §3.1。

具体演进路径见 `docs/V1-SCOPE.md` §3。
