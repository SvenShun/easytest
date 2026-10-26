# 页面内实时真机画面（scrcpy / ws-scrcpy）

设备管理之前是“截图轮询 + ADB 动作”，不流畅。现在设备管理弹窗内嵌 **scrcpy 实时视频流**，
并支持鼠标点击/拖动直接操作真机。

## 1. 方案

- 视频：**ws-scrcpy**（MIT）把 scrcpy 的 H.264 流通过 WebSocket 推到浏览器，使用 **WebCodecs** 解码。
- 控制：页面覆盖层把鼠标坐标换算成设备坐标，调用现有 ADB 接口下发 `tap/swipe`。
  - 这样不依赖 ws-scrcpy 原生控制通道，MIUI/不同 ROM 下也能操作。
- 端口：ws-scrcpy `:8000`；TestHub 页面 `:8080`；两者同主机，iframe 直接加载。

## 2. 安装与启动

```bash
cd /Users/wen/DeepseekWorkspace/EasyTest
make scrcpy-setup     # clone ws-scrcpy + npm ci + node-pty + webpack build（仅首次）
make scrcpy-up        # 启动 :8000
```

`make up` / `dev-up` 会在已构建时自动启动 ws-scrcpy；`make down` 会停止。

打开平台：

```
http://127.0.0.1:8080/#/devices
→ 设备 f04af072 →「管理」
```

弹窗内是实时画面；鼠标点击/拖动会下发到手机。弹窗下方仍保留截图、返回/Home、坐标点击、文本输入、安装 APK、启动应用。

## 3. 直连 URL

平台会按当前页面主机名生成：

```
http://<host>:8000/#!action=stream&udid=<serial>&player=webcodecs&ws=<proxy-adb websocket>&fitToScreen=true
```

也可直接打开：

```
http://127.0.0.1:8000/
```

## 4. 验证记录

- 实时画面：`data/scrcpy-webcodecs.png`（launcher 1080×2340）
- 平台页面内画面 + 鼠标点击：`data/platform-scrcpy.png`，Toast 显示“已点击 950,1456”，手机从桌面打开了对应 App
- 真机任务仍可同时下发执行（`run_03741a294bdb4924bcdf`）

## 5. 依赖与限制

- Node 18+；Chromium/Edge 等支持 **WebCodecs** 的浏览器。Safari 16.4+ 也支持，但建议 Chrome。
- ws-scrcpy 构建时固定 `node-pty@1.1.0`（带 darwin-arm64 预编译）；`tools/` 已被 gitignore。
- 实时流一个设备只能有一个 scrcpy 会话；不要同时开桌面 scrcpy。
- 覆盖层控制走 ADB，不是 scrcpy 原生 control socket；拖拽会转成 `swipe`，不支持多指手势。
- 鼠标映射按 UI 树分辨率换算，视频 letterbox 会造成几像素偏差；需要像素级精确时可先用“坐标点击”输入。
- 无音频；scrcpy-server 为 ws-scrcpy 内置版本（`1.19-ws8`）。
- **安全**：ws-scrcpy 默认无鉴权且监听 0.0.0.0:8000。生产必须加反向代理/登录/防火墙，不要直接暴露公网。
