import { useCallback, useEffect, useState } from 'react';
import { apiAgentsApi, uiexecApi } from '../api';
import type { ApiAgent } from '../api/apiagents';
import type { Agent } from '../api/types';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Button, Callout, Card, CardHead, EmptyState, ErrorPanel, PageHeading, Pill, SkeletonRows } from '../components/ui';

export function AgentsPage() {
  const [apiAgents, setApiAgents] = useState<ApiAgent[]>([]);
  const [uiAgents, setUiAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [api, ui] = await Promise.allSettled([apiAgentsApi.list(), uiexecApi.agents()]);
      if (api.status === 'fulfilled') setApiAgents(api.value);
      if (ui.status === 'fulfilled') setUiAgents(ui.value);
      if (api.status === 'rejected' && ui.status === 'rejected') {
        setError(safeErrorMessage(api.reason));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className="page-stack">
      <PageHeading title="执行节点" description="接口 Agent 与 UI Agent 的注册、能力、负载和最近心跳。"
        actions={<Button variant="secondary" icon="refresh" loading={loading} onClick={() => void load()}>刷新</Button>} />
      <Callout tone="info" title="分布式执行">
        接口 Agent 向 core 注册能力/槽位/负载，core 按能力匹配和最低负载选择；UI Agent 通过 ui-execution 注册设备。
      </Callout>
      {error ? <ErrorPanel message={error} onRetry={() => void load()} /> : null}
      <Card>
        <CardHead title="接口 Agent" description="api-execution 注册节点；槽位=worker 数，activeTasks=正在执行任务数" icon="api" />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Agent</th><th>地址</th><th>能力</th><th>负载</th><th>状态</th><th>最近心跳</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={3} cols={6} /> : apiAgents.length === 0 ? (
                <tr><td colSpan={6}><EmptyState compact icon="api" title="暂无接口 Agent" description="启动 api-execution 后会自动注册" /></td></tr>
              ) : apiAgents.map((agent) => (
                <tr key={agent.agentId}>
                  <td>{agent.name}<div className="cell-sub mono">{agent.agentId}</div></td>
                  <td className="mono">{agent.baseUrl}</td>
                  <td>{agent.capabilities.map((cap) => <Pill key={cap} tone="blue">{cap}</Pill>)}</td>
                  <td>{agent.activeTasks} / {agent.slots}</td>
                  <td><Pill tone={agent.status === 'DRAINING' ? 'orange' : 'green'}>{agent.status || 'UP'}</Pill></td>
                  <td>{formatDateTime(agent.lastSeenAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Card>
        <CardHead title="UI Agent" description="设备节点通过 ui-execution 注册，设备池按设备 FIFO 调度" icon="devices" />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Agent</th><th>主机</th><th>状态</th><th>设备数</th><th>最近心跳</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={3} cols={5} /> : uiAgents.length === 0 ? (
                <tr><td colSpan={5}><EmptyState compact icon="devices" title="暂无 UI Agent" description="启动 device-agent 并配置 ui-execution 地址" /></td></tr>
              ) : uiAgents.map((agent) => (
                <tr key={agent.agentId}>
                  <td>{agent.name}<div className="cell-sub mono">{agent.agentId}</div></td>
                  <td>{agent.host || '—'}</td>
                  <td><Pill tone={agent.status === 'ONLINE' ? 'green' : 'gray'}>{agent.status || '—'}</Pill></td>
                  <td>{agent.devices?.length ?? 0}</td>
                  <td>{formatDateTime(agent.lastHeartbeat)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
