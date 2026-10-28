import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { apiCasesApi, apiEnvironmentsApi, casesApi, plansApi, uiexecApi } from '../api';
import { MAIN_ONLY } from '../config';
import type {
  CaseDetail,
  Device,
  ExecutorType,
  ApiCase,
  ApiEnvironment,
  PlanCase,
  PlanDetail,
  RunSummary,
  TriggerResponse,
} from '../api/types';
import {
  DEVICE_STATUS_META,
  EXECUTOR_META,
  PLAN_RUN_STATUS_META,
  REVIEW_STATUS_META,
  TASK_STATUS_META,
  metaOf,
} from '../constants';
import { usePolling } from '../hooks';
import { buildPath, navigate, useHashRoute } from '../router';
import { cx, formatDateTime, isTerminalRun, parseTags, safeErrorMessage } from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Callout,
  Card,
  CardHead,
  EmptyState,
  ErrorPanel,
  Field,
  IconButton,
  Input,
  LoadingPanel,
  PageHeading,
  Pill,
  Select,
  StatusPill,
  Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { TriggerResultModal } from '../components/TriggerResultModal';
import { toastError, toastSuccess } from '../components/Toast';

const RUN_POLL_MS = 5000;

function executorOf(planCase: PlanCase): ExecutorType {
  if (planCase.caseType === 'API') return 'API';
  if (planCase.executorTypeOverride) return planCase.executorTypeOverride;
  if (planCase.defaultExecutor) return planCase.defaultExecutor;
  return planCase.hasScript ? 'SCRIPT' : 'AI_RUNTIME';
}

function RunStats({ run }: { run: { total?: number; success?: number; failed?: number; timeout?: number; cancelled?: number; running?: number; pending?: number } }) {
  const parts = [
    { label: '总', value: run.total, tone: 'slate' as const },
    { label: '成功', value: run.success, tone: 'green' as const },
    { label: '失败', value: run.failed, tone: 'red' as const },
    { label: '超时', value: run.timeout, tone: 'orange' as const },
    { label: '取消', value: run.cancelled, tone: 'gray' as const },
  ];
  return (
    <div className="run-stats">
      {parts.map((part) => (
        <span key={part.label} className={cx('run-stat', `tone-text-${part.tone}`)}>
          <span className="run-stat-label">{part.label}</span>
          <span className="run-stat-value">{part.value ?? 0}</span>
        </span>
      ))}
    </div>
  );
}

type AddCaseMode = 'default' | 'SCRIPT' | 'AI_RUNTIME';

