import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { uiexecApi } from '../api';
import type { TaskArtifact, TaskDetail, TaskLog, TaskStep } from '../api/types';
import { canReview, useAuth } from '../auth';
import {
  EXECUTOR_META,
  FAILURE_CATEGORY_META,
  STEP_STATUS_META,
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
  isTerminalTask,
  safeErrorMessage,
  truncate,
} from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Callout,
  Card,
  CardHead,
  CodeBlock,
  EmptyState,
  ErrorPanel,
  LoadingPanel,
  PageHeading,
  StatusPill,
} from '../components/ui';
import { ConfirmDialog, ImageLightbox } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const POLL_MS = 2000;

function ScreenshotThumb({
  src,
  alt,
  onClick,
}: {
  src?: string | null;
  alt: string;
  onClick: () => void;
}) {
  const [broken, setBroken] = useState(false);
  useEffect(() => {
    setBroken(false);
  }, [src]);

  if (!src || broken) {
    return (
      <div className="screenshot-thumb is-broken" title="截图不可用">
        <Icon name="image" size={18} />
        <span>截图不可用</span>
      </div>
    );
  }
  return (
    <button type="button" className="screenshot-thumb" onClick={onClick} title="点击查看大图">
      <img src={src} alt={alt} loading="lazy" onError={() => setBroken(true)} />
      <span className="screenshot-zoom">
        <Icon name="eye" size={13} />
      </span>
    </button>
  );
}

function StepCard({ step, onOpenImage }: { step: TaskStep; onOpenImage: (src: string, title: string) => void }) {
  const [expanded, setExpanded] = useState(false);
  const meta = metaOf(STEP_STATUS_META, step.status);
  return (
    <li className="task-step">
      <div className="task-step-rail">
        <span className={cx('task-step-dot', `tone-bg-${meta.tone}`)} />
      </div>
      <div className="task-step-card">
        <div className="task-step-head">
          <span className="step-seq">#{step.seq}</span>
          <StatusPill meta={meta} />
          {step.action ? <span className="step-action mono">{truncate(step.action, 90)}</span> : null}
          <span className="step-elapsed">{formatDurationMs(step.elapsedMs)}</span>
        </div>
        <div className="task-step-meta">
          {step.createdAt ? <span>{formatDateTime(step.createdAt)}</span> : null}
          {step.stepId ? <span className="mono">{step.stepId}</span> : null}
        </div>
        {step.decision ? (
          <div className="decision-block">
            <button type="button" className="decision-toggle" onClick={() => setExpanded((prev) => !prev)}>
              <Icon name={expanded ? 'chevron-up' : 'chevron-down'} size={14} />
              {expanded ? '收起决策原文' : '展开决策原文'}
            </button>
            {expanded ? (
              <div className="decision-code">
                <CodeBlock code={step.decision} title="模型决策原文" maxHeight={220} wrap />
              </div>
            ) : null}
          </div>
        ) : null}
        {step.screenshotUrl ? (
          <ScreenshotThumb
            src={step.screenshotUrl}
            alt={`第 ${step.seq} 步截图`}
            onClick={() => onOpenImage(step.screenshotUrl as string, `第 ${step.seq} 步截图`)}
          />
        ) : null}
      </div>
    </li>
  );
}

function ArtifactList({ artifacts }: { artifacts: TaskArtifact[] }) {
  if (!artifacts.length) {
    return <EmptyState compact icon="download" title="暂无产物" description="任务结束后可下载脚本报告等产物" />;
  }
  return (
    <div className="artifact-list">
      {artifacts.map((artifact, index) => (
        <div className="artifact-row" key={`${artifact.name}-${index}`}>
          <span className="artifact-icon">
            <Icon name="download" size={15} />
          </span>
          <div className="artifact-main">
            <div className="artifact-name">{artifact.name}</div>
            {artifact.type ? <div className="artifact-type">{artifact.type}</div> : null}
          </div>
          {artifact.url ? (
            <a className="link-btn" href={artifact.url} target="_blank" rel="noreferrer">
              下载
              <Icon name="external" size={12} />
            </a>
          ) : (
            <span className="text-muted">—</span>
          )}
        </div>
      ))}
    </div>
  );
}

