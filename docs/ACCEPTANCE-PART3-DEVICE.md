# 第三部分（设备子服务器 / ADB 驱动）验收

> 目标：device-agent 的 ADB 真机通道可用：设备发现、热插拔、截图、UI 树、动作、APK 安装。
> 本次用**假 adb**做可重复验收；物理设备接入步骤见 §4。

## 1. 交付内容

| 能力 | 实现 |
|---|---|
| ADB 设备发现 | `adb devices -l` 解析 serial/model，`mode=adb/auto` |
| 热插拔 | `DeviceManager` 定时刷新（`testhub.agent.device-refresh-ms`，默认 15s）+ `POST /agent/devices/refresh` |
| 截图 | `/device/{serial}/screenshot`（`adb exec-out screencap -p`） |
| UI 树 | `adb shell uiautomator dump` + 解析节点/文本/clickable/editable/bounds |
| 动作 | click/tap、input、swipe、back、home、launch、wait（ADB 命令） |
| APK 安装 | `POST /device/{serial}/install`（multipart，`adb install -r`） |
| 配置 | `testhub.agent.adb`、`testhub.agent.device-refresh-ms` |

## 2. 自动验收（假 adb）

```bash
cd /Users/wen/DeepseekWorkspace/EasyTest
make part4
```

```
PART 3 DEVICE RESULT: 10 checks passed, 0 failed
```

| 组 | 检查项 | 结果 |
|---|---|---|
| 发现 | 启动发现 `fake-1` | 1/1 |
| 热插拔 | 新增 `fake-2`、移除 `fake-1` | 2/2 |
| 截图/UI 树 | PNG 头、200x400 bounds、文本“登录” | 3/3 |
| 动作 | click 成功、ADB `input tap 60 50` 已发送 | 2/2 |
| 安装 | APK 上传、ADB `install -r` 已发送 | 2/2 |

脚本用 Python 生成假 `adb` 可执行文件，device-agent 以 `mode=adb` 启动在 :8091，
**不注册 ui-execution、不依赖真机/模拟器**；日志见临时目录与 `logs/` 输出。

## 3. 代码位置

- `device-agent/.../driver/AdbDeviceDriver`：发现、截图、UI 树、动作、安装
- `device-agent/.../driver/DeviceManager`：adb 热插拔、auto 回退模拟设备
- `device-agent/.../http/AgentController`：`POST /agent/devices/refresh`
- `device-agent/.../http/DeviceController`：`POST /device/{serial}/install`
- 测试：`AdbDeviceDriverTest`、`DeviceManagerTest`

## 4. 真机接入步骤

1. 安装 Android platform-tools，确认：

```bash
adb devices -l
# 出现 <serial> device product:... model:...
```

2. 以 ADB 模式启动 device-agent（替换 adb 路径）：

```bash
TESTHUB_AGENT_MODE=adb \
TESTHUB_AGENT_ADB=/Users/you/Library/Android/sdk/platform-tools/adb \
TESTHUB_AGENT_DEVICE_REFRESH_MS=5000 \
./scripts/mvn.sh -q -pl device-agent spring-boot:run
```

3. 检查：

```bash
curl -H "X-Agent-Token: testhub-dev-agent-token" http://127.0.0.1:8090/agent/devices
curl -o /tmp/s.png http://127.0.0.1:8090/device/<serial>/screenshot
curl http://127.0.0.1:8090/device/<serial>/ui-tree
```

4. 安装 APK：

```bash
curl -H "X-Agent-Token: testhub-dev-agent-token" \
  -F file=@app-debug.apk "http://127.0.0.1:8090/device/<serial>/install?replace=true"
```

5. 执行动作：

```bash
curl -X POST http://127.0.0.1:8090/device/<serial>/action \
  -H 'Content-Type: application/json' \
  -d '{"action":"click","target":{"text":"登录"}}'
```

> 真机需解锁屏幕、允许 USB 调试，并确保 `uiautomator dump` 可写 `/sdcard/window_dump.xml`。
>
> **MIUI/部分国产 ROM**：还需在开发者选项开启「USB 调试（安全设置）」与「USB 安装」，否则 `adb shell input` 可能返回 0
> 但实际被 `INJECT_EVENTS` 拦截；驱动会识别该错误并返回失败，而不是误报成功。

## 5. 已知边界

- 物理设备尚未在本机验证；本次是假 adb 协议验收，真机步骤已文档化。
- 没有 scrcpy 投屏/实时视频；截图是单帧。
- 只支持 Android；iOS/WDA 未实现。
- `input text` 仅做空格转义，复杂 Unicode/中文输入可能受 ADB 限制，后续可接 ADBKeyboard。
- APK 安装仅 `adb install -r`，未做签名校验、分包安装。
- 热插拔刷新是全量 diff；大规模设备池需要增量事件（ADB track-devices）。
