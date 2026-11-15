# API 单步组件契约

接口用例（`api_case`）除了请求与断言，还可以声明可复用组件的输入/输出变量契约：

| 字段 | 方向 | 示例 |
|---|---|---|
| `inputContract` | 组件依赖上游提供 | `{"token":"登录后的 token"}` |
| `outputContract` | 组件向下游产出 | `{"userId":"响应 user.id"}` |

- 契约是 `Map<String,String>`，键为变量名，值为说明/来源；
- 在「接口用例」编辑弹窗中填写，保存到 `input_contract_json` / `output_contract_json`；
- `GET/POST/PUT /api/v1/api-cases` 的 view/request 均包含这两个字段；
- 当前阶段先做显式登记，后续在场景引入组件时做输入契约静态校验和输出变量注入。

示例：

```json
{
  "name": "Login component",
  "method": "POST",
  "path": "/api/v1/auth/login",
  "inputContract": {"username": "登录账号", "password": "登录密码"},
  "outputContract": {"token": "响应 $.token"}
}
```
