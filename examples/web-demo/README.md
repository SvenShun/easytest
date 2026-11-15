# EasyTest Web Demo

用于 Web UI STEP 自动化本地验收。

```bash
python3 examples/web-demo/serve.py
# http://127.0.0.1:18090/
```

步骤示例：

```yaml
- stepType: LAUNCH
  value: http://127.0.0.1:18090/
- stepType: INPUT
  selectorType: ID
  selector: username
  value: tester
- stepType: CLICK
  selectorType: TEXT
  selector: 登录
- stepType: ASSERT_TEXT
  selectorType: TEXT
  selector: 登录成功
```

设备 Agent 启动 Web 模式：

```bash
EASYTEST_AGENT_MODE=web \
EASYTEST_AGENT_WEB_URL=http://127.0.0.1:18090/ \
EASYTEST_AGENT_NAME=web-agent-local \
SERVER_PORT=8091 \
java -jar device-agent/target/device-agent-1.0.0.jar
```
