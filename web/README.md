# TestHub V1 · Web 控制台

React 18 + Vite 5 + TypeScript 的测试平台控制台，对接 `docs/API-CONTRACT.md` 定义的 core（8080）与 ui-execution（8081）服务。

覆盖：登录、总览、用例与评审、AI BDD 草稿、测试计划与运行报告、执行任务轨迹、设备池与 FIFO 队列、系统设置（模型配置 / Skills 指针 / 审计日志 / 用户）。

## 1. 快速开始

要求：Node.js 20+（开发环境使用 Node 22）、npm 10。

```bash
cd web
npm install
npm run dev
# 打开 http://localhost:5173
```

生产构建与本地预览：

```bash
npm run build     # tsc --noEmit && vite build，产物在 web/dist
npm run preview
```

开发服务器固定 `5173`，`host=true`（局域网/容器可通过宿主机访问）。构建前必须先通过 TypeScript 类型检查，项目内已包含 `package-lock.json`，可使用 `npm ci` 复现安装。

### 演示账号

| 用户名 | 密码 | 角色 | 默认可见范围 |
|---|---|---|---|
| admin | admin123 | ADMIN | 全部页面 + 系统设置 |
| tester | test123 | TESTER | 总览/设备/任务/用例/计划/AI |
| business | biz123 | BUSINESS | 总览/设备/任务/用例（只读）/AI |
| ops | ops123 | OPS | 总览/设备/任务 |

登录页可点击账号卡片自动填充。账号由后端种子数据提供，后端未启动时登录会给出「无法连接服务」提示。

## 2. 环境变量与服务地址

默认值：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `VITE_CORE_URL` | `http://127.0.0.1:8080` | testhub-server（登录、用例、计划、AI、系统设置） |
| `VITE_UEXEC_URL` | `http://127.0.0.1:8081` | ui-execution（任务、设备、统计、Skills、审计） |

可在 `web/.env.local` 中覆盖（参考 `.env.example`）：

```dotenv
VITE_CORE_URL=http://127.0.0.1:8080
VITE_UEXEC_URL=http://127.0.0.1:8081
```

运行期也可以在**登录页「服务地址设置」**或**系统设置 → 客户端服务地址**中修改，修改后立即对后续请求生效，并持久化到浏览器 localStorage：

- `testhub.config.coreUrl`
- `testhub.config.uexecUrl`
- `testhub.token`（JWT）
- `testhub.user`（当前用户快照）

> 服务地址与登录态都是**浏览器本地配置**，不同浏览器/设备互不影响。生产部署建议通过环境变量或反向代理固定地址。

## 3. 页面清单

| 路由 | 页面 | 可见角色 | 主要能力 |
|---|---|---|---|
| `#/login` | 登录 | 未登录 | 账号密码登录、演示账号、服务地址设置、已登录自动跳转 |
| `#/dashboard` | 总览 | 全部 | 设备在线/空闲、执行中任务、今日失败、失败分类、设备卡片、最近 10 条任务，10s 轮询 |
| `#/cases` | 用例管理 | ADMIN/TESTER/BUSINESS | 筛选、新建/编辑、提交/通过/驳回、删除、详情抽屉、评审时间线、失败分析、`?highlight=` 定位 |
| `#/ai` | AI 分析 | ADMIN/TESTER/BUSINESS | 需求/文本生成 BDD 草稿、草稿去评审、历史任务 |
| `#/plans` | 测试计划 | ADMIN/TESTER | 列表、新建、触发、删除、用例数/运行次数补全 |
| `#/plans/:id` | 计划详情 | ADMIN/TESTER | 计划用例、添加/移除、触发弹窗（设备/标签/超时）、运行列表、5s 轮询 |
| `#/runs/:runId` | 运行报告 | ADMIN/TESTER | 汇总卡、按执行器统计、失败分类、用例明细、轨迹/报告链接、5s 轮询 |
| `#/tasks` | 执行任务 | 全部 | 状态/执行器/计划运行/关键字筛选，分页，进入轨迹 |
| `#/tasks/:id` | 任务详情 | 全部（取消仅 TESTER/ADMIN） | 步骤时间线与截图、决策原文、实时日志、产物、失败报告，2s 轮询 |
| `#/devices` | 设备池 | 全部 | 设备卡、状态/标签/队列/当前任务、队列模态、Agent 列表，10s 轮询 |
| `#/settings` | 系统设置 | ADMIN | 模型配置、Skills 指针、合并审计日志、用户列表、客户端服务地址 |

