import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { plansApi } from '../api';
import type { Plan, TriggerResponse } from '../api/types';
import { PLAN_RUN_STATUS_META, metaOf } from '../constants';
import { useDebouncedValue } from '../hooks';
import { navigate } from '../router';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Card,
  EmptyState,
  ErrorPanel,
  Field,
  IconButton,
  Input,
  PageHeading,
  Pagination,
  SkeletonRows,
  StatusPill,
  Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { TriggerResultModal } from '../components/TriggerResultModal';
import { toastError, toastSuccess } from '../components/Toast';

const PAGE_SIZE = 10;

interface Enrichment {
  caseCount?: number;
  runCount?: number;
  lastStatus?: string;
  lastRunAt?: string;
}

export function PlansPage() {
  const [items, setItems] = useState<Plan[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [enrichment, setEnrichment] = useState<Record<string, Enrichment>>({});
  const [enrichLoading, setEnrichLoading] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({ name: '', description: '' });
  const [createErrors, setCreateErrors] = useState<{ name?: string }>({});
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');

  const [deleteTarget, setDeleteTarget] = useState<Plan | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const [triggerTarget, setTriggerTarget] = useState<Plan | null>(null);
  const [triggerLoading, setTriggerLoading] = useState(false);
  const [triggerResult, setTriggerResult] = useState<TriggerResponse | null>(null);
  const [triggerResultOpen, setTriggerResultOpen] = useState(false);

  const debouncedKeyword = useDebouncedValue(keywordInput, 380);
  useEffect(() => {
    setKeyword(debouncedKeyword.trim());
    setPage(1);
  }, [debouncedKeyword]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await plansApi.list(keyword, page, PAGE_SIZE);
      setItems(data.items);
      setTotal(data.total);
      setEnrichment({});
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [keyword, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (items.length === 0) return undefined;
    const missing = items.filter(
      (plan) =>
        plan.caseCount === undefined ||
        plan.runCount === undefined ||
        (!plan.lastRunStatus && !plan.lastStatus),
    );
    if (missing.length === 0) {
      setEnrichLoading(false);
      return undefined;
    }
    let cancelled = false;
    setEnrichLoading(true);
    void Promise.allSettled(missing.map((plan) => plansApi.get(plan.id))).then((results) => {
      if (cancelled) return;
      const next: Record<string, Enrichment> = {};
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          const detail = result.value;
          const latestRun = detail.runs?.[0];
          next[missing[index].id] = {
            caseCount: detail.cases?.length ?? 0,
            runCount: detail.runs?.length ?? 0,
            lastStatus: latestRun?.status,
            lastRunAt: latestRun?.startedAt || latestRun?.finishedAt || undefined,
          };
        }
      });
      setEnrichment((prev) => ({ ...prev, ...next }));
      setEnrichLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [items]);

  const openCreate = () => {
    setCreateForm({ name: '', description: '' });
    setCreateErrors({});
    setCreateError('');
    setCreateOpen(true);
  };

  const saveCreate = async () => {
    const errors: { name?: string } = {};
    if (!createForm.name.trim()) errors.name = '请输入计划名称';
    setCreateErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setCreating(true);
    setCreateError('');
    try {
      await plansApi.create({ name: createForm.name.trim(), description: createForm.description.trim() });
      toastSuccess('测试计划已创建');
      setCreateOpen(false);
      await load();
    } catch (err) {
      setCreateError(safeErrorMessage(err));
    } finally {
      setCreating(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await plansApi.remove(deleteTarget.id);
      toastSuccess('测试计划已删除');
      setDeleteTarget(null);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err), '删除失败');
    } finally {
      setDeleteLoading(false);
    }
  };

  const confirmTrigger = async () => {
    if (!triggerTarget) return;
    setTriggerLoading(true);
    try {
      const result = await plansApi.trigger(triggerTarget.id, {
        targetDeviceId: null,
        targetTags: [],
        timeoutSec: 120,
        stepTimeoutSec: 30,
      });
      setTriggerResult(result);
      setTriggerResultOpen(true);
      setTriggerTarget(null);
      toastSuccess(`已创建运行 ${result.runId}`, '计划已触发');
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err), '触发失败');
    } finally {
      setTriggerLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <PageHeading
        title="测试计划"
        description="把已通过的用例组合成计划，按执行器路由触发运行并统一查看报告。"
        actions={
          <Button variant="primary" icon="plus" onClick={openCreate}>
            新建计划
          </Button>
        }
      />

      <Card>
        <div className="filter-bar">
          <div className="filter-fields">
            <div className="filter-item filter-grow">
              <span className="filter-label">关键字</span>
              <Input
                icon="search"
                placeholder="搜索计划名称或描述"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    setKeyword(keywordInput.trim());
                    setPage(1);
                  }
                }}
              />
            </div>
          </div>
          <div className="filter-actions">
            <Button
              size="sm"
              variant="ghost"
              icon="refresh"
              onClick={() => {
                setKeyword(keywordInput.trim());
                setPage(1);
                void load();
              }}
            >
              刷新
            </Button>
          </div>
        </div>

        <div className="card-body no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>计划名称</th>
                  <th>用例数</th>
                  <th>运行次数</th>
                  <th>最近状态</th>
                  <th>创建时间</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <SkeletonRows rows={5} cols={6} />
                ) : error ? (
                  <tr>
                    <td colSpan={6}>
                      <ErrorPanel message={error} onRetry={() => void load()} />
                    </td>
                  </tr>
                ) : items.length === 0 ? (
                  <tr>
                    <td colSpan={6}>
                      <EmptyState
                        icon="plans"
                        title={keyword ? '没有匹配的测试计划' : '还没有测试计划'}
                        description={
                          keyword
                            ? '尝试更换关键字，或清空搜索条件'
                            : '点击右上角「新建计划」，添加用例后即可触发运行'
                        }
                        action={
                          !keyword ? (
                            <Button size="sm" variant="primary" icon="plus" onClick={openCreate}>
                              新建计划
                            </Button>
                          ) : undefined
                        }
                      />
                    </td>
                  </tr>
                ) : (
                  items.map((plan) => {
                    const extra = enrichment[plan.id] || {};
                    const caseCount = plan.caseCount ?? extra.caseCount;
                    const runCount = plan.runCount ?? extra.runCount;
                    const lastStatus = plan.lastRunStatus || plan.lastStatus || extra.lastStatus;
                    const lastAt = extra.lastRunAt;
                    return (
                      <tr key={plan.id} className="row-clickable" onClick={() => navigate(`/plans/${plan.id}`)}>
                        <td>
                          <div className="cell-title">{plan.name}</div>
                          <div className="cell-sub">
                            {plan.description ? plan.description : `计划 ID：${plan.id}`}
                          </div>
                        </td>
                        <td>
                          {caseCount === undefined && enrichLoading ? (
                            <span className="skeleton sk-w-30" />
                          ) : (
                            <span className="cell-strong">{caseCount ?? '—'}</span>
                          )}
                        </td>
                        <td>
                          {runCount === undefined && enrichLoading ? (
                            <span className="skeleton sk-w-30" />
                          ) : (
                            runCount ?? '—'
                          )}
                        </td>
                        <td>
                          {lastStatus ? (
                            <div>
                              <StatusPill meta={metaOf(PLAN_RUN_STATUS_META, lastStatus)} />
                              {lastAt ? <div className="cell-sub">{formatDateTime(lastAt)}</div> : null}
                            </div>
                          ) : (
                            <span className="text-muted">未运行</span>
                          )}
                        </td>
                        <td className="text-muted">{formatDateTime(plan.createdAt)}</td>
                        <td className="td-right td-actions" onClick={(event) => event.stopPropagation()}>
                          <div className="row-actions">
                            <Button
                              size="sm"
                              variant="secondary"
                              icon="eye"
                              onClick={() => navigate(`/plans/${plan.id}`)}
                            >
                              打开
                            </Button>
                            <Button
                              size="sm"
                              variant="primary"
                              icon="play"
                              onClick={() => setTriggerTarget(plan)}
                            >
                              触发
                            </Button>
                            <IconButton
                              icon="trash"
                              label="删除计划"
                              tone="danger"
                              onClick={() => setDeleteTarget(plan)}
                            />
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <Pagination page={page} size={PAGE_SIZE} total={total} disabled={loading} onChange={setPage} />
          </div>
        </div>
      </Card>

      <Modal
        open={createOpen}
        title="新建测试计划"
        description="创建后可添加已通过的用例，并按需覆盖执行器。"
        onClose={() => {
          if (!creating) setCreateOpen(false);
        }}
        size="sm"
        closeOnOverlay={!creating}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={creating}>
              取消
            </Button>
            <Button variant="primary" loading={creating} onClick={() => void saveCreate()}>
              创建计划
            </Button>
          </>
        }
      >
        <form
          className="form-stack"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            void saveCreate();
          }}
        >
          {createError ? <div className="field-error">{createError}</div> : null}
          <Field label="计划名称" htmlFor="plan-name" required error={createErrors.name}>
            <Input
              id="plan-name"
              autoFocus
              placeholder="例如：登录核心链路回归"
              value={createForm.name}
              invalid={Boolean(createErrors.name)}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, name: event.target.value }))}
            />
          </Field>
          <Field label="描述" htmlFor="plan-desc" hint="可选，说明计划覆盖范围与执行时机">
            <Textarea
              id="plan-desc"
              rows={3}
              placeholder="例如：每个版本回归一次，覆盖登录、下单与支付"
              value={createForm.description}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </Field>
          <button type="submit" hidden />
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="删除测试计划"
        message={
          deleteTarget ? (
            <span>
              确认删除计划「<strong>{deleteTarget.name}</strong>」？计划下的运行记录将不再展示，历史运行数据由后端决定是否保留。
            </span>
          ) : null
        }
        confirmText="删除"
        tone="danger"
        loading={deleteLoading}
        onConfirm={() => void confirmDelete()}
        onClose={() => setDeleteTarget(null)}
      />

      <ConfirmDialog
        open={Boolean(triggerTarget)}
        title="触发测试计划"
        message={
          triggerTarget ? (
            <span>
              将使用默认参数触发「<strong>{triggerTarget.name}</strong>」：目标设备自动选择、总超时 120 秒、单步超时 30 秒。
              每个已通过的用例都会创建独立任务，单个失败不会中断计划。
            </span>
          ) : null
        }
        confirmText="立即触发"
        tone="primary"
        loading={triggerLoading}
        onConfirm={() => void confirmTrigger()}
        onClose={() => setTriggerTarget(null)}
      >
        <div className="confirm-hint">
          <Icon name="info" size={14} />
          需要指定设备、标签或自定义超时？请打开计划详情后使用「触发」。
        </div>
      </ConfirmDialog>

      <TriggerResultModal
        open={triggerResultOpen}
        result={triggerResult}
        onClose={() => setTriggerResultOpen(false)}
      />
    </div>
  );
}
