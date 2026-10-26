import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { casesApi, requirementsApi, scriptsApi } from '../api';
import type {
  CaseDetail,
  CaseInput,
  CaseStep,
  ExecutionMode,
  ExecutorType,
  SelectorType,
  StepType,
  Requirement,
  ReviewAction,
  ReviewStatus,
  ScriptInfo,
} from '../api/types';
import { canManageCases, canReview, useAuth } from '../auth';
import {
  EXECUTOR_META,
  FAILURE_CATEGORY_META,
  REVIEW_ACTION_META,
  REVIEW_STATUS_META,
  SCRIPT_TYPE_LABEL,
  SOURCE_META,
  metaOf,
} from '../constants';
import { useDebouncedValue } from '../hooks';
import { buildPath, navigate, useHashRoute } from '../router';
import { cx, formatDateTime, safeErrorMessage, truncate } from '../utils';
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
  Pagination,
  Pill,
  Select,
  SkeletonRows,
  StatusPill,
  Textarea,
} from '../components/ui';
import { ConfirmDialog, Drawer, Modal, PromptDialog } from '../components/Modal';
import { toastError, toastSuccess, toastWarning } from '../components/Toast';

const PAGE_SIZE = 10;
const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待评审' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
];

interface CaseFormState {
  title: string;
  given: string;
  when: string;
  then: string;
  requirementId: string;
  scriptId: string;
}

const EMPTY_FORM: CaseFormState = {
  title: '',
  given: '',
  when: '',
  then: '',
  requirementId: '',
  scriptId: '',
};

function availableExecutorsFor(item: CaseDetail): ExecutorType[] {
  if (item.capability?.availableExecutors?.length) return item.capability.availableExecutors;
  return item.script ? ['SCRIPT', 'AI_RUNTIME'] : ['AI_RUNTIME'];
}

function defaultExecutorFor(item: CaseDetail): ExecutorType {
  return item.capability?.defaultExecutor || (item.script ? 'SCRIPT' : 'AI_RUNTIME');
}

function capabilityText(item: CaseDetail): string {
  if (item.executionMode === 'STEPS') return '按用例步骤确定性执行（真机/模拟器）';
  const available = availableExecutorsFor(item);
  if (available.includes('SCRIPT') && available.includes('AI_RUNTIME')) {
    return `传统脚本 + AI Runtime（默认${defaultExecutorFor(item) === 'SCRIPT' ? '传统脚本' : 'AI Runtime'}）`;
  }
  if (available.includes('SCRIPT')) return '仅传统脚本';
  return '仅 AI Runtime（未关联脚本）';
}

function failureInfo(item: CaseDetail): { summary?: string; reason?: string; reportUrl?: string } | null {
  const analysis = item.failureAnalysis;
  let summary: string | undefined;
  let reason: string | undefined;
  let reportUrl: string | undefined = item.failureReportUrl || undefined;

  if (typeof analysis === 'string' && analysis.trim()) {
    const text = analysis.trim();
    if (text.startsWith('{')) {
      try {
        const parsed = JSON.parse(text) as Record<string, unknown>;
        summary = typeof parsed.summary === 'string' ? parsed.summary : text;
        reason = typeof parsed.reasonCategory === 'string' ? parsed.reasonCategory : undefined;
        reportUrl = typeof parsed.reportUrl === 'string' ? parsed.reportUrl : reportUrl;
      } catch {
        summary = text;
      }
    } else {
      summary = text;
    }
  } else if (analysis && typeof analysis === 'object') {
    summary = typeof analysis.summary === 'string' ? analysis.summary : undefined;
    reason = typeof analysis.reasonCategory === 'string' ? analysis.reasonCategory : undefined;
    reportUrl = typeof analysis.reportUrl === 'string' ? analysis.reportUrl : reportUrl;
  }

  if (!summary && !reason && !reportUrl) return null;
  return { summary, reason, reportUrl };
}

function requirementLabel(item: CaseDetail, requirements: Requirement[]): string {
  if (item.requirementTitle) return item.requirementTitle;
  if (!item.requirementId) return '—';
  return requirements.find((requirement) => requirement.id === item.requirementId)?.title || item.requirementId;
}

