# 阶段七验收：YAML 接口场景

日期：2026-10-28

## 已实现

- YAML 场景模型、核心 CRUD/运行历史/校验
- api-execution 场景任务队列（`scenario:` 前缀）与 worker 分发、启动恢复
- 步骤：HTTP、SQL、Groovy、set、assert、wait
- 变量插值（`${}`/`{{}}`）、HTTP JSONPath/Header 提取、SQL 行提取、步骤间数据传递
- HTTP/SQL/变量断言；步骤级/场景级失败策略
- 场景页面：YAML 编辑、校验、运行、步骤报告、运行历史

## 端到端证据

场景 `scene_57a535711b7645159aeb`：

- 步骤 1 登录 HTTP → 提取 token/userId
- 步骤 2 SQL（H2 datasource）→ 提取 n
- 步骤 3 Groovy → 组装变量
- 步骤 4 变量断言
- 运行 `scenario-run_3a...`（task `scenario-task_a970...`）→ **SUCCESS，4/4 steps passed**
- 前端运行弹窗显示 `SUCCESS`、4 个步骤、每步状态/耗时；截图 `data/page-api-scenario-run.png`
- 队列恢复：重启 api-execution 后 PENDING 任务被重新入队并成功执行
- 计划集成：计划 `plan_e47e7aa2a78045179a2d` 挂载场景后触发 → 运行 `run_b7d958a6439643d5bef8` → 运行项 `SCENARIO` **SUCCESS**，计划 `COMPLETED`
- 前端：API 场景页面、计划详情场景条目/运行弹窗无 console error

## 测试

- `ScenarioExecutorTest`：HTTP+SQL+Groovy+变量传递成功；断言失败默认终止。
- `./scripts/check.sh` 待最终全量运行。