未知路由显示 404；角色无权限时显示「无权访问」状态页；所有页面都有 ErrorBoundary 兜底，不会白屏。

### 业务角色限制

- BUSINESS 访问用例页为只读：不渲染新建、编辑、删除、评审操作。
- OPS 不显示用例/计划/AI/设置菜单，直接访问受保护路由会展示无权提示。
- 系统设置仅 ADMIN 可见、可写。

## 4. 目录结构

```text
web/
├─ index.html
├─ vite.config.ts              # port 5173 / host true / outDir dist
├─ tsconfig.json
├─ src/
│  ├─ main.tsx                 # React 挂载 + ErrorBoundary
│  ├─ App.tsx                  # 路由分发、角色守卫、顶部刷新
│  ├─ config.ts                # 服务地址默认值/环境变量/localStorage/订阅
│  ├─ auth.tsx                 # AuthContext（token/user、登录/登出、401 处理）
│  ├─ router.tsx               # 轻量 hash 路由（useHashRoute/navigate）
│  ├─ constants.ts             # 状态中文标签、色板、失败分类、角色
│  ├─ utils.ts                 # 本地时间、时长、分页/状态工具
│  ├─ hooks.ts                 # usePolling / useDebouncedValue
│  ├─ styles.css               # 设计系统与响应式（无 CSS 框架）
│  ├─ api/
│  │  ├─ client.ts             # fetch 封装、Bearer、20s 超时、错误体解析、401
│  │  ├─ types.ts              # 契约类型
│  │  ├─ auth.ts / system.ts / requirements.ts / scripts.ts
│  │  ├─ cases.ts / ai.ts / plans.ts / uiexec.ts
│  │  └─ index.ts              # 按域导出
│  ├─ components/
│  │  ├─ icons.tsx             # 内联 SVG 图标 + 品牌标
│  │  ├─ ui.tsx                # 按钮/输入/表格辅助/空态/骨架/分页/Toast 等
│  │  ├─ Modal.tsx             # Modal / Drawer / ConfirmDialog / PromptDialog / Lightbox
│  │  ├─ Toast.tsx             # 轻量 Toast store + Host
│  │  ├─ Layout.tsx            # 侧边栏、顶部栏、403/404
│  │  ├─ TriggerResultModal.tsx
│  │  └─ ErrorBoundary.tsx
│  └─ pages/                   # Login / Dashboard / Cases / Ai / Plans / PlanDetail
│                              # RunDetail / Tasks / TaskDetail / Devices / Settings
```

## 5. 后端依赖

| 页面/能力 | 依赖接口 | 服务 |
|---|---|---|
| 登录 | `POST /api/v1/auth/login`、`GET /api/v1/auth/me` | core |
| 总览 | `GET /api/v1/ui-execution/stats`、`/devices`、`/tasks` | ui-execution |
| 用例 | `GET/POST/PUT/DELETE /api/v1/cases`、`/review`、`/reviews`、`GET /requirements`、`GET /scripts` | core |
| AI | `POST /api/v1/ai/bdd-drafts`、`GET /api/v1/ai/jobs`、`/jobs/{id}` | core |
| 计划/报告 | `/api/v1/plans`、`/{id}`、`/cases`、`/trigger`、`/runs`、`/plan-runs/{id}`、`/report` | core |
| 任务/设备 | `/api/v1/ui-execution/tasks`、`/tasks/{id}`、`/cancel`、`/devices`、`/{id}/queue`、`/agents` | ui-execution |
| 设置 | `/api/v1/system/model-configs`、`/audit-logs`、`/users`；`/api/v1/ui-execution/skills/pointer*`、`/audit-logs` | core + ui-execution |

