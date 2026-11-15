# EasyTest 一期验收清单

## 交付范围

### P0 契约执行

- `contract-api` SDK：`@EasyContract`、`ContractContext`、`ContractResult`
- 工程方法 JAR 扫描与平台注册
- 场景 YAML `component` 步骤调用工程方法
- 方法输入契约保存校验
- 方法输出契约执行校验（`CONTRACT_OUTPUT_MISSING`）
- manifest 生成与导入
- 场景/计划执行血缘：sourcePath、sourceCommit、lineageJson

### P0 接口 Agent 化

- API Agent 注册/心跳/能力/槽位
- 数据库共享 Agent 注册表（多 core）
- 按能力/负载选择 Agent
- 槽位预占、失败释放、故障转移
- 满槽进入 `api_dispatch_task` 持久化队列
- 队列租约领取，避免多 core 重复下发
- DRAINING 优雅摘除

### P0 复杂接口

- HTTP / SQL / Groovy / component
- Kafka 原生 produce/consume（key/value/headers/extract）
- 条件、循环、等待、前后置、数据驱动
- 图形化分析、静态变量分析、YAML diff

### P1 多项目隔离

- 项目/成员/项目上下文（`X-Project-Id`）
- 已隔离：UI 用例、接口用例、API 场景、测试计划、API 环境、UI 知识库、定时任务、计划报告、设备池、UI 执行任务
- 项目角色：ADMIN / TESTER / VIEWER
- 非成员访问 403，跨项目资源 404

### P1 UI 自动化工程化

- AI / STEPS / SCRIPT 三种执行模式
- STEP 契约：14 种步骤，含 WAIT_FOR / ASSERT_NOT_TEXT / SCROLL
- Step schema 接口
- UI 脚本 manifest：contractId、inputs、outputs、entryCommand
- UI 脚本管理页面
- iOS WDA 驱动：截图、UI 树、点击/输入/滑动/返回/Home/启动

### P1 执行记录契约血缘

- 计划运行项 lineageJson
- 场景运行 sourcePath/sourceCommit
- UI 任务 scriptId/contractId/entryCommand
- 血缘导出接口与报告下载

## 自动化验证

```text
Java tests: 330
Python tests: 48
Result: all checks passed
```

统计口径：`./scripts/check.sh` 生成的 surefire 报告。

## 核心 API 冒烟

```text
projects               PASS
engineering methods    PASS
api agents             PASS
scripts                PASS
scenario list          PASS
contract scenario run  PASS   SUCCESS 2/2 steps passed
project isolation      PASS   403
```

## 部署

```bash
docker compose -f deploy/docker-compose.middleware.yml up -d
./scripts/mvn.sh -q -DskipTests package
EASYTEST_INFRA=docker ./scripts/dev-up.sh
```

访问：

```text
http://127.0.0.1:8080
```

账号：

```text
admin/admin123 tester/test123 business/biz123 ops/ops123
```

## 已知限制

- iOS 当前以 mock WDA 单测覆盖协议，真机需启动 WDA 后联调
- 设备安装/文件/Shell 等 Android 专属能力在 iOS 未实现
- Agent 租约续约、多 core 注册表故障恢复仍需生产化增强
- 项目级设备分配当前为管理员 API，页面入口待补
- UI 报告导出与失败分析联动为后续增强
- Dubbo 等协议当前通过 `component` 工程代码覆盖，未做原生步骤

## 验收入口

| 页面 | 地址 |
| --- | --- |
| 总览 | `/#/dashboard` |
| 用例 | `/#/cases` |
| 接口用例 | `/#/api-cases` |
| API 场景 | `/#/api-scenarios` |
| 工程方法 | `/#/engineering-methods` |
| 执行节点 | `/#/agents` |
| 项目 | `/#/projects` |
| UI 脚本 | `/#/scripts` |
| UI 知识库 | `/#/kb` |
| 设备池 | `/#/devices` |
| 测试计划 | `/#/plans` |
| 运行报告 | `/#/runs/:id` |
