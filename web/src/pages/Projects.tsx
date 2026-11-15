import { useCallback, useEffect, useState } from 'react';
import { projectsApi } from '../api';
import type { Project, ProjectMember } from '../api/projects';
import { canManageCases, useAuth } from '../auth';
import { safeErrorMessage } from '../utils';
import { Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill, Select, SkeletonRows, Textarea } from '../components/ui';
import { Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

export function ProjectsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [items, setItems] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ name: '', key: '', description: '' });
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [membersOf, setMembersOf] = useState<Project | null>(null);
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [memberName, setMemberName] = useState('');
  const [memberRole, setMemberRole] = useState('TESTER');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await projectsApi.list());
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const create = async () => {
    if (!form.name.trim()) { setFormError('项目名称不能为空'); return; }
    setSaving(true);
    setFormError('');
    try {
      await projectsApi.create({ name: form.name.trim(), key: form.key.trim() || undefined, description: form.description });
      setCreateOpen(false);
      setForm({ name: '', key: '', description: '' });
      toastSuccess('项目已创建');
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const openMembers = async (project: Project) => {
    setMembersOf(project);
    setMembers([]);
    try {
      setMembers(await projectsApi.members(project.id));
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const addMember = async () => {
    if (!membersOf || !memberName.trim()) return;
    try {
      await projectsApi.addMember(membersOf.id, { username: memberName.trim(), role: memberRole });
      setMemberName('');
      setMembers(await projectsApi.members(membersOf.id));
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  return (
    <div className="page-stack">
      <PageHeading title="项目" description="部门级项目/空间；用例、接口用例、API 场景、测试计划按项目隔离。"
        actions={canWrite ? <Button variant="primary" icon="plus" onClick={() => { setFormError(''); setCreateOpen(true); }}>新建项目</Button> : undefined} />
      {error ? <ErrorPanel message={error} onRetry={() => void load()} /> : null}
      <Card>
        <CardHead title="项目列表" description="右上角项目切换器决定当前工作空间" icon="cases" />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>项目</th><th>Key</th><th>成员</th><th>描述</th><th>操作</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={3} cols={5} /> : items.length === 0 ? (
                <tr><td colSpan={5}><EmptyState compact icon="cases" title="暂无项目" description="默认项目由系统初始化" /></td></tr>
              ) : items.map((project) => (
                <tr key={project.id}>
                  <td>{project.name}<div className="cell-sub mono">{project.id}</div></td>
                  <td>{project.key || '—'}</td>
                  <td>{project.memberCount ?? 0}</td>
                  <td>{project.description || '—'}</td>
                  <td><Button size="sm" variant="ghost" onClick={() => void openMembers(project)}>成员</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Modal open={createOpen} title="新建项目" onClose={() => { if (!saving) setCreateOpen(false); }} closeOnOverlay={!saving}
        footer={<><Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={saving}>取消</Button>
          <Button variant="primary" loading={saving} onClick={() => void create()}>创建</Button></>}>
        <div className="form-stack">
          {formError ? <Callout tone="error" title="创建失败">{formError}</Callout> : null}
          <Field label="项目名称" htmlFor="project-name" required><Input id="project-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
          <Field label="Key" htmlFor="project-key"><Input id="project-key" value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value })} /></Field>
          <Field label="描述" htmlFor="project-desc"><Textarea id="project-desc" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
        </div>
      </Modal>
      <Modal open={!!membersOf} title={`项目成员 · ${membersOf?.name || ''}`} onClose={() => setMembersOf(null)}
        footer={<Button variant="secondary" onClick={() => setMembersOf(null)}>关闭</Button>}>
        <div className="form-stack">
          {canWrite ? <div className="row-actions">
            <Input placeholder="用户名" value={memberName} onChange={(e) => setMemberName(e.target.value)} />
            <Select value={memberRole} onChange={(e) => setMemberRole(e.target.value)}>
              <option value="TESTER">TESTER</option><option value="ADMIN">ADMIN</option><option value="VIEWER">VIEWER</option>
            </Select>
            <Button size="sm" variant="secondary" onClick={() => void addMember()}>添加成员</Button>
          </div> : null}
          <div className="table-wrap">
            <table className="table"><thead><tr><th>用户</th><th>角色</th></tr></thead>
              <tbody>{members.map((member) => <tr key={member.id}><td>{member.username}</td><td><Pill tone="blue">{member.role}</Pill></td></tr>)}</tbody>
            </table>
          </div>
        </div>
      </Modal>
    </div>
  );
}
