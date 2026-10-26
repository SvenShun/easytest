# Sonic 能力移植进度（TestHub）

依据本地 Sonic 源码研究：[SONIC-PORT-INVENTORY.md](SONIC-PORT-INVENTORY.md)。

## 已落地

| Sonic 能力 | TestHub 实现 |
|---|---|
| 设备发现/热插拔 | device-agent ADB 轮询（5s）+ `/agent/devices/refresh` |
| 设备池/在线状态 | Web 设备池、Agent 列表、设备占用（10 分钟租约） |
| 实时画面 | ws-scrcpy + WebCodecs 内嵌设备管理弹窗 |
| 鼠标/触摸控制 | 页面覆盖层 → ADB tap/swipe；返回/Home/旋转/坐标点击/文本输入 |
| 截图/UI 树 | 设备接口 + 页内截图；UI 树用于分辨率与断言 |
| 应用管理 | 列表、启动、停止、清除、卸载、APK 安装 |
| 文件 | 上传到 `/sdcard/Download` |
| Shell/终端 | 远程 Shell 控制台（单条命令，输出/退出码） |
| 性能 | 内存/CPU/电量快照 |
| 用例执行 | BDD + AI Runtime、真实设备步骤轨迹、逐步截图（MinIO） |
| 计划/套件 | 测试计划挂载 UI/API 用例，混合执行 |
| 定时任务 | Cron 任务（启停、立即执行、下次执行时间） |
| 报告 | 通过率、最近运行、失败分类看板 |
| 接口自动化 | 独立 api-execution（环境/登录鉴权/数据驱动/Redis 队列） |

## 进行中

- **步骤化 UI 用例**：`case_step` 模型 + 确定性执行（CLICK/INPUT/ASSERT 等），由子任务实现中。

## 尚未移植（Sonic 对照）

- 多设备矩阵 + case-major/device-major 覆盖策略（当前计划单目标设备）。
- 元素库（Elements）与步骤复用/公共步骤。
- 93 种步骤类型、if/else/while/公共步骤/迭代器。
- UIA2 生命周期管理（安装/instrument/forward）与 Sonic APK（IME/多点触控/剪贴板）。
- 性能持续采样图表、录屏、截图对比。
- 通知机器人、日/周报、资源级 RBAC、项目/模块/版本管理。
- iOS（sib/WDA）与 USB Hub。

## 验收命令

```bash
make part4                 # 假 adb 设备协议
make device-check          # 真机预检
make scrcpy-up             # 实时画面
make part3                 # 环境/数据驱动/共享队列
python3 scripts/part2_smoke.py
make check
```
