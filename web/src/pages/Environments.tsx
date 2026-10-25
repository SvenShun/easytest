import { useCallback, useEffect, useState } from 'react';
import { apiEnvironmentsApi } from '../api';
import type { ApiAuthConfig, ApiEnvironment, ApiEnvironmentInput } from '../api/types';
import { canManageCases, useAuth } from '../auth';
import { useDebouncedValue } from '../hooks';
import { formatDateTime, safeErrorMessage } from '../utils';
import {
  Button,
  Callout,
  Card,
  CardHead,
  EmptyState,
  ErrorPanel,
  Field,
  Input,
  PageHeading,
  Pagination,
  Pill,
  Select,
  SkeletonRows,
  Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const PAGE_SIZE = 10;
const AUTH_TYPES = ['NONE', 'BEARER', 'BASIC', 'API_KEY', 'LOGIN'];
const AUTH_LABELS: Record<string, string> = {
  NONE: '无鉴权',
  BEARER: 'Bearer Token',
  BASIC: 'Basic Auth',
  API_KEY: 'API Key Header',
  LOGIN: '登录接口提取 Token',
};

interface FormState {
  name: string;
  baseUrl: string;
  headers: string;
  variables: string;
  enabled: boolean;
  authType: string;
  token: string;
  username: string;
  password: string;
  headerName: string;
  headerValue: string;
  prefix: string;
  loginMethod: string;
  loginPath: string;
  loginHeaders: string;
  loginBody: string;
  tokenPath: string;
  tokenHeader: string;
  tokenPrefix: string;
}

const EMPTY_FORM: FormState = {
  name: '',
  baseUrl: 'http://127.0.0.1:8080',
  headers: '{}',
  variables: '{}',
  enabled: true,
  authType: 'NONE',
  token: '',
  username: '',
  password: '',
  headerName: 'X-API-Key',
  headerValue: '',
  prefix: '',
  loginMethod: 'POST',
  loginPath: '/login',
  loginHeaders: '{"Content-Type":"application/json"}',
  loginBody: '{"username":"${username}","password":"${password}"}',
  tokenPath: '$.data.token',
  tokenHeader: 'Authorization',
  tokenPrefix: 'Bearer ',
};

function authLabel(type?: string): string {
  return AUTH_LABELS[type || 'NONE'] || type || 'NONE';
}

export function EnvironmentsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const [items, setItems] = useState<ApiEnvironment[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ApiEnvironment | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [deleting, setDeleting] = useState<ApiEnvironment | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await apiEnvironmentsApi.list(debouncedKeyword, page, PAGE_SIZE);
      setItems(data.items);
      setTotal(data.total);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [debouncedKeyword, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setPage(1);
  }, [debouncedKeyword]);

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError('');
    setModalOpen(true);
  };

  const openEdit = (env: ApiEnvironment) => {
    const auth = env.auth || { type: 'NONE' };
    setEditing(env);
    setForm({
      ...EMPTY_FORM,
      name: env.name,
      baseUrl: env.baseUrl,
      headers: JSON.stringify(env.headers || {}, null, 2),
      variables: JSON.stringify(env.variables || {}, null, 2),
      enabled: env.enabled !== false,
      authType: auth.type || 'NONE',
      token: auth.token || '',
      username: auth.username || '',
      password: auth.password || '',
      headerName: auth.headerName || 'X-API-Key',
      headerValue: auth.headerValue || '',
      prefix: auth.prefix || '',
      loginMethod: auth.loginMethod || 'POST',
      loginPath: auth.loginPath || '/login',
      loginHeaders: JSON.stringify(auth.loginHeaders || { 'Content-Type': 'application/json' }, null, 2),
      loginBody: auth.loginBody || EMPTY_FORM.loginBody,
      tokenPath: auth.tokenPath || '$.data.token',
      tokenHeader: auth.tokenHeader || 'Authorization',
      tokenPrefix: auth.tokenPrefix ?? 'Bearer ',
    });
    setFormError('');
    setModalOpen(true);
  };

  const save = async () => {
    setFormError('');
    let headers: Record<string, string>;
    let variables: Record<string, string>;
    let loginHeaders: Record<string, string>;
    try {
      headers = JSON.parse(form.headers || '{}');
      variables = JSON.parse(form.variables || '{}');
      loginHeaders = JSON.parse(form.loginHeaders || '{}');
    } catch (err) {
      setFormError(`JSON 格式错误：${safeErrorMessage(err)}`);
      return;
    }
    if (!form.name.trim() || !form.baseUrl.trim()) {
      setFormError('环境名称与 Base URL 不能为空');
      return;
    }
    const auth: ApiAuthConfig = { type: form.authType };
    if (form.authType === 'BEARER') auth.token = form.token;
    if (form.authType === 'BASIC') {
      auth.username = form.username;
      auth.password = form.password;
    }
    if (form.authType === 'API_KEY') {
      auth.headerName = form.headerName;
      auth.headerValue = form.headerValue;
    }
    if (form.authType === 'LOGIN') {
      auth.loginMethod = form.loginMethod;
      auth.loginPath = form.loginPath;
      auth.loginHeaders = loginHeaders;
      auth.loginBody = form.loginBody;
      auth.tokenPath = form.tokenPath;
      auth.tokenHeader = form.tokenHeader;
      auth.tokenPrefix = form.tokenPrefix;
    }
    const input: ApiEnvironmentInput = {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      headers,
      variables,
      auth,
      enabled: form.enabled,
    };
    setSaving(true);
    try {
      if (editing) {
        await apiEnvironmentsApi.update(editing.id, input);
        toastSuccess('环境已更新');
      } else {
        await apiEnvironmentsApi.create(input);
        toastSuccess('环境已创建');
      }
      setModalOpen(false);
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setDeletingBusy(true);
    try {
      await apiEnvironmentsApi.remove(deleting.id);
      toastSuccess('环境已删除');
      setDeleting(null);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setDeletingBusy(false);
    }
  };

  return (
    <div className="page-stack">
      <PageHeading
        title="环境管理"
        description="配置 Base URL、公共 Headers、变量与鉴权；测试计划选择环境后，接口用例可用 ${变量} 引用。"
        actions={
          canWrite ? (
            <Button variant="primary" icon="plus" onClick={openCreate}>
              新建环境
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardHead
          title="环境列表"
          description="支持 Bearer / Basic / API Key / 登录接口提取 Token"
          actions={
            <div className="toolbar-actions">
              <Input
                placeholder="搜索名称或 Base URL..."
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                icon="search"
              />
              <Button variant="secondary" icon="refresh" loading={loading} onClick={() => void load()}>
                刷新
              </Button>
            </div>
          }
        />
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>名称</th>
                <th>Base URL</th>
                <th>鉴权</th>
                <th>变量</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && items.length === 0 ? (
                <SkeletonRows rows={4} cols={7} />
              ) : error ? (
                <tr>
                  <td colSpan={7}>
                    <ErrorPanel message={error} compact onRetry={() => void load()} />
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <EmptyState compact icon="server" title="暂无环境" description="点击右上角新建环境" />
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.name}
                      <div className="cell-sub mono">{item.id}</div>
                    </td>
                    <td className="mono">{item.baseUrl}</td>
                    <td>
                      <Pill tone={item.auth?.type && item.auth.type !== 'NONE' ? 'purple' : 'slate'}>
                        {authLabel(item.auth?.type)}
                      </Pill>
                    </td>
                    <td>{Object.keys(item.variables || {}).length}</td>
                    <td>
                      <Pill tone={item.enabled ? 'green' : 'red'} dot>
                        {item.enabled ? '启用' : '禁用'}
                      </Pill>
                    </td>
                    <td>{item.updatedAt ? formatDateTime(item.updatedAt) : '—'}</td>
                    <td>
                      <div className="row-actions">
                        <Button size="sm" variant="ghost" onClick={() => openEdit(item)} disabled={!canWrite}>
                          编辑
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => setDeleting(item)}
                          disabled={!canWrite}
                        >
                          删除
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className="table-foot">
          <div className="table-foot-note">共 {total} 个环境</div>
          <Pagination page={page} size={PAGE_SIZE} total={total} disabled={loading} onChange={setPage} />
        </div>
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑环境' : '新建环境'}
        description="变量可在 Base URL/路径/Headers/Query/Body/断言中用 ${name} 引用；数据集行用 ${data.field} 或 ${field}。"
        onClose={() => {
          if (!saving) setModalOpen(false);
        }}
        size="lg"
        closeOnOverlay={!saving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>
              取消
            </Button>
            <Button variant="primary" loading={saving} onClick={() => void save()}>
              {editing ? '保存' : '创建'}
            </Button>
          </>
        }
      >
        <div className="form-stack">
          {formError ? <Callout tone="error" title="操作失败">{formError}</Callout> : null}
          <div className="form-grid-2">
            <Field label="环境名称" htmlFor="env-name" required>
              <Input
                id="env-name"
                value={form.name}
                placeholder="例如：测试环境"
                onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
              />
            </Field>
            <Field label="状态" htmlFor="env-enabled">
              <Select
                id="env-enabled"
                value={form.enabled ? '1' : '0'}
                onChange={(event) => setForm((prev) => ({ ...prev, enabled: event.target.value === '1' }))}
              >
                <option value="1">启用</option>
                <option value="0">禁用</option>
              </Select>
            </Field>
          </div>
          <Field label="Base URL" htmlFor="env-base-url" required hint="可引用变量，例如 ${host}">
            <Input
              id="env-base-url"
              value={form.baseUrl}
              onChange={(event) => setForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
            />
          </Field>
          <div className="form-grid-2">
            <Field label="变量 (JSON)" htmlFor="env-variables">
              <Textarea
                id="env-variables"
                rows={4}
                className="mono"
                value={form.variables}
                onChange={(event) => setForm((prev) => ({ ...prev, variables: event.target.value }))}
              />
            </Field>
            <Field label="公共 Headers (JSON)" htmlFor="env-headers">
              <Textarea
                id="env-headers"
                rows={4}
                className="mono"
                value={form.headers}
                onChange={(event) => setForm((prev) => ({ ...prev, headers: event.target.value }))}
              />
            </Field>
          </div>
          <Field label="鉴权方式" htmlFor="env-auth-type">
            <Select
              id="env-auth-type"
              value={form.authType}
              onChange={(event) => setForm((prev) => ({ ...prev, authType: event.target.value }))}
            >
              {AUTH_TYPES.map((type) => (
                <option key={type} value={type}>
                  {AUTH_LABELS[type]}
                </option>
              ))}
            </Select>
          </Field>
          {form.authType === 'BEARER' ? (
            <Field label="Token" htmlFor="env-token" hint="支持 ${变量}">
              <Input
                id="env-token"
                value={form.token}
                onChange={(event) => setForm((prev) => ({ ...prev, token: event.target.value }))}
              />
            </Field>
          ) : null}
          {form.authType === 'BASIC' ? (
            <div className="form-grid-2">
              <Field label="用户名" htmlFor="env-username">
                <Input
                  id="env-username"
                  value={form.username}
                  onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                />
              </Field>
              <Field label="密码" htmlFor="env-password">
                <Input
                  id="env-password"
                  type="password"
                  value={form.password}
                  onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                />
              </Field>
            </div>
          ) : null}
          {form.authType === 'API_KEY' ? (
            <div className="form-grid-2">
              <Field label="Header 名" htmlFor="env-key-name">
                <Input
                  id="env-key-name"
                  value={form.headerName}
                  onChange={(event) => setForm((prev) => ({ ...prev, headerName: event.target.value }))}
                />
              </Field>
              <Field label="Header 值" htmlFor="env-key-value">
                <Input
                  id="env-key-value"
                  value={form.headerValue}
                  onChange={(event) => setForm((prev) => ({ ...prev, headerValue: event.target.value }))}
                />
              </Field>
            </div>
          ) : null}
          {form.authType === 'LOGIN' ? (
            <div className="form-stack">
              <div className="form-grid-2">
                <Field label="登录方法" htmlFor="env-login-method">
                  <Select
                    id="env-login-method"
                    value={form.loginMethod}
                    onChange={(event) => setForm((prev) => ({ ...prev, loginMethod: event.target.value }))}
                  >
                    <option value="POST">POST</option>
                    <option value="GET">GET</option>
                  </Select>
                </Field>
                <Field label="登录路径" htmlFor="env-login-path" required>
                  <Input
                    id="env-login-path"
                    value={form.loginPath}
                    onChange={(event) => setForm((prev) => ({ ...prev, loginPath: event.target.value }))}
                  />
                </Field>
              </div>
              <Field label="登录 Headers (JSON)" htmlFor="env-login-headers">
                <Textarea
                  id="env-login-headers"
                  rows={3}
                  className="mono"
                  value={form.loginHeaders}
                  onChange={(event) => setForm((prev) => ({ ...prev, loginHeaders: event.target.value }))}
                />
              </Field>
              <Field label="登录 Body" htmlFor="env-login-body" hint="支持 ${username} / ${password} 等变量">
                <Textarea
                  id="env-login-body"
                  rows={4}
                  className="mono"
                  value={form.loginBody}
                  onChange={(event) => setForm((prev) => ({ ...prev, loginBody: event.target.value }))}
                />
              </Field>
              <div className="form-grid-2">
                <Field label="Token JSONPath" htmlFor="env-token-path" required>
                  <Input
                    id="env-token-path"
                    value={form.tokenPath}
                    onChange={(event) => setForm((prev) => ({ ...prev, tokenPath: event.target.value }))}
                  />
                </Field>
                <Field label="Token Header" htmlFor="env-token-header">
                  <Input
                    id="env-token-header"
                    value={form.tokenHeader}
                    onChange={(event) => setForm((prev) => ({ ...prev, tokenHeader: event.target.value }))}
                  />
                </Field>
              </div>
              <Field label="Token 前缀" htmlFor="env-token-prefix" hint="例如 'Bearer '，不需要可留空">
                <Input
                  id="env-token-prefix"
                  value={form.tokenPrefix}
                  onChange={(event) => setForm((prev) => ({ ...prev, tokenPrefix: event.target.value }))}
                />
              </Field>
              <Callout tone="info">登录只会在计划触发时执行一次，Token 注入到本次运行的所有接口请求。</Callout>
            </div>
          ) : null}
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleting}
        title="删除环境"
        message={`确认删除环境“${deleting?.name || ''}”？被测试计划引用的环境不能删除。`}
        confirmText="删除"
        tone="danger"
        loading={deletingBusy}
        onConfirm={() => void remove()}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
