# 阶段六验收：UIA2、性能/录屏/对比、通知、知识库

日期：2026-10-28

## 已实现

1. **UIA2 生命周期**：安装/启动 Appium UIA2 server，切换 Sonic 驱动；支持中文 `sendKeys` 和 UIA2 `/touch/perform` 双指触控。
2. **性能曲线**：`ue_device_metric` 采样 + 设备页 3 秒轮询折线图。
3. **录屏**：`adb shell screenrecord` 启停，mp4 存产物并返回 URL。
4. **截图对比**：基准图 vs 当前屏幕，像素差异百分比 + 三张产物图。
5. **通知机器人**：通用 Webhook / 钉钉 / 企业微信 / 飞书；运行终态自动推送一次，支持测试发送。
6. **计划级设备绑定**：计划详情「执行设备」多选持久化，触发未指定设备时按绑定设备矩阵执行。
7. **UI 知识库**：应用/页面/控件/操作四层模型，text/id/desc/intent/截图/来源/置信度，页面可编辑，支持批量导入 API。

## 验证

- `./scripts/check.sh`：Java **177** + Python **48** = 225，前端构建通过（新增 ImageDiffServiceTest、KbApiTest、PlanTriggerTest 绑定设备用例）。
- 通知：本地 mock webhook 收到 `RUN_COMPLETED`，字段 runId/planName/status/text 正确。
- 计划绑定：API 保存/读取 deviceIds；触发实际创建对应运行项。
- 知识库：API 创建 APP/页面/控件/操作成功，列表计数正确；截图上传/公开读取 200。
- 前端：KB/通知页面无 console error。
- 假 Agent HTTP 全链路：设备注册 → 性能采样 `cpuPercent=12.5/memUsedPercent=48.2` → 历史查询 → 截图对比 `mismatchPercent=0`（三张产物 URL）→ 录屏开始/停止（22 字节 mp4 产物 URL），全部成功。
- 通知：本地 Webhook 收到 RUN_COMPLETED（runId/planName/status/text/url 完整）。
- 计划绑定：API 保存/读取绑定设备，触发按绑定设备生成运行项（单元测试）。

## 受限

当前真机 `f04af072` 在开发过程中断开（`adb devices` 为空），因此 UIA2 启停/中文输入、录屏、性能曲线、截图对比的**真机**端到端验证待设备重新连接后补跑；相关接口、单元测试与 UI 已完成。
