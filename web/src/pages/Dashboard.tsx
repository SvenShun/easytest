import { useEffect, useMemo, useState } from 'react';
import { uiexecApi } from '../api';
import type { Device, DeviceStats, TaskSummary } from '../api/types';
import { useAuth } from '../auth';
import {
  DEVICE_STATUS_META,
  EXECUTOR_META,
  ROLE_META,
  TASK_STATUS_META,
  metaOf,
} from '../constants';
import { usePolling } from '../hooks';
import { navigate } from '../router';
import {
  cx,
  formatDateTime,
  formatDurationMs,
  isSameLocalDay,
  userDisplayName,
} from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Callout,
  Card,
  CardHead,
  EmptyState,
  ErrorPanel,
  FailureBars,
  PageHeading,
  Pill,
  Skeleton,
  StatCard,
  StatusPill,
} from '../components/ui';
import { toastInfo } from '../components/Toast';

const POLL_MS = 10000;

type SectionKey = 'stats' | 'devices' | 'recent' | 'failures' | 'running';

function greeting(date = new Date()): string {
  const hour = date.getHours();
  if (hour < 6) return '夜深了';
  if (hour < 9) return '早上好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}

function errorText(reason: unknown): string {
  if (reason instanceof Error) return reason.message;
  return String(reason || '请求失败');
}

export function DashboardPage() {
  const { user } = useAuth();
  const [stats, setStats] = useState<DeviceStats | null>(null);
  const [devices, setDevices] = useState<Device[]>([]);
  const [recentTasks, setRecentTasks] = useState<TaskSummary[]>([]);
  const [todayFailed, setTodayFailed] = useState<number | null>(null);
  const [runningTotal, setRunningTotal] = useState<number | null>(null);
  const [errors, setErrors] = useState<Partial<Record<SectionKey, string>>>({});
  const [loading, setLoading] = useState(true);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = async (silent = false) => {
    if (!silent) setRefreshing(true);
    const nextErrors: Partial<Record<SectionKey, string>> = {};

    const [statsRes, devicesRes, recentRes, failedRes, timeoutRes, runningRes, dispatchedRes] =
      await Promise.allSettled([
        uiexecApi.stats(),
        uiexecApi.devices(),
        uiexecApi.tasks({ page: 1, size: 10 }),
        uiexecApi.tasks({ status: 'FAILED', page: 1, size: 100 }),
        uiexecApi.tasks({ status: 'TIMEOUT', page: 1, size: 100 }),
        uiexecApi.tasks({ status: 'RUNNING', page: 1, size: 1 }),
        uiexecApi.tasks({ status: 'DISPATCHED', page: 1, size: 1 }),
      ]);

    if (statsRes.status === 'fulfilled') setStats(statsRes.value);
    else nextErrors.stats = errorText(statsRes.reason);

    if (devicesRes.status === 'fulfilled') setDevices(devicesRes.value.items);
    else nextErrors.devices = errorText(devicesRes.reason);

    if (recentRes.status === 'fulfilled') setRecentTasks(recentRes.value.items);
    else nextErrors.recent = errorText(recentRes.reason);

    if (failedRes.status === 'fulfilled' || timeoutRes.status === 'fulfilled') {
      const failedItems = failedRes.status === 'fulfilled' ? failedRes.value.items : [];
      const timeoutItems = timeoutRes.status === 'fulfilled' ? timeoutRes.value.items : [];
      const count = [...failedItems, ...timeoutItems].filter((task) =>
        isSameLocalDay(task.finishedAt || task.createdAt),
      ).length;
      setTodayFailed(count);
    } else {
      nextErrors.failures = errorText(failedRes.reason || timeoutRes.reason);
    }

    if (runningRes.status === 'fulfilled' || dispatchedRes.status === 'fulfilled') {
      const running = runningRes.status === 'fulfilled' ? runningRes.value.total : 0;
      const dispatched = dispatchedRes.status === 'fulfilled' ? dispatchedRes.value.total : 0;
      setRunningTotal(running + dispatched);
    } else {
      nextErrors.running = errorText(runningRes.reason || dispatchedRes.reason);
    }

    setErrors(nextErrors);
    setUpdatedAt(new Date());
    if (!silent) {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  usePolling(() => load(true), POLL_MS);

  const deviceStats = stats?.devices;
  const online = deviceStats?.online ?? devices.filter((device) => device.online).length;
  const total = deviceStats?.total ?? devices.length;
  const idle = deviceStats?.idle ?? devices.filter((device) => device.status === 'IDLE').length;
  const displayRunning =
    runningTotal ?? (stats?.tasks ? stats.tasks.running ?? 0 : 0);
  const roleMeta = metaOf(ROLE_META, user?.role);
  const hasAnyError = Object.keys(errors).length > 0;
  const totalFailures = (stats?.failureCategories || []).reduce((sum, item) => sum + item.count, 0);
  const greetingText = greeting();

  const deviceList = useMemo(() => devices.slice(0, 6), [devices]);

  const openQueue = (deviceId: string) => navigate(`/devices?queue=${encodeURIComponent(deviceId)}`);
  const openTask = (taskId?: string | null) => {
    if (taskId) navigate(`/tasks/${encodeURIComponent(taskId)}`);
  };

  return (
    <div className="page-stack">
      <PageHeading
        eyebrow={`${formatDateTime(new Date())} · ${roleMeta.label}`}
        title={
          <span>
            {greetingText}，{userDisplayName(user) || '测试同学'}
          </span>
        }
        description="这是你的测试工作台：设备状态、执行任务与失败分布一目了然。"
        actions={
          <div className="heading-status">
            <span className={cx('live-dot', refreshing && 'is-active')} />
            <span>
              每 10 秒自动刷新
              {updatedAt ? ` · 更新于 ${formatDateTime(updatedAt)}` : ''}
            </span>
          </div>
        }
      />

      {hasAnyError ? (
        <Callout tone="warning" title="部分数据加载失败">
          后端服务可能暂未就绪。失败模块：{Object.keys(errors).map((key) => {
            const map: Record<string, string> = {
              stats: '统计数据',
              devices: '设备',
              recent: '最近任务',
              failures: '失败任务',
              running: '执行中任务',
            };
            return map[key] || key;
          }).join('、')}。请检查 core / ui-execution 服务是否已启动。
          <div className="callout-inline-action">
            <Button size="sm" variant="secondary" icon="refresh" onClick={() => void load()}>
              重新加载
            </Button>
          </div>
        </Callout>
      ) : null}

      <div className="stats-grid">
        <StatCard
          label="设备在线 / 总数"
          value={
            loading ? (
              <Skeleton className="sk-value" />
            ) : (
              <>
                {online}
                <span className="stat-value-sub"> / {total}</span>
              </>
            )
          }
          hint={total ? `在线率 ${Math.round((online / total) * 100)}%` : '暂未注册设备'}
          icon="wifi"
          tone="blue"
          loading={loading}
        />
        <StatCard
          label="空闲设备"
          value={loading ? <Skeleton className="sk-value" /> : idle}
          hint="可立即承接新任务"
          icon="devices"
          tone="green"
          loading={loading}
        />
        <StatCard
          label="执行中任务"
          value={loading ? <Skeleton className="sk-value" /> : displayRunning}
          hint="执行中 + 已下发"
          icon="tasks"
          tone="purple"
          loading={loading}
        />
        <StatCard
          label="今日失败数"
          value={
            loading ? (
              <Skeleton className="sk-value" />
            ) : todayFailed === null ? (
              '—'
            ) : (
              todayFailed
            )
          }
          hint="今日创建或结束的失败 / 超时任务"
          icon="warning"
          tone={todayFailed ? 'red' : 'gray'}
          loading={loading}
        />
      </div>

      <div className="dashboard-split">
        <Card className="failure-card">
          <CardHead
            title="失败原因分布"
            description={`来自 ui-execution 统计 · 共 ${totalFailures} 次失败`}
            icon="warning"
            actions={
              <Button size="sm" variant="ghost" icon="refresh" onClick={() => void load()}>
                刷新
              </Button>
            }
          />
          <div className="card-body">
            {errors.stats ? (
              <ErrorPanel message={errors.stats} compact onRetry={() => void load()} />
            ) : loading ? (
              <div className="bars-skeleton">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div className="bar-row" key={index}>
                    <Skeleton className="sk-w-40" />
                    <Skeleton className="sk-bar" />
                  </div>
                ))}
              </div>
            ) : (
              <FailureBars
                data={stats?.failureCategories}
                emptyText="当前没有失败样本，继续保持！"
              />
            )}
          </div>
        </Card>

        <Card className="devices-overview">
          <CardHead
            title="设备池概览"
            description="点击设备查看 FIFO 队列，或直接进入当前任务轨迹"
            icon="devices"
            actions={
              <Button size="sm" variant="ghost" icon="arrow-right" onClick={() => navigate('/devices')}>
                全部设备
              </Button>
            }
          />
          <div className="card-body">
            {errors.devices ? (
              <ErrorPanel message={errors.devices} compact onRetry={() => void load()} />
            ) : loading ? (
              <div className="device-mini-grid">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div className="device-mini-card" key={index}>
                    <Skeleton className="sk-w-70" />
                    <Skeleton className="sk-w-40" />
                    <Skeleton className="sk-w-40" />
                  </div>
                ))}
              </div>
            ) : deviceList.length === 0 ? (
              <EmptyState
                compact
                icon="devices"
                title="还没有注册设备"
                description="启动 device-agent 后，设备会自动同步到平台"
              />
            ) : (
              <div className="device-mini-grid">
                {deviceList.map((device) => {
                  const meta = metaOf(DEVICE_STATUS_META, device.status);
                  return (
                    <div
                      className="device-mini-card"
                      key={device.deviceId}
                      role="button"
                      tabIndex={0}
                      onClick={() => openQueue(device.deviceId)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          openQueue(device.deviceId);
                        }
                      }}
                    >
                      <span className={cx('device-status-line', `tone-bg-${meta.tone}`)} />
                      <div className="device-mini-head">
                        <span className="device-mini-serial mono">{device.serial}</span>
                        <StatusPill meta={meta} />
                      </div>
                      <div className="device-mini-sub">
                        {device.name || device.model || '未命名设备'}
                        {device.platform ? ` · ${device.platform}` : ''}
                      </div>
                      <div className="device-mini-meta">
                        <span>
                          <Icon name="queue" size={13} /> 队列 {device.queueLength ?? 0}
                        </span>
                        {device.currentTaskId ? (
                          <span
                            className="device-mini-task"
                            role="link"
                            tabIndex={0}
                            onClick={(event) => {
                              event.stopPropagation();
                              openTask(device.currentTaskId);
                            }}
                            onKeyDown={(event) => {
                              if (event.key === 'Enter') {
                                event.stopPropagation();
                                openTask(device.currentTaskId);
                              }
                            }}
                          >
                            当前任务
                            <Icon name="arrow-right" size={12} />
                          </span>
                        ) : (
                          <span className="device-mini-idle">无运行任务</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </Card>
      </div>

      <Card>
        <CardHead
          title="最近任务"
          description="最新 10 条执行任务，点击查看逐步轨迹"
          icon="terminal"
          actions={
            <Button size="sm" variant="secondary" icon="tasks" onClick={() => navigate('/tasks')}>
              查看全部任务
            </Button>
          }
        />
        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>用例</th>
                  <th>执行器</th>
                  <th>状态</th>
                  <th>设备</th>
                  <th>创建时间</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={6}>
                      <div className="loading-panel">
                        <span className="spinner" />
                        正在加载最近任务…
                      </div>
                    </td>
                  </tr>
                ) : errors.recent ? (
                  <tr>
                    <td colSpan={6}>
                      <ErrorPanel message={errors.recent} compact onRetry={() => void load()} />
                    </td>
                  </tr>
                ) : recentTasks.length === 0 ? (
                  <tr>
                    <td colSpan={6}>
                      <EmptyState
                        icon="terminal"
                        title="还没有执行任务"
                        description="在测试计划中触发运行后，任务会出现在这里"
                        action={
                          <Button size="sm" variant="primary" icon="plans" onClick={() => navigate('/plans')}>
                            去测试计划
                          </Button>
                        }
                      />
                    </td>
                  </tr>
                ) : (
                  recentTasks.map((task) => (
                    <tr key={task.taskId} className="row-clickable" onClick={() => openTask(task.taskId)}>
                      <td>
                        <div className="cell-title">{task.caseTitle || task.caseId || '—'}</div>
                        <div className="cell-sub mono">{task.taskId}</div>
                      </td>
                      <td>
                        <StatusPill meta={metaOf(EXECUTOR_META, task.executorType)} dot={false} />
                      </td>
                      <td>
                        <StatusPill meta={metaOf(TASK_STATUS_META, task.status)} />
                      </td>
                      <td>
                        {task.deviceSerial ? (
                          <span className="mono">{task.deviceSerial}</span>
                        ) : (
                          <span className="text-muted">未分配</span>
                        )}
                      </td>
                      <td className="text-muted">{formatDateTime(task.createdAt)}</td>
                      <td className="td-right">
                        <button
                          type="button"
                          className="link-btn"
                          onClick={(event) => {
                            event.stopPropagation();
                            openTask(task.taskId);
                          }}
                        >
                          查看轨迹
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
      </Card>

      <div className="dashboard-footnote">
        <Icon name="info" size={14} />
        总览数据每 10 秒自动轮询；失败原因分类来自 ui-execution 的失败报告聚合。
      </div>
    </div>
  );
}
