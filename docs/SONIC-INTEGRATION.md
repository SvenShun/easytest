# Sonic 接入说明（设备层）

> 上游源码：`/Users/wen/Workspace/TestPlatform/sonic`（sonic-server / sonic-agent / sonic-driver-core /
> sonic-client-web / sonic-ios-bridge / sonic-android-apk）。接入所用副本位于 `upstream/sonic-driver-core/`（gitignored）。

## 1. 许可证（必须区分）

| 组件 | 许可证 | 结论 |
|---|---|---|
| `sonic-driver-core` | **Apache-2.0** | 可作为依赖安全使用 |
| `sonic-agent` | **AGPL-3.0** | 修改/分发需评估强 copyleft 义务 |
| `sonic-server` | **AGPL-3.0** | 同上 |
| `sonic-ios-bridge` | AGPL-3.0 | 同上 |

V1 只引入 **driver-core（Apache-2.0）**，不复制 agent/server 代码，因此不引入 AGPL 传染。

## 2. 已实现

- 从提供的源码构建 `io.github.soniccloudorg:sonic-driver-core:1.1.33` 并安装到工作区 Maven 仓库
  （`./scripts/mvn.sh -f upstream/sonic-driver-core/pom.xml install`，或直接依赖该坐标）。
- `device-agent` 新增 **`mode=sonic`**：使用 Sonic 的 `AndroidDriver`（UIA2 客户端）驱动真机。
  - 截图：`AndroidDriver.screenshot()`
  - UI 树：`getPageSource()`（uiautomator XML）→ 解析为 TestHub 的 `{width,height,screen:{...},elements}`
  - 点击/输入：按 `resource-id` 或 `text` 生成 XPATH，调用 `findElement(...).click()/sendKeys()`
  - 滑动：`AndroidDriver.swipe(...)`
  - back/home/launch：`adb shell input keyevent` / `adb shell monkey`（Sonic 本身依赖 adb）
- 配置示例：

```yaml
testhub:
  agent:
    mode: sonic
    sonic:
      adb: adb
      devices:
        - serial: emulator-5554
          name: Pixel 7
          model: sdk_gphone64
          uiaUrl: http://127.0.0.1:6790   # Sonic-Agent 启动的 UIA2 服务
          tags: [android, sonic]
```

- 自动化测试：`SonicUiTreeParserTest`（uiautomator XML → 元素/边界/可编辑类型/顶层别名）。

## 3. 未实现 / 前置条件

- **真实运行未验证**：当前无 Android 真机；`mode=sonic` 需要每台设备有 UIA2 服务
  （Sonic-Agent 配合 `sonic-android-apk` 启动），否则连接失败并回退为空设备列表。
- 未接入：Sonic-Agent 的 WebSocket/Eureka 传输、scrcpy 投屏、iOS（`sonic-ios-bridge` / WDA）。
- 未使用 sonic-agent 的 `bridge`（ADB/scrcpy 封装），因为这些类不在 driver-core 中且 agent 为 AGPL；
  V1 通过 adb 命令补齐 back/home/launch，后续可按需替换为自有实现。
- 投屏与远程控制仍按 V1 范围不做。

## 4. 验证方式

```bash
./scripts/mvn.sh -pl device-agent test            # 36 个测试
./scripts/mvn.sh -pl device-agent -DskipTests package
# 真机环境：启动 Sonic-Agent（或单独启动 UIA2 服务）→ 配置 mode=sonic → 启动 device-agent
TESTHUB_AGENT_MODE=sonic ./scripts/mvn.sh -pl device-agent spring-boot:run
curl http://127.0.0.1:8090/agent/devices
```

## 5. 与架构文档的对应

架构文档要求「Sonic-Agent 保留并增强为设备子服务器的执行底座」。
V1 的实现是：**TestHub device-agent 作为执行底座，复用 Sonic driver-core 作为真机驱动**；
设备注册/心跳、任务下发、脚本执行、AI Runtime 桥接仍由 TestHub 负责。
若后续需要完整 Sonic-Agent 能力（scrcpy、iOS、设备管理），建议以独立进程运行 Sonic-Agent 并通过适配器接入，
而不是直接 fork（AGPL 与版本维护成本）。
