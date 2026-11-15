import { useCallback, useEffect, useState } from 'react';
import { scriptsApi } from '../api';
import type { ScriptInfo } from '../api/types';
import { SCRIPT_TYPE_LABEL } from '../constants';
import { canManageCases, useAuth } from '../auth';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill, Select, SkeletonRows, Textarea } from '../components/ui';
import { Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const DEFAULT_MANIFEST = `schemaVersion: 1
id: ui.login
name: 登录脚本
description: 使用测试工程代码驱动登录
entryCommand: sh run.sh
inputs: [username, password]
outputs: [token]
`;

export function ScriptsPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [items, setItems] = useState<ScriptInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [scriptType, setScriptType] = useState('UIAUTOMATOR2');
  const [entryCommand, setEntryCommand] = useState('sh run.sh');
  const [manifest, setManifest] = useState(DEFAULT_MANIFEST);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const page = await scriptsApi.list();
      setItems(page.items);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const upload = async () => {
    if (!file) { setFormError('请选择脚本 zip 包'); return; }
    setSaving(true);
    setFormError('');
    try {
      const created = await scriptsApi.upload({ file, scriptType, entryCommand, manifest });
      toastSuccess(`脚本已上传${created.contractId ? ` · 契约 ${created.contractId}` : ''}`);
      setOpen(false);
      setFile(null);
      await load();
    } catch (err) {
      setFormError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="page-stack">
      <PageHeading title="UI 脚本工程" description="上传 zip 脚本包并声明 manifest 契约；用例可关联脚本并以 SCRIPT 模式执行。"
        actions={canWrite ? <Button variant="primary" icon="plus" onClick={() => { setFormError(''); setOpen(true); }}>上传脚本</Button> : undefined} />
      <Callout tone="info" title="脚本契约">
        脚本包入口、契约 ID、输入/输出在 manifest 中声明；上传后用例详情会展示脚本契约。
      </Callout>
      {error ? <ErrorPanel message={error} onRetry={() => void load()} /> : null}
      <Card>
        <CardHead title="脚本列表" description={`${items.length} 个脚本`} icon="code" />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>脚本</th><th>类型</th><th>契约</th><th>输入/输出</th><th>入口命令</th><th>上传时间</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={4} cols={6} /> : items.length === 0 ? (
                <tr><td colSpan={6}><EmptyState compact icon="code" title="暂无脚本" description="上传 UI 脚本 zip 包开始" /></td></tr>
              ) : items.map((script) => (
                <tr key={script.id}>
                  <td>{script.name}<div className="cell-sub mono">{script.id}</div></td>
                  <td><Pill tone="blue">{SCRIPT_TYPE_LABEL[script.scriptType] || script.scriptType}</Pill></td>
                  <td>{script.contractId || '—'}{script.description ? <div className="cell-sub">{script.description}</div> : null}</td>
                  <td>
                    <div className="pill-row">
                      {script.inputs?.map((v) => <Pill key={`in-${v}`} tone="blue">in:{v}</Pill>)}
                      {script.outputs?.map((v) => <Pill key={`out-${v}`} tone="green">out:{v}</Pill>)}
                    </div>
                  </td>
                  <td className="mono">{script.entryCommand || '—'}</td>
                  <td>{formatDateTime(script.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Modal open={open} title="上传 UI 脚本" size="lg" onClose={() => { if (!saving) setOpen(false); }} closeOnOverlay={!saving}
        footer={<><Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>取消</Button>
          <Button variant="primary" loading={saving} onClick={() => void upload()}>上传</Button></>}>
        <div className="form-stack">
          {formError ? <Callout tone="error" title="上传失败">{formError}</Callout> : null}
          <Field label="脚本 zip 包" required>
            <input type="file" accept=".zip" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          </Field>
          <div className="form-grid-2">
            <Field label="脚本类型"><Select value={scriptType} onChange={(e) => setScriptType(e.target.value)}>
              <option value="UIAUTOMATOR2">uiautomator2</option><option value="TESTNG">TestNG</option>
              <option value="PYTEST">Pytest</option><option value="CUSTOM">自定义</option>
            </Select></Field>
            <Field label="入口命令"><Input value={entryCommand} onChange={(e) => setEntryCommand(e.target.value)} /></Field>
          </div>
          <Field label="manifest（可选）" hint="YAML：id、description、entryCommand、inputs、outputs">
            <Textarea rows={12} className="yaml-editor" value={manifest} onChange={(e) => setManifest(e.target.value)} />
          </Field>
        </div>
      </Modal>
    </div>
  );
}