export function CasesPage() {
  const { user } = useAuth();
  const route = useHashRoute();
  const manage = canManageCases(user);
  const reviewer = canReview(user);

  const [items, setItems] = useState<CaseDetail[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [requirements, setRequirements] = useState<Requirement[]>([]);
  const [scripts, setScripts] = useState<ScriptInfo[]>([]);
  const [metaReady, setMetaReady] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [stepDetail, setStepDetail] = useState<CaseDetail | null>(null);
  const [stepMode, setStepMode] = useState<ExecutionMode>('AI');
  const [stepRows, setStepRows] = useState<CaseStep[]>([]);
  const [stepSaving, setStepSaving] = useState(false);
  const [stepError, setStepError] = useState('');
  const [editing, setEditing] = useState<CaseDetail | null>(null);
  const [form, setForm] = useState<CaseFormState>(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState<Partial<CaseFormState>>({});
  const [formSaving, setFormSaving] = useState(false);
  const [formError, setFormError] = useState('');

  const [detailId, setDetailId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const [rejectTarget, setRejectTarget] = useState<CaseDetail | null>(null);
  const [rejectLoading, setRejectLoading] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<CaseDetail | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [reviewBusy, setReviewBusy] = useState<string | null>(null);

  const [highlightId, setHighlightId] = useState<string | null>(null);
  const [highlightNotice, setHighlightNotice] = useState('');
  const highlightHandled = useRef<string | null>(null);

  const debouncedKeyword = useDebouncedValue(keywordInput, 380);
  const highlightParam = route.query.get('highlight');

  useEffect(() => {
    setKeyword(debouncedKeyword.trim());
    setPage(1);
  }, [debouncedKeyword]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await casesApi.list({
        reviewStatus: status || undefined,
        keyword: keyword || undefined,
        page,
        size: PAGE_SIZE,
      });
      setItems(result.items);
      setTotal(result.total);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [status, keyword, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    void Promise.allSettled([requirementsApi.list(), scriptsApi.list()]).then(([reqRes, scriptRes]) => {
      if (cancelled) return;
      if (reqRes.status === 'fulfilled') setRequirements(reqRes.value.items);
      if (scriptRes.status === 'fulfilled') setScripts(scriptRes.value.items);
      setMetaReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const openDetail = useCallback(async (id: string) => {
    setDetailId(id);
    setDetailOpen(true);
    setDetailLoading(true);
    setDetailError('');
    try {
      const data = await casesApi.get(id);
      setDetail(data);
    } catch (err) {
      setDetail(null);
      setDetailError(safeErrorMessage(err));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  /* 从 AI 分析页跳转：定位并高亮用例 */
  useEffect(() => {
    if (!highlightParam) {
      setHighlightId(null);
      setHighlightNotice('');
      highlightHandled.current = null;
      return;
    }
    setHighlightId(highlightParam);
    const existing = items.find((item) => item.id === highlightParam);
    if (existing) {
      setHighlightNotice('');
      window.setTimeout(() => {
        document.getElementById(`case-row-${highlightParam}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }, 80);
      return;
    }
    if (highlightHandled.current !== highlightParam && !loading && items.length >= 0) {
      highlightHandled.current = highlightParam;
      void casesApi
        .get(highlightParam)
        .then((data) => {
          setHighlightNotice(`已定位用例「${data.title}」，它不在当前筛选结果中。`);
          setDetail(data);
          setDetailId(data.id);
          setDetailOpen(true);
          setDetailError('');
        })
        .catch(() => {
          setHighlightNotice('未能定位目标用例，可能已被删除或当前账号无权限查看。');
        });
    }
  }, [highlightParam, items, loading]);

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormErrors({});
    setFormError('');
    setFormOpen(true);
  };

  const openEdit = (item: CaseDetail) => {
    setEditing(item);
    setForm({
      title: item.title || '',
      given: item.given || '',
      when: item.when || '',
      then: item.then || '',
      requirementId: item.requirementId || '',
      scriptId: item.scriptId || item.script?.id || '',
    });
    setFormErrors({});
    setFormError('');
    setFormOpen(true);
  };

  const validateForm = (): boolean => {
    const errors: Partial<CaseFormState> = {};
    if (!form.title.trim()) errors.title = '请输入用例标题';
    if (!form.given.trim()) errors.given = '请填写 Given（前置条件）';
    if (!form.when.trim()) errors.when = '请填写 When（操作步骤）';
    if (!form.then.trim()) errors.then = '请填写 Then（预期结果）';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const openStepEditor = (item: CaseDetail) => {
    setStepDetail(item);
    setStepMode(item.executionMode || 'AI');
    setStepRows((item.steps || []).map((step) => ({ ...step })));
    setStepError('');
  };

  const addStep = () => {
    setStepRows((prev) => [...prev, {
      stepType: 'CLICK', selectorType: 'TEXT', selector: '', value: '',
      assertValue: '', timeoutMs: 10000, enabled: true,
    }]);
  };

  const updateStep = (index: number, patch: Partial<CaseStep>) => {
    setStepRows((prev) => prev.map((step, i) => (i === index ? { ...step, ...patch } : step)));
  };

  const moveStep = (index: number, delta: number) => {
    setStepRows((prev) => {
      const next = [...prev];
      const target = index + delta;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const saveSteps = async () => {
    if (!stepDetail) return;
    if (stepMode === 'STEPS' && stepRows.length === 0) {
      setStepError('步骤模式至少需要一条步骤');
      return;
    }
    setStepSaving(true);
    setStepError('');
    try {
      await casesApi.update(stepDetail.id, {
        title: stepDetail.title,
        given: stepDetail.given,
        when: stepDetail.when,
        then: stepDetail.then,
        requirementId: stepDetail.requirementId || null,
        scriptId: stepDetail.scriptId || stepDetail.script?.id || null,
        source: stepDetail.source,
        executionMode: stepMode,
        steps: stepRows.map((step, index) => ({ ...step, seq: index + 1 })),
      });
      toastSuccess('执行步骤已保存');
      setStepDetail(null);
      await load();
      await openDetail(stepDetail.id);
    } catch (err) {
      setStepError(safeErrorMessage(err));
    } finally {
      setStepSaving(false);
    }
  };

  const saveCase = async () => {
    if (!validateForm()) return;
    setFormSaving(true);
    setFormError('');
    const payload: CaseInput = {
      title: form.title.trim(),
      given: form.given.trim(),
      when: form.when.trim(),
      then: form.then.trim(),
      requirementId: form.requirementId || null,
      scriptId: form.scriptId || null,
      executionMode: editing?.executionMode || 'AI',
      steps: editing?.steps || [],
    };
    try {
      if (editing) {
        await casesApi.update(editing.id, payload);
        toastSuccess('用例已更新');
      } else {
        await casesApi.create({ ...payload, source: 'MANUAL' });
        toastSuccess('用例已创建，请提交评审');
      }
      setFormOpen(false);
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setFormSaving(false);
    }
  };

  const submitForm = (event: FormEvent) => {
    event.preventDefault();
    void saveCase();
  };

  const runReview = async (item: CaseDetail, action: ReviewAction, comment?: string) => {
    setReviewBusy(`${item.id}:${action}`);
    try {
      await casesApi.review(item.id, action, comment);
      const labels: Record<ReviewAction, string> = {
        SUBMIT: '已提交评审',
        APPROVE: '评审已通过',
        REJECT: '已驳回该用例',
      };
      toastSuccess(labels[action]);
      setRejectTarget(null);
      await load();
      if (detailOpen && detailId === item.id) {
        try {
          setDetail(await casesApi.get(item.id));
        } catch {
          // 详情刷新失败不阻断列表操作
        }
      }
    } catch (err) {
      toastError(safeErrorMessage(err), '评审操作失败');
    } finally {
      setReviewBusy(null);
    }
  };

  const confirmReject = async (comment: string) => {
    if (!rejectTarget) return;
    if (!comment.trim()) {
      toastWarning('请填写驳回原因');
      return;
    }
    setRejectLoading(true);
    await runReview(rejectTarget, 'REJECT', comment.trim());
    setRejectLoading(false);
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await casesApi.remove(deleteTarget.id);
      toastSuccess('用例已删除');
      setDeleteTarget(null);
      if (detailOpen && detailId === deleteTarget.id) setDetailOpen(false);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err), '删除失败');
    } finally {
      setDeleteLoading(false);
    }
  };

  const detailFailure = detail ? failureInfo(detail) : null;
  const activeRequirements = useMemo(
    () => requirements.slice().sort((a, b) => a.title.localeCompare(b.title, 'zh-CN')),
    [requirements],
  );

  const metaLoading = !metaReady;

  return (
    <div className="page-stack">
      <PageHeading
        title="用例管理"
        description="维护 BDD 用例、评审流转与执行能力；AI 生成的草稿必须人工评审通过后才能执行。"
        actions={
          manage ? (
            <Button variant="primary" icon="plus" onClick={openCreate}>
              新建用例
            </Button>
          ) : (
            <Pill tone="gray">只读访问</Pill>
          )
        }
      />

      {!manage ? (
        <Callout tone="info" title="只读模式">
          业务角色可以查看用例与评审结果，但不能新建、编辑或评审用例。
        </Callout>
      ) : null}

      {highlightNotice ? (
        <Callout tone="info" title="用例定位">
          {highlightNotice}
          <div className="callout-inline-action">
            <Button
              size="sm"
              variant="ghost"
              icon="close"
              onClick={() => {
                setHighlightNotice('');
                setHighlightId(null);
                navigate('/cases');
              }}
            >
              清除高亮
            </Button>
          </div>
        </Callout>
      ) : null}

      <Card>
        <div className="filter-bar">
          <div className="filter-fields">
            <div className="filter-item">
              <span className="filter-label">评审状态</span>
              <Select
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value);
                  setPage(1);
                }}
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="filter-item filter-grow">
              <span className="filter-label">关键字</span>
              <Input
                icon="search"
                placeholder="搜索标题、Given / When / Then"
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
            <table className="table table-cases">
              <thead>
                <tr>
                  <th>标题</th>
                  <th>评审状态</th>
                  <th>来源</th>
                  <th>关联脚本</th>
                  <th>可用执行器</th>
                  <th>关联需求</th>
                  <th>更新时间</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <SkeletonRows rows={6} cols={8} />
                ) : error ? (
                  <tr>
                    <td colSpan={8}>
                      <ErrorPanel message={error} onRetry={() => void load()} />
                    </td>
                  </tr>
                ) : items.length === 0 ? (
                  <tr>
                    <td colSpan={8}>
                      <EmptyState
                        icon="cases"
                        title={keyword || status ? '没有匹配的用例' : '还没有用例'}
                        description={
                          keyword || status
                            ? '尝试调整筛选条件，或清空关键字后重新搜索'
                            : '点击右上角「新建用例」，或先到 AI 分析页生成 BDD 草稿'
                        }
                        action={
                          manage ? (
                            <Button size="sm" variant="primary" icon="plus" onClick={openCreate}>
                              新建用例
                            </Button>
                          ) : undefined
                        }
                      />
                    </td>
                  </tr>
                ) : (
                  items.map((item) => {
                    const available = availableExecutorsFor(item);
                    const isHighlight = highlightId === item.id;
                    return (
                      <tr
                        key={item.id}
                        id={`case-row-${item.id}`}
                        className={cx('row-clickable', isHighlight && 'is-highlight')}
                        onClick={() => void openDetail(item.id)}
                      >
                        <td>
                          <div className="cell-title">{item.title}</div>
                          <div className="cell-sub mono">{item.id}</div>
                        </td>
                        <td>
                          <StatusPill meta={metaOf(REVIEW_STATUS_META, item.reviewStatus)} />
                        </td>
                        <td>
                          <StatusPill meta={metaOf(SOURCE_META, item.source)} dot={false} />
                        </td>
                        <td>
                          {item.script ? (
                            <div>
                              <div className="cell-title sm">{truncate(item.script.name, 28)}</div>
                              <div className="cell-sub">
                                {SCRIPT_TYPE_LABEL[item.script.scriptType] || item.script.scriptType}
                              </div>
                            </div>
                          ) : (
                            <span className="text-muted">无</span>
                          )}
                        </td>
                        <td>
                          <div className="pill-row">
                            {available.map((executor) => (
                              <StatusPill key={executor} meta={metaOf(EXECUTOR_META, executor)} dot={false} />
                            ))}
                          </div>
                        </td>
                        <td className="td-requirement">{requirementLabel(item, requirements)}</td>
                        <td className="text-muted">{formatDateTime(item.updatedAt || item.createdAt)}</td>
                        <td className="td-right td-actions" onClick={(event) => event.stopPropagation()}>
                          <div className="row-actions">
                            <IconButton icon="eye" label="查看详情" onClick={() => void openDetail(item.id)} />
                            {manage ? (
                              <IconButton icon="edit" label="编辑用例" onClick={() => openEdit(item)} />
                            ) : null}
                            {reviewer && (item.reviewStatus === 'DRAFT' || item.reviewStatus === 'REJECTED') ? (
                              <Button
                                size="sm"
                                variant="secondary"
                                loading={reviewBusy === `${item.id}:SUBMIT`}
                                disabled={Boolean(reviewBusy) && reviewBusy !== `${item.id}:SUBMIT`}
                                onClick={() => void runReview(item, 'SUBMIT')}
                              >
                                提交评审
                              </Button>
                            ) : null}
                            {reviewer && item.reviewStatus === 'PENDING' ? (
                              <>
                                <Button
                                  size="sm"
                                  variant="success"
                                  loading={reviewBusy === `${item.id}:APPROVE`}
                                  disabled={Boolean(reviewBusy) && reviewBusy !== `${item.id}:APPROVE`}
                                  onClick={() => void runReview(item, 'APPROVE')}
                                >
                                  通过
                                </Button>
                                <Button
                                  size="sm"
                                  variant="danger"
                                  disabled={Boolean(reviewBusy)}
                                  onClick={() => setRejectTarget(item)}
                                >
                                  驳回
                                </Button>
                              </>
                            ) : null}
                            {manage ? (
                              <IconButton
                                icon="trash"
                                label="删除用例"
                                tone="danger"
                                onClick={() => setDeleteTarget(item)}
                              />
                            ) : null}
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
            <Pagination
              page={page}
              size={PAGE_SIZE}
              total={total}
              disabled={loading}
              onChange={(next) => setPage(next)}
            />
          </div>
        </div>
      </Card>

      {/* 新建 / 编辑用例 */}
      <Modal
        open={formOpen}
        title={editing ? '编辑用例' : '新建用例'}
        description="用例采用 Given / When / Then 结构；可关联一个脚本工程，也可仅由 AI Runtime 执行。"
        onClose={() => {
          if (!formSaving) setFormOpen(false);
        }}
        size="lg"
        closeOnOverlay={!formSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={formSaving}>
              取消
            </Button>
            <Button variant="primary" loading={formSaving} onClick={() => void saveCase()}>
              {editing ? '保存修改' : '创建用例'}
            </Button>
          </>
        }
      >
        <form className="form-stack" onSubmit={submitForm}>
          {formError ? <Callout tone="error" title="保存失败">{formError}</Callout> : null}
          <Field label="用例标题" htmlFor="case-title" required error={formErrors.title}>
            <Input
              id="case-title"
              placeholder="例如：用户使用正确凭据登录"
              value={form.title}
              invalid={Boolean(formErrors.title)}
              onChange={(event) => {
                setForm((prev) => ({ ...prev, title: event.target.value }));
                setFormErrors((prev) => ({ ...prev, title: undefined }));
              }}
            />
          </Field>

          <div className="form-grid-2">
            <Field label="关联需求" htmlFor="case-requirement" hint="可不关联；关联后便于追溯">
              <Select
                id="case-requirement"
                value={form.requirementId}
                disabled={metaLoading}
                onChange={(event) => setForm((prev) => ({ ...prev, requirementId: event.target.value }))}
              >
                <option value="">不关联需求</option>
                {activeRequirements.map((requirement) => (
                  <option key={requirement.id} value={requirement.id}>
                    {requirement.title}
                  </option>
                ))}
              </Select>
            </Field>
            <Field
              label="关联脚本"
              htmlFor="case-script"
              hint={
                scripts.length === 0 && metaReady
                  ? '暂无可选脚本；不关联脚本时仅支持 AI Runtime'
                  : '可留空，不关联脚本时只能使用 AI Runtime'
              }
            >
              <Select
                id="case-script"
                value={form.scriptId}
                disabled={metaLoading}
                onChange={(event) => setForm((prev) => ({ ...prev, scriptId: event.target.value }))}
              >
                <option value="">无脚本（仅 AI Runtime）</option>
                {scripts.map((script) => (
                  <option key={script.id} value={script.id}>
                    {script.name} · {SCRIPT_TYPE_LABEL[script.scriptType] || script.scriptType}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

          <div className="bdd-form-grid">
            <Field label="Given 前置条件" htmlFor="case-given" required error={formErrors.given}>
              <Textarea
                id="case-given"
                rows={4}
                placeholder="例如：已打开 App 且处于未登录状态"
                value={form.given}
                invalid={Boolean(formErrors.given)}
                onChange={(event) => {
                  setForm((prev) => ({ ...prev, given: event.target.value }));
                  setFormErrors((prev) => ({ ...prev, given: undefined }));
                }}
              />
            </Field>
            <Field label="When 操作步骤" htmlFor="case-when" required error={formErrors.when}>
              <Textarea
                id="case-when"
                rows={4}
                placeholder="例如：输入用户名 tester 和密码 secret 并点击登录"
                value={form.when}
                invalid={Boolean(formErrors.when)}
                onChange={(event) => {
                  setForm((prev) => ({ ...prev, when: event.target.value }));
                  setFormErrors((prev) => ({ ...prev, when: undefined }));
                }}
              />
            </Field>
            <Field label="Then 预期结果" htmlFor="case-then" required error={formErrors.then}>
              <Textarea
                id="case-then"
                rows={4}
                placeholder="例如：进入首页并显示「登录成功」"
                value={form.then}
                invalid={Boolean(formErrors.then)}
                onChange={(event) => {
                  setForm((prev) => ({ ...prev, then: event.target.value }));
                  setFormErrors((prev) => ({ ...prev, then: undefined }));
                }}
              />
            </Field>
          </div>
          <button type="submit" hidden />
        </form>
      </Modal>

      {/* 用例详情 */}
      <Drawer
        open={detailOpen}
        title={
          <span className="drawer-title-row">
            {detail?.title || '用例详情'}
            {detail ? <StatusPill meta={metaOf(REVIEW_STATUS_META, detail.reviewStatus)} /> : null}
          </span>
        }
        description={detail ? `用例 ID：${detail.id}` : undefined}
        onClose={() => setDetailOpen(false)}
        width={760}
        footer={
          detail ? (
            <>
              <Button variant="secondary" onClick={() => setDetailOpen(false)}>
                关闭
              </Button>
              {reviewer && (detail.reviewStatus === 'DRAFT' || detail.reviewStatus === 'REJECTED') ? (
                <Button
                  variant="primary"
                  icon="check"
                  loading={reviewBusy === `${detail.id}:SUBMIT`}
                  onClick={() => void runReview(detail, 'SUBMIT')}
                >
                  提交评审
                </Button>
              ) : null}
              {reviewer && detail.reviewStatus === 'PENDING' ? (
                <>
                  <Button
                    variant="danger"
                    icon="close"
                    onClick={() => setRejectTarget(detail)}
                    disabled={Boolean(reviewBusy)}
                  >
                    驳回
                  </Button>
                  <Button
                    variant="success"
                    icon="check"
                    loading={reviewBusy === `${detail.id}:APPROVE`}
                    onClick={() => void runReview(detail, 'APPROVE')}
                  >
                    通过
                  </Button>
                </>
              ) : null}
              {manage ? (
                <Button
                  variant="secondary"
                  icon="edit"
                  onClick={() => {
                    setDetailOpen(false);
                    openEdit(detail);
                  }}
                >
                  编辑
                </Button>
              ) : null}
            </>
          ) : null
        }
      >
        {detailLoading ? (
          <LoadingPanel text="正在加载用例详情…" />
        ) : detailError ? (
          <ErrorPanel
            message={detailError}
            onRetry={() => {
              if (detailId) void openDetail(detailId);
            }}
          />
        ) : detail ? (
          <div className="drawer-stack">
            <div className="detail-badges">
              <StatusPill meta={metaOf(SOURCE_META, detail.source)} dot={false} />
              {detail.modelId ? <Pill tone="purple">模型：{detail.modelId}</Pill> : null}
              <Pill tone="gray">关联需求：{requirementLabel(detail, requirements)}</Pill>
            </div>

            <section>
              <h4 className="detail-section-title">BDD 场景</h4>
              <div className="bdd-grid">
                <div className="bdd-col bdd-given">
                  <span className="bdd-tag">Given</span>
                  <p>{detail.given || '—'}</p>
                </div>
                <div className="bdd-col bdd-when">
                  <span className="bdd-tag">When</span>
                  <p>{detail.when || '—'}</p>
                </div>
                <div className="bdd-col bdd-then">
                  <span className="bdd-tag">Then</span>
                  <p>{detail.then || '—'}</p>
                </div>
              </div>
            </section>

            <section>
              <h4 className="detail-section-title">执行步骤</h4>
              <div className="capability-row">
                <span className="capability-label">执行模式</span>
                <Pill tone={detail.executionMode === 'STEPS' ? 'blue' : 'purple'}>
                  {detail.executionMode === 'STEPS' ? '步骤化执行' : 'AI Runtime'}
                </Pill>
                <span className="capability-text">
                  {detail.executionMode === 'STEPS'
                    ? `${detail.steps?.length || 0} 条确定性步骤`
                    : '按 BDD 由 AI Runtime 决策执行'}
                </span>
                {manage ? (
                  <Button size="sm" variant="secondary" onClick={() => openStepEditor(detail)}>编辑步骤</Button>
                ) : null}
              </div>
              {detail.executionMode === 'STEPS' && detail.steps?.length ? (
                <ol className="step-list">
                  {detail.steps.map((step) => (
                    <li key={step.id || step.seq}>
                      <span className="step-seq">{step.seq}</span>
                      <Pill tone="blue">{step.stepType}</Pill>
                      <span className="mono">{step.selectorType || ''} {step.selector || ''}</span>
                      {step.value ? <span className="text-muted">= {step.value}</span> : null}
                      {step.assertValue ? <span className="text-muted">期望 {step.assertValue}</span> : null}
                    </li>
                  ))}
                </ol>
              ) : null}
            </section>

            <section>
              <h4 className="detail-section-title">执行能力</h4>
              {detail.script ? (
                <div className="script-card">
                  <span className="script-icon">
                    <Icon name="code" size={18} />
                  </span>
                  <div className="script-main">
                    <div className="script-name">{detail.script.name}</div>
                    <div className="script-meta">
                      类型：{SCRIPT_TYPE_LABEL[detail.script.scriptType] || detail.script.scriptType}
                      {detail.script.entryCommand ? ` · 入口命令：${detail.script.entryCommand}` : ''}
                    </div>
                  </div>
                  <Pill tone="blue">传统脚本</Pill>
                </div>
              ) : (
                <div className="script-card is-empty">
                  <span className="script-icon">
                    <Icon name="ai" size={18} />
                  </span>
                  <div className="script-main">
                    <div className="script-name">未关联脚本工程</div>
                    <div className="script-meta">该用例只能由 AI Runtime 执行，执行时按视觉模型决策</div>
                  </div>
                  <Pill tone="purple">仅 AI Runtime</Pill>
                </div>
              )}
              <div className="capability-row">
                <span className="capability-label">可用执行器</span>
                <div className="pill-row">
                  {availableExecutorsFor(detail).map((executor) => (
                    <StatusPill key={executor} meta={metaOf(EXECUTOR_META, executor)} dot={false} />
                  ))}
                </div>
                <span className="capability-text">{capabilityText(detail)}</span>
              </div>
              <div className="capability-row">
                <span className="capability-label">默认执行器</span>
                <StatusPill meta={metaOf(EXECUTOR_META, defaultExecutorFor(detail))} dot={false} />
                <span className="capability-text">
                  计划触发时若未覆盖执行器，按路由规则自动选择。
                </span>
              </div>
            </section>

            <section>
              <h4 className="detail-section-title">评审记录</h4>
              {detail.reviews && detail.reviews.length > 0 ? (
                <ol className="timeline">
                  {detail.reviews.map((review) => {
                    const actionMeta = metaOf(REVIEW_ACTION_META, review.action);
                    return (
                      <li key={review.id} className="timeline-item">
                        <span className={cx('timeline-dot', `tone-bg-${actionMeta.tone}`)} />
                        <div className="timeline-content">
                          <div className="timeline-head">
                            <StatusPill meta={actionMeta} dot={false} />
                            <span className="timeline-actor">{review.actor || '—'}</span>
                            <span className="timeline-time">{formatDateTime(review.createdAt)}</span>
                          </div>
                          {review.comment ? <p className="timeline-comment">{review.comment}</p> : null}
                        </div>
                      </li>
                    );
                  })}
                </ol>
              ) : (
                <EmptyState compact icon="audit" title="暂无评审记录" description="提交评审后，审批动作与意见会记录在这里" />
              )}
            </section>

            {detailFailure ? (
              <section>
                <h4 className="detail-section-title">失败分析</h4>
                <div className="failure-analysis-card">
                  <div className="failure-analysis-head">
                    <span className="failure-analysis-icon">
                      <Icon name="warning" size={17} />
                    </span>
                    <div className="failure-analysis-title">
                      {detailFailure.reason
                        ? metaOf(FAILURE_CATEGORY_META, detailFailure.reason).label
                        : '最近一次失败'}
                    </div>
                    {detailFailure.reason ? (
                      <StatusPill meta={metaOf(FAILURE_CATEGORY_META, detailFailure.reason)} dot={false} />
                    ) : null}
                  </div>
                  <p className="failure-analysis-summary">
                    {detailFailure.summary || '平台已记录失败任务，但未提供摘要。'}
                  </p>
                  <div className="failure-analysis-actions">
                    {detailFailure.reportUrl ? (
                      <a className="btn btn-secondary btn-sm" href={detailFailure.reportUrl} target="_blank" rel="noreferrer">
                        <Icon name="external" size={14} />
                        打开失败报告
                      </a>
                    ) : null}
                    <span className="failure-analysis-note">
                      <Icon name="info" size={13} />
                      由平台自动写入，不覆盖人工备注
                    </span>
                  </div>
                </div>
              </section>
            ) : null}

            <section>
              <h4 className="detail-section-title">元信息</h4>
              <div className="detail-meta-grid">
                <div>
                  <span>创建人</span>
                  <strong>{detail.createdBy || '—'}</strong>
                </div>
                <div>
                  <span>创建时间</span>
                  <strong>{formatDateTime(detail.createdAt)}</strong>
                </div>
                <div>
                  <span>更新时间</span>
                  <strong>{formatDateTime(detail.updatedAt)}</strong>
                </div>
                <div>
                  <span>来源</span>
                  <strong>{metaOf(SOURCE_META, detail.source).label}</strong>
                </div>
              </div>
            </section>
          </div>
        ) : null}
      </Drawer>

      <PromptDialog
        open={Boolean(rejectTarget)}
        title="驳回用例"
        message={
          rejectTarget ? (
            <span>
              即将驳回「<strong>{rejectTarget.title}</strong>」。驳回后用例可修改并重新提交评审。
            </span>
          ) : null
        }
        label="驳回原因"
        placeholder="请说明不通过的原因，便于修改后重新提交"
        confirmText="确认驳回"
        tone="danger"
        loading={rejectLoading}
        onClose={() => setRejectTarget(null)}
        onConfirm={(value) => void confirmReject(value)}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="删除用例"
        message={
          deleteTarget ? (
            <span>
              确认删除用例「<strong>{deleteTarget.title}</strong>」？该操作不可撤销；若已被未完成计划引用，后端会拒绝删除。
            </span>
          ) : null
        }
        confirmText="删除"
        tone="danger"
        loading={deleteLoading}
        onConfirm={() => void confirmDelete()}
        onClose={() => setDeleteTarget(null)}
      />
    </div>
  );
}

export function openCaseFromAi(caseId: string) {
  navigate(buildPath('/cases', { highlight: caseId }));
}
