import { useCallback, useEffect, useState } from 'react';
import { apiCasesApi } from '../api';
import type { ApiCase, ApiCaseInput } from '../api/types';
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
const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
const DEFAULT_ASSERTIONS = '[{"type":"STATUS_CODE","operator":"EQ","expected":"200"}]';

interface FormState {
  name: string;
  method: string;
  baseUrl: string;
  path: string;
  headers: string;
  query: string;
  body: string;
  assertions: string;
  dataset: string;
  timeoutSec: string;
  enabled: boolean;
}

const EMPTY_FORM: FormState = {
  name: '',
  method: 'GET',
  baseUrl: 'http://127.0.0.1:8080',
  path: '/actuator/health',
  headers: '{}',
  query: '{}',
  body: '',
  assertions: DEFAULT_ASSERTIONS,
  dataset: '[]',
  timeoutSec: '30',
  enabled: true,
};

export function ApiCasesPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const [items, setItems] = useState<ApiCase[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ApiCase | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [deleting, setDeleting] = useState<ApiCase | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await apiCasesApi.list(debouncedKeyword, page, PAGE_SIZE);
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

  const openEdit = (item: ApiCase) => {
    setEditing(item);
    setForm({
      name: item.name,
      method: item.method,
      baseUrl: item.baseUrl || '',
      path: item.path,
      headers: JSON.stringify(item.headers || {}, null, 2),
      query: JSON.stringify(item.query || {}, null, 2),
      body: item.body || '',
      assertions: JSON.stringify(item.assertions || [], null, 2),
      dataset: JSON.stringify(item.dataset || [], null, 2),
      timeoutSec: String(item.timeoutSec || 30),
      enabled: item.enabled !== false,
    });
    setFormError('');
    setModalOpen(true);
  };

  const save = async () => {
    setFormError('');
    let headers: Record<string, string>;
    let query: Record<string, string>;
    let assertions: Array<Record<string, unknown>>;
    let dataset: Array<Record<string, unknown>>;
    try {
      headers = JSON.parse(form.headers || '{}');
      query = JSON.parse(form.query || '{}');
      assertions = JSON.parse(form.assertions || '[]');
      dataset = JSON.parse(form.dataset || '[]');
      if (typeof headers !== 'object' || Array.isArray(headers)) throw new Error('Headers 必须是 JSON 对象');
      if (typeof query !== 'object' || Array.isArray(query)) throw new Error('Query 必须是 JSON 对象');
      if (!Array.isArray(assertions)) throw new Error('断言必须是 JSON 数组');
      if (!Array.isArray(dataset)) throw new Error('数据集必须是 JSON 数组');
      if (dataset.length > 200) throw new Error('数据集最多 200 行');
    } catch (err) {
      setFormError(`JSON 格式错误：${safeErrorMessage(err)}`);
      return;
    }
    const timeout = Number(form.timeoutSec || 30);
    if (!form.name.trim() || !form.path.trim()) {
      setFormError('用例名称与请求路径不能为空');
      return;
    }
    if (!Number.isFinite(timeout) || timeout < 1 || timeout > 600) {
      setFormError('超时需在 1-600 秒之间');
      return;
    }
    const input: ApiCaseInput = {
      name: form.name.trim(),
      method: form.method,
      baseUrl: form.baseUrl.trim(),
      path: form.path.trim(),
      headers,
      query,
      body: form.body,
      assertions,
      dataset,
      timeoutSec: timeout,
      enabled: form.enabled,
    };
    setSaving(true);
    try {
      if (editing) {
        await apiCasesApi.update(editing.id, input);
        toastSuccess('接口用例已更新');
      } else {
        await apiCasesApi.create(input);
        toastSuccess('接口用例已创建');
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
      await apiCasesApi.remove(deleting.id);
      toastSuccess('接口用例已删除');
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
        title="接口用例"
        description="维护 HTTP 请求与断言；挂载到测试计划后由独立的 api-execution 服务执行。"
        actions={
          canWrite ? (
            <Button variant="primary" icon="plus" onClick={openCreate}>
              新建接口用例
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardHead
          title="接口用例列表"
          description="支持按名称/路径搜索"
          actions={
            <div className="toolbar-actions">
              <Input
                placeholder="搜索名称或路径..."
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
                <th style={{ width: 80 }}>方法</th>
                <th>请求</th>
                <th style={{ width: 100 }}>断言</th>
                <th style={{ width: 90 }}>数据行</th>
                <th style={{ width: 90 }}>状态</th>
                <th style={{ width: 170 }}>更新时间</th>
                <th style={{ width: 130 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && items.length === 0 ? (
                <SkeletonRows rows={5} cols={8} />
              ) : error ? (
                <tr>
                  <td colSpan={7}>
                    <ErrorPanel message={error} compact onRetry={() => void load()} />
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <EmptyState
                      compact
                      icon="cases"
                      title="暂无接口用例"
                      description={canWrite ? '点击右上角新建接口用例' : '当前没有可查看的接口用例'}
                    />
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.name}
                      <div className="cell-sub mono">{item.id}</div>
                    </td>
                    <td>
                      <Pill tone="blue">{item.method}</Pill>
                    </td>
                    <td className="mono">
                      {item.baseUrl || ''}
                      {item.path}
                    </td>
                    <td>{(item.assertions || []).length}</td>
                    <td>{(item.dataset || []).length}</td>
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
          <div className="table-foot-note">共 {total} 个接口用例</div>
          <Pagination page={page} size={PAGE_SIZE} total={total} disabled={loading} onChange={setPage} />
        </div>
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑接口用例' : '新建接口用例'}
        description="baseUrl + path 组成请求地址；Headers/Query/断言使用 JSON。"
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
            <Field label="用例名称" htmlFor="api-name" required>
              <Input
                id="api-name"
                value={form.name}
                placeholder="例如：健康检查"
                onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
              />
            </Field>
            <Field label="请求方法" htmlFor="api-method" required>
              <Select
                id="api-method"
                value={form.method}
                onChange={(event) => setForm((prev) => ({ ...prev, method: event.target.value }))}
              >
                {METHOD_OPTIONS.map((method) => (
                  <option key={method} value={method}>
                    {method}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="Base URL" htmlFor="api-base-url" hint="可留空，此时 path 必须是完整 URL">
            <Input
              id="api-base-url"
              value={form.baseUrl}
              placeholder="http://127.0.0.1:8080"
              onChange={(event) => setForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
            />
          </Field>
          <Field label="Path" htmlFor="api-path" required>
            <Input
              id="api-path"
              value={form.path}
              placeholder="/actuator/health"
              onChange={(event) => setForm((prev) => ({ ...prev, path: event.target.value }))}
            />
          </Field>
          <div className="form-grid-2">
            <Field label="Headers (JSON)" htmlFor="api-headers">
              <Textarea
                id="api-headers"
                rows={4}
                className="mono"
                value={form.headers}
                onChange={(event) => setForm((prev) => ({ ...prev, headers: event.target.value }))}
              />
            </Field>
            <Field label="Query (JSON)" htmlFor="api-query">
              <Textarea
                id="api-query"
                rows={4}
                className="mono"
                value={form.query}
                onChange={(event) => setForm((prev) => ({ ...prev, query: event.target.value }))}
              />
            </Field>
          </div>
          <Field label="请求体" htmlFor="api-body" hint="GET/HEAD 可留空">
            <Textarea
              id="api-body"
              rows={4}
              className="mono"
              value={form.body}
              placeholder='{"username":"tester"}'
              onChange={(event) => setForm((prev) => ({ ...prev, body: event.target.value }))}
            />
          </Field>
          <Field
            label="数据集 (JSON 数组)"
            htmlFor="api-dataset"
            hint='每行一次执行；行字段可用 ${field} 或 ${data.field} 引用，例如 [{"username":"a","password":"b"}]'
          >
            <Textarea
              id="api-dataset"
              rows={5}
              className="mono"
              value={form.dataset}
              placeholder='[{"username":"tester","password":"secret"}]'
              onChange={(event) => setForm((prev) => ({ ...prev, dataset: event.target.value }))}
            />
          </Field>
          <Field
            label="断言 (JSON)"
            htmlFor="api-assertions"
            hint="支持 STATUS_CODE / JSON_PATH / XML_PATH / JSON_SCHEMA / BODY_CONTAINS / RESPONSE_TIME_MS / HEADER_CONTAINS"
          >
            <Textarea
              id="api-assertions"
              rows={6}
              className="mono"
              value={form.assertions}
              onChange={(event) => setForm((prev) => ({ ...prev, assertions: event.target.value }))}
            />
          </Field>
          <div className="form-grid-2">
            <Field label="超时（秒）" htmlFor="api-timeout" required>
              <Input
                id="api-timeout"
                type="number"
                min={1}
                max={600}
                value={form.timeoutSec}
                onChange={(event) => setForm((prev) => ({ ...prev, timeoutSec: event.target.value }))}
              />
            </Field>
            <Field label="状态" htmlFor="api-enabled">
              <Select
                id="api-enabled"
                value={form.enabled ? '1' : '0'}
                onChange={(event) => setForm((prev) => ({ ...prev, enabled: event.target.value === '1' }))}
              >
                <option value="1">启用</option>
                <option value="0">禁用</option>
              </Select>
            </Field>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleting}
        title="删除接口用例"
        message={`确认删除“${deleting?.name || ''}”？已挂载到计划的用例不能删除。`}
        confirmText="删除"
        tone="danger"
        loading={deletingBusy}
        onConfirm={() => void remove()}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
