# TestHub V1

一站式测试平台 V1：以 **MeterSphere（测试管理/接口测试）+ Sonic（云真机）** 的紧耦合融合为目标，
实现「BDD 用例 → 人工评审 → 混合测试计划 → 设备 FIFO 队列 → 传统脚本 / AI Runtime 双执行通道 →
逐步轨迹 → 失败轨迹报告 → 用例回写 → 统一报告」的完整闭环。

> 依据 `docs/V1-SCOPE.md`（范围与偏差）与 `docs/API-CONTRACT.md`（接口契约）实现。

**当前状态**：V1 + 第二部分接口自动化已完成验收 —— **200 个自动化测试全绿**，E2E 47 项、接口执行 18 项、增强能力 16 项、设备 10 项检查全部通过
（本地模式与 MySQL/Redis/MinIO/Kafka 模式各一轮），证据见 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) 与
[docs/ACCEPTANCE-PART2.md](docs/ACCEPTANCE-PART2.md)。

## 组件

| 组件 | 目录 | 端口 | 说明 |
|---|---|---|---|
| testhub-server | `testhub-server/` | 8080 | 主服务器：用例/评审、测试计划、系统设置、AI 分析 Agent（模块化单体，边界可按服务拆分） |
| ui-execution | `ui-execution/` | 8081 | UI 执行编排：执行器路由、设备 FIFO 队列、状态机、轨迹、失败报告、Skills 指针 |
| api-execution | `api-execution/` | 8084 | 接口执行服务（独立部署/水平扩容）：HTTP 请求、断言、超时/取消、报告、回调、Redis 共享队列 |
| device-agent | `device-agent/` | 8090 | 设备子服务器 Agent：注册心跳、模拟设备/ADB（热插拔/截图/UI 树/APK 安装）、脚本执行器、AI Runtime 桥接、Skills 缓存；设备池支持截图/操作/安装/启动 |
| ai-runtime | `agents/ai-runtime/` | 8095 | 设备侧 AI 执行期决策器（Python）：截图+UI 树 → 模型决策 → 动作 → 轨迹 |
| web | `web/` | 5173 | React 控制台 |
| ws-scrcpy（可选） | `tools/ws-scrcpy/` | 8000 | 真机实时画面（scrcpy H.264 → WebCodecs），设备管理弹窗内嵌 |
| common | `common/` | — | JWT/角色/服务令牌/异常/加密等共享能力 |

中间件：MySQL 8（业务库）、Redis 7（设备 FIFO）、MinIO（产物）、Kafka 3.8（任务事件流）；
默认本地模式用 H2/内存/本地文件/日志替代，`TESTHUB_INFRA=docker` 切换真实中间件。

```
Browser ── web :5173
   │  JWT
   ├──────────────► testhub-server :8080 ──内部 HTTP──► ui-execution :8081
   │                 (用例/计划/系统/AI)                 (UI 路由/队列/轨迹/报告)
   │                                      └────────────► api-execution :8084
   │                                                      (接口请求/断言/报告)
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
python3 scripts/part2_smoke.py   # 接口自动化 18 项
make part3                       # 环境/数据驱动 14 项（共享队列另启实例后 16 项）
make part4                       # ADB 设备驱动 10 项（假 adb）
make device-check                # 真机预检（输入注入/APK 安装）
make scrcpy-setup / scrcpy-up    # 实时真机画面（ws-scrcpy :8000）

# 只部署第一部分（主服务：用户/用例/计划等，不含执行）
make main-up        # http://127.0.0.1:8080
python3 scripts/part1_smoke.py
make main-down
```

需要真实中间件（MySQL 8 / Redis 7 / MinIO）时：

```bash
make infra
TESTHUB_INFRA=docker ./scripts/dev-up.sh
make e2e

# MeterSphere（真实源码，需 JDK 21）
make ms-install-module && make ms-build && make ms-up   # http://127.0.0.1:8082, admin/metersphere
eval "$(make -s ms-token)"         # 导出 MeterSphere 会话变量
TESTHUB_INFRA=docker TESTHUB_CASE_SOURCE=metersphere ./scripts/dev-up.sh
```

演示账号：`admin/admin123`（管理员）、`tester/test123`（测试工程师）、
`business/biz123`（业务用户）、`ops/ops123`（设备运维）。

默认使用 H2 文件库 + 内存 FIFO 队列 + 本地文件产物 + **mock 大模型** + **模拟设备**，
因此完全离线即可演示：AI 生成的 BDD 草稿、AI Runtime 的逐步轨迹与截图、传统脚本产物与失败报告都是真实产生的。

## 5 分钟演示路径

1. 登录 `tester/test123` → **AI 分析**：选择需求，生成 BDD 草稿（默认 mock 模型，确定性输出）。
2. 到 **用例管理**：草稿状态为「草稿」→ 提交评审 → 通过；页面展示「可用执行器」。
3. **测试计划**：新建计划并绑定 API 环境，混合挂载「UI 用例（AI Runtime / 传统脚本）」与「接口用例」，按类型自动路由。
4. **设备池**：观察设备 FIFO 队列（排队位置）、执行状态。
5. **执行任务**：打开任务详情，查看每步截图、模型决策原文、动作、耗时与实时日志。
6. 失败用例自动生成**失败轨迹报告**（JSON），并回写到用例的「失败分析」字段。
7. **计划报告**：接口（API）/ 传统脚本 / AI Runtime 统一汇总，失败原因分类统计。
8. **系统设置**：文本/视觉模型配置、Skills 全局指针（同步/回滚）、审计日志。

