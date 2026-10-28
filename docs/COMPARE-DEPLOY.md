# MeterSphere / Sonic 体验环境

两套系统使用本地源码部署，和 TestHub 分开端口，不修改产品功能代码。

| 系统 | 页面 | 账号 |
|---|---|---|
| MeterSphere 3.5.7 | http://127.0.0.1:5100 | `admin` / `metersphere` |
| Sonic 2.0 | http://127.0.0.1:3001 | `sonic` / `sonic` |

## MeterSphere

- 后端：`upstream/metersphere-clean`（JDK 21），API `:8082`
- 前端：`upstream/metersphere/frontend/dist`，由 `deploy/compare/static-proxy.mjs :5100` 提供静态页并把 API 代理到 `:8082`
- 构建：`JAVA_HOME=<jdk21> mvn -f backend/pom.xml -DskipTests package`；前端 `pnpm build`
- 说明：前端 `vue-tsc` 在当前依赖组合下有类型错误，使用 `vite build` 跳过类型检查；页面产物来自原始源码。

## Sonic

- 后端：`upstream/sonic/sonic-server-main` 的 eureka/controller/folder/gateway 四个 jar
- 前端：`upstream/sonic/sonic-client-web-main/dist`，`:3001` 静态页 + `/server` 代理到 gateway `:3000`
- MySQL：`sonic` 库（TestHub 的 MySQL 容器）
- 首次体验账号：`sonic` / `sonic`（已注册，且 `PERMISSION_SUPER_ADMIN=sonic`）
- 当前没有连接真机/启动 Sonic Agent；用于对比 UI、场景/步骤模型和控制器交互

## 启动/停止

```bash
./deploy/compare/sonic-up.sh
./deploy/compare/metersphere-ui-up.sh
./deploy/compare/sonic-down.sh
```

MeterSphere 后端使用 `scripts/metersphere-up.sh`（`METERSPHERE_SRC=upstream/metersphere-clean`）。
当前已启动并验证 API 登录；按你的要求没有做浏览器页面自动化验证。

## 快速状态

```bash
./deploy/compare/status.sh
```

预期：MeterSphere UI/API 200、Sonic UI/API 200。

## 注意

- MeterSphere 前端在当前依赖组合下 `vue-tsc` 有类型错误，部署只用 `vite build` 产出运行包，未修改业务代码。
- Sonic 源码在 Node 22 下 `vite.config.js` 的 `__dirname` 与 ESM 不兼容，部署时使用等价的 `vite.config.cjs` 构建，未修改业务代码。
- Sonic 首次启动时创建了 `sonic/sonic` 账号；Sonic 没有启动 Agent，也没有连接真机。
- TestHub 仍在 `http://127.0.0.1:8080`，未改动。
