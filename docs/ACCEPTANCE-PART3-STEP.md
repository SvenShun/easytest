# 步骤化 UI 用例验收

日期：2026-10-27 · 真机 Xiaomi Mi 10（`f04af072`，Android 12）

## 链路

1. 用例 `case_86d58c28f9ca435fb8ad`：`executionMode=STEPS`，4 条步骤：
   LAUNCH `com.testhub.demo` → WAIT 1500ms → ASSERT_TEXT `登录` → SCREENSHOT。
2. 提交评审（tester）→ 管理员通过（admin）。
3. 计划 `plan_609ce53d65a84ff0a0d8` 挂载该用例。
4. 触发计划，指定真机；核心按用例模式路由为 `STEP`。
5. ui-execution 将步骤直接下发到已选设备，逐步回调并截图。

## 结果

- 运行：`run_0610f624e7d0458abfbd` → **SUCCESS**
- 任务：`task_a6d715eed08646c39b80`
- 步骤：**4/4 OK**
  - `LAUNCH com.testhub.demo` → `launched`
  - `WAIT 1500ms` → `waited`
  - `ASSERT_TEXT 命中文本: 登录` → `matched`
  - `SCREENSHOT` → `captured`
- 每步截图：`http://127.0.0.1:8081/artifacts/task_a6d715eed08646c39b80/steps/{1..4}.png`
- 摘要：`4/4 steps passed`

## 复现

```bash
# 服务已在 docker profile 启动
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"tester","password":"test123"}'
# 触发 plan_609ce53d65a84ff0a0d8（需 targetDeviceId=device_ac6f86892126487fa451）
```

## 测试

- `StepCaseTest` 4/4
- `StepTaskRunnerTest` 6/6
- `./scripts/mvn.sh -q -pl testhub-server,ui-execution -am test` 通过
- Web `npm run build` 通过

## 注意

- 步骤化执行不走 AI；断言基于 `uiautomator` UI 树。
- 步骤模式至少需要一条启用步骤；非 STEP 用例不能覆盖为 STEP。
- 旧库 `ue_task.executor_type` / `steps_json`、`plan_run_item.error/summary` 由启动时 `SchemaCompatibility` 扩宽。
