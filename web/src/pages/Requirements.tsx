import { useCallback, useEffect, useState } from 'react';
import { requirementsApi } from '../api';
import type { Requirement } from '../api/types';
import { canManageCases } from '../auth';
import { useAuth } from '../auth';
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
  SkeletonRows,
  Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const PAGE_SIZE = 10;

export function RequirementsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const [items, setItems] = useState<Requirement[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Requirement | null>(null);
  const [form, setForm] = useState({ title: '', description: '' });
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [deleting, setDeleting] = useState<Requirement | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await requirementsApi.list(debouncedKeyword, page, PAGE_SIZE);
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
    setForm({ title: '', description: '' });
    setFormError('');
    setModalOpen(true);
  };

  const openEdit = (item: Requirement) => {
    setEditing(item);
    setForm({ title: item.title, description: item.description || '' });
    setFormError('');
    setModalOpen(true);
  };

  const save = async () => {
    if (!form.title.trim()) {
      setFormError('需求标题不能为空');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      if (editing) {
        await requirementsApi.update(editing.id, {
          title: form.title.trim(),
          description: form.description.trim(),
        });
        toastSuccess('需求已更新');
      } else {
        await requirementsApi.create({
          title: form.title.trim(),
          description: form.description.trim(),
        });
        toastSuccess('需求已创建');
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
      await requirementsApi.remove(deleting.id);
      toastSuccess('需求已删除');
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
        title="需求管理"
        description="维护测试需求，作为用例的来源与关联依据。"
        actions={
          canWrite ? (
            <Button variant="primary" icon="plus" onClick={openCreate}>
              新建需求
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardHead
          title="需求列表"
          description="支持按标题/描述关键字搜索"
          actions={
            <div className="toolbar-actions">
              <Input
                placeholder="搜索需求..."
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
                <th style={{ width: 320 }}>标题</th>
                <th>描述</th>
                <th style={{ width: 160 }}>创建人</th>
                <th style={{ width: 180 }}>创建时间</th>
                <th style={{ width: 150 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && items.length === 0 ? (
                <SkeletonRows rows={5} cols={5} />
              ) : error ? (
                <tr>
                  <td colSpan={5}>
                    <ErrorPanel message={error} compact onRetry={() => void load()} />
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <EmptyState
                      compact
                      icon="cases"
                      title="暂无需求"
                      description={canWrite ? '点击右上角新建需求，或在用例中直接关联' : '当前没有可查看的需求'}
                    />
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.title}</td>
                    <td className="muted">{item.description || '—'}</td>
                    <td>{item.createdBy || '—'}</td>
                    <td>{item.createdAt ? formatDateTime(item.createdAt) : '—'}</td>
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
          <div className="table-foot-note">共 {total} 个需求</div>
          <Pagination page={page} size={PAGE_SIZE} total={total} disabled={loading} onChange={setPage} />
        </div>
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑需求' : '新建需求'}
        description="需求描述会用于 AI 分析生成 BDD 草稿，请尽量写清业务背景与验收目标。"
        onClose={() => {
          if (!saving) setModalOpen(false);
        }}
        size="md"
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
          <Field label="需求标题" htmlFor="req-title" required>
            <Input
              id="req-title"
              value={form.title}
              placeholder="例如：用户登录"
              onChange={(event) => setForm((prev) => ({ ...prev, title: event.target.value }))}
            />
          </Field>
          <Field label="需求描述" htmlFor="req-description" hint="包含角色、操作与预期结果">
            <Textarea
              id="req-description"
              rows={6}
              value={form.description}
              placeholder="例如：用户可以使用用户名 tester 和密码 secret 登录，登录成功后进入首页并显示登录成功"
              onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </Field>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleting}
        title="删除需求"
        message={`确认删除需求“${deleting?.title || ''}”？已关联的用例不会被删除，但会失去需求引用。`}
        confirmText="删除"
        tone="danger"
        loading={deletingBusy}
        onConfirm={() => void remove()}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
