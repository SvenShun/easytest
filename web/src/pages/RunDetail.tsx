import { useCallback, useEffect, useMemo, useState } from 'react';
import { plansApi } from '../api';
import type { ExecutorStat, FailureCategoryStat, PlanRun, RunItem, RunReport, RunSummary } from '../api/types';
import {
  EXECUTOR_META,
  FAILURE_CATEGORY_META,
  PLAN_RUN_STATUS_META,
  TASK_STATUS_META,
  metaOf,
} from '../constants';
import { usePolling } from '../hooks';
import { navigate, useHashRoute } from '../router';
import {
  cx,
  durationBetween,
  formatDateTime,
  formatDurationMs,
  isTerminalRun,
  isTerminalTask,
  percent,
  safeErrorMessage,
  truncate,
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
  LoadingPanel,
  PageHeading,
  ProgressBar,
  StatusPill,
} from '../components/ui';

const POLL_MS = 5000;

const EMPTY_SUMMARY: RunSummary = {
  total: 0,
  success: 0,
  failed: 0,
  timeout: 0,
  cancelled: 0,
  running: 0,
  pending: 0,
  skipped: 0,
};

function buildSummary(run: PlanRun | undefined, items: RunItem[]): RunSummary {
  if (!items.length) {
    return {
      total: run?.total ?? 0,
      success: run?.success ?? 0,
      failed: run?.failed ?? 0,
      timeout: run?.timeout ?? 0,
      cancelled: run?.cancelled ?? 0,
      running: run?.running ?? 0,
      pending: run?.pending ?? 0,
      skipped: 0,
    };
  }
  const summary: RunSummary = { ...EMPTY_SUMMARY, total: items.length };
  items.forEach((item) => {
    switch (item.status) {
      case 'SUCCESS':
        summary.success += 1;
        break;
      case 'FAILED':
        summary.failed += 1;
        break;
      case 'TIMEOUT':
        summary.timeout += 1;
        break;
      case 'CANCELLED':
        summary.cancelled += 1;
        break;
      case 'SKIPPED':
        summary.skipped = (summary.skipped ?? 0) + 1;
        break;
      case 'RUNNING':
      case 'DISPATCHED':
        summary.running += 1;
        break;
      default:
        summary.pending += 1;
    }
  });
  return summary;
}

function buildExecutorStats(items: RunItem[]): ExecutorStat[] {
  const map = new Map<string, ExecutorStat>();
  items.forEach((item) => {
    const key = String(item.executorType || 'UNKNOWN');
    const stat = map.get(key) || { executorType: key, total: 0, success: 0, failed: 0 };
    stat.total += 1;
    if (item.status === 'SUCCESS') stat.success += 1;
    if (item.status === 'FAILED' || item.status === 'TIMEOUT') stat.failed += 1;
    map.set(key, stat);
  });
  return Array.from(map.values());
}

function buildFailureCategories(items: RunItem[]): FailureCategoryStat[] {
  const map = new Map<string, number>();
  items.forEach((item) => {
    if (!item.failureReason) return;
    if (item.status === 'SUCCESS' || item.status === 'SKIPPED') return;
    map.set(item.failureReason, (map.get(item.failureReason) || 0) + 1);
  });
  return Array.from(map.entries()).map(([category, count]) => ({ category, count }));
}

