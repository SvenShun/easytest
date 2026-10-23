import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { uiexecApi } from '../api';
import type { Agent, Device, DeviceQueue } from '../api/types';
import { AGENT_STATUS_META, DEVICE_STATUS_META, EXECUTOR_META, TASK_STATUS_META, metaOf } from '../constants';
import { usePolling } from '../hooks';
import { navigate, useHashRoute } from '../router';
import { cx, formatDateTime, safeErrorMessage, shortId, truncate } from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Card,
  CardHead,
  EmptyState,
  ErrorPanel,
  Input,
  LoadingPanel,
  PageHeading,
  Pill,
  Select,
  SkeletonRows,
  StatusPill,
} from '../components/ui';
import { Modal } from '../components/Modal';

const POLL_MS = 10000;

const DEVICE_FILTERS = [
  { value: '', label: '全部状态' },
  { value: 'IDLE', label: '空闲' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'SCRIPT_RUNNING', label: '脚本执行中' },
  { value: 'AI_RUNNING', label: 'AI 执行中' },
  { value: 'OFFLINE', label: '离线' },
  { value: 'MAINTENANCE', label: '维护' },
];

export function DevicesPage() {
  const route = useHashRoute();
  const [devices, setDevices] = useState<Device[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [agentsLoading, setAgentsLoading] = useState(true);
  const [devicesError, setDevicesError] = useState('');
  const [agentsError, setAgentsError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [keyword, setKeyword] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const [queueDevice, setQueueDevice] = useState<Device | null>(null);
  const [queueData, setQueueData] = useState<DeviceQueue | null>(null);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueError, setQueueError] = useState('');
  const queueHandled = useRef<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setRefreshing(true);
    const [deviceRes, agentRes] = await Promise.allSettled([uiexecApi.devices(), uiexecApi.agents()]);
    if (deviceRes.status === 'fulfilled') {
      setDevices(deviceRes.value.items);
      setDevicesError('');
    } else {
      setDevicesError(safeErrorMessage(deviceRes.reason));
    }
    if (agentRes.status === 'fulfilled') {
      setAgents(agentRes.value);
      setAgentsError('');
    } else {
      setAgentsError(safeErrorMessage(agentRes.reason));
    }
    setDevicesLoading(false);
    setAgentsLoading(false);
    setRefreshing(false);
    setUpdatedAt(new Date());
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  usePolling(() => void load(true), POLL_MS);

  const openQueue = useCallback(async (device: Device) => {
    setQueueDevice(device);
    setQueueData(null);
    setQueueError('');
    setQueueLoading(true);
    try {
      const data = await uiexecApi.deviceQueue(device.deviceId);
      setQueueData(data);
    } catch (err) {
      setQueueError(safeErrorMessage(err));
    } finally {
      setQueueLoading(false);
    }
  }, []);

  useEffect(() => {
    const queryDeviceId = route.query.get('queue');
    if (!queryDeviceId) {
      queueHandled.current = null;
      return;
    }
    if (queueHandled.current === queryDeviceId) return;
    const device = devices.find((item) => item.deviceId === queryDeviceId);
    if (device) {
      queueHandled.current = queryDeviceId;
      void openQueue(device);
    }
  }, [route.query, devices, openQueue]);

  const closeQueue = () => {
    setQueueDevice(null);
    setQueueData(null);
    setQueueError('');
    queueHandled.current = null;
    if (route.query.get('queue')) navigate('/devices', { replace: true });
  };

  const refreshQueue = async () => {
    if (!queueDevice) return;
    setQueueLoading(true);
    setQueueError('');
    try {
      setQueueData(await uiexecApi.deviceQueue(queueDevice.deviceId));
    } catch (err) {
      setQueueError(safeErrorMessage(err));
    } finally {
      setQueueLoading(false);
    }
  };

  const filteredDevices = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    return devices.filter((device) => {
      if (statusFilter && device.status !== statusFilter) return false;
      if (!text) return true;
      const haystack = [device.serial, device.name, device.model, device.platform, ...(device.tags || [])]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return haystack.includes(text);
    });
  }, [devices, statusFilter, keyword]);

  const summary = useMemo(() => {
    const total = devices.length;
    const online = devices.filter((device) => device.online).length;
    const idle = devices.filter((device) => device.status === 'IDLE').length;
    const busy = devices.filter(
      (device) => device.status === 'SCRIPT_RUNNING' || device.status === 'AI_RUNNING' || device.status === 'QUEUED',
    ).length;
    const offline = devices.filter((device) => device.status === 'OFFLINE').length;
    return { total, online, idle, busy, offline };
  }, [devices]);

  return (
    <div className="page-stack">
      <PageHeading
        title="设备池"
        description="查看 Agent 同步的设备状态、FIFO 队列与当前任务；每 10 秒自动刷新。"
        actions={
          <div className="page-actions">
            <span className="heading-status">
              <span className={cx('live-dot', refreshing && 'is-active')} />
              {updatedAt ? `更新于 ${formatDateTime(updatedAt)}` : '加载中'}
            </span>
            <Button variant="secondary" icon="refresh" loading={refreshing} onClick={() => void load(true)}>
              刷新
            </Button>
          </div>
        }
      />

      <div className="device-summary-row">
        <div className="summary-chip">
          <span className="summary-chip-label">设备总数</span>
          <span className="summary-chip-value">{summary.total}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">在线</span>
          <span className="summary-chip-value text-success">{summary.online}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">空闲</span>
          <span className="summary-chip-value text-success">{summary.idle}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">执行 / 排队</span>
          <span className="summary-chip-value text-info">{summary.busy}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">离线</span>
          <span className="summary-chip-value text-muted">{summary.offline}</span>
        </div>
      </div>

      <Card>
        <CardHead
          title="设备列表"
          description="设备状态由 Agent 心跳与任务状态机共同维护"
          icon="devices"
          actions={
            <div className="filter-inline">
              <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                {DEVICE_FILTERS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
              <Input
                icon="search"
                placeholder="serial / 名称 / 标签"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>
          }
        />
        <div className="card-body">
          {devicesError ? (
            <ErrorPanel
              message={devicesError}
              onRetry={() => void load()}
              hint="请确认 ui-execution（默认 8081）服务已启动。"
            />
          ) : devicesLoading ? (
            <div className="device-card-grid">
              {Array.from({ length: 3 }).map((_, index) => (
                <div className="device-card" key={index}>
                  <span className="skeleton sk-w-70" />
                  <span className="skeleton sk-w-40" />
                  <span className="skeleton sk-w-40" />
                </div>
              ))}
            </div>
          ) : filteredDevices.length === 0 ? (
            <EmptyState
              icon="devices"
              title={devices.length === 0 ? '还没有注册设备' : '没有匹配的设备'}
              description={
                devices.length === 0
                  ? '启动 device-agent 并完成注册后，设备会自动同步到平台'
                  : '尝试调整状态筛选或关键字'
              }
              action={
                devices.length > 0 ? (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      setStatusFilter('');
                      setKeyword('');
                    }}
                  >
                    清空筛选
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <div className="device-card-grid">
              {filteredDevices.map((device) => {
                const meta = metaOf(DEVICE_STATUS_META, device.status);
                return (
                  <article className="device-card" key={device.deviceId}>
                    <span className={cx('device-status-line', `tone-bg-${meta.tone}`)} />
                    <div className="device-card-head">
                      <div>
                        <div className="device-serial mono">{device.serial}</div>
                        <div className="device-name">
                          {device.name || device.model || '未命名设备'}
                          {device.name && device.model ? ` · ${device.model}` : ''}
                          {device.platform ? <span className="device-platform"> · {device.platform}</span> : null}
                        </div>
                      </div>
                      <StatusPill meta={meta} />
                    </div>

                    <div className="tag-row">
                      {(device.tags || []).length > 0 ? (
                        (device.tags || []).slice(0, 5).map((tag) => (
                          <Pill tone="gray" key={tag}>
                            {tag}
                          </Pill>
                        ))
                      ) : (
                        <span className="text-muted">无标签</span>
                      )}
                      {(device.tags || []).length > 5 ? <Pill tone="gray">+{(device.tags || []).length - 5}</Pill> : null}
                    </div>

                    <div className="device-info-grid">
                      <div>
                        <span className="meta-label">队列长度</span>
                        <strong className={device.queueLength ? 'text-info' : ''}>{device.queueLength ?? 0}</strong>
                      </div>
                      <div>
                        <span className="meta-label">所属 Agent</span>
                        <strong title={device.agentId || ''}>
                          {device.agentName || shortId(device.agentId)}
                        </strong>
                      </div>
                      <div>
                        <span className="meta-label">最近心跳</span>
                        <strong>{formatDateTime(device.lastSeenAt)}</strong>
                      </div>
                      <div>
                        <span className="meta-label">在线</span>
                        <strong className={device.online ? 'text-success' : 'text-muted'}>
                          {device.online ? '是' : '否'}
                        </strong>
                      </div>
                    </div>

                    <div className="device-card-foot">
                      {device.currentTaskId ? (
                        <button
                          type="button"
                          className="device-current-task"
                          onClick={() => navigate(`/tasks/${device.currentTaskId}`)}
                          title={device.currentTaskId}
                        >
                          <Icon name="terminal" size={14} />
                          当前任务 {shortId(device.currentTaskId)}
                          <Icon name="arrow-right" size={12} />
                        </button>
                      ) : (
                        <span className="device-idle-text">
                          <Icon name="check-circle" size={14} />
                          无运行任务
                        </span>
                      )}
                      <Button size="sm" variant="secondary" icon="queue" onClick={() => void openQueue(device)}>
                        查看队列
                      </Button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </div>
      </Card>

      <Card>
        <CardHead
          title="Agent 列表"
          description="设备子服务器注册与心跳状态"
          icon="server"
          actions={
            <Button
              size="sm"
              variant="ghost"
              icon="refresh"
              loading={refreshing}
              onClick={() => void load(true)}
            >
              刷新
            </Button>
          }
        />
        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>host</th>
                  <th>baseUrl</th>
                  <th>状态</th>
                  <th>最后心跳</th>
                  <th>设备数</th>
                </tr>
              </thead>
              <tbody>
                {agentsLoading ? (
                  <SkeletonRows rows={3} cols={6} />
                ) : agentsError ? (
                  <tr>
                    <td colSpan={6}>
                      <ErrorPanel message={agentsError} compact onRetry={() => void load()} />
                    </td>
                  </tr>
                ) : agents.length === 0 ? (
                  <tr>
                    <td colSpan={6}>
                      <EmptyState
                        compact
                        icon="server"
                        title="还没有注册 Agent"
                        description="启动 device-agent 后，会自动注册并周期心跳"
                      />
                    </td>
                  </tr>
                ) : (
                  agents.map((agent) => {
                    const statusMeta = metaOf(AGENT_STATUS_META, agent.status);
                    return (
                      <tr key={agent.agentId}>
                        <td>
                          <div className="cell-title">{agent.name}</div>
                          <div className="cell-sub mono">{agent.agentId}</div>
                        </td>
                        <td className="mono">{agent.host || '—'}</td>
                        <td className="mono">{truncate(agent.baseUrl, 40) || '—'}</td>
                        <td>
                          <StatusPill meta={statusMeta} />
                        </td>
                        <td className="text-muted">{formatDateTime(agent.lastHeartbeat)}</td>
                        <td>
                          <Pill tone="blue">{agent.devices?.length ?? 0} 台</Pill>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </Card>

      <Modal
        open={Boolean(queueDevice)}
        title="设备 FIFO 队列"
        description={
          queueDevice
            ? `${queueDevice.serial} · ${queueDevice.name || queueDevice.model || '未命名设备'}`
            : undefined
        }
        onClose={closeQueue}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={closeQueue}>
              关闭
            </Button>
            <Button variant="primary" icon="refresh" loading={queueLoading} onClick={() => void refreshQueue()}>
              刷新队列
            </Button>
          </>
        }
      >
        {queueError ? (
          <ErrorPanel message={queueError} onRetry={() => void refreshQueue()} />
        ) : queueLoading ? (
          <LoadingPanel text="正在加载设备队列…" />
        ) : queueData ? (
          <div className="form-stack">
            <div className="queue-summary">
              <Pill tone="blue">队列长度 {queueData.queueLength}</Pill>
              {queueData.serial ? <span className="mono">{queueData.serial}</span> : null}
              <span className="text-muted">队列按 FIFO 顺序执行，设备空闲时自动出队下一个任务。</span>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>位置</th>
                    <th>用例</th>
                    <th>状态</th>
                    <th>执行器</th>
                    <th>创建时间</th>
                    <th className="th-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(queueData.tasks || []).length === 0 ? (
                    <tr>
                      <td colSpan={6}>
                        <EmptyState
                          compact
                          icon="check-circle"
                          title="队列为空"
                          description="设备空闲，可以立即承接新任务"
                        />
                      </td>
                    </tr>
                  ) : (
                    (queueData.tasks || [])
                      .slice()
                      .sort((a, b) => a.position - b.position)
                      .map((task) => (
                        <tr key={task.taskId}>
                          <td>
                            <span className="queue-position">#{task.position}</span>
                          </td>
                          <td>
                            <div className="cell-title sm">{task.caseTitle || '—'}</div>
                            <div className="cell-sub mono">{task.taskId}</div>
                          </td>
                          <td>
                            <StatusPill meta={metaOf(TASK_STATUS_META, task.status)} />
                          </td>
                          <td>
                            <StatusPill meta={metaOf(EXECUTOR_META, task.executorType)} dot={false} />
                          </td>
                          <td className="text-muted">{formatDateTime(task.createdAt)}</td>
                          <td className="td-right">
                            <button
                              type="button"
                              className="link-btn"
                              onClick={() => {
                                closeQueue();
                                navigate(`/tasks/${task.taskId}`);
                              }}
                            >
                              轨迹
                              <Icon name="arrow-right" size={13} />
                            </button>
                          </td>
                        </tr>
                      ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </Modal>

      <div className="dashboard-footnote">
        <Icon name="info" size={14} />
        V1 不提供设备投屏；模拟设备由 device-agent 提供截图，任务截图请进入任务轨迹查看。
        {devices.length > 0 ? ` 当前共 ${devices.length} 台设备。` : ''}
      </div>

    </div>
  );
}
