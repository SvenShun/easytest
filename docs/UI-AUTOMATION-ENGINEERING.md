# UI 自动化工程化

UI 自动化支持三种执行模式，平台用例是契约，执行由独立 ui-execution / device-agent 完成。

## 执行模式

| 模式 | 说明 | 适合 |
| --- | --- | --- |
| `AI` | BDD 文本 + AI Runtime 视觉驱动 | 探索性、表达性用例 |
| `STEPS` | 确定性 STEP 步骤驱动真机 | 工程化回归、可重复执行 |
| `SCRIPT` | 脚本包 + `entryCommand` + JUnit XML | 已有 UI 测试工程代码 |

## STEP 契约

平台 `case_step` 与 ui-execution `StepSpec` 对齐：

```text
seq, stepType, selectorType, selector, value, assertValue, timeoutMs, enabled
```

当前步骤类型：

```text
CLICK INPUT TAP SWIPE SCROLL BACK HOME WAIT LAUNCH
ASSERT_TEXT ASSERT_NOT_TEXT ASSERT_EXISTS WAIT_FOR SCREENSHOT
```

- `WAIT_FOR`：轮询 UI 树等待元素出现
- `ASSERT_NOT_TEXT`：断言文本不存在
- `SCROLL`：坐标滚动 `x1,y1,x2,y2`
- 每一步保存状态、耗时、截图（尽力）

规范化契约接口：

```text
GET http://127.0.0.1:8081/api/v1/ui-execution/step-schema
```

## 脚本工程契约

脚本包（zip）由平台上传，用例关联 `scriptId`：

```text
scriptType = JUNIT / PYTEST / ...（按现有脚本类型）
entryCommand = sh run.sh
```

上传时可附带 `manifest` 表单字段（YAML）：

```yaml
schemaVersion: 1
id: ui.login
name: 登录脚本
description: 使用测试工程代码驱动登录
entryCommand: sh run.sh
inputs: [username, password]
outputs: [token]
```

- 平台保存 `contractId/description/inputs/outputs`
- 用例详情显示脚本契约
- `entryCommand` 未显式传入时使用 manifest 中的入口
- 缺少 `id` 的 manifest 会被 400 拒绝

平台「UI 脚本」页面支持 zip 上传、类型/入口命令和 manifest 编辑，列表展示契约 ID、输入/输出。

device-agent 下载脚本包、执行、解析 JUnit XML 并回传结果/工件。

## 设备与项目

- 设备默认归属 `default` 项目，可分配项目
- 计划触发时 UI 任务携带项目 ID
- ui-execution 只选择当前项目设备

## iOS

当前 Android 已支持 ADB/Sonic/UIA2；iOS 排在最后，后续通过同一 `DeviceDriver` 契约接入
XCTest/WDA，不改变平台 STEP/SCRIPT 契约。
