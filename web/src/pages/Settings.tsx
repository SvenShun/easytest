import { useCallback, useEffect, useMemo, useState } from 'react';
import { systemApi, uiexecApi } from '../api';
import { ApiError } from '../api/client';
import type { AuditLog, ModelConfig, SkillsPointer, User } from '../api/types';
import { isAdmin, useAuth } from '../auth';
import {
  MODEL_PROVIDERS,
  ROLE_META,
  metaOf,
} from '../constants';
import { DEFAULT_CONFIG, resetConfig, setConfig, useAppConfig } from '../config';
import { formatDateTime, formatDetail, safeErrorMessage, toText } from '../utils';
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
  Tabs,
} from '../components/ui';
import { Modal, PromptDialog } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const AUDIT_PAGE_SIZE = 15;

const MODEL_TYPES = [
  {
    key: 'text',
    title: '文本生成模型',
    description: 'AI 分析 Agent 用它将需求转换为 BDD 草稿',
    icon: 'ai' as const,
  },
  {
    key: 'vision',
    title: '视觉决策模型',
    description: 'AI Runtime 用它观察截图与 UI 树并决策下一步动作',
    icon: 'eye' as const,
  },
];

interface MergedAudit extends AuditLog {
  source: 'core' | 'ui-execution';
}

