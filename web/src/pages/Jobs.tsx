import { useCallback, useEffect, useState } from 'react';
import { jobsApi, plansApi, uiexecApi } from '../api';
import type { Device, Plan, TestJob } from '../api/types';
import { canManageCases, useAuth } from '../auth';
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
  Pill,
  Select,
  SkeletonRows,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const PRESETS = [
  { label: '每小时', cron: '0 0 * * * *' },
  { label: '每天 02:00', cron: '0 0 2 * * *' },
  { label: '每周一 03:00', cron: '0 0 3 * * MON' },
  { label: '每 30 分钟', cron: '0 */30 * * * *' },
];

interface FormState {
  name: string;
  planId: string;
  cronExpression: string;
  deviceId: string;
  deviceTags: string;
  enabled: boolean;
}

const EMPTY: FormState = {
  name: '',
  planId: '',
  cronExpression: '0 0 2 * * *',
  deviceId: '',
  deviceTags: '',
  enabled: true,
};

export function JobsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [items, setItems] = useState<TestJob[]>([]);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<TestJob | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [running, setRunning] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<TestJob | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await jobsApi.list('', 1, 100);
      setItems(data.items);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    void plansApi.list('', 1, 200).then((data) => setPlans(data.items)).catch(() => setPlans([]));
    void uiexecApi.devices().then((data) => setDevices(data.items)).catch(() => setDevices([]));
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY, planId: plans[0]?.id || '' });
    setFormError('');
    setModalOpen(true);
  };

  const openEdit = (job: TestJob) => {
    setEditing(job);
    setForm({
      name: job.name,
      planId: job.planId,
      cronExpression: job.cronExpression,
      deviceId: job.deviceId || '',
      deviceTags: job.deviceTags || '',
      enabled: job.enabled,
    });
    setFormError('');
    setModalOpen(true);
  };

  const save = async () => {
    if (!form.name.trim() || !form.planId || !form.cronExpression.trim()) {
      setFormError('名称、计划、Cron 均不能为空');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      const input = {
        name: form.name.trim(),
        planId: form.planId,
        cronExpression: form.cronExpression.trim(),
        deviceId: form.deviceId || null,
        deviceTags: form.deviceTags.trim() || null,
        enabled: form.enabled,
      };
      if (editing) {
        await jobsApi.update(editing.id, input);
        toastSuccess('任务已更新');
      } else {
        await jobsApi.create(input);
        toastSuccess('任务已创建');
      }
      setModalOpen(false);
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const runNow = async (job: TestJob) => {
    setRunning(job.id);
    try {
      const updated = await jobsApi.run(job.id);
      toastSuccess('任务已触发');
      await load();
      if (updated.lastRunId) {
        window.location.hash = `#/runs/${updated.lastRunId}`;
      }
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setRunning(null);
    }
  };

  const toggle = async (job: TestJob) => {
    try {
      await jobsApi.toggle(job.id, !job.enabled);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setDeletingBusy(true);
    try {
      await jobsApi.remove(deleting.id);
      toastSuccess('任务已删除');
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
        title="定时任务"
        description="按 Cron 定时触发测试计划（套件），可指定设备或设备标签；也可立即执行。"
        actions={
          canWrite ? (
            <Button variant="primary" icon="plus" onClick={openCreate}>
              新建任务
            </Button>
          ) : undefined
        }
      />

      <Card>
        <CardHead title="任务列表" description="调度器每 15 秒检查一次到期任务" icon="clock" />
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>名称</th>
                <th>计划</th>
                <th>Cron</th>
                <th>设备</th>
                <th>状态</th>
                <th>上次执行</th>
                <th>下次执行</th>
                <th className="th-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <SkeletonRows rows={4} cols={8} />
              ) : error ? (
                <tr><td colSpan={8}><ErrorPanel message={error} onRetry={() => void load()} /></td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={8}><EmptyState icon="clock" title="暂无定时任务" description="点击右上角新建任务" /></td></tr>
              ) : (
                items.map((job) => (
                  <tr key={job.id}>
                    <td>{job.name}<div className="cell-sub mono">{job.id}</div></td>
                    <td>{job.planName || job.planId}</td>
                    <td className="mono">{job.cronExpression}</td>
                    <td className="mono">{job.deviceId || job.deviceTags || '任意设备'}</td>
                    <td><Pill tone={job.enabled ? 'green' : 'red'} dot>{job.enabled ? '启用' : '暂停'}</Pill></td>
                    <td>{job.lastRunAt ? formatDateTime(job.lastRunAt) : '—'}</td>
                    <td>{job.nextRunAt ? formatDateTime(job.nextRunAt) : '—'}</td>
                    <td>
                      <div className="row-actions">
                        <Button size="sm" variant="ghost" loading={running === job.id} onClick={() => void runNow(job)}>
                          立即执行
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => openEdit(job)} disabled={!canWrite}>编辑</Button>
                        <Button size="sm" variant="ghost" onClick={() => void toggle(job)} disabled={!canWrite}>
                          {job.enabled ? '暂停' : '启用'}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setDeleting(job)} disabled={!canWrite}>删除</Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑定时任务' : '新建定时任务'}
        description="Cron 使用 Spring 6 段格式：秒 分 时 日 月 周。"
        onClose={() => { if (!saving) setModalOpen(false); }}
        size="md"
        closeOnOverlay={!saving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>取消</Button>
            <Button variant="primary" loading={saving} onClick={() => void save()}>{editing ? '保存' : '创建'}</Button>
          </>
        }
      >
        <div className="form-stack">
          {formError ? <Callout tone="error" title="操作失败">{formError}</Callout> : null}
          <Field label="任务名称" htmlFor="job-name" required>
            <Input id="job-name" value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
          </Field>
          <Field label="测试计划" htmlFor="job-plan" required>
            <Select id="job-plan" value={form.planId} onChange={(event) => setForm((prev) => ({ ...prev, planId: event.target.value }))}>
              <option value="">选择计划</option>
              {plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name}</option>)}
            </Select>
          </Field>
          <Field label="Cron" htmlFor="job-cron" required hint="例如 0 0 2 * * * 表示每天 02:00">
            <Input id="job-cron" value={form.cronExpression} onChange={(event) => setForm((prev) => ({ ...prev, cronExpression: event.target.value }))} />
          </Field>
          <div className="row-actions">
            {PRESETS.map((preset) => (
              <Button key={preset.cron} size="sm" variant="ghost"
                      onClick={() => setForm((prev) => ({ ...prev, cronExpression: preset.cron }))}>
                {preset.label}
              </Button>
            ))}
          </div>
          <div className="form-grid-2">
            <Field label="目标设备" htmlFor="job-device" hint="留空则由执行器选设备">
              <Select id="job-device" value={form.deviceId} onChange={(event) => setForm((prev) => ({ ...prev, deviceId: event.target.value }))}>
                <option value="">任意在线设备</option>
                {devices.filter((device) => device.online).map((device) => (
                  <option key={device.deviceId} value={device.deviceId}>{device.serial} · {device.name || device.model}</option>
                ))}
              </Select>
            </Field>
            <Field label="设备标签" htmlFor="job-tags" hint="逗号分隔，作为设备选择条件">
              <Input id="job-tags" value={form.deviceTags} onChange={(event) => setForm((prev) => ({ ...prev, deviceTags: event.target.value }))} />
            </Field>
          </div>
          <Field label="状态" htmlFor="job-enabled">
            <Select id="job-enabled" value={form.enabled ? '1' : '0'} onChange={(event) => setForm((prev) => ({ ...prev, enabled: event.target.value === '1' }))}>
              <option value="1">启用</option>
              <option value="0">暂停</option>
            </Select>
          </Field>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleting}
        title="删除定时任务"
        message={`确认删除“${deleting?.name || ''}”？历史运行记录保留。`}
        confirmText="删除"
        tone="danger"
        loading={deletingBusy}
        onConfirm={() => void remove()}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
