import { useCallback, useEffect, useState } from 'react';
import { uiElementsApi } from '../api';
import type { UiElement } from '../api/uielements';
import { canManageCases, useAuth } from '../auth';
import { useDebouncedValue } from '../hooks';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill, Select, SkeletonRows, Textarea } from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const TYPES = ['TEXT', 'ID', 'XPATH', 'COORD'];
const EMPTY = { name: '', elementType: 'TEXT', elementValue: '', description: '' };

export function UiElementsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [keyword, setKeyword] = useState('');
  const q = useDebouncedValue(keyword, 300);
  const [items, setItems] = useState<UiElement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<UiElement | null>(null);
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [deleting, setDeleting] = useState<UiElement | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await uiElementsApi.list(q, 1, 100);
      setItems(data.items);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [q]);

  useEffect(() => { void load(); }, [load]);

  const openCreate = () => { setEditing(null); setForm(EMPTY); setFormError(''); setOpen(true); };
  const openEdit = (item: UiElement) => {
    setEditing(item);
    setForm({ name: item.name, elementType: item.elementType, elementValue: item.elementValue, description: item.description || '' });
    setFormError('');
    setOpen(true);
  };

  const save = async () => {
    if (!form.name.trim() || !form.elementValue.trim()) { setFormError('名称和值不能为空'); return; }
    setSaving(true);
    setFormError('');
    try {
      if (editing) await uiElementsApi.update(editing.id, form);
      else await uiElementsApi.create(form);
      toastSuccess(editing ? '元素已更新' : '元素已创建');
      setOpen(false);
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
      await uiElementsApi.remove(deleting.id);
      toastSuccess('元素已删除');
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
      <PageHeading title="元素库" description="可复用的定位器（文本/ID/XPath/坐标），供步骤化 UI 用例引用。"
        actions={canWrite ? <Button variant="primary" icon="plus" onClick={openCreate}>新建元素</Button> : undefined} />
      <Card>
        <CardHead title="元素列表" actions={<div className="toolbar-actions">
          <Input icon="search" placeholder="搜索名称/值" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          <Button variant="secondary" icon="refresh" loading={loading} onClick={() => void load()}>刷新</Button>
        </div>} />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>名称</th><th>类型</th><th>值</th><th>描述</th><th>更新时间</th><th>操作</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={4} cols={6} /> : error ? <tr><td colSpan={6}><ErrorPanel message={error} onRetry={() => void load()} /></td></tr> :
                items.length === 0 ? <tr><td colSpan={6}><EmptyState icon="cases" title="暂无元素" description="新建元素后可在步骤中复用" /></td></tr> :
                items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.name}<div className="cell-sub mono">{item.id}</div></td>
                    <td><Pill tone="blue">{item.elementType}</Pill></td>
                    <td className="mono">{item.elementValue}</td>
                    <td>{item.description || '—'}</td>
                    <td>{formatDateTime(item.updatedAt)}</td>
                    <td><div className="row-actions">
                      <Button size="sm" variant="ghost" onClick={() => openEdit(item)} disabled={!canWrite}>编辑</Button>
                      <Button size="sm" variant="ghost" onClick={() => setDeleting(item)} disabled={!canWrite}>删除</Button>
                    </div></td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Modal open={open} title={editing ? '编辑元素' : '新建元素'} onClose={() => !saving && setOpen(false)} size="md"
        footer={<><Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>取消</Button>
          <Button variant="primary" loading={saving} onClick={() => void save()}>{editing ? '保存' : '创建'}</Button></>}>
        <div className="form-stack">
          {formError ? <Callout tone="error" title="操作失败">{formError}</Callout> : null}
          <Field label="名称" htmlFor="ele-name" required><Input id="ele-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
          <Field label="类型" htmlFor="ele-type"><Select id="ele-type" value={form.elementType} onChange={(e) => setForm({ ...form, elementType: e.target.value })}>
            {TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
          </Select></Field>
          <Field label="值" htmlFor="ele-value" required hint="TEXT 填文本，ID 填 resource-id，XPATH 填表达式，COORD 填 x,y"><Input id="ele-value" value={form.elementValue} onChange={(e) => setForm({ ...form, elementValue: e.target.value })} /></Field>
          <Field label="描述" htmlFor="ele-desc"><Textarea id="ele-desc" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
        </div>
      </Modal>
      <ConfirmDialog open={!!deleting} title="删除元素" message={`确认删除“${deleting?.name || ''}”？`} confirmText="删除" tone="danger" loading={deletingBusy} onConfirm={() => void remove()} onClose={() => setDeleting(null)} />
    </div>
  );
}