## 关键设计

- **执行器路由**：计划层可覆盖；未指定时有脚本→传统脚本，无脚本→AI Runtime；接口用例固定 api-execution。
- **环境与鉴权**：环境提供 Base URL/公共 Headers/变量，支持 Bearer/Basic/API Key/登录接口提取 Token；登录每次计划触发执行一次。
- **数据驱动**：接口用例数据集一行一次执行，运行项带 `[i/n]` 和 `iteration`，行字段可用 `${field}`/`${data.field}`。
- **共享队列**：api-execution 可多实例部署，Redis List 共享任务队列，任务记录 `workerId`。
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
- [docs/METERSPHERE-INTEGRATION.md](docs/METERSPHERE-INTEGRATION.md) — MeterSphere v3 适配、源码位置、限制
- [docs/SONIC-INTEGRATION.md](docs/SONIC-INTEGRATION.md) — Sonic driver-core 真机驱动与许可证说明
- [docs/METERSPHERE-EMBEDDING.md](docs/METERSPHERE-EMBEDDING.md) — 源码级内嵌模块、安装与验证
- [docs/RUNBOOK.md](docs/RUNBOOK.md) — 部署、环境变量、切换到 MySQL/Redis/MinIO、排障
- [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) — E2E 验收结果与证据
- [docs/ACCEPTANCE-PART2.md](docs/ACCEPTANCE-PART2.md) — 第二部分（接口自动化）验收
- [docs/API-EXECUTION-CONTRACT.md](docs/API-EXECUTION-CONTRACT.md) — 接口执行契约
- [docs/ACCEPTANCE-PART3.md](docs/ACCEPTANCE-PART3.md) — 环境/鉴权、数据驱动、共享队列验收
- [docs/ACCEPTANCE-PART3-DEVICE.md](docs/ACCEPTANCE-PART3-DEVICE.md) — ADB 设备驱动验收（假 adb/真机步骤）
- [docs/SCRCPY-WEB.md](docs/SCRCPY-WEB.md) — 页面内 scrcpy 实时画面与鼠标控制
- [docs/DEVICE-MANAGEMENT.md](docs/DEVICE-MANAGEMENT.md) — 真机管理/远程操作/应用/文件/Shell/性能
- [docs/SONIC-PORT-INVENTORY.md](docs/SONIC-PORT-INVENTORY.md) — Sonic 源码功能清单与移植差距
- [docs/SONIC-PORTING-STATUS.md](docs/SONIC-PORTING-STATUS.md) — Sonic 能力移植进度
- [docs/ACCEPTANCE-PART3-STEP.md](docs/ACCEPTANCE-PART3-STEP.md) — 步骤化 UI 用例真机验收
- [docs/ACCEPTANCE-PART5-PLATFORM.md](docs/ACCEPTANCE-PART5-PLATFORM.md) — 平台能力阶段验收汇总

## 目录结构

```
common/           共享安全与 Web 能力
testhub-server/   主服务器（用例/计划/系统/AI 分析）
ui-execution/     执行编排服务（UI）
api-execution/    接口执行服务（独立部署）
device-agent/     设备子服务器 Agent
agents/ai-runtime/设备侧 AI Runtime（Python）
web/              React 控制台
deploy/           Docker Compose + Dockerfile
docs/             范围、契约、运维、验收
examples/scripts/ 演示用脚本工程包
scripts/          构建/启动/验收脚本
```

## V1 已知边界

接口执行 V1 + 增强已实现（HTTP + 断言 + 超时/取消 + 环境/登录鉴权 + 数据驱动 + Redis 共享队列）；前后置脚本、用例级重试、CSV 文件数据源、Token 过期自动刷新不在本版；
真机 ADB 驱动已在 Xiaomi Mi 10（Android 12）接入并通过登录 UI 用例（4 步 SUCCESS）；设备管理弹窗已内嵌 scrcpy 实时画面与鼠标操作；投屏/scrcpy、Kafka 异步下发、集群化、Flyway、Windows 设备子服务器不在 V1 范围；
MeterSphere v3 已按你提供的源码**本地构建并运行**，已真实验证“用例源 + 失败评论回写 + 计划结果回写”，
并完成 **ui-execution 源码级内嵌**（MS 同进程；FIFO 队列、看门狗、失败报告、取消、计划关联、会话自动登录）；
Sonic 的 **driver-core（Apache-2.0）** 已接入为 `mode=sonic` 真机驱动。
计划-用例关联自动化、源码级模块内嵌与真机运行仍未完成，见 [docs/METERSPHERE-INTEGRATION.md](docs/METERSPHERE-INTEGRATION.md)
与 [docs/SONIC-INTEGRATION.md](docs/SONIC-INTEGRATION.md)。
MySQL / Redis / MinIO / Kafka 已完成真实验收（Kafka 仅承载任务事件，下发仍走 HTTP），详见 V1-SCOPE §3.1。

具体演进路径见 `docs/V1-SCOPE.md` §3。


## License

TestHub is released under the **GNU Affero General Public License v3.0** (see [LICENSE](LICENSE)).
Third-party components and their obligations are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
and [docs/LEGAL.md](docs/LEGAL.md).
