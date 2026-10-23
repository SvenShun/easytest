import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { aiApi, requirementsApi } from '../api';
import { ApiError } from '../api/client';
import type { AiDraftResponse, AiJob, Requirement } from '../api/types';
import { JOB_STATUS_META, REVIEW_STATUS_META, metaOf } from '../constants';
import { buildPath, navigate } from '../router';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Icon } from '../components/icons';
import {
  Button,
  Callout,
  Card,
  CardHead,
  EmptyState,
  ErrorPanel,
  Field,
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
import { Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const HISTORY_SIZE = 8;

export function AiPage() {
  const [requirements, setRequirements] = useState<Requirement[]>([]);
  const [requirementsError, setRequirementsError] = useState('');
  const [requirementId, setRequirementId] = useState('');
  const [requirementText, setRequirementText] = useState('');
  const [sampleCount, setSampleCount] = useState('3');
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState('');
  const [formError, setFormError] = useState('');
  const [result, setResult] = useState<AiDraftResponse | null>(null);

  const [jobs, setJobs] = useState<AiJob[]>([]);
  const [jobsTotal, setJobsTotal] = useState(0);
  const [jobsPage, setJobsPage] = useState(1);
  const [jobsLoading, setJobsLoading] = useState(true);
  const [jobsError, setJobsError] = useState('');

  const [jobDetailOpen, setJobDetailOpen] = useState(false);
  const [jobDetail, setJobDetail] = useState<AiJob | null>(null);
  const [jobDetailLoading, setJobDetailLoading] = useState(false);
  const [jobDetailError, setJobDetailError] = useState('');

  useEffect(() => {
    requirementsApi
      .list()
      .then((data) => setRequirements(data.items))
      .catch((err) => setRequirementsError(safeErrorMessage(err)));
  }, []);

  const loadJobs = useCallback(async () => {
    setJobsLoading(true);
    setJobsError('');
    try {
      const data = await aiApi.jobs(jobsPage, HISTORY_SIZE);
      setJobs(data.items);
      setJobsTotal(data.total);
    } catch (err) {
      setJobsError(safeErrorMessage(err));
    } finally {
      setJobsLoading(false);
    }
  }, [jobsPage]);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  const selectRequirement = (id: string) => {
    setRequirementId(id);
    const found = requirements.find((item) => item.id === id);
    if (found) setRequirementText(found.description || found.title || '');
  };

  const generate = async (event: FormEvent) => {
    event.preventDefault();
    setFormError('');
    setGenerateError('');
    const text = requirementText.trim();
    if (!requirementId && !text) {
      setFormError('请选择需求，或直接输入需求文本');
      return;
    }
    const count = Number(sampleCount);
    if (!Number.isFinite(count) || count < 1 || count > 10) {
      setFormError('样本数量需在 1 - 10 之间');
      return;
    }
    setGenerating(true);
    try {
      const data = await aiApi.generate({
        requirementId: requirementId || null,
        requirementText: text,
        sampleCount: count,
      });
      setResult(data);
      if (data.drafts?.length) {
        toastSuccess(`已生成 ${data.drafts.length} 条 BDD 草稿，等待人工评审`);
      } else {
        toastError('任务完成，但未生成任何草稿', '生成结果为空');
      }
      await loadJobs();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'MODEL_NOT_CONFIGURED') {
        setGenerateError('文本生成模型未配置，请联系管理员在「系统设置 → 模型配置」中配置后重试。');
      } else {
        setGenerateError(safeErrorMessage(err));
      }
    } finally {
      setGenerating(false);
    }
  };

  const openJobDetail = async (job: AiJob) => {
    setJobDetailOpen(true);
    setJobDetail(job);
    setJobDetailLoading(true);
    setJobDetailError('');
    try {
      const data = await aiApi.job(job.id);
      setJobDetail(data);
    } catch (err) {
      setJobDetailError(safeErrorMessage(err));
    } finally {
      setJobDetailLoading(false);
    }
  };

  const goReview = (caseId: string) => {
    navigate(buildPath('/cases', { highlight: caseId }));
  };

  return (
    <div className="page-stack">
      <PageHeading
        title="AI 分析"
        description="从需求文本生成结构化 BDD 草稿，进入用例库后必须经过人工评审才能被执行。"
        actions={<Pill tone="purple">文本模型驱动</Pill>}
      />

      <div className="ai-layout">
        <Card className="ai-form-card">
          <CardHead title="生成 BDD 草稿" description="选择已有需求，或直接粘贴需求描述" icon="ai" />
          <form className="card-body form-stack" onSubmit={generate}>
            <Field
              label="选择需求"
              htmlFor="ai-requirement"
              hint={requirementsError ? `需求列表加载失败：${requirementsError}` : '可与需求库关联，便于后续追溯'}
            >
              <Select
                id="ai-requirement"
                value={requirementId}
                disabled={requirements.length === 0}
                onChange={(event) => selectRequirement(event.target.value)}
              >
                <option value="">不关联需求（仅输入文本）</option>
                {requirements.map((requirement) => (
                  <option key={requirement.id} value={requirement.id}>
                    {requirement.title}
                  </option>
                ))}
              </Select>
            </Field>

            <Field
              label="需求文本"
              htmlFor="ai-requirement-text"
              required
              hint="建议包含业务目标、操作路径与预期结果，描述越完整草稿质量越高"
            >
              <Textarea
                id="ai-requirement-text"
                rows={7}
                placeholder="例如：用户可以使用用户名和密码登录。登录成功后进入首页；连续 5 次失败后账号锁定 10 分钟。"
                value={requirementText}
                invalid={Boolean(formError)}
                onChange={(event) => setRequirementText(event.target.value)}
              />
            </Field>

            <Field label="样本数量" htmlFor="ai-sample" hint="1 - 10 条；数量越多覆盖角度越丰富">
              <Input
                id="ai-sample"
                type="number"
                min={1}
                max={10}
                value={sampleCount}
                onChange={(event) => setSampleCount(event.target.value)}
              />
            </Field>

            {formError ? <div className="field-error">{formError}</div> : null}

            <Callout tone="warning" title="AI 仅生成草稿，必须人工评审">
              草稿会以「草稿」状态写入用例库并由人工评审；AI 不参与评审决策。无文本模型配置时生成会失败。
            </Callout>

            <Button type="submit" variant="primary" size="lg" block icon="ai" loading={generating} loadingText="正在生成草稿…">
              生成 BDD 草稿
            </Button>
          </form>
        </Card>

        <Card className="ai-result-card">
          <CardHead
            title="生成结果"
            description={result ? `任务 ${result.jobId}` : '生成的任务信息与草稿会展示在这里'}
            icon="book"
            actions={
              result ? (
                <StatusPill meta={metaOf(JOB_STATUS_META, result.status)} />
              ) : null
            }
          />
          <div className="card-body">
            {generating ? (
              <div className="ai-generating">
                <span className="ai-pulse">
                  <Icon name="ai" size={22} />
                </span>
                <div>
                  <div className="ai-generating-title">模型正在生成 BDD 草稿…</div>
                  <div className="ai-generating-text">同步调用模型服务，通常在数秒内完成，请勿关闭页面。</div>
                </div>
              </div>
            ) : generateError ? (
              <ErrorPanel message={generateError} />
            ) : result ? (
              <div className="ai-result-stack">
                <div className="ai-task-info">
                  <div>
                    <span className="meta-label">任务 ID</span>
                    <span className="mono">{result.jobId}</span>
                  </div>
                  <div>
                    <span className="meta-label">模型</span>
                    <span className="mono">{result.modelId || '—'}</span>
                  </div>
                  <div>
                    <span className="meta-label">状态</span>
                    <StatusPill meta={metaOf(JOB_STATUS_META, result.status)} />
                  </div>
                  <div>
                    <span className="meta-label">草稿数</span>
                    <strong>{result.drafts?.length ?? 0}</strong>
                  </div>
                </div>

                {result.error ? <Callout tone="error" title="任务错误">{result.error}</Callout> : null}

                {result.drafts && result.drafts.length > 0 ? (
                  <div className="draft-list">
                    {result.drafts.map((draft, index) => (
                      <article className="draft-card" key={draft.caseId || index}>
                        <div className="draft-head">
                          <span className="draft-index">{String(index + 1).padStart(2, '0')}</span>
                          <div className="draft-title">{draft.title}</div>
                          <StatusPill meta={metaOf(REVIEW_STATUS_META, draft.reviewStatus || 'DRAFT')} />
                        </div>
                        <div className="draft-bdd">
                          <div>
                            <span className="bdd-inline-tag given">G</span>
                            <p>{draft.given || '—'}</p>
                          </div>
                          <div>
                            <span className="bdd-inline-tag when">W</span>
                            <p>{draft.when || '—'}</p>
                          </div>
                          <div>
                            <span className="bdd-inline-tag then">T</span>
                            <p>{draft.then || '—'}</p>
                          </div>
                        </div>
                        <div className="draft-foot">
                          <span className="draft-id mono">{draft.caseId}</span>
                          <Button
                            size="sm"
                            variant="primary"
                            icon="arrow-right"
                            onClick={() => goReview(draft.caseId)}
                          >
                            去评审
                          </Button>
                        </div>
                      </article>
                    ))}
                  </div>
                ) : (
                  <EmptyState
                    icon="ai"
                    title="本次没有生成草稿"
                    description="可以补充更详细的需求描述后重试，或检查模型配置是否可用"
                  />
                )}
              </div>
            ) : (
              <EmptyState
                icon="ai"
                title="还没有生成结果"
                description="在左侧选择需求或输入需求文本，然后点击「生成 BDD 草稿」"
              />
            )}
          </div>
        </Card>
      </div>

      <Card>
        <CardHead
          title="历史生成任务"
          description="AI 分析任务记录，草稿会同步写入用例库"
          icon="clock"
          actions={
            <Button
              size="sm"
              variant="ghost"
              icon="refresh"
              onClick={() => void loadJobs()}
              loading={jobsLoading}
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
                  <th>任务 ID</th>
                  <th>关联需求</th>
                  <th>模型</th>
                  <th>状态</th>
                  <th>草稿数</th>
                  <th>创建时间</th>
                  <th>完成时间</th>
                  <th className="th-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {jobsLoading ? (
                  <SkeletonRows rows={4} cols={8} />
                ) : jobsError ? (
                  <tr>
                    <td colSpan={8}>
                      <ErrorPanel message={jobsError} compact onRetry={() => void loadJobs()} />
                    </td>
                  </tr>
                ) : jobs.length === 0 ? (
                  <tr>
                    <td colSpan={8}>
                      <EmptyState
                        compact
                        icon="ai"
                        title="还没有 AI 生成任务"
                        description="首次生成 BDD 草稿后，任务记录会出现在这里"
                      />
                    </td>
                  </tr>
                ) : (
                  jobs.map((job) => (
                    <tr key={job.id} className="row-clickable" onClick={() => void openJobDetail(job)}>
                      <td className="mono">{job.id}</td>
                      <td>{job.requirementId || '—'}</td>
                      <td className="mono">{job.modelId || '—'}</td>
                      <td>
                        <StatusPill meta={metaOf(JOB_STATUS_META, job.status)} />
                      </td>
                      <td>{job.outputCaseIds?.length ?? 0}</td>
                      <td className="text-muted">{formatDateTime(job.createdAt)}</td>
                      <td className="text-muted">{formatDateTime(job.finishedAt)}</td>
                      <td className="td-right">
                        <button
                          type="button"
                          className="link-btn"
                          onClick={(event) => {
                            event.stopPropagation();
                            void openJobDetail(job);
                          }}
                        >
                          查看详情
                          <Icon name="arrow-right" size={13} />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <Pagination
              page={jobsPage}
              size={HISTORY_SIZE}
              total={jobsTotal}
              disabled={jobsLoading}
              onChange={setJobsPage}
            />
          </div>
        </div>
      </Card>

      <Modal
        open={jobDetailOpen}
        title="AI 生成任务详情"
        description={jobDetail ? `任务 ${jobDetail.id}` : undefined}
        onClose={() => setJobDetailOpen(false)}
        size="md"
        footer={
          <Button variant="secondary" onClick={() => setJobDetailOpen(false)}>
            关闭
          </Button>
        }
      >
        {jobDetailLoading ? (
          <LoadingPanel text="正在加载任务详情…" />
        ) : jobDetailError ? (
          <ErrorPanel message={jobDetailError} />
        ) : jobDetail ? (
          <div className="form-stack">
            <div className="detail-meta-grid">
              <div>
                <span>状态</span>
                <strong>
                  <StatusPill meta={metaOf(JOB_STATUS_META, jobDetail.status)} />
                </strong>
              </div>
              <div>
                <span>模型</span>
                <strong className="mono">{jobDetail.modelId || '—'}</strong>
              </div>
              <div>
                <span>提示词版本</span>
                <strong>{jobDetail.promptVersion || '—'}</strong>
              </div>
              <div>
                <span>关联需求</span>
                <strong className="mono">{jobDetail.requirementId || '—'}</strong>
              </div>
              <div>
                <span>创建时间</span>
                <strong>{formatDateTime(jobDetail.createdAt)}</strong>
              </div>
              <div>
                <span>完成时间</span>
                <strong>{formatDateTime(jobDetail.finishedAt)}</strong>
              </div>
            </div>
            {jobDetail.error ? <Callout tone="error" title="任务错误">{jobDetail.error}</Callout> : null}
            <div>
              <h4 className="detail-section-title">输出草稿</h4>
              {jobDetail.outputCaseIds && jobDetail.outputCaseIds.length > 0 ? (
                <div className="output-case-list">
                  {jobDetail.outputCaseIds.map((caseId) => (
                    <div className="output-case-row" key={caseId}>
                      <span className="mono">{caseId}</span>
                      <Button size="sm" variant="secondary" icon="arrow-right" onClick={() => goReview(caseId)}>
                        去评审
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState compact icon="cases" title="没有输出用例" description="任务可能失败或尚未生成草稿" />
              )}
            </div>
          </div>
        ) : null}
      </Modal>

      <div className="dashboard-footnote">
        <Icon name="info" size={14} />
        生成任务为同步接口：模型不可用或被禁用时，页面会直接展示后端错误，不会写入假草稿。
        {requirements.length > 0 ? ` 当前需求库已加载 ${requirements.length} 条。` : ''}
      </div>
    </div>
  );
}
