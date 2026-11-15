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

已有历史数据在启动时回填到 `default` 项目。其它资产（环境、设备、报告、定时任务）
仍为全局，后续按同样方式接入项目。

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

## 前端

- 顶部项目切换器读写 `localStorage.easytest.projectId`，切换后刷新页面
- 所有 core API 自动追加 `X-Project-Id`
- 「项目」页面支持创建项目和查看/添加成员

## 尚未完成

- 项目成员的强访问控制（当前接口按项目过滤，但项目列表/成员权限仍是基础版）
- 环境、设备、知识库、报告、定时任务的项目隔离
- 项目级配额/权限矩阵