前端不直接访问 device-agent（8090）或 ai-runtime（8095）。所有请求带 `Authorization: Bearer <JWT>`，超时 20 秒。

开发期浏览器从 `5173` 直连 `8080/8081`，需要后端配置 CORS（允许 `http://localhost:5173` / `http://127.0.0.1:5173`），或由反向代理统一域名。

### 后端未就绪时的表现

- 登录页：内联展示「无法连接服务 …，请确认服务已启动或检查服务地址配置」。
- 列表/详情页：展示带「重试」的 ErrorPanel + 服务启动提示，保留导航，不白屏。
- 总览：部分请求失败时展示「部分数据加载失败」Callout，其余模块仍可渲染。
- 不写入任何假数据；空列表使用带图标与引导文案的空态。
- 401：清除 token/user，Toast 提示会话过期并跳转 `#/login`。

## 6. 设计说明

- 浅色专业风：背景 `#f6f7fb`、白卡片、1px `#e6e8ef` 边框、10px 圆角、轻阴影；主色 `#2f6bff`，hover `#1f56e0`；成功 `#16a34a`、警告 `#d97706`、危险 `#dc2626`、次要文字 `#6b7280`。
- 布局：固定 232px 侧边栏 + 顶栏（标题/时间/刷新/用户/退出）+ 内容区最大宽 1400px，卡片间距 16px。
- 响应式：≤1100px 侧边栏变为顶部横向滚动导航，顶栏变为普通流；表格横向滚动；≤860/640px 继续压缩统计卡、筛选栏与详情网格。
- 图标全部为自绘内联 SVG（16/18/20px 线性图标），不使用 emoji。
- 状态均为中文标签 + 颜色；时间为本地 `YYYY-MM-DD HH:mm:ss`。
- 组件：primary/secondary/danger/ghost/success 按钮（loading 禁用）、输入框、下拉、Pill、表格（hover/空态/横向滚动）、Modal、Drawer、Toast、ConfirmDialog、PromptDialog、Skeleton、ProgressBar、CodeBlock、Pagination、Tabs、失败分类条形图。
- 轮询：总览/设备 10s、计划运行 5s（有活动运行时）、任务详情 2s（未终止时）。页面隐藏时跳过轮询，恢复后立即补拉。
- 无 UI 框架、CSS 框架、图表库、状态管理库、react-router；仅 React 运行时依赖。

## 7. 已知问题 / TODO

1. **计划用例执行器覆盖**：V1 core 契约没有逐用例更新覆盖值的接口，计划详情中以下拉框只读展示并附说明；覆盖值只能在「添加用例」时统一指定。
2. **任务列表前端过滤**：执行器、关键字为前端过滤，单次最多加载 100 条任务；状态与计划运行 ID 走服务端。超大数据量建议后续改为服务端过滤。
3. **审计日志合并分页**：core 与 ui-execution 各取最近 50 条在前端合并排序，再做客户端分页；更早日志暂不可见。
4. **用例选择上限**：添加用例模态一次加载最多 100 条已通过用例。
5. **未使用 SSE**：任务详情按 2s 轮询，后续可切换到契约中的 `/stream` SSE 以降低延迟和请求量。
6. **未实现的页面**：脚本工程上传、需求 CRUD、设备投屏不在需求页面清单内，仅提供下拉数据读取。
7. **部署**：hash 路由本身不需要 SPA fallback，但静态托管仍建议配置 fallback；JWT 存 localStorage 属 V1 取舍，生产需评估 XSS 风险。
8. **服务地址变更**：设置页修改后不会自动重载当前页面数据，切换页面/刷新后生效；这是为了避免打断正在编辑的表单。

## 8. 验证记录

```text
npm install            # 生成 package-lock.json
npm run build          # tsc --noEmit + vite build，成功
npm run dev            # Vite 5173，curl http://127.0.0.1:5173/ 返回 200 HTML
```

开发期另使用 jsdom + 模拟契约响应做了全页面冒烟（登录、总览、用例评审、AI、计划触发、运行报告、任务轨迹/取消、设备队列、设置四标签、角色权限、404、后端不可用、业务只读、OPS 越权），未发现运行时错误；该测试不进入项目依赖。
