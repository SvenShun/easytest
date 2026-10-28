# UIA2 / 中文输入 / 多点触控 / 通知机器人

## UIA2（Appium UIA2 server）

真机默认使用 ADB 驱动。启用 UIA2 后，device-agent 会：

1. 检查/安装 Sonic 打包的 Appium UIA2 server APK（`io.appium.uiautomator2.server`）。
2. `adb forward tcp:<port> tcp:6790`。
3. 启动 `androidx.test.runner.AndroidJUnitRunner`。
4. 把设备切换到 `SonicAndroidDeviceDriver`（底层 `sonic-driver-core`，Apache-2.0）。

入口：设备池 → 管理 → 高级工具 → **启用 UIA2**。

准备 APK：

```bash
SONIC_HOME=/path/to/sonic ./scripts/uia2-setup.sh
# 或设置 -Dtesthub.agent.uia2-server-apk=/abs/path.apk
```

效果：
- **中文/Unicode 输入**：UIA2 的 `sendKeys`/元素输入走无障碍注入，不再受 `adb shell input text` 只能 ASCII 的限制。
- **多点触控**：设备管理的「双指放大 / 双指缩小」，通过 UIA2 `/touch/perform` 发送两根手指的 W3C pointer 序列。
- 停止 UIA2 会移除 forward、强制停止 server/test，并恢复 ADB 驱动。

> 需要设备允许后台运行/无障碍；首次启动可能 20–40 秒。UIA2 是可选模式，不影响现有 ADB/AI/步骤链路。

## 性能曲线

- `POST /devices/{id}/performance/sample` 采样并写入 `ue_device_metric`
- `GET /devices/{id}/performance/history?minutes=60`
- 设备管理「性能曲线」每 3 秒采样，绘制 CPU/内存折线。

## 录屏

- `POST /devices/{id}/recording/start` → `adb shell screenrecord`（最长 180 秒）
- `POST /devices/{id}/recording/stop` → 拉取 mp4，写入产物存储并返回 URL
- 设备管理「开始录屏/停止并保存」。

## 截图对比

- `POST /devices/{id}/screenshot/compare`（multipart `baseline`）
- 返回差异百分比、基准/当前/差异三张图的 URL
- 设备管理，上传基准截图后与当前屏幕对比。

## 通知机器人

- 菜单：通知机器人
- 类型：通用 Webhook / 钉钉 / 企业微信 / 飞书
- 计划运行结束（COMPLETED/PARTIAL/FAILED）自动推送一次；有「测试」按钮
- 钉钉支持可选加签密钥；企业微信/飞书使用 markdown/text 消息

配置示例：

```json
{"name":"研发群","channelType":"GENERIC","webhookUrl":"https://example/hook","enabled":true,"events":"RUN_COMPLETED"}
```
