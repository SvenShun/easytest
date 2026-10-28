import { useCallback, useEffect, useState } from 'react';
import { notificationsApi } from '../api';
import type { NotificationChannel, NotificationInput } from '../api/notifications';
import { canManageCases, useAuth } from '../auth';
import { formatDateTime, safeErrorMessage } from '../utils';
import {
  Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill,
  Select, SkeletonRows, Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const TYPE_LABEL: Record<string, string> = {
  GENERIC: '通用 Webhook',
  DINGTALK: '钉钉',
  WECOM: '企业微信',
  FEISHU: '飞书',
};

const EMPTY: NotificationInput = {
  name: '', channelType: 'GENERIC', webhookUrl: '', secret: '', enabled: true, events: 'RUN_COMPLETED',
};

export function NotificationsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [items, setItems] = useState<NotificationChannel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<NotificationChannel | null>(null);
  const [form, setForm] = useState<NotificationInput>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [testing, setTesting] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<NotificationChannel | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await notificationsApi.list());
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY });
    setFormError('');
    setOpen(true);
  };

  const openEdit = (item: NotificationChannel) => {
    setEditing(item);
    setForm({
      name: item.name,
      channelType: item.channelType,
      webhookUrl: item.webhookUrl,
      secret: '',
      enabled: item.enabled,
      events: item.events || 'RUN_COMPLETED',
    });
    setFormError('');
    setOpen(true);
  };

  const save = async () => {
    if (!form.name.trim() || !form.webhookUrl.trim()) {
      setFormError('名称和 Webhook 不能为空');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      if (editing) await notificationsApi.update(editing.id, form);
      else await notificationsApi.create(form);
      toastSuccess(editing ? '机器人已更新' : '机器人已创建');
      setOpen(false);
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const sendTest = async (item: NotificationChannel) => {
    setTesting(item.id);
    try {
      const result = await notificationsApi.test(item.id);
      toastSuccess(result.message || '测试消息已发送');
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setTesting(null);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await notificationsApi.remove(deleting.id);
      toastSuccess('机器人已删除');
      setDeleting(null);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setDeleteBusy(false);
    }
  };

  return (
    <div className="page-stack">
      <PageHeading
        title="通知机器人"
        description="计划运行结束后推送执行结果到钉钉、企业微信、飞书或通用 Webhook。"
        actions={canWrite ? <Button variant="primary" icon="plus" onClick={openCreate}>新建机器人</Button> : undefined}
      />
      <Card>
        <CardHead title="机器人列表" description="仅在运行从 RUNNING 变为完成/失败时触发一次" icon="server"
          actions={<Button size="sm" variant="ghost" icon="refresh" loading={loading} onClick={() => void load()}>刷新</Button>} />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>名称</th><th>类型</th><th>Webhook</th><th>事件</th><th>状态</th><th>最近测试</th><th className="th-right">操作</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={3} cols={7} /> : error ? (
                <tr><td colSpan={7}><ErrorPanel message={error} onRetry={() => void load()} /></td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={7}><EmptyState icon="server" title="暂无通知机器人" description="新建后绑定 Webhook，运行结束自动推送" /></td></tr>
              ) : items.map((item) => (
                <tr key={item.id}>
                  <td>{item.name}<div className="cell-sub mono">{item.id}</div></td>
                  <td><Pill tone="blue">{TYPE_LABEL[item.channelType] || item.channelType}</Pill></td>
                  <td className="mono">{item.webhookUrl.length > 42 ? item.webhookUrl.slice(0, 42) + '…' : item.webhookUrl}</td>
                  <td className="mono">{item.events}</td>
                  <td><Pill tone={item.enabled ? 'green' : 'red'} dot>{item.enabled ? '启用' : '停用'}</Pill></td>
                  <td>{item.lastTestAt ? `${formatDateTime(item.lastTestAt)} · ${item.lastTestStatus || ''}` : '—'}</td>
                  <td><div className="row-actions">
                    <Button size="sm" variant="ghost" loading={testing === item.id} onClick={() => void sendTest(item)} disabled={!canWrite}>测试</Button>
                    <Button size="sm" variant="ghost" onClick={() => openEdit(item)} disabled={!canWrite}>编辑</Button>
                    <Button size="sm" variant="ghost" onClick={() => setDeleting(item)} disabled={!canWrite}>删除</Button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal open={open} title={editing ? '编辑机器人' : '新建机器人'} size="md"
        onClose={() => { if (!saving) setOpen(false); }} closeOnOverlay={!saving}
        footer={<><Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>取消</Button>
          <Button variant="primary" loading={saving} onClick={() => void save()}>{editing ? '保存' : '创建'}</Button></>}>
        <div className="form-stack">
          {formError ? <Callout tone="error" title="操作失败">{formError}</Callout> : null}
          <Field label="名称" htmlFor="notify-name" required>
            <Input id="notify-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="类型" htmlFor="notify-type">
            <Select id="notify-type" value={form.channelType} onChange={(e) => setForm({ ...form, channelType: e.target.value })}>
              {Object.entries(TYPE_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </Field>
          <Field label="Webhook URL" htmlFor="notify-url" required hint="钉钉/企业微信/飞书群机器人 Webhook">
            <Input id="notify-url" value={form.webhookUrl} onChange={(e) => setForm({ ...form, webhookUrl: e.target.value })} />
          </Field>
          <Field label="签名密钥（钉钉可选）" htmlFor="notify-secret" hint="留空表示未开启加签；编辑时留空不修改原密钥">
            <Input id="notify-secret" value={form.secret || ''} onChange={(e) => setForm({ ...form, secret: e.target.value })} />
          </Field>
          <Field label="事件" htmlFor="notify-events">
            <Textarea id="notify-events" rows={2} value={form.events} onChange={(e) => setForm({ ...form, events: e.target.value })} />
          </Field>
          <Field label="状态" htmlFor="notify-enabled">
            <Select id="notify-enabled" value={form.enabled ? '1' : '0'} onChange={(e) => setForm({ ...form, enabled: e.target.value === '1' })}>
              <option value="1">启用</option><option value="0">停用</option>
            </Select>
          </Field>
        </div>
      </Modal>

      <ConfirmDialog open={!!deleting} title="删除通知机器人" message={`确认删除“${deleting?.name || ''}”？`}
        confirmText="删除" tone="danger" loading={deleteBusy} onConfirm={() => void remove()} onClose={() => setDeleting(null)} />
    </div>
  );
}
