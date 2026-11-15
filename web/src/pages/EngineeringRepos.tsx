import { useCallback, useEffect, useState } from 'react';
import { engineeringReposApi } from '../api';
import type { EngineeringRepo, EngineeringRepoMethod, RepoScanResult } from '../api/engineeringRepos';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill, SkeletonRows } from '../components/ui';
import { Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const EMPTY_FORM = { name: '', repoType: 'API', repoUrl: '', branchName: 'main', rootDirectory: '.', webhookSecret: '' };

export function EngineeringReposPage() {
  const [repos, setRepos] = useState<EngineeringRepo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [scanning, setScanning] = useState('');
  const [methodsOf, setMethodsOf] = useState<EngineeringRepo | null>(null);
  const [methods, setMethods] = useState<EngineeringRepoMethod[]>([]);
  const [methodsLoading, setMethodsLoading] = useState(false);
  const [lastScan, setLastScan] = useState<RepoScanResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setRepos(await engineeringReposApi.list());
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const create = async () => {
    if (!form.name.trim() || !form.repoUrl.trim()) { setFormError('名称和 Git 地址不能为空'); return; }
    setSaving(true);
    setFormError('');
    try {
      const repo = await engineeringReposApi.create({ ...form, name: form.name.trim(), repoUrl: form.repoUrl.trim() });
      toastSuccess(`Git 工程已添加：${repo.name}`);
      setFormOpen(false);
      setForm(EMPTY_FORM);
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const scan = async (repo: EngineeringRepo) => {
    setScanning(repo.id);
    setLastScan(null);
    try {
      const result = await engineeringReposApi.scan(repo.id);
      setLastScan(result);
      if (result.errors?.length) toastError(result.errors.join('；'));
      else toastSuccess(`扫描完成：${result.scanned} 个方法，新增 ${result.created}，更新 ${result.updated}`);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setScanning('');
    }
  };

  const runMethod = async (method: EngineeringRepoMethod) => {
    if (!methodsOf) return;
    const deviceId = window.prompt('设备 ID（留空自动选择可用设备）') || '';
    try {
      const result = await engineeringReposApi.runMethod(methodsOf.id, method.id, deviceId);
      toastSuccess(`已下发任务 ${result.taskId}`);
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const openMethods = async (repo: EngineeringRepo) => {
    setMethodsOf(repo);
    setMethodsLoading(true);
    try {
      setMethods(await engineeringReposApi.methods(repo.id));
    } catch (err) {
      toastError(safeErrorMessage(err));
      setMethods([]);
    } finally {
      setMethodsLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <PageHeading title="Git 测试工程" description="接入独立 API/UI 自动化工程仓库，按约定扫描方法并加载到平台。"
        actions={<Button variant="primary" icon="plus" onClick={() => { setFormError(''); setFormOpen(true); }}>接入仓库</Button>} />
      <Callout tone="info" title="仓库约定">
        API 仓库放 <code>easytest.api.yaml</code>，UI 仓库放 <code>easytest.ui.yaml</code>；支持 GitHub/GitLab webhook 和定时 commit 检测。
      </Callout>
      {lastScan ? <Callout tone={lastScan.errors?.length ? 'warning' : 'success'} title="最近扫描">
        扫描 {lastScan.scanned}，新增 {lastScan.created}，更新 {lastScan.updated}，移除 {lastScan.removed}
      </Callout> : null}
      {error ? <ErrorPanel message={error} onRetry={() => void load()} /> : null}
      <Card>
        <CardHead title="工程仓库" description={`${repos.length} 个仓库`} icon="code" />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>工程</th><th>类型</th><th>分支</th><th>方法</th><th>最近扫描</th><th>操作</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={4} cols={6} /> : repos.length === 0 ? (
                <tr><td colSpan={6}><EmptyState compact icon="code" title="尚未接入 Git 工程" description="填写仓库地址和分支开始扫描" /></td></tr>
              ) : repos.map((repo) => (
                <tr key={repo.id}>
                  <td>{repo.name}<div className="cell-sub mono">{repo.repoUrl}</div></td>
                  <td><Pill tone="blue">{repo.repoType}</Pill></td>
                  <td className="mono">{repo.branchName}</td>
                  <td>{repo.methodCount ?? 0}</td>
                  <td>{repo.lastScanAt ? formatDateTime(repo.lastScanAt) : '未扫描'}{repo.lastError ? <div className="cell-sub text-warning">{repo.lastError}</div> : null}</td>
                  <td><div className="row-actions">
                    <Button size="sm" variant="secondary" loading={scanning === repo.id} onClick={() => void scan(repo)}>扫描</Button>
                    <Button size="sm" variant="ghost" onClick={() => void openMethods(repo)}>方法</Button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Modal open={formOpen} title="接入 Git 测试工程" size="lg" onClose={() => { if (!saving) setFormOpen(false); }} closeOnOverlay={!saving}
        footer={<><Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>取消</Button>
          <Button variant="primary" loading={saving} onClick={() => void create()}>添加</Button></>}>
        <div className="form-stack">
          {formError ? <Callout tone="error" title="添加失败">{formError}</Callout> : null}
          <div className="form-grid-2">
            <Field label="工程名称" required><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="仓库类型"><select className="input" value={form.repoType} onChange={(e) => setForm({ ...form, repoType: e.target.value })}>
              <option value="API">API 自动化工程</option><option value="UI">UI 自动化工程</option>
            </select></Field>
          </div>
          <Field label="Git 仓库地址" required><Input placeholder="https://github.com/org/repo.git 或 git@github.com:org/repo.git" value={form.repoUrl} onChange={(e) => setForm({ ...form, repoUrl: e.target.value })} /></Field>
          <div className="form-grid-2">
            <Field label="分支"><Input value={form.branchName} onChange={(e) => setForm({ ...form, branchName: e.target.value })} /></Field>
            <Field label="扫描目录"><Input value={form.rootDirectory} onChange={(e) => setForm({ ...form, rootDirectory: e.target.value })} /></Field>
          </div>
          <Field label="Webhook secret（可选）" hint="配置后可用 X-Webhook-Token 或 GitHub HMAC 触发重扫">
            <Input value={form.webhookSecret} onChange={(e) => setForm({ ...form, webhookSecret: e.target.value })} />
          </Field>
        </div>
      </Modal>
      <Modal open={!!methodsOf} title={`方法目录 · ${methodsOf?.name || ''}`} size="lg" onClose={() => setMethodsOf(null)}
        footer={<Button variant="secondary" onClick={() => setMethodsOf(null)}>关闭</Button>}>
        {methodsLoading ? <SkeletonRows rows={4} cols={4} /> : methods.length === 0 ? (
          <EmptyState compact icon="code" title="暂无方法" description="先扫描仓库，或检查 manifest 约定" />
        ) : (
          <div className="table-wrap"><table className="table">
            <thead><tr><th>方法</th><th>类型</th><th>methodPath</th><th>输入/输出</th><th>操作</th></tr></thead>
            <tbody>{methods.map((method) => (
              <tr key={method.id}>
                <td>{method.name}<div className="cell-sub">{method.description}</div></td>
                <td><Pill tone="blue">{method.kind || methodsOf?.repoType}</Pill></td>
                <td className="mono">{method.methodPath}</td>
                <td><div className="pill-row">
                  {method.inputs?.map((v) => <Pill key={`i-${v}`} tone="blue">in:{v}</Pill>)}
                  {method.outputs?.map((v) => <Pill key={`o-${v}`} tone="green">out:{v}</Pill>)}
                </div></td>
                <td>{method.kind === 'UI' ? <Button size="sm" variant="secondary" onClick={() => void runMethod(method)}>运行</Button> : <span className="text-muted">组件步骤</span>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </Modal>
    </div>
  );
}
