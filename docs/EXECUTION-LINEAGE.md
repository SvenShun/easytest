# 执行记录契约血缘

每条执行记录固化“它执行的是哪一版契约”，用于审计和问题复盘。

## 计划执行项

`plan_run_item`：

| 字段 | 说明 |
| --- | --- |
| `source_path` | API 场景 Git 源文件 |
| `source_commit` | API 场景 Git commit |
| `lineage_json` | 用例/脚本/契约快照 |

`lineage_json` 按执行类型包含：

```json
{
  "caseType": "API | UI | SCENARIO",
  "caseId": "...",
  "caseRevision": "2026-...",
  "inputContract": "...",
  "outputContract": "...",
  "scriptId": "...",
  "scriptContractId": "ui.login",
  "scriptEntryCommand": "sh run.sh",
  "sourcePath": "scenarios/login.yaml",
  "sourceCommit": "3967d249..."
}
```

- API 场景：sourcePath/sourceCommit + caseId
- 接口用例：input/output contract JSON + case revision
- UI 用例：executionMode、scriptId、脚本 manifest 契约、入口命令、case revision

报告页面展示“已固化执行血缘”，可通过 API 读取原始 `lineageJson`。

## 导出

```text
GET /api/v1/plan-runs/{runId}/lineage
```

返回运行、计划和每个执行项的血缘 JSON；运行报告页面提供「导出血缘」下载。

## 场景执行记录

`api_scenario_run` 继续保存：

- `sourcePath`
- `sourceCommit`
- 组件步骤的 contractId / methodPath / description

## UI 任务级血缘

`ue_task` 直接保存：

- `script_id`
- `script_contract_id`
- `script_entry_command`
- `skills_commit`

任务详情接口返回这些字段，跨服务可直接查询 UI 执行使用的脚本契约版本。

## 后续

- 血缘与报告导出、失败分析联动
- iOS 设备执行血缘