export function SettingsPage() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const [tab, setTab] = useState('models');
  const appConfig = useAppConfig();
  const [coreUrl, setCoreUrl] = useState(appConfig.coreUrl);
  const [uexecUrl, setUexecUrl] = useState(appConfig.uexecUrl);
  const [serviceConfigError, setServiceConfigError] = useState('');

  const [configs, setConfigs] = useState<ModelConfig[]>([]);
  const [configsLoading, setConfigsLoading] = useState(false);
  const [configsError, setConfigsError] = useState('');
  const [modelOpen, setModelOpen] = useState(false);
  const [editingType, setEditingType] = useState<string>('text');
  const [modelForm, setModelForm] = useState({ provider: 'mock', endpoint: '', apiKey: '', modelId: '' });
  const [modelSaving, setModelSaving] = useState(false);
  const [modelError, setModelError] = useState('');

  const [pointer, setPointer] = useState<SkillsPointer | null>(null);
  const [pointerLoading, setPointerLoading] = useState(false);
  const [pointerError, setPointerError] = useState('');
  const [pointerForm, setPointerForm] = useState({ gitUrl: '', branch: '' });
  const [pointerSaving, setPointerSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [rollbackOpen, setRollbackOpen] = useState(false);
  const [rollingBack, setRollingBack] = useState(false);

  const [coreLogs, setCoreLogs] = useState<AuditLog[]>([]);
  const [uexecLogs, setUexecLogs] = useState<AuditLog[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [coreAuditError, setCoreAuditError] = useState('');
  const [uexecAuditError, setUexecAuditError] = useState('');
  const [auditPage, setAuditPage] = useState(1);

  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [usersError, setUsersError] = useState('');
  const [usersTotal, setUsersTotal] = useState(0);

  useEffect(() => {
    setCoreUrl(appConfig.coreUrl);
    setUexecUrl(appConfig.uexecUrl);
  }, [appConfig.coreUrl, appConfig.uexecUrl]);

  const saveServiceConfig = () => {
    setServiceConfigError('');
    if (!coreUrl.trim() || !uexecUrl.trim()) {
      setServiceConfigError('两个服务地址都不能为空');
      return;
    }
    setConfig({ coreUrl, uexecUrl });
    toastSuccess('客户端服务地址已保存，后续请求立即生效');
  };

  const resetServiceConfig = () => {
    const next = resetConfig();
    setCoreUrl(next.coreUrl);
    setUexecUrl(next.uexecUrl);
    toastSuccess('已恢复默认服务地址');
  };

  const loadConfigs = useCallback(async () => {
    setConfigsLoading(true);
    setConfigsError('');
    try {
      const data = await systemApi.modelConfigs();
      setConfigs(Array.isArray(data) ? data : data?.items || []);
    } catch (err) {
      setConfigsError(safeErrorMessage(err));
    } finally {
      setConfigsLoading(false);
    }
  }, []);

  const loadPointer = useCallback(async () => {
    setPointerLoading(true);
    setPointerError('');
    try {
      const data = await uiexecApi.skillsPointer();
      setPointer(data);
      setPointerForm({ gitUrl: data.gitUrl || '', branch: data.branch || '' });
    } catch (err) {
      setPointerError(safeErrorMessage(err));
    } finally {
      setPointerLoading(false);
    }
  }, []);

  const loadAudit = useCallback(async () => {
    setAuditLoading(true);
    setCoreAuditError('');
    setUexecAuditError('');
    const [coreRes, uexecRes] = await Promise.allSettled([
      systemApi.auditLogs(1, 50),
      uiexecApi.auditLogs(1, 50),
    ]);
    if (coreRes.status === 'fulfilled') setCoreLogs(coreRes.value.items);
    else setCoreAuditError(safeErrorMessage(coreRes.reason));
    if (uexecRes.status === 'fulfilled') setUexecLogs(uexecRes.value.items);
    else setUexecAuditError(safeErrorMessage(uexecRes.reason));
    setAuditLoading(false);
    setAuditPage(1);
  }, []);

  const loadUsers = useCallback(async () => {
    setUsersLoading(true);
    setUsersError('');
    try {
      const data = await systemApi.users(1, 100);
      setUsers(data.items);
      setUsersTotal(data.total);
    } catch (err) {
      setUsersError(safeErrorMessage(err));
    } finally {
      setUsersLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === 'models') void loadConfigs();
    if (tab === 'skills') void loadPointer();
    if (tab === 'audit') void loadAudit();
    if (tab === 'users') void loadUsers();
  }, [tab, loadConfigs, loadPointer, loadAudit, loadUsers]);

  const openModelEdit = (type: string) => {
    const config = configs.find((item) => item.configType === type);
    setEditingType(type);
    setModelForm({
      provider: config?.provider || 'mock',
      endpoint: config?.endpoint || '',
      apiKey: '',
      modelId: config?.modelId || '',
    });
    setModelError('');
    setModelOpen(true);
  };

  const saveModelConfig = async () => {
    if (!modelForm.provider) {
      setModelError('请选择 provider');
      return;
    }
    if (!modelForm.modelId.trim()) {
      setModelError('请填写 modelId');
      return;
    }
    if (modelForm.provider !== 'mock' && !modelForm.endpoint.trim()) {
      setModelError('非 mock provider 需要填写 endpoint');
      return;
    }
    setModelSaving(true);
    setModelError('');
    try {
      const updated = await systemApi.updateModelConfig(editingType, {
        provider: modelForm.provider,
        endpoint: modelForm.endpoint.trim(),
        apiKey: modelForm.apiKey,
        modelId: modelForm.modelId.trim(),
      });
      setConfigs((prev) => {
        const next = prev.filter((item) => item.configType !== editingType);
        return [...next, updated];
      });
      toastSuccess('模型配置已更新');
      setModelOpen(false);
      await loadConfigs();
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setModelError('仅管理员可以修改模型配置');
      } else {
        setModelError(safeErrorMessage(err));
      }
    } finally {
      setModelSaving(false);
    }
  };

  const savePointer = async () => {
    if (!pointerForm.gitUrl.trim()) {
      setPointerError('请填写 Git 仓库地址');
      return;
    }
    setPointerSaving(true);
    setPointerError('');
    try {
      const updated = await uiexecApi.updateSkillsPointer({
        gitUrl: pointerForm.gitUrl.trim(),
        branch: pointerForm.branch.trim() || 'main',
      });
      setPointer(updated);
      setPointerForm({ gitUrl: updated.gitUrl || '', branch: updated.branch || '' });
      toastSuccess('Skills 指针已保存');
    } catch (err) {
      setPointerError(safeErrorMessage(err));
      toastError(safeErrorMessage(err), '保存指针失败');
    } finally {
      setPointerSaving(false);
    }
  };

  const syncPointer = async () => {
    setSyncing(true);
    setPointerError('');
    try {
      const result = await uiexecApi.syncSkills();
      toastSuccess(`同步成功，activeCommit：${result.activeCommit}`, 'Skills 已同步');
      await loadPointer();
    } catch (err) {
      const message = safeErrorMessage(err);
      setPointerError(message);
      toastError(message, 'Skills 同步失败');
    } finally {
      setSyncing(false);
    }
  };

  const rollbackPointer = async (commit: string) => {
    if (!commit.trim()) return;
    setRollingBack(true);
    try {
      const updated = await uiexecApi.rollbackSkills(commit.trim());
      setPointer(updated);
      setRollbackOpen(false);
      toastSuccess(`已回滚到 ${updated.activeCommit || commit}`, '回滚成功');
      await loadPointer();
    } catch (err) {
      toastError(safeErrorMessage(err), '回滚失败');
    } finally {
      setRollingBack(false);
    }
  };

  const mergedAudit = useMemo<MergedAudit[]>(() => {
    const merged: MergedAudit[] = [
      ...coreLogs.map((log) => ({ ...log, source: 'core' as const })),
      ...uexecLogs.map((log) => ({ ...log, source: 'ui-execution' as const })),
    ];
    return merged.sort((a, b) => {
      const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return bTime - aTime;
    });
  }, [coreLogs, uexecLogs]);

  const auditPageItems = mergedAudit.slice(
    (auditPage - 1) * AUDIT_PAGE_SIZE,
    auditPage * AUDIT_PAGE_SIZE,
  );

  const copyCommit = async () => {
    if (!pointer?.activeCommit) return;
    try {
      await navigator.clipboard.writeText(pointer.activeCommit);
      toastSuccess('commit 已复制');
    } catch {
      toastError('复制失败');
    }
  };

  const tabs = [
    { key: 'models', label: '模型配置' },
    { key: 'skills', label: 'Skills 指针' },
    { key: 'audit', label: '审计日志' },
    { key: 'users', label: '用户' },
  ];

  return (
    <div className="page-stack">
      <PageHeading
        title="系统设置"
        description="模型密钥、Skills 指针、审计日志与用户管理；所有配置修改仅管理员可操作。"
        actions={<Pill tone="purple">ADMIN</Pill>}
      />

      {!admin ? (
        <Callout tone="warning" title="只读访问">
          当前账号不是管理员，只能查看设置项，无法保存修改。
        </Callout>
      ) : null}

      <Card>
        <div className="settings-tabs">
          <Tabs items={tabs} value={tab} onChange={setTab} />
        </div>

        {tab === 'models' ? (
          <div className="card-body">
            {configsLoading && configs.length === 0 ? (
              <LoadingPanel text="正在加载模型配置…" />
            ) : configsError ? (
              <ErrorPanel message={configsError} onRetry={() => void loadConfigs()} />
            ) : (
              <>
                <div className="settings-intro">
                  <Icon name="info" size={15} />
                  模型配置由 core 服务持久化，API Key 仅返回掩码；provider 为 mock 时忽略 endpoint 与 key。
                </div>
                <div className="model-grid">
                  {MODEL_TYPES.map((type) => {
                    const config = configs.find((item) => item.configType === type.key);
                    return (
                      <div className="model-card" key={type.key}>
                        <div className="model-card-head">
                          <span className="model-icon">
                            <Icon name={type.icon} size={19} />
                          </span>
                          <div className="model-card-title">
                            <h3>{type.title}</h3>
                            <p>{type.description}</p>
                          </div>
                          <Pill tone={config?.configured ? 'green' : 'orange'}>
                            {config?.configured ? '已配置' : '未配置'}
                          </Pill>
                        </div>
                        <div className="model-fields">
                          <div>
                            <span className="meta-label">Provider</span>
                            <strong className="mono">{config?.provider || '—'}</strong>
                          </div>
                          <div>
                            <span className="meta-label">Model ID</span>
                            <strong className="mono">{config?.modelId || '—'}</strong>
                          </div>
                          <div className="is-wide">
                            <span className="meta-label">Endpoint</span>
                            <strong className="mono">{config?.endpoint || '（mock 无需 endpoint）'}</strong>
                          </div>
                          <div>
                            <span className="meta-label">API Key</span>
                            <strong className="mono">{config?.apiKeyMasked || '未设置'}</strong>
                          </div>
                          <div>
                            <span className="meta-label">更新人</span>
                            <strong>{config?.updatedBy || '—'}</strong>
                          </div>
                          <div className="is-wide">
                            <span className="meta-label">更新时间</span>
                            <strong>{formatDateTime(config?.updatedAt)}</strong>
                          </div>
                        </div>
                        <div className="model-card-foot">
                          <span className="meta-label">配置类型：{type.key}</span>
                          <Button
                            size="sm"
                            variant={admin ? 'secondary' : 'ghost'}
                            icon="edit"
                            disabled={!admin}
                            onClick={() => openModelEdit(type.key)}
                          >
                            编辑配置
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        ) : null}

        {tab === 'skills' ? (
          <div className="card-body">
            {pointerLoading && !pointer ? (
              <LoadingPanel text="正在加载 Skills 指针…" />
            ) : pointerError && !pointer ? (
              <ErrorPanel message={pointerError} onRetry={() => void loadPointer()} />
            ) : (
              <div className="skills-layout">
                <div className="skills-main">
                  <Callout tone="info" title="全局唯一指针">
                    Skills 指针由 ui-execution 维护，全局唯一；执行时 Agent 按 <code>activeCommit</code> 拉取 Skills
                    并注入 AI Runtime，不落库内容。
                  </Callout>
                  {pointerError ? <Callout tone="error" title="操作失败">{pointerError}</Callout> : null}

                  <div className="pointer-card">
                    <div className="pointer-card-head">
                      <div>
                        <div className="pointer-title">当前指针</div>
                        <div className="pointer-sub">更新人 {pointer?.updatedBy || '—'}</div>
                      </div>
                      <Pill tone={pointer?.configured ? 'green' : 'gray'}>
                        {pointer?.configured ? '已配置' : '未配置'}
                      </Pill>
                    </div>
                    <div className="pointer-grid">
                      <div className="is-wide">
                        <span className="meta-label">Git 仓库</span>
                        <span className="mono pointer-mono">{pointer?.gitUrl || '—'}</span>
                      </div>
                      <div>
                        <span className="meta-label">分支</span>
                        <span className="mono">{pointer?.branch || '—'}</span>
                      </div>
                      <div>
                        <span className="meta-label">activeCommit</span>
                        <span className="commit-line">
                          <span className="mono commit-hash">{pointer?.activeCommit || '—'}</span>
                          {pointer?.activeCommit ? (
                            <IconButton icon="copy" label="复制 commit" onClick={() => void copyCommit()} />
                          ) : null}
                        </span>
                      </div>
                      <div>
                        <span className="meta-label">更新时间</span>
                        <span>{formatDateTime(pointer?.updatedAt)}</span>
                      </div>
                      <div>
                        <span className="meta-label">最近同步</span>
                        <span>{formatDateTime(pointer?.syncedAt)}</span>
                      </div>
                    </div>
                    <div className="pointer-actions">
                      <Button
                        variant="secondary"
                        icon="sync"
                        loading={syncing}
                        disabled={!admin || !pointer?.gitUrl}
                        onClick={() => void syncPointer()}
                      >
                        同步最新
                      </Button>
                      <Button
                        variant="secondary"
                        icon="rollback"
                        disabled={!admin}
                        onClick={() => setRollbackOpen(true)}
                      >
                        回滚到 Commit
                      </Button>
                    </div>
                  </div>

                  <div className="pointer-form">
                    <h4 className="detail-section-title">更新指针</h4>
                    <div className="form-grid-2">
                      <Field label="Git 仓库地址" htmlFor="skills-git-url" required>
                        <Input
                          id="skills-git-url"
                          placeholder="例如：git@github.com:org/skills.git"
                          value={pointerForm.gitUrl}
                          disabled={!admin}
                          onChange={(event) =>
                            setPointerForm((prev) => ({ ...prev, gitUrl: event.target.value }))
                          }
                        />
                      </Field>
                      <Field label="分支" htmlFor="skills-branch" hint="默认 main">
                        <Input
                          id="skills-branch"
                          placeholder="main"
                          value={pointerForm.branch}
                          disabled={!admin}
                          onChange={(event) =>
                            setPointerForm((prev) => ({ ...prev, branch: event.target.value }))
                          }
                        />
                      </Field>
                    </div>
                    <div className="pointer-form-foot">
                      <span className="field-hint">
                        保存指针不会自动同步；点击「同步最新」由 ui-execution 拉取并生成新的 activeCommit。
                      </span>
                      <Button
                        variant="primary"
                        icon="check"
                        loading={pointerSaving}
                        disabled={!admin}
                        onClick={() => void savePointer()}
                      >
                        保存指针
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        ) : null}

        {tab === 'audit' ? (
          <div className="card-body no-pad">
            <div className="audit-toolbar">
              <div>
                <div className="audit-title">操作审计</div>
                <div className="audit-sub">
                  合并 core 与 ui-execution 两个来源，按时间倒序展示，每来源最多加载最近 50 条
                </div>
              </div>
              <Button variant="secondary" icon="refresh" loading={auditLoading} onClick={() => void loadAudit()}>
                刷新
              </Button>
            </div>
            {coreAuditError || uexecAuditError ? (
              <div className="audit-error">
                <Callout tone="warning" title="部分来源加载失败">
                  {coreAuditError ? `core：${coreAuditError}；` : ''}
                  {uexecAuditError ? `ui-execution：${uexecAuditError}` : ''}
                </Callout>
              </div>
            ) : null}
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>来源</th>
                    <th>操作人</th>
                    <th>动作</th>
                    <th>对象</th>
                    <th>详情</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLoading && mergedAudit.length === 0 ? (
                    <SkeletonRows rows={6} cols={6} />
                  ) : mergedAudit.length === 0 ? (
                    <tr>
                      <td colSpan={6}>
                        <EmptyState
                          icon="audit"
                          title="暂无审计日志"
                          description="模型配置、Skills 指针、任务与计划等操作会记录在这里"
                        />
                      </td>
                    </tr>
                  ) : (
                    auditPageItems.map((log) => (
                      <tr key={`${log.source}-${log.id}`}>
                        <td className="text-muted">{formatDateTime(log.createdAt)}</td>
                        <td>
                          <Pill tone={log.source === 'core' ? 'blue' : 'purple'}>{log.source}</Pill>
                        </td>
                        <td>{log.actor || '—'}</td>
                        <td className="mono">{log.action || '—'}</td>
                        <td className="mono">{log.target || '—'}</td>
                        <td className="td-summary" title={toText(log.detail)}>
                          {formatDetail(log.detail, 90)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="table-foot">
              <div className="table-foot-note">core {coreLogs.length} 条 · ui-execution {uexecLogs.length} 条</div>
              <Pagination
                page={auditPage}
                size={AUDIT_PAGE_SIZE}
                total={mergedAudit.length}
                disabled={auditLoading}
                onChange={setAuditPage}
              />
            </div>
          </div>
        ) : null}

        {tab === 'users' ? (
          <div className="card-body no-pad">
            <div className="audit-toolbar">
              <div>
                <div className="audit-title">用户列表</div>
                <div className="audit-sub">V1 用户由种子数据与系统管理创建，此处只读展示</div>
              </div>
              <Button variant="secondary" icon="refresh" loading={usersLoading} onClick={() => void loadUsers()}>
                刷新
              </Button>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>用户名</th>
                    <th>显示名</th>
                    <th>角色</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  {usersLoading && users.length === 0 ? (
                    <SkeletonRows rows={4} cols={4} />
                  ) : usersError ? (
                    <tr>
                      <td colSpan={4}>
                        <ErrorPanel message={usersError} compact onRetry={() => void loadUsers()} />
                      </td>
                    </tr>
                  ) : users.length === 0 ? (
                    <tr>
                      <td colSpan={4}>
                        <EmptyState compact icon="user" title="暂无用户" description="请检查后端种子数据是否初始化" />
                      </td>
                    </tr>
                  ) : (
                    users.map((item) => {
                      const disabled = item.enabled === false || item.status === 'DISABLED' || item.status === 'INACTIVE';
                      const roleMeta = metaOf(ROLE_META, item.role);
                      return (
                        <tr key={item.id || item.username}>
                          <td className="mono">{item.username}</td>
                          <td>{item.displayName || item.name || '—'}</td>
                          <td>
                            <Pill tone={roleMeta.tone}>{roleMeta.label}</Pill>
                          </td>
                          <td>
                            <Pill tone={disabled ? 'red' : 'green'} dot>
                              {disabled ? '禁用' : '正常'}
                            </Pill>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
            <div className="table-foot">
              <div className="table-foot-note">共 {usersTotal} 个用户</div>
            </div>
          </div>
        ) : null}
      </Card>

      <Card>
        <CardHead
          title="客户端服务地址"
          description="仅影响当前浏览器；保存后后续请求立即使用新地址"
          icon="server"
          actions={
            <Button size="sm" variant="ghost" icon="rollback" onClick={resetServiceConfig}>
              恢复默认
            </Button>
          }
        />
        <div className="card-body form-stack">
          <Callout tone="info">
            地址持久化在浏览器 localStorage，用于本地联调。默认 Core 服务 {DEFAULT_CONFIG.coreUrl}，UI Execution 服务{' '}
            {DEFAULT_CONFIG.uexecUrl}；也可通过 VITE_CORE_URL / VITE_UEXEC_URL 环境变量覆盖默认值。
          </Callout>
          <div className="form-grid-2">
            <Field label="Core 服务地址" htmlFor="settings-core-url" hint="testhub-server，默认 8080">
              <Input
                id="settings-core-url"
                value={coreUrl}
                placeholder={DEFAULT_CONFIG.coreUrl}
                onChange={(event) => setCoreUrl(event.target.value)}
              />
            </Field>
            <Field label="UI Execution 服务地址" htmlFor="settings-uexec-url" hint="ui-execution，默认 8081">
              <Input
                id="settings-uexec-url"
                value={uexecUrl}
                placeholder={DEFAULT_CONFIG.uexecUrl}
                onChange={(event) => setUexecUrl(event.target.value)}
              />
            </Field>
          </div>
          {serviceConfigError ? <div className="field-error">{serviceConfigError}</div> : null}
          <div className="pointer-form-foot">
            <span className="field-hint">
              当前生效：Core <span className="mono">{appConfig.coreUrl}</span> · UI Execution{' '}
              <span className="mono">{appConfig.uexecUrl}</span>
            </span>
            <Button variant="primary" icon="check" disabled={!admin} onClick={saveServiceConfig}>
              保存服务地址
            </Button>
          </div>
        </div>
      </Card>

      <Modal
        open={modelOpen}
        title={`编辑${editingType === 'vision' ? '视觉决策' : '文本生成'}模型`}
        description="API Key 留空表示保留原密钥；provider=mock 时忽略 endpoint 与 key。"
        onClose={() => {
          if (!modelSaving) setModelOpen(false);
        }}
        size="md"
        closeOnOverlay={!modelSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModelOpen(false)} disabled={modelSaving}>
              取消
            </Button>
            <Button variant="primary" loading={modelSaving} disabled={!admin} onClick={() => void saveModelConfig()}>
              保存配置
            </Button>
          </>
        }
      >
        <div className="form-stack">
          {modelError ? <Callout tone="error" title="保存失败">{modelError}</Callout> : null}
          <Field label="Provider" htmlFor="model-provider" required>
            <Select
              id="model-provider"
              value={modelForm.provider}
              onChange={(event) => setModelForm((prev) => ({ ...prev, provider: event.target.value }))}
            >
              {MODEL_PROVIDERS.map((provider) => (
                <option key={provider} value={provider}>
                  {provider}
                </option>
              ))}
            </Select>
          </Field>
          <Field
            label="Endpoint"
            htmlFor="model-endpoint"
            hint={modelForm.provider === 'mock' ? 'mock provider 忽略 endpoint' : 'OpenAI 兼容端点，例如 https://api.deepseek.com'}
          >
            <Input
              id="model-endpoint"
              placeholder="https://api.deepseek.com"
              value={modelForm.endpoint}
              onChange={(event) => setModelForm((prev) => ({ ...prev, endpoint: event.target.value }))}
            />
          </Field>
          <Field
            label="API Key"
            htmlFor="model-api-key"
            hint="留空表示保留原密钥；后端仅保存加密后的密钥并返回掩码"
          >
            <Input
              id="model-api-key"
              type="password"
              autoComplete="new-password"
              placeholder="留空表示保留原密钥"
              value={modelForm.apiKey}
              onChange={(event) => setModelForm((prev) => ({ ...prev, apiKey: event.target.value }))}
            />
          </Field>
          <Field label="Model ID" htmlFor="model-id" required>
            <Input
              id="model-id"
              placeholder="例如：deepseek-chat"
              value={modelForm.modelId}
              onChange={(event) => setModelForm((prev) => ({ ...prev, modelId: event.target.value }))}
            />
          </Field>
          <Callout tone="info">
            文本模型用于 AI 分析生成 BDD 草稿；视觉模型用于 AI Runtime。未配置视觉模型时，无脚本用例将无法执行。
          </Callout>
        </div>
      </Modal>

      <PromptDialog
        open={rollbackOpen}
        title="回滚 Skills 指针"
        message={`将把全局 activeCommit 回滚到指定 commit。当前值：${pointer?.activeCommit || '未配置'}`}
        label="目标 Commit"
        placeholder="输入完整 commit hash"
        confirmText="确认回滚"
        tone="danger"
        mono
        loading={rollingBack}
        onConfirm={(value) => void rollbackPointer(value)}
        onClose={() => setRollbackOpen(false)}
      />
    </div>
  );
}
