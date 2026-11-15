# Git 测试工程仓库接入

平台支持接入多个独立的 API / UI 自动化测试工程 Git 仓库，按约定扫描测试方法并加载到平台。

## 仓库类型与约定

### API 工程仓库

仓库根目录（或配置的 `rootDirectory`）放 `easytest.api.yaml`：

```yaml
schemaVersion: 1
repoType: api
methods:
  - id: git.login
    name: Git 仓库登录
    description: 登录测试方法
    methodPath: com.example.gitdemo.GitDemoCases#login
    inputs: [username, password]
    outputs: [token, userId]
    tags: [git, login]
    artifact: target/git-demo-api.jar
```

- 平台扫描后写入工程方法注册表
- 若 `artifact` 指向仓库内 JAR，扫描时自动上传到 API Agent 并 reload
- 之后可在 API 场景的 `component` 步骤中按 `methodPath` 选择执行

### UI 工程仓库

仓库根目录（或 `rootDirectory`）放 `easytest.ui.yaml`：

```yaml
schemaVersion: 1
repoType: ui
cases:
  - id: ui.git.login
    name: Git UI 登录
    description: UI 工程 STEP 用例
    steps:
      - seq: 1
        stepType: LAUNCH
        value: com.easytest.demo
      - seq: 2
        stepType: CLICK
        selectorType: TEXT
        selector: 登录
```

- 平台扫描后生成/更新对应的 UI STEP 用例（`uigit-...`）
- 方法目录页可直接选择「运行」，平台下发 UI STEP 任务
- 也可在用例/计划中继续使用生成的用例

## 平台操作

页面：

```text
/#/engineering-repos
```

填写：

- 仓库名称
- 类型 API/UI
- Git 地址（https / git@ / file://）
- 分支
- 扫描目录
- Webhook secret（可选）

点击「扫描」即可加载方法目录。

## 提交触发重扫

### Webhook

```text
POST /api/v1/engineering-repos/{repoId}/webhook
```

认证方式：

- `X-Webhook-Token: <secret>`
- 或 GitHub `X-Hub-Signature-256` HMAC

### 定时轮询

core 每 `easytest.engineering-repo-poll-ms`（默认 120000ms）检查远端分支 HEAD；
提交变化后自动重新扫描。

## API

```text
GET    /api/v1/engineering-repos
POST   /api/v1/engineering-repos
PUT    /api/v1/engineering-repos/{id}
DELETE /api/v1/engineering-repos/{id}
GET    /api/v1/engineering-repos/{id}/methods
POST   /api/v1/engineering-repos/{id}/scan
POST   /api/v1/engineering-repos/{id}/methods/{methodId}/run
POST   /api/v1/engineering-repos/{id}/webhook
```

## Demo 仓库

- `examples/engineering-api-repo/`：API 工程约定 + `GitDemoCases`
- `examples/engineering-ui-repo/`：UI STEP 工程约定

将目录作为独立 git 仓库推到 GitHub/GitLab 后，在平台填写地址即可。

## 限制

- 私有仓库认证暂未接入（仅支持公开/免密 git URL）
- API 工程执行需要仓库内有可提交/构建的 JAR，或由 CI 产出并提交 `artifact`
- UI 方法当前生成 STEP 用例执行；复杂 UI 工程代码建议走 SCRIPT 包装
