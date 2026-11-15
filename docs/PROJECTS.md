# 项目 / 部门隔离

EasyTest 用项目（Project）作为部门级工作空间边界。数据按项目归属，前端通过请求头
`X-Project-Id` 指定当前项目；未指定时使用系统初始化的 `default` 项目。

## 已隔离资产

| 资产 | 表 | 规则 |
| --- | --- | --- |
| UI 用例 | `client_case.project_id` | 列表、创建、详情、更新按项目 |
| 接口用例 | `api_case.project_id` | 列表、创建、详情、更新按项目 |
| API 场景 | `api_scenario.project_id` | 列表、创建、详情、更新、运行按项目 |
| 测试计划 | `test_plan.project_id` | 列表、创建、详情、触发按项目 |
| API 环境 | `api_environment.project_id` | 列表、创建、详情、更新、计划引用按项目 |
| UI 知识库应用 | `kb_app.project_id` | 应用列表、创建、详情、页面/控件按应用项目 |
| 定时任务 | `test_job.project_id` | 列表、创建、详情、计划/场景引用按项目 |
| 计划报告 | 通过计划/运行所属项目 | 计划运行/报告跨项目读取 404 |
| 设备池 | `ue_device.project_id` | ui-execution 按 `X-Project-Id` 过滤设备和队列 |
| UI 任务 | `ue_task.project_id` | 任务创建/列表/详情按项目；计划触发时由 core 传入项目 |

已有历史数据在启动时回填到 `default` 项目。设备默认归属 `default`，管理员可将设备
分配到项目：

```text
POST /api/v1/ui-execution/devices/{deviceId}/project
{ "projectId": "project_xxx" }
```

ui-execution 通过 `X-Project-Id` 过滤设备和 UI 任务；计划触发时 core 在任务 payload 中携带项目。

## 接口

```text
GET    /api/v1/projects
POST   /api/v1/projects
PUT    /api/v1/projects/{id}
DELETE /api/v1/projects/{id}
GET    /api/v1/projects/{id}/members
POST   /api/v1/projects/{id}/members
DELETE /api/v1/projects/{id}/members/{memberId}
```

创建项目时创建者自动成为 `ADMIN` 成员；默认项目由系统初始化，并给四个种子账号建立成员关系。

访问控制：

- 全局 `ADMIN` 可访问所有项目；
- 其他用户只能看到自己加入的项目；
- `X-Project-Id` 指向非成员项目时返回 403；
- 项目资源详情/更新按当前项目校验，跨项目读取返回 404。

项目角色：

| 角色 | 权限 |
| --- | --- |
| `ADMIN` | 修改项目、管理成员、访问项目内资源 |
| `TESTER` | 访问项目内资源；不能修改项目/成员 |
| `VIEWER` | 访问项目内资源（只读） |

创建项目时创建者为 `ADMIN`；全局 `ADMIN` 可管理所有项目。

## 前端

- 顶部项目切换器读写 `localStorage.easytest.projectId`，切换后刷新页面
- 所有 core API 自动追加 `X-Project-Id`
- 「项目」页面支持创建项目和查看/添加成员

## 尚未完成

- 项目成员角色（ADMIN/TESTER/...）的细粒度权限矩阵
- 设备分配 UI 操作入口（当前为管理员 API）
- 项目级配额/权限矩阵
