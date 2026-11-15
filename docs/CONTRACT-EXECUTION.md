# 契约执行（一期 P0）

## 目标

平台保存用例契约；复杂逻辑留在测试工程代码中。平台不需要理解工程内部实现，只保存：

- `type: component`
- `methodPath`：`类全名#方法名`
- `description`：平台展示的行为说明
- `inputs`：从平台变量注入
- `outputs`：工程方法回传到后续步骤的变量

## Java 工程 SDK

依赖：

```xml
<dependency>
  <groupId>com.easytest</groupId>
  <artifactId>easytest-contract-api</artifactId>
  <version>1.0.0</version>
</dependency>
```

方法：

```java
@EasyContract(
    id = "engineering.login",
    name = "代码实现的登录",
    description = "调用工程代码完成登录；平台只保存 methodPath、输入变量和行为说明。",
    inputs = {"username", "password"},
    outputs = {"token", "userId"}
)
public ContractResult login(ContractContext context) {
    String username = context.requireString("username");
    context.log("login " + username);
    return ContractResult.ok("ok", Map.of("token", "token-" + username,
            "userId", "user-" + username));
}
```

约束：

- 方法必须是 `public`；
- 一个参数 `ContractContext`；
- 返回 `ContractResult` 或 `void`（void 时使用 `context.output(...)`）；
- 方法不能假设平台特定实现，HTTP/SDK/DB 等由工程自己选择。

## Agent 加载

api-execution Agent 从以下目录加载工程 JAR：

```text
data/engineering/
```

或环境变量：

```bash
EASYTEST_APIEXEC_ENGINEERING_DIR=/opt/easytest/engineering
```

一个 JAR 可以包含多个 `@EasyContract` 方法。Agent 启动时扫描并注册；平台契约执行时按 `methodPath` 调用。

## YAML 契约

```yaml
name: engineering-login
variables:
  username: tester
  password: secret
steps:
  - name: engineering login
    type: component
    methodPath: com.example.easytest.LoginEngineeringCases#loginWithCode
    description: 调用工程代码完成登录
    inputs:
      username: "${username}"
      password: "${password}"
    timeoutMs: 30000
  - name: verify token
    type: assert
    assertions:
      - {type: VARIABLE, path: token, operator: CONTAINS, expected: token-}
```

示例工程：[examples/api-engineering](../examples/api-engineering/README.md)。

## 尚未完成

- 自动扫描工程源码生成 manifest（目前方法通过 JAR 扫描注册，平台契约需要填写 methodPath/描述）
- Git 工程 commit 固化到执行记录
- Python 工程 SDK
- 平台端契约方法列表/补全 UI（目前可在场景表单手工填写）
- 代码方法级输入/输出契约的保存时静态校验