export function TaskDetailPage() {
  const route = useHashRoute();
  const taskId = route.segments[1] || '';
  const { user } = useAuth();
  const [task, setTask] = useState<TaskDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [lightbox, setLightbox] = useState<{ src: string; title: string } | null>(null);
  const logRef = useRef<HTMLDivElement>(null);

  const load = useCallback(
    async (silent = false) => {
      if (!taskId) {
        setError('缺少任务 ID');
        setLoading(false);
        return;
      }
      if (!silent) setLoading(true);
      else setRefreshing(true);
      setError('');
      try {
        const data = await uiexecApi.task(taskId);
        setTask(data);
      } catch (err) {
        if (!silent) setError(safeErrorMessage(err));
      } finally {
        if (!silent) setLoading(false);
        setRefreshing(false);
      }
    },
    [taskId],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const active = task ? !isTerminalTask(task.status) : false;
  usePolling(() => void load(true), active ? POLL_MS : null);

  const steps = useMemo(
    () => [...(task?.steps || [])].sort((a, b) => (a.seq || 0) - (b.seq || 0)),
    [task?.steps],
  );
  const logs: TaskLog[] = useMemo(
    () => [...(task?.logs || [])].sort((a, b) => (a.seq || 0) - (b.seq || 0)),
    [task?.logs],
  );
  const artifacts = task?.artifacts || [];

  useEffect(() => {
    const element = logRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [logs.length, task?.status]);

  const confirmCancel = async () => {
    if (!taskId) return;
    setCancelling(true);
    try {
      await uiexecApi.cancelTask(taskId);
      toastSuccess('已发送取消请求，等待 Agent 回报最终状态', '取消已受理');
      setCancelOpen(false);
      await load(true);
    } catch (err) {
      toastError(safeErrorMessage(err), '取消失败');
    } finally {
      setCancelling(false);
    }
  };

  const duration = task
    ? durationBetween(task.startedAt || task.createdAt, task.finishedAt, task.durationMs ?? null)
    : null;
  const failureUrl = task?.failureReportUrl || task?.failureReport?.reportUrl || '';
  const failureReason = task?.failureReason || task?.failureReport?.reasonCategory;
  const failureSummary = task?.failureSummary || task?.failureReport?.summary;
  const hasFailureCard =
    ['FAILED', 'TIMEOUT', 'CANCELLED'].includes(String(task?.status || '')) ||
    Boolean(failureReason || failureUrl);
  const statusMeta = metaOf(TASK_STATUS_META, task?.status);
  const executorMeta = metaOf(EXECUTOR_META, task?.executorType);

  if (loading && !task) {
    return (
      <div className="page-stack">
        <LoadingPanel text="正在加载任务详情…" />
      </div>
    );
  }

  if (error && !task) {
    return (
      <div className="page-stack">
        <PageHeading
          title="任务详情"
          actions={
            <Button variant="secondary" icon="arrow-left" onClick={() => navigate('/tasks')}>
              返回任务列表
            </Button>
          }
        />
        <Card>
          <div className="card-body">
            <ErrorPanel
              message={error}
              onRetry={() => void load()}
              hint="请确认任务 ID 正确，且 ui-execution（默认 8081）服务已启动。"
            />
            <div className="detail-back-hint">
              如果是刚触发的任务，可能仍在排队；返回列表后稍等再试。
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
          <button type="button" className="back-link" onClick={() => navigate('/tasks')}>
            <Icon name="arrow-left" size={14} />
            执行任务
          </button>
        }
        title={
          <span className="title-with-pill">
            {task?.caseTitle || '任务详情'}
            {task ? <StatusPill meta={statusMeta} /> : null}
          </span>
        }
        description={
          <span>
            任务 <span className="mono">{taskId}</span>
            {task?.executorType ? (
              <>
                {' · '}执行器 <StatusPill meta={executorMeta} dot={false} />
              </>
            ) : null}
            {task?.deviceSerial ? (
              <>
                {' · '}设备 <span className="mono">{task.deviceSerial}</span>
              </>
            ) : null}
          </span>
        }
        actions={
          <div className="page-actions">
            {active ? (
              <span className="polling-indicator">
                <span className={cx('live-dot', refreshing && 'is-active')} />
                每 2 秒自动刷新
              </span>
            ) : (
              <span className="polling-indicator is-ended">任务已结束</span>
            )}
            {failureUrl ? (
              <a className="btn btn-secondary btn-md" href={failureUrl} target="_blank" rel="noreferrer">
                <Icon name="external" size={15} />
                失败报告
              </a>
            ) : null}
            <Button variant="secondary" icon="refresh" loading={refreshing} onClick={() => void load(true)}>
              刷新
            </Button>
            {active && canReview(user) ? (
              <Button variant="danger" icon="stop" onClick={() => setCancelOpen(true)}>
                取消任务
              </Button>
            ) : null}
          </div>
        }
      />

      <div className="run-meta-strip">
        <div className="run-meta-item">
          <span className="meta-label">创建时间</span>
          <strong>{formatDateTime(task?.createdAt)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">开始时间</span>
          <strong>{formatDateTime(task?.startedAt)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">结束时间</span>
          <strong>{formatDateTime(task?.finishedAt)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">耗时</span>
          <strong>{duration === null ? '—' : formatDurationMs(duration)}</strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">总超时 / 单步</span>
          <strong>
            {task?.timeoutSec ?? '—'}s / {task?.stepTimeoutSec ?? '—'}s
          </strong>
        </div>
        <div className="run-meta-item">
          <span className="meta-label">设备 / Agent</span>
          <strong className="mono">
            {task?.deviceSerial || task?.deviceId || '—'}
            {task?.agentId ? ` / ${task.agentId}` : ''}
          </strong>
        </div>
        {task?.planRunId ? (
          <div className="run-meta-item">
            <span className="meta-label">计划运行</span>
            <button
              type="button"
              className="link-btn"
              onClick={() => navigate(`/runs/${task.planRunId}`)}
            >
              {task.planRunId}
              <Icon name="arrow-right" size={12} />
            </button>
          </div>
        ) : null}
        {task?.planId ? (
          <div className="run-meta-item">
            <span className="meta-label">测试计划</span>
            <button type="button" className="link-btn" onClick={() => navigate(`/plans/${task.planId}`)}>
              {task.planId}
              <Icon name="arrow-right" size={12} />
            </button>
          </div>
        ) : null}
      </div>

      {hasFailureCard ? (
        <Card className="failure-card-strong">
          <div className="failure-card-strong-head">
            <span className="failure-card-strong-icon">
              <Icon name="warning" size={19} />
            </span>
            <div>
              <div className="failure-card-strong-title">失败报告</div>
              <div className="failure-card-strong-sub">
                {failureReason ? metaOf(FAILURE_CATEGORY_META, failureReason).label : '任务失败'}
              </div>
            </div>
            {failureReason ? (
              <StatusPill meta={metaOf(FAILURE_CATEGORY_META, failureReason)} dot={false} />
            ) : null}
            <div className="failure-card-strong-actions">
              {failureUrl ? (
                <a className="btn btn-secondary btn-sm" href={failureUrl} target="_blank" rel="noreferrer">
                  <Icon name="external" size={14} />
                  打开完整报告
                </a>
              ) : null}
            </div>
          </div>
          <p className="failure-card-strong-summary">
            {failureSummary || '平台已记录失败任务，但后端未提供摘要。'}
          </p>
          {task?.status === 'CANCELLED' ? (
            <div className="failure-card-strong-note">
              <Icon name="info" size={13} />
              任务被人工取消，已产生的轨迹保留。
            </div>
          ) : null}
        </Card>
      ) : null}

      {task?.bdd ? (
        <Card>
          <CardHead title="用例背景（BDD）" icon="book" />
          <div className="card-body">
            <div className="bdd-grid compact">
              <div className="bdd-col bdd-given">
                <span className="bdd-tag">Given</span>
                <p>{task.bdd.given || '—'}</p>
              </div>
              <div className="bdd-col bdd-when">
                <span className="bdd-tag">When</span>
                <p>{task.bdd.when || '—'}</p>
              </div>
              <div className="bdd-col bdd-then">
                <span className="bdd-tag">Then</span>
                <p>{task.bdd.then || '—'}</p>
              </div>
            </div>
          </div>
        </Card>
      ) : null}

      <div className="task-detail-grid">
        <Card className="task-steps-card">
          <CardHead
            title="步骤时间线"
            description={`共 ${steps.length} 步${active ? ' · 实时接收 Agent 回调' : ''}`}
            icon="tasks"
          />
          <div className="card-body">
            {steps.length === 0 ? (
              <EmptyState
                icon="tasks"
                title={active ? '等待 Agent 上报步骤' : '任务没有上报步骤'}
                description={
                  active
                    ? '任务可能仍在排队 / 下发中，页面每 2 秒自动刷新'
                    : '该任务没有产生执行步骤，可查看失败报告或日志了解原因'
                }
              />
            ) : (
              <ol className="task-steps">
                {steps.map((step) => (
                  <StepCard
                    key={step.stepId || step.seq}
                    step={step}
                    onOpenImage={(src, title) => setLightbox({ src, title })}
                  />
                ))}
              </ol>
            )}
          </div>
        </Card>

        <div className="task-side">
          <Card className="task-logs-card">
            <CardHead
              title="实时日志"
              description={`${logs.length} 行${active ? ' · 自动滚动到底部' : ''}`}
              icon="terminal"
            />
            <div className="log-panel" ref={logRef}>
              {logs.length === 0 ? (
                <div className="log-empty">暂无日志</div>
              ) : (
                logs.map((log) => (
                  <div className="log-line" key={`${log.seq}-${log.createdAt || ''}`}>
                    <span className="log-time">{formatDateTime(log.createdAt)}</span>
                    <span className={cx('log-level', `level-${(log.level || 'INFO').toLowerCase()}`)}>
                      {(log.level || 'INFO').toUpperCase()}
                    </span>
                    <span className="log-message">{log.message || ''}</span>
                  </div>
                ))
              )}
            </div>
          </Card>

          <Card>
            <CardHead title="产物" description="脚本报告、截图包等执行产物" icon="download" />
            <div className="card-body">
              <ArtifactList artifacts={artifacts} />
            </div>
          </Card>

          {task?.skills ? (
            <Card>
              <CardHead title="Skills" description="本次执行使用的 Skills 指针" icon="book" />
              <div className="card-body">
                <div className="skills-mini">
                  <div>
                    <span className="meta-label">仓库</span>
                    <span className="mono">{task.skills.repo || '—'}</span>
                  </div>
                  <div>
                    <span className="meta-label">Commit</span>
                    <span className="mono">{task.skills.commit || '—'}</span>
                  </div>
                </div>
              </div>
            </Card>
          ) : null}
        </div>
      </div>

      <div className="dashboard-footnote">
        <Icon name="info" size={14} />
        执行状态由 ui-execution 状态机维护：PENDING → DISPATCHED → RUNNING → 终止态；任务结束前页面每 2 秒轮询。
      </div>

      <ConfirmDialog
        open={cancelOpen}
        title="取消执行任务"
        message={
          <span>
            确认取消任务 <span className="mono">{taskId}</span>？
            排队中的任务会直接出队；已下发的任务会向 Agent 发送取消信号，并保留已产生的轨迹。
          </span>
        }
        confirmText="确认取消"
        tone="danger"
        loading={cancelling}
        onConfirm={() => void confirmCancel()}
        onClose={() => setCancelOpen(false)}
      />

      <ImageLightbox
        open={Boolean(lightbox)}
        src={lightbox?.src}
        title={lightbox?.title}
        onClose={() => setLightbox(null)}
      />
    </div>
  );
}