export function PlanDetailPage() {
  const route = useHashRoute();
  const planId = route.segments[1] || '';

  const [detail, setDetail] = useState<PlanDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const [editOpen, setEditOpen] = useState(false);
  const [editForm, setEditForm] = useState({ name: '', description: '', environmentId: '' });
  const [environments, setEnvironments] = useState<ApiEnvironment[]>([]);
  const [editError, setEditError] = useState('');
  const [editFieldError, setEditFieldError] = useState('');
  const [editSaving, setEditSaving] = useState(false);

  const [addOpen, setAddOpen] = useState(false);
  const [approvedCases, setApprovedCases] = useState<CaseDetail[]>([]);
  const [approvedLoading, setApprovedLoading] = useState(false);
  const [approvedError, setApprovedError] = useState('');
  const [caseSearch, setCaseSearch] = useState('');
  const [selectedCases, setSelectedCases] = useState<Set<string>>(new Set());
  const [apiCases, setApiCases] = useState<ApiCase[]>([]);
  const [apiCasesLoading, setApiCasesLoading] = useState(false);
  const [apiCasesError, setApiCasesError] = useState('');
  const [selectedApiCases, setSelectedApiCases] = useState<Set<string>>(new Set());
  const [addMode, setAddMode] = useState<AddCaseMode>('default');
  const [addSaving, setAddSaving] = useState(false);
  const [addError, setAddError] = useState('');

  const [removeTarget, setRemoveTarget] = useState<PlanCase | null>(null);
  const [removeLoading, setRemoveLoading] = useState(false);

  const [triggerOpen, setTriggerOpen] = useState(false);
  const [triggerForm, setTriggerForm] = useState({
    targetDeviceId: '',
    targetDeviceIds: [] as string[],
    targetTags: '',
    timeoutSec: '120',
    stepTimeoutSec: '30',
  });
  const [triggerError, setTriggerError] = useState('');
  const [triggering, setTriggering] = useState(false);
  const [triggerResult, setTriggerResult] = useState<TriggerResponse | null>(null);
  const [triggerResultOpen, setTriggerResultOpen] = useState(false);
  const [devices, setDevices] = useState<Device[]>([]);
  const [devicesError, setDevicesError] = useState('');
  const [deviceBindOpen, setDeviceBindOpen] = useState(false);
  const [boundDevices, setBoundDevices] = useState<string[]>([]);
  const [deviceBindBusy, setDeviceBindBusy] = useState(false);
  const [deviceBindError, setDeviceBindError] = useState('');

  const load = useCallback(
    async (silent = false) => {
      if (!planId) {
        setError('缺少计划 ID');
        setLoading(false);
        return;
      }
      if (!silent) setLoading(true);
      else setRefreshing(true);
      setError('');
      try {
        const data = await plansApi.get(planId);
        setDetail(data);
      } catch (err) {
        if (!silent) setError(safeErrorMessage(err));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [planId],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const hasActiveRun = useMemo(() => {
    if (!detail?.runs?.length) return false;
    return detail.runs.some((run) => {
      if (run.status && isTerminalRun(run.status)) return false;
      return (run.running ?? 0) > 0 || (run.pending ?? 0) > 0 || run.status === 'RUNNING' || run.status === 'PENDING';
    });
  }, [detail]);

  usePolling(() => void load(true), hasActiveRun ? RUN_POLL_MS : null);

  const plan = detail?.plan;
  const planCases = detail?.cases || [];
  const runs = useMemo(() => {
    const list = [...(detail?.runs || [])];
    return list.sort((a, b) => {
      const aTime = a.startedAt ? new Date(a.startedAt).getTime() : 0;
      const bTime = b.startedAt ? new Date(b.startedAt).getTime() : 0;
      return bTime - aTime;
    });
  }, [detail?.runs]);

  const openEdit = () => {
    if (!plan) return;
    setEditForm({ name: plan.name || '', description: plan.description || '',
      environmentId: plan.environmentId || '' });
    setEditError('');
    setEditFieldError('');
    setEditOpen(true);
  };

  const saveEdit = async () => {
    if (!planId) return;
    if (!editForm.name.trim()) {
      setEditFieldError('请输入计划名称');
      return;
    }
    setEditSaving(true);
    setEditError('');
    try {
      await plansApi.update(planId, { name: editForm.name.trim(), description: editForm.description.trim(),
        environmentId: editForm.environmentId || null });
      toastSuccess('计划信息已更新');
      setEditOpen(false);
      await load(true);
    } catch (err) {
      setEditError(safeErrorMessage(err));
    } finally {
      setEditSaving(false);
    }
  };

  const openAddCases = async () => {
    setAddOpen(true);
    setSelectedCases(new Set());
    setSelectedApiCases(new Set());
    setCaseSearch('');
    setAddMode('default');
    setAddError('');
    setApprovedLoading(true);
    setApprovedError('');
    try {
      const data = await casesApi.list({ reviewStatus: 'APPROVED', page: 1, size: 100 });
      setApprovedCases(data.items);
    } catch (err) {
      setApprovedError(safeErrorMessage(err));
    } finally {
      setApprovedLoading(false);
    }
    setApiCasesLoading(true);
    setApiCasesError('');
    try {
      const data = await apiCasesApi.list('', 1, 100);
      setApiCases(data.items);
    } catch (err) {
      setApiCasesError(safeErrorMessage(err));
    } finally {
      setApiCasesLoading(false);
    }
  };

  const toggleCase = (id: string) => {
    setSelectedCases((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleApiCase = (id: string) => {
    setSelectedApiCases((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const saveAddCases = async () => {
    if (!planId) return;
    const total = selectedCases.size + selectedApiCases.size;
    if (total === 0) {
      setAddError('请至少选择一条用例');
      return;
    }
    setAddSaving(true);
    setAddError('');
    try {
      await plansApi.addCases(planId, Array.from(selectedCases),
        addMode === 'default' ? null : addMode, Array.from(selectedApiCases));
      toastSuccess(`已添加 ${total} 条用例`);
      setAddOpen(false);
      await load(true);
    } catch (err) {
      setAddError(safeErrorMessage(err));
    } finally {
      setAddSaving(false);
    }
  };

  const confirmRemove = async () => {
    if (!planId || !removeTarget) return;
    setRemoveLoading(true);
    try {
      await plansApi.removeCase(planId, removeTarget.caseId);
      toastSuccess('已从计划中移除用例');
      setRemoveTarget(null);
      await load(true);
    } catch (err) {
      toastError(safeErrorMessage(err), '移除失败');
    } finally {
      setRemoveLoading(false);
    }
  };

  const openTrigger = async () => {
    setTriggerForm({ targetDeviceId: '', targetDeviceIds: [], targetTags: '', timeoutSec: '120', stepTimeoutSec: '30' });
    setTriggerError('');
    setTriggerOpen(true);
    setDevicesError('');
    try {
      const data = await uiexecApi.devices();
      setDevices(data.items);
    } catch (err) {
      setDevicesError(safeErrorMessage(err));
    }
  };

  const openDeviceBind = () => {
    setBoundDevices(detail?.deviceIds || []);
    setDeviceBindError('');
    setDeviceBindOpen(true);
  };

  const saveDeviceBind = async () => {
    if (!planId) return;
    setDeviceBindBusy(true);
    setDeviceBindError('');
    try {
      await plansApi.bindDevices(planId, boundDevices);
      toastSuccess('计划设备已保存');
      setDeviceBindOpen(false);
      await load();
    } catch (err) {
      setDeviceBindError(safeErrorMessage(err));
    } finally {
      setDeviceBindBusy(false);
    }
  };

  const submitTrigger = async () => {
    if (!planId) return;
    const timeoutSec = Number(triggerForm.timeoutSec);
    const stepTimeoutSec = Number(triggerForm.stepTimeoutSec);
    if (!Number.isFinite(timeoutSec) || timeoutSec <= 0) {
      setTriggerError('总超时需为大于 0 的秒数');
      return;
    }
    if (!Number.isFinite(stepTimeoutSec) || stepTimeoutSec <= 0) {
      setTriggerError('单步超时需为大于 0 的秒数');
      return;
    }
    if (stepTimeoutSec > timeoutSec) {
      setTriggerError('单步超时不能大于总超时');
      return;
    }
    setTriggering(true);
    setTriggerError('');
    try {
      const result = await plansApi.trigger(planId, {
        targetDeviceId: triggerForm.targetDeviceId || null,
        targetDeviceIds: triggerForm.targetDeviceIds,
        targetTags: parseTags(triggerForm.targetTags),
        timeoutSec,
        stepTimeoutSec,
      });
      setTriggerResult(result);
      setTriggerOpen(false);
      setTriggerResultOpen(true);
      toastSuccess(`已创建运行 ${result.runId}`, '计划已触发');
      await load(true);
    } catch (err) {
      setTriggerError(safeErrorMessage(err));
    } finally {
      setTriggering(false);
    }
  };

  const saveTrigger = (event: FormEvent) => {
    event.preventDefault();
    void submitTrigger();
  };

  const alreadyInPlan = useMemo(() => new Set(planCases.map((item) => item.caseId)), [planCases]);
  const filteredApiCases = useMemo(() => {
    const text = caseSearch.trim().toLowerCase();
    return apiCases
      .filter((item) => item.enabled && !alreadyInPlan.has(item.id))
      .filter(
        (item) =>
          !text ||
          item.name.toLowerCase().includes(text) ||
          item.path.toLowerCase().includes(text) ||
          item.id.toLowerCase().includes(text),
      );
  }, [apiCases, alreadyInPlan, caseSearch]);

  const filteredApproved = useMemo(() => {
    const text = caseSearch.trim().toLowerCase();
    return approvedCases
      .filter((item) => !alreadyInPlan.has(item.id))
      .filter(
        (item) =>
          !text ||
          item.title.toLowerCase().includes(text) ||
          (item.id || '').toLowerCase().includes(text) ||
          (item.given || '').toLowerCase().includes(text),
      );
  }, [approvedCases, alreadyInPlan, caseSearch]);

  useEffect(() => {
    void apiEnvironmentsApi.list('', 1, 100)
      .then((data) => setEnvironments(data.items))
      .catch(() => setEnvironments([]));
  }, []);

  const activeRunCount = runs.filter((run) => run.status === 'RUNNING' || run.status === 'PENDING').length;
  const environmentName = environments.find((env) => env.id === plan?.environmentId)?.name;

  if (loading && !detail) {
    return (
      <div className="page-stack">
        <LoadingPanel text="正在加载测试计划…" />
      </div>
    );
  }

  if (error && !detail) {
    return (
      <div className="page-stack">
        <PageHeading
          title="测试计划详情"
          actions={
            <Button variant="secondary" icon="arrow-left" onClick={() => navigate('/plans')}>
              返回列表
            </Button>
          }
        />
        <Card>
          <div className="card-body">
            <ErrorPanel message={error} onRetry={() => void load()} />
            <div className="detail-back-hint">
              如果该计划已被删除，或后端服务暂不可用，请返回列表后重试。
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
          <button type="button" className="back-link" onClick={() => navigate('/plans')}>
            <Icon name="arrow-left" size={14} />
            测试计划
          </button>
        }
        title={
          <span className="title-with-pill">
            {plan?.name || '测试计划'}
            {runs[0] ? <StatusPill meta={metaOf(PLAN_RUN_STATUS_META, runs[0].status)} /> : null}
          </span>
        }
        description={plan?.description || '该计划暂无描述'}
        actions={
          <div className="page-actions">
            <Button variant="ghost" icon="arrow-left" onClick={() => navigate('/plans')}>
              返回
            </Button>
            <Button variant="secondary" icon="edit" onClick={openEdit}>
              编辑
            </Button>
            {!MAIN_ONLY ? (
              <Button variant="primary" icon="play" onClick={() => void openTrigger()}>
                触发计划
              </Button>
            ) : null}
          </div>
        }
      />

      {hasActiveRun ? (
        <Callout tone="info" title="运行进行中">
          检测到未完成的运行，页面每 5 秒自动刷新运行状态。
          <span className={cx('live-dot', refreshing && 'is-active')} />
        </Callout>
      ) : null}

      <div className="detail-summary-row">
        <div className="summary-chip">
          <span className="summary-chip-label">用例</span>
          <span className="summary-chip-value">{planCases.length}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">运行</span>
          <span className="summary-chip-value">{runs.length}</span>
        </div>
        <div className="summary-chip">
          <span className="summary-chip-label">进行中</span>
          <span className="summary-chip-value">{activeRunCount}</span>
        </div>
        <div className="summary-chip is-wide">
          <span className="summary-chip-label">环境</span>
          <span className="summary-chip-value sm">{environmentName || '未绑定'}</span>
        </div>
        <div className="summary-chip is-wide">
          <span className="summary-chip-label">创建</span>
          <span className="summary-chip-value sm">{formatDateTime(plan?.createdAt)}</span>
        </div>
        <div className="summary-chip is-wide">
          <span className="summary-chip-label">计划 ID</span>
          <span className="summary-chip-value sm mono">{plan?.id}</span>
        </div>
      </div>

      <Card>
        <CardHead
          title="计划用例"
          description="UI 用例需已评审；接口用例需启用；触发时按类型路由到 ui-execution / api-execution"
          icon="cases"
          actions={
            <Button size="sm" variant="primary" icon="plus" onClick={() => void openAddCases()}>
              添加用例
            </Button>
          }
        />
        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>用例</th>
                  <th>类型</th>
                  <th>评审状态</th>
                  <th>关联脚本</th>
                  <th>默认执行器</th>
                  <th>
                    本次覆盖执行器
                    <span className="th-note">V1 只读</span>
                  </th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {planCases.length === 0 ? (
                  <tr>
                    <td colSpan={7}>
                      <EmptyState
                        icon="plans"
                        title="计划还没有用例"
                        description="点击右上角「添加用例」，选择已通过评审的用例加入计划"
                        action={
                          <Button size="sm" variant="primary" icon="plus" onClick={() => void openAddCases()}>
                            添加用例
                          </Button>
                        }
                      />
                    </td>
                  </tr>
                ) : (
                  planCases.map((item) => {
                    const isApi = item.caseType === 'API';
                    return (
                    <tr key={item.caseId}>
                      <td>
                        <button
                          type="button"
                          className="cell-link"
                          onClick={() => navigate(buildPath(isApi ? '/api-cases' : '/cases', { highlight: item.caseId }))}
                        >
                          {item.title}
                        </button>
                        <div className="cell-sub mono">{item.caseId}</div>
                      </td>
                      <td>
                        <Pill tone={isApi ? 'purple' : 'blue'}>{isApi ? '接口' : 'UI'}</Pill>
                        {isApi && item.method ? <div className="cell-sub mono">{item.method} {item.path}</div> : null}
                      </td>
                      <td>
                        {isApi ? (
                          <span className="text-muted">—</span>
                        ) : (
                          <StatusPill meta={metaOf(REVIEW_STATUS_META, item.reviewStatus)} />
                        )}
                      </td>
                      <td>
                        {isApi ? (
                          <span className="text-muted">—</span>
                        ) : item.hasScript ? (
                          <Pill tone="blue">{item.scriptType || '有脚本'}</Pill>
                        ) : (
                          <span className="text-muted">无</span>
                        )}
                      </td>
                      <td>
                        <StatusPill meta={metaOf(EXECUTOR_META, executorOf(item))} dot={false} />
                      </td>
                      <td>
                        {isApi ? (
                          <span className="text-muted">固定 API</span>
                        ) : (
                          <div className="override-cell" title="V1 后端暂不提供单独修改覆盖执行器的接口">
                            <Select
                              value={item.executorTypeOverride || ''}
                              disabled
                              aria-label="本次覆盖执行器（只读）"
                            >
                              <option value="">跟随默认</option>
                              <option value="SCRIPT">传统脚本</option>
                              <option value="AI_RUNTIME">AI Runtime</option>
                            </Select>
                          </div>
                        )}
                      </td>
                      <td className="td-right">
                        <IconButton
                          icon="trash"
                          label="从计划中移除"
                          tone="danger"
                          onClick={() => setRemoveTarget(item)}
                        />
                      </td>
                    </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          <div className="table-note">
            <Icon name="info" size={14} />
            V1 后端未提供单独更新覆盖执行器的接口：覆盖值在「添加用例」时可统一指定；若为「跟随默认」，UI 用例按
            有脚本 → SCRIPT、无脚本 → AI_RUNTIME 路由，接口用例固定走 api-execution。
          </div>
        </div>
      </Card>

      <Card>
        <CardHead
          title="执行设备"
          description="绑定后，触发计划且未手动选择设备时，默认按这些设备生成矩阵运行项"
          icon="devices"
          actions={<Button size="sm" variant="secondary" icon="edit" onClick={openDeviceBind}>绑定设备</Button>}
        />
        <div className="card-body">
          {(detail?.deviceIds || []).length === 0 ? (
            <span className="text-muted">未绑定计划设备；触发时自动选择在线设备。</span>
          ) : (
            <div className="pill-row">
              {(detail?.deviceIds || []).map((deviceId) => {
                const device = devices.find((item) => item.deviceId === deviceId);
                return <Pill key={deviceId} tone="blue">{device ? `${device.serial} · ${device.name || device.model}` : deviceId}</Pill>;
              })}
            </div>
          )}
        </div>
      </Card>

      <Card>
        <CardHead
          title="运行记录"
          description="每次触发创建一个计划运行，点击「报告」查看汇总、按执行器统计与失败分类"
          icon="tasks"
          actions={
            <div className="card-actions-inline">
              {hasActiveRun ? <span className="polling-indicator">自动刷新中</span> : null}
              <Button size="sm" variant="ghost" icon="refresh" onClick={() => void load(true)} loading={refreshing}>
                刷新
              </Button>
            </div>
          }
        />
        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>运行 ID</th>
                  <th>状态</th>
                  <th>触发人</th>
                  <th>开始时间</th>
                  <th>结束时间</th>
                  <th>统计</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {runs.length === 0 ? (
                  <tr>
                    <td colSpan={7}>
                      <EmptyState
                        compact
                        icon="play"
                        title="还没有运行记录"
                        description="点击右上角「触发计划」创建第一次运行"
                      />
                    </td>
                  </tr>
                ) : (
                  runs.map((run) => (
                    <tr key={run.id} className="row-clickable" onClick={() => navigate(`/runs/${run.id}`)}>
                      <td className="mono">{run.id}</td>
                      <td>
                        <StatusPill meta={metaOf(PLAN_RUN_STATUS_META, run.status)} />
                      </td>
                      <td>{run.triggeredBy || '—'}</td>
                      <td className="text-muted">{formatDateTime(run.startedAt)}</td>
                      <td className="text-muted">{formatDateTime(run.finishedAt)}</td>
                      <td>
                        <RunStats run={run} />
                      </td>
                      <td className="td-right">
                        <button
                          type="button"
                          className="link-btn"
                          onClick={(event) => {
                            event.stopPropagation();
                            navigate(`/runs/${run.id}`);
                          }}
                        >
                          报告
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

      {/* 编辑计划 */}
      <Modal
        open={editOpen}
        title="编辑测试计划"
        onClose={() => {
          if (!editSaving) setEditOpen(false);
        }}
        size="sm"
        closeOnOverlay={!editSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditOpen(false)} disabled={editSaving}>
              取消
            </Button>
            <Button variant="primary" loading={editSaving} onClick={() => void saveEdit()}>
              保存
            </Button>
          </>
        }
      >
        <form
          className="form-stack"
          onSubmit={(event) => {
            event.preventDefault();
            void saveEdit();
          }}
        >
          {editError ? <Callout tone="error" title="保存失败">{editError}</Callout> : null}
          <Field label="计划名称" htmlFor="edit-plan-name" required error={editFieldError}>
            <Input
              id="edit-plan-name"
              value={editForm.name}
              invalid={Boolean(editFieldError)}
              onChange={(event) => {
                setEditForm((prev) => ({ ...prev, name: event.target.value }));
                setEditFieldError('');
              }}
            />
          </Field>
          <Field label="API 环境" htmlFor="edit-plan-environment" hint="接口用例执行时解析变量与鉴权">
            <Select
              id="edit-plan-environment"
              value={editForm.environmentId}
              onChange={(event) => setEditForm((prev) => ({ ...prev, environmentId: event.target.value }))}
            >
              <option value="">不绑定环境</option>
              {environments.filter((env) => env.enabled).map((env) => (
                <option key={env.id} value={env.id}>{env.name}</option>
              ))}
            </Select>
          </Field>
          <Field label="描述" htmlFor="edit-plan-desc">
            <Textarea
              id="edit-plan-desc"
              rows={3}
              value={editForm.description}
              onChange={(event) => setEditForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </Field>
          <button type="submit" hidden />
        </form>
      </Modal>

      <Modal
        open={deviceBindOpen}
        title="绑定计划设备"
        description="可多选；触发时如未指定设备，将按绑定设备执行矩阵。"
        onClose={() => !deviceBindBusy && setDeviceBindOpen(false)}
        size="md"
        closeOnOverlay={!deviceBindBusy}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeviceBindOpen(false)} disabled={deviceBindBusy}>取消</Button>
            <Button variant="primary" loading={deviceBindBusy} onClick={() => void saveDeviceBind()}>保存</Button>
          </>
        }
      >
        <div className="form-stack">
          {deviceBindError ? <Callout tone="error" title="保存失败">{deviceBindError}</Callout> : null}
          {devices.map((device) => (
            <label key={device.deviceId} className="checkbox-row">
              <input
                type="checkbox"
                checked={boundDevices.includes(device.deviceId)}
                onChange={(event) => setBoundDevices((prev) => event.target.checked
                  ? [...prev, device.deviceId]
                  : prev.filter((id) => id !== device.deviceId))}
              />
              <span className="mono">{device.serial}</span>
              <span className="text-muted">{device.name || device.model} · {metaOf(DEVICE_STATUS_META, device.status).label}</span>
            </label>
          ))}
          {devices.length === 0 ? <span className="text-muted">暂无已注册设备</span> : null}
        </div>
      </Modal>

      {/* 添加用例 */}
      <Modal
        open={addOpen}
        title="添加用例到计划"
        description={`UI 用例需已通过评审；接口用例需启用；均只列出尚未加入计划的（当前计划 ${planCases.length} 条）`}
        onClose={() => {
          if (!addSaving) setAddOpen(false);
        }}
        size="lg"
        closeOnOverlay={!addSaving}
        footer={
          <>
            <span className="modal-foot-note">
              已选择 {selectedCases.size + selectedApiCases.size} 条（UI {selectedCases.size} · 接口 {selectedApiCases.size}）
            </span>
            <Button variant="secondary" onClick={() => setAddOpen(false)} disabled={addSaving}>
              取消
            </Button>
            <Button variant="primary" icon="plus" loading={addSaving} onClick={() => void saveAddCases()}>
              添加所选用例
            </Button>
          </>
        }
      >
        <div className="form-stack">
          {addError ? <Callout tone="error" title="添加失败">{addError}</Callout> : null}
          <div className="add-case-toolbar">
            <Input
              icon="search"
              placeholder="搜索标题 / ID / Given"
              value={caseSearch}
              onChange={(event) => setCaseSearch(event.target.value)}
            />
            <Field label="执行器覆盖" className="add-mode-field">
              <Select value={addMode} onChange={(event) => setAddMode(event.target.value as AddCaseMode)}>
                <option value="default">默认（按路由规则）</option>
                <option value="SCRIPT">传统脚本</option>
                <option value="AI_RUNTIME">AI Runtime</option>
              </Select>
            </Field>
          </div>
          <div className="add-case-list">
            {approvedLoading ? (
              <LoadingPanel text="正在加载已通过用例…" />
            ) : approvedError ? (
              <ErrorPanel message={approvedError} compact onRetry={() => void openAddCases()} />
            ) : filteredApproved.length === 0 ? (
              <EmptyState
                compact
                icon="cases"
                title={approvedCases.length === 0 ? '没有已通过的用例' : '没有可添加的用例'}
                description={
                  approvedCases.length === 0
                    ? '请先在用例管理中完成评审通过'
                    : '已通过用例都已加入计划，或不符合搜索条件'
                }
              />
            ) : (
              filteredApproved.map((item) => {
                const checked = selectedCases.has(item.id);
                return (
                  <label key={item.id} className={cx('add-case-row', checked && 'is-checked')}>
                    <input type="checkbox" checked={checked} onChange={() => toggleCase(item.id)} />
                    <span className="add-case-main">
                      <span className="add-case-title">{item.title}</span>
                      <span className="add-case-sub">
                        {item.id} · 有脚本：{item.script ? '是' : '否'} · 默认由
                        {item.script ? '传统脚本' : ' AI Runtime'} 执行
                      </span>
                    </span>
                    <Pill tone="green">已通过</Pill>
                  </label>
                );
              })
            )}
          </div>
          <div className="add-case-section-title">接口用例（由 api-execution 独立执行）</div>
          <div className="add-case-list add-case-list-api">
            {apiCasesLoading ? (
              <LoadingPanel text="正在加载接口用例…" />
            ) : apiCasesError ? (
              <ErrorPanel message={apiCasesError} compact onRetry={() => void openAddCases()} />
            ) : filteredApiCases.length === 0 ? (
              <EmptyState
                compact
                icon="api"
                title={apiCases.length === 0 ? '没有接口用例' : '没有可添加的接口用例'}
                description={
                  apiCases.length === 0
                    ? '请先在「接口用例」页面创建'
                    : '已启用的接口用例都已加入计划，或不符合搜索条件'
                }
              />
            ) : (
              filteredApiCases.map((item) => {
                const checked = selectedApiCases.has(item.id);
                return (
                  <label key={item.id} className={cx('add-case-row', checked && 'is-checked')}>
                    <input type="checkbox" checked={checked} onChange={() => toggleApiCase(item.id)} />
                    <span className="add-case-main">
                      <span className="add-case-title">{item.name}</span>
                      <span className="add-case-sub mono">
                        {item.method} {item.baseUrl || ''}{item.path} · 断言 {(item.assertions || []).length} 条
                      </span>
                    </span>
                    <Pill tone="blue">接口</Pill>
                  </label>
                );
              })
            )}
          </div>
          <div className="field-hint">执行器覆盖只对 UI 用例生效；接口用例固定由 api-execution 执行。</div>
        </div>
      </Modal>

      {/* 触发计划 */}
      <Modal
        open={triggerOpen}
        title="触发测试计划"
        description="为每个已挂载且已通过的用例创建独立执行任务"
        onClose={() => {
          if (!triggering) setTriggerOpen(false);
        }}
        size="md"
        closeOnOverlay={!triggering}
        footer={
          <>
            <Button variant="secondary" onClick={() => setTriggerOpen(false)} disabled={triggering}>
              取消
            </Button>
            <Button variant="primary" icon="play" loading={triggering} onClick={() => void submitTrigger()}>
              确认触发
            </Button>
          </>
        }
      >
        <form className="form-stack" onSubmit={saveTrigger}>
          {triggerError ? <Callout tone="error" title="触发失败">{triggerError}</Callout> : null}
          <Callout tone="info" title="触发说明">
            未指定设备时由 ui-execution 按设备 FIFO 队列自动分配；目标标签会缩小设备候选范围。
          </Callout>
          <Field
            label="目标设备"
            htmlFor="trigger-device"
            hint={devicesError ? `设备列表加载失败：${devicesError}；可不指定设备` : '可不指定，由调度器自动选择'}
          >
            <Select
              id="trigger-device"
              value={triggerForm.targetDeviceId}
              onChange={(event) => setTriggerForm((prev) => ({ ...prev, targetDeviceId: event.target.value }))}
            >
              <option value="">自动选择设备</option>
              {devices.map((device) => (
                <option key={device.deviceId} value={device.deviceId}>
                  {device.serial} · {metaOf(DEVICE_STATUS_META, device.status).label}
                  {device.name ? ` · ${device.name}` : ''}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="矩阵设备（可多选）" htmlFor="trigger-devices"
                 hint="选中多台时，每个 UI 用例会为每台设备各生成一个运行项；不选则使用上方目标设备/自动分配">
            <select
              id="trigger-devices"
              multiple
              className="input"
              style={{ minHeight: 96 }}
              value={triggerForm.targetDeviceIds}
              onChange={(event) => setTriggerForm((prev) => ({
                ...prev,
                targetDeviceIds: Array.from(event.target.selectedOptions).map((option) => option.value),
              }))}
            >
              {devices.filter((device) => device.online).map((device) => (
                <option key={device.deviceId} value={device.deviceId}>
                  {device.serial} · {device.name || device.model}
                </option>
              ))}
            </select>
          </Field>
          <Field label="目标标签" htmlFor="trigger-tags" hint="多个标签用英文逗号或空格分隔，可留空">
            <Input
              id="trigger-tags"
              placeholder="例如：android, pixel"
              value={triggerForm.targetTags}
              onChange={(event) => setTriggerForm((prev) => ({ ...prev, targetTags: event.target.value }))}
            />
          </Field>
          <div className="form-grid-2">
            <Field label="总超时（秒）" htmlFor="trigger-timeout" required>
              <Input
                id="trigger-timeout"
                type="number"
                min={1}
                value={triggerForm.timeoutSec}
                onChange={(event) => setTriggerForm((prev) => ({ ...prev, timeoutSec: event.target.value }))}
              />
            </Field>
            <Field label="单步超时（秒）" htmlFor="trigger-step-timeout" required>
              <Input
                id="trigger-step-timeout"
                type="number"
                min={1}
                value={triggerForm.stepTimeoutSec}
                onChange={(event) => setTriggerForm((prev) => ({ ...prev, stepTimeoutSec: event.target.value }))}
              />
            </Field>
          </div>
          <div className="trigger-preview">
            <Pill tone="blue">将触发 {planCases.length} 条用例</Pill>
            <Pill tone="purple">总超时 {triggerForm.timeoutSec}s</Pill>
            <Pill tone="orange">单步超时 {triggerForm.stepTimeoutSec}s</Pill>
          </div>
          <button type="submit" hidden />
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(removeTarget)}
        title="移除计划用例"
        message={
          removeTarget ? (
            <span>
              确认从计划中移除「<strong>{removeTarget.title}</strong>」？已产生的运行记录不会被删除。
            </span>
          ) : null
        }
        confirmText="移除"
        tone="danger"
        loading={removeLoading}
        onConfirm={() => void confirmRemove()}
        onClose={() => setRemoveTarget(null)}
      />

      <TriggerResultModal
        open={triggerResultOpen}
        result={triggerResult}
        onClose={() => setTriggerResultOpen(false)}
      />
    </div>
  );
}