export function RunDetailPage() {
  const route = useHashRoute();
  const runId = route.segments[1] || '';
  const [report, setReport] = useState<RunReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [fallbackUsed, setFallbackUsed] = useState(false);

  const load = useCallback(
    async (silent = false) => {
      if (!runId) {
        setError('缺少运行 ID');
        setLoading(false);
        return;
      }
      if (!silent) setLoading(true);
      else setRefreshing(true);
      setError('');
      try {
        const data = await plansApi.report(runId);
        setReport(data);
        setFallbackUsed(false);
      } catch (err) {
        try {
          const fallback = await plansApi.run(runId);
          const items = fallback.items || [];
          setReport({
            run: fallback.run,
            summary: buildSummary(fallback.run, items),
            executors: buildExecutorStats(items),
            failureCategories: buildFailureCategories(items),
            items,
          });
          setFallbackUsed(true);
        } catch {
          if (!silent) setError(safeErrorMessage(err));
        }
      } finally {
        if (!silent) setLoading(false);
        setRefreshing(false);
        setLastUpdated(new Date());
      }
    },
    [runId],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const run = report?.run;
  const items = report?.items || [];
  const summary: RunSummary = { ...EMPTY_SUMMARY, ...(report?.summary || buildSummary(run, items)) };
  const executors = report?.executors?.length ? report.executors : items.length ? buildExecutorStats(items) : [];
  const failureCategories = report?.failureCategories?.length
    ? report.failureCategories
    : items.length
      ? buildFailureCategories(items)
      : [];

  const active = useMemo(() => {
    if (!run) return false;
    if (!isTerminalRun(run.status)) return true;
    return items.some((item) => !isTerminalTask(item.status));
  }, [run, items]);

  usePolling(() => void load(true), active ? POLL_MS : null);

  const duration = run?.startedAt ? durationBetween(run.startedAt, run.finishedAt) : null;
  const successRate = summary.total > 0 ? Math.round((summary.success / summary.total) * 100) : 0;
  const backPlanId = run?.planId;

  if (loading && !report) {
    return (
      <div className="page-stack">
        <LoadingPanel text="正在加载运行报告…" />
      </div>
    );
  }

  if (error && !report) {
    return (
      <div className="page-stack">
        <PageHeading
          title="运行报告"
          actions={
            <Button variant="secondary" icon="arrow-left" onClick={() => navigate('/plans')}>
              返回计划列表
            </Button>
          }
        />
        <Card>
          <div className="card-body">
            <ErrorPanel message={error} onRetry={() => void load()} />
            <div className="detail-back-hint">
              运行报告依赖 core 服务的 plan-runs 接口。请确认计划运行 ID 正确、后端服务已启动。
            </div>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="page-stack">
      <PageHeading
        eyebrow={
          <button
            type="button"
            className="back-link"
            onClick={() => navigate(backPlanId ? `/plans/${backPlanId}` : '/plans')}
          >
            <Icon name="arrow-left" size={14} />
            返回测试计划
          </button>
        }
        title={
          <span className="title-with-pill">
            运行报告
            {run ? <StatusPill meta={metaOf(PLAN_RUN_STATUS_META, run.status)} /> : null}
          </span>
        }
        description={`运行 ID：${runId}`}
        actions={
          <div className="page-actions">
            {active ? (
              <span className="polling-indicator">
                <span className={cx('live-dot', refreshing && 'is-active')} />
                每 5 秒自动刷新
              </span>
            ) : null}
            <Button variant="secondary" icon="refresh" loading={refreshing} onClick={() => void load(true)}>
              刷新
            </Button>
            <Button
              variant="primary"
              icon="arrow-left"
              onClick={() => navigate(backPlanId ? `/plans/${backPlanId}` : '/plans')}
            >
              返回计划
            </Button>
          </div>
        }
      />

      {fallbackUsed ? (
        <Callout tone="warning" title="报告接口不可用，已降级展示">
          `GET /plan-runs/&#123;runId&#125;/report` 请求失败，当前数据来自运行详情接口，部分统计为前端汇总。
        </Callout>
      ) : null}

      <div className="run-meta-strip">
        <div className="run-meta-item">
          <span className="meta-label">触发人</span>
          <strong>{run?.triggeredBy || '—'}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">开始时间</span>
          <strong>{formatDateTime(run?.startedAt)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">结束时间</span>
          <strong>{formatDateTime(run?.finishedAt)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">耗时</span>
          <strong>{duration === null ? '—' : formatDurationMs(duration)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">成功率</span>
          <strong className={successRate >= 80 ? 'text-success' : successRate > 0 ? 'text-warning' : 'text-muted'}>
            {successRate}%
          </strong>
        </div>
        {lastUpdated ? (
          <div className="run-meta-item is-muted">
            <span className="meta-label">更新于</span>
            <strong>{formatDateTime(lastUpdated)}</strong>
          </div>
        ) : null}
      </div>

      <div className="run-summary-grid">
        {[
          { key: 'total', label: '总用例', value: summary.total, tone: 'slate', icon: 'cases' as const },
          { key: 'success', label: '成功', value: summary.success, tone: 'green', icon: 'check-circle' as const },
          { key: 'failed', label: '失败', value: summary.failed, tone: 'red', icon: 'x-circle' as const },
          { key: 'timeout', label: '超时', value: summary.timeout, tone: 'orange', icon: 'clock' as const },
          { key: 'cancelled', label: '已取消', value: summary.cancelled, tone: 'gray', icon: 'stop' as const },
          {
            key: 'skipped',
            label: '已跳过',
            value: summary.skipped ?? 0,
            tone: 'gray',
            icon: 'arrow-right' as const,
          },
        ].map((card) => (
          <div className={cx('run-summary-card', `tone-border-${card.tone}`)} key={card.key}>
            <span className={cx('run-summary-icon', `tone-${card.tone}`)}>
              <Icon name={card.icon} size={18} />
            </span>
            <div>
              <div className="run-summary-label">{card.label}</div>
              <div className="run-summary-value">{card.value}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="run-report-split">
        <Card>
          <CardHead title="按执行器统计" description="同一计划内可混合传统脚本与 AI Runtime" icon="cpu" />
          <div className="card-body no-pad">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>执行器</th>
                    <th>总数</th>
                    <th>成功</th>
                    <th>失败 / 超时</th>
                    <th>成功率</th>
                  </tr>
                </thead>
                <tbody>
                  {executors.length === 0 ? (
                    <tr>
                      <td colSpan={5}>
                        <EmptyState compact icon="cpu" title="暂无执行器统计" />
                      </td>
                    </tr>
                  ) : (
                    executors.map((executor) => {
                      const rate = executor.total ? Math.round((executor.success / executor.total) * 100) : 0;
                      return (
                        <tr key={executor.executorType}>
                          <td>
                            <StatusPill meta={metaOf(EXECUTOR_META, executor.executorType)} dot={false} />
                          </td>
                          <td>{executor.total}</td>
                          <td className="text-success">{executor.success}</td>
                          <td className={executor.failed > 0 ? 'text-danger' : 'text-muted'}>{executor.failed}</td>
                          <td>
                            <div className="rate-cell">
                              <ProgressBar
                                value={executor.success}
                                max={executor.total}
                                tone={rate === 100 ? 'green' : rate > 0 ? 'orange' : 'red'}
                              />
                              <span>{percent(executor.success, executor.total)}</span>
                            </div>
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

        <Card>
          <CardHead title="失败分类" description="失败原因来自任务失败报告聚合" icon="warning" />
          <div className="card-body">
            <FailureBars data={failureCategories} emptyText="本次运行没有失败样本" />
          </div>
        </Card>
      </div>

      <Card>
        <CardHead
          title="用例明细"
          description={`共 ${items.length} 条用例结果 · 点击「轨迹」查看每一步动作与截图`}
          icon="cases"
          actions={
            active ? (
              <span className="polling-indicator">
                <span className={cx('live-dot', refreshing && 'is-active')} />
                运行进行中
              </span>
            ) : null
          }
        />
        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table table-run-items">
              <thead>
                <tr>
                  <th>用例</th>
                  <th>执行器</th>
                  <th>状态</th>
                  <th>失败原因</th>
                  <th>摘要</th>
                  <th>设备</th>
                  <th>报告</th>
                  <th className="th-right">任务轨迹</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={8}>
                      <EmptyState
                        icon="cases"
                        title="没有用例结果"
                        description="运行可能尚未开始，或计划触发时没有创建任务"
                      />
                    </td>
                  </tr>
                ) : (
                  items.map((item) => (
                    <tr key={`${item.caseId}-${item.taskId || ''}`}>
                      <td>
                        <div className="cell-title">{item.caseTitle}</div>
                        <div className="cell-sub mono">{item.caseId}</div>
                      </td>
                      <td>
                        <StatusPill meta={metaOf(EXECUTOR_META, item.executorType)} dot={false} />
                      </td>
                      <td>
                        <StatusPill meta={metaOf(TASK_STATUS_META, item.status)} />
                      </td>
                      <td>
                        {item.failureReason ? (
                          <StatusPill meta={metaOf(FAILURE_CATEGORY_META, item.failureReason)} dot={false} />
                        ) : (
                          <span className="text-muted">—</span>
                        )}
                      </td>
                      <td className="td-summary" title={item.summary || ''}>
                        {item.summary ? truncate(item.summary, 56) : <span className="text-muted">—</span>}
                      </td>
                      <td>
                        {item.deviceId ? <span className="mono">{item.deviceId}</span> : <span className="text-muted">—</span>}
                      </td>
                      <td>
                        {item.failureReportUrl ? (
                          <a
                            className="link-btn"
                            href={item.failureReportUrl}
                            target="_blank"
                            rel="noreferrer"
                          >
                            报告
                            <Icon name="external" size={12} />
                          </a>
                        ) : (
                          <span className="text-muted">—</span>
                        )}
                      </td>
                      <td className="td-right">
                        {item.taskId ? (
                          <Button
                            size="sm"
                            variant="secondary"
                            icon="terminal"
                            onClick={() => navigate(`/tasks/${item.taskId}`)}
                          >
                            轨迹
                          </Button>
                        ) : (
                          <span className="text-muted">未创建</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="table-note">
            <Icon name="info" size={14} />
            计划运行状态由各用例 item 汇总：全部成功为「已完成」，存在失败/超时/取消为「部分失败」，全部失败为「失败」。
          </div>
        </div>
      </Card>
    </div>
  );
}
