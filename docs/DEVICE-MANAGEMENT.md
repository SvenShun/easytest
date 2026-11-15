# 真机管理与远程操作

设备池页面：`http://127.0.0.1:8080/#/devices`

## 能力

| 能力 | 入口 | 后端 |
|---|---|---|
| 在线状态/热插拔 | 设备卡片；5s Agent 刷新 | `/api/v1/ui-execution/devices` |
| 实时画面 | 「管理」弹窗 scrcpy 流 | ws-scrcpy `:8000` |
| 鼠标点击/拖动 | 画面覆盖层 | `POST /devices/{id}/action` |
| 返回/Home/旋转 | 管理弹窗快捷操作 | action / rotate |
| 坐标点击/文本输入 | 管理弹窗 | action tap/text |
| 占用人/释放 | 管理弹窗 | `POST /devices/{id}/occupy|release`（10 分钟） |
| APK 安装 | 管理弹窗上传 | `POST /devices/{id}/install` |
| 应用列表/启动/停止/清除/卸载 | 高级工具 | `.../apps`、`.../apps/{action}`、`.../uninstall` |
| 文件上传 | 高级工具 | `POST /devices/{id}/files` → `/sdcard/Download` |
| 远程 Shell | 高级工具 | `POST /devices/{id}/shell` |
| 性能 | 高级工具 | `GET /devices/{id}/performance`（内存/CPU/电量） |
| 截图 | 管理弹窗 | `GET /devices/{id}/screenshot` |
| UI 树/分辨率 | 管理弹窗自动获取 | `GET /devices/{id}/ui-tree` |

## 执行

- 测试计划 → 触发 → 选择真机 `f04af072`；任务在设备 FIFO 队列中串行执行。
- 定时任务页面可绑定该设备，按 Cron 触发计划；「立即执行」用于手动验收。
- 步骤截图：UI 任务逐步截图写入 MinIO，并在任务轨迹中返回 `screenshotUrl`。

## 安全

- Shell/安装/文件上传仅 ADMIN/TESTER。
- 占用是软租约，不阻止任务队列；用于避免多人同时远程操作。
- ws-scrcpy `:8000` 无鉴权，生产需反向代理+认证。
- Android 设备需开启 USB 调试（安全设置）与 USB 安装；MIUI 未开启时驱动会返回明确错误。

## iOS（WDA）

iOS 通过 WebDriverAgent 接入同一个设备/STEP 契约：

```bash
EASYTEST_AGENT_MODE=ios
EASYTEST_AGENT_IOS_DEVICES_0_SERIAL=<udid>
EASYTEST_AGENT_IOS_DEVICES_0_NAME=iPhone\ 15
EASYTEST_AGENT_IOS_DEVICES_0_WDA_URL=http://127.0.0.1:8100
EASYTEST_AGENT_IOS_DEVICES_0_TAGS=ios,wda
```

启动前需在设备/模拟器上运行 WDA 并确保端口可达。已支持：

- 截图
- UI 树（WDA source XML 转 EasyTest UiTree）
- 点击/文本输入/滑动/返回/Home/启动 App/等待
- 设备在注册时上报 `platform=ios`，ui-execution 的 STEP 执行器无需改动

限制：

- 安装/卸载/文件/Shell 等 Android 专属操作暂不支持
- WDA 会话为懒创建；WDA 不可达时动作返回明确失败
- iOS 排期最后，先保证 STEP/脚本执行主链路

## Web（HtmlUnit Headless）

`EASYTEST_AGENT_MODE=web` + `EASYTEST_AGENT_WEB_URL` 会注册一个 `platform=web` 的虚拟设备。
复用现有 UI STEP/队列/报告链路：

```bash
EASYTEST_AGENT_MODE=web EASYTEST_AGENT_WEB_URL=http://127.0.0.1:18090/ EASYTEST_AGENT_NAME=web-agent-local SERVER_PORT=8091 java -jar device-agent/target/device-agent-1.0.0.jar
```

Web STEP 支持 CLICK/INPUT/LAUNCH/ASSERT_TEXT/WAIT（HtmlUnit DOM）。
后续可替换 Playwright/Selenium，不影响平台 STEP 契约与任务模型。
