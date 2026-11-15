# API Agent（一期 P0）

## 当前协议

api-execution 启动后会向 core 注册自身：

```text
POST /internal/v1/api-agents/register
X-Service-Token: ...
{
  "agentId": "apiexec-local-1",
  "name": "apiexec-local-1",
  "baseUrl": "http://127.0.0.1:8084",
  "capabilities": ["HTTP", "SQL", "GROOVY", "COMPONENT"],
  "slots": 4,
  "activeTasks": 0
}
```

每 10 秒发送 `POST /internal/v1/api-agents/heartbeat`，更新能力、槽位和当前负载。

core 维护注册表，并按：

1. 能力匹配
2. `activeTasks < slots`
3. 最低负载

选择 Agent。`/api/v1/api-agents` 可供页面查看（执行节点页）。

## 页面

`执行节点` 页面展示：

- 接口 Agent：地址、能力、`activeTasks / slots`、最近心跳
- UI Agent：主机、在线状态、设备数、最近心跳

## 当前边界

- 注册表目前在 core 内存中，重启后由 Agent 重新注册；多 core 实例共享需要 Redis/数据库。
- core 在 Agent 满槽时最多等待 `EASYTEST_API_AGENT_WAIT_SECONDS`（默认 10 秒）再失败，避免瞬时并发打满。
- 任务下发失败会释放该 Agent 的预占槽位，并尝试其他可用 Agent（故障转移）。
- 没有持久化等待队列和任务租约；Agent 长时间满槽时任务会失败，不会静默落到静态 URL。
- core 仍保留 `EASYTEST_API_EXECUTION_URLS` 兼容旧部署：只有没有任何已注册 Agent 时才使用静态地址。
- 尚未实现 Agent 版本/能力热更新、优雅摘除、HTTP/Redis 双队列迁移。
