# 开源与许可证说明

> 结论：本仓库以 **AGPL-3.0** 开源，兼容 MeterSphere 的 GPLv3（含附加条款）与 Sonic 的 AGPL-3.0；
> 当前集成方式（HTTP API + Apache-2.0 driver-core 依赖）不触发源码级 copyleft；若后续把
> MeterSphere 源码内嵌为模块，按 GPLv3/AGPL 要求公开对应源码即可。

## 1. 本仓库许可证

- 根目录 `LICENSE`：GNU Affero General Public License v3.0。
- 选择 AGPL 的原因：同时兼容 MeterSphere（GPLv3+附加条款）与 Sonic（AGPL-3.0），
  且覆盖"通过网络提供服务"的场景。

## 2. 与上游的关系

| 上游 | 许可证 | 当前使用方式 | 义务 |
|---|---|---|---|
| MeterSphere `v3.x` | GPLv3 + 附加条款 | 独立进程 + HTTP API；源码仅本地构建 | 分发 MeterSphere 二进制/修改版时须提供源码、保留版权与附加条款；API 调用本身不传染 |
| Sonic `sonic-driver-core` | Apache-2.0 | Maven 依赖（未修改） | 保留 NOTICE/许可证文本 |
| Sonic `sonic-agent`/`server` | AGPL-3.0 | 未链接、未复制 | 若未来链接/分发，需按 AGPL 公开源码（含网络服务条款） |
| 其他依赖 | 见表 `THIRD_PARTY_NOTICES.md` | 二进制依赖或外部服务 | 按其许可证保留声明 |

## 3. 若进行源码级内嵌（阶段 2）

把 ui-execution 作为 MeterSphere `backend/services` 模块后：

1. 组合作品按 **GPLv3**（及 MeterSphere 附加条款）分发；本仓库的 AGPL-3.0 与之兼容。
2. 必须公开：
   - MeterSphere 的修改版源码（含新增模块）；
   - 构建与安装脚本；
   - 许可证与版权声明。
3. 不得移除 MeterSphere 的 Logo/版权标识（附加条款第 1 条）。
4. 贡献者条款（附加条款第 2 条）需在 README/CONTRIBUTING 中说明。
5. 若同时链接 Sonic-Agent（AGPL），整体按 AGPL-3.0 分发，并提供 Corresponding Source。

## 4. 发布清单

- [x] 根 `LICENSE`（AGPL-3.0）
- [x] `THIRD_PARTY_NOTICES.md`
- [ ] 每个源文件加 SPDX 头（可选，建议批量处理）
- [ ] 提供 MeterSphere 构建脚本与版本固定（`docs/METERSPHERE-INTEGRATION.md` 已记录）
- [ ] 若内嵌：在仓库内保留 MeterSphere 源码/补丁与构建产物说明
- [ ] 前端依赖许可证扫描（`npm licenses`）
- [ ] 发布时附 `SOURCE` 说明（如何获取对应源码）

## 5. 商标

"MeterSphere"、"Sonic" 为其各自权利人的商标。本仓库仅作技术集成说明，不代表其官方发行版。
