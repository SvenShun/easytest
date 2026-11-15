# 接口测试工程示例

这是 EasyTest 契约执行的第一种工程形态：

1. 用例契约仍在平台/YAML 中管理；
2. 复杂逻辑写在 Java 测试工程里，并用 `@EasyContract` 标注：
   - `id`、`name`
   - `description`（替代注释，平台展示）
   - `inputs` / `outputs`
   - `methodPath` 为 `类全名#方法名`
3. 工程编译成 JAR，放到 api-execution Agent 的 `data/engineering/`（或 `EASYTEST_ENGINEERING_DIR`）。

构建：

```bash
./scripts/mvn.sh -q -pl contract-api -am -DskipTests install
./scripts/mvn.sh -q -f examples/api-engineering/pom.xml -DskipTests package
mkdir -p data/engineering
cp examples/api-engineering/target/api-engineering-example-1.0.0.jar data/engineering/
```

平台场景契约示例：

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
  - name: verify token
    type: assert
    assertions:
      - {type: VARIABLE, path: token, operator: CONTAINS, expected: token-}
```

当前 api-execution 在方法注册后执行；后续阶段会把注册/心跳/能力/槽位暴露为独立 API Agent 协议。
