import { useCallback, useEffect, useState } from 'react';
import { engineeringMethodsApi } from '../api';
import type { EngineeringMethod } from '../api/engineering';
import { formatDateTime, safeErrorMessage } from '../utils';
import { Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, PageHeading, Pill, SkeletonRows, Textarea } from '../components/ui';
import { Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

export function EngineeringMethodsPage() {
  const [items, setItems] = useState<EngineeringMethod[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [manifest, setManifest] = useState('schemaVersion: 1\nmethods: []\n');
  const [importError, setImportError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await engineeringMethodsApi.list());
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const sync = async () => {
    setBusy(true);
    try {
      const result = await engineeringMethodsApi.sync();
      toastSuccess(`已同步 ${result.imported} 个工程方法`);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const submitImport = async () => {
    setBusy(true);
    setImportError('');
    try {
      const result = await engineeringMethodsApi.importManifest(manifest);
      toastSuccess(`已导入 ${result.imported} 个工程方法`);
      setImportOpen(false);
      await load();
    } catch (err) {
      setImportError(safeErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page-stack">
      <PageHeading title="工程方法" description="测试工程契约方法注册表：methodPath、行为说明、输入/输出变量。"
        actions={<div className="row-actions">
          <Button variant="secondary" onClick={() => { setImportError(''); setImportOpen(true); }}>导入 manifest</Button>
          <Button variant="primary" icon="refresh" loading={busy} onClick={() => void sync()}>从 Agent 同步</Button>
        </div>} />
      <Callout tone="info" title="契约源">
        工程方法由测试工程 `@EasyContract` 注解产生；Agent 加载 JAR 后平台可同步，或导入 ContractManifestGenerator 生成的 manifest。
      </Callout>
      {error ? <ErrorPanel message={error} onRetry={() => void load()} /> : null}
      <Card>
        <CardHead title="方法列表" description={`${items.length} 个方法`} icon="code"
          actions={<Button size="sm" variant="ghost" icon="refresh" loading={loading} onClick={() => void load()}>刷新</Button>} />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>方法</th><th>契约 ID</th><th>行为说明</th><th>输入/输出</th><th>来源</th><th>更新时间</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={4} cols={6} /> : items.length === 0 ? (
                <tr><td colSpan={6}><EmptyState compact icon="code" title="暂无工程方法" description="启动 API Agent 后点击「从 Agent 同步」，或导入 manifest" /></td></tr>
              ) : items.map((method) => (
                <tr key={method.id || method.methodPath}>
                  <td>{method.name}<div className="cell-sub mono">{method.methodPath}</div></td>
                  <td className="mono">{method.id || '—'}</td>
                  <td>{method.description || '—'}</td>
                  <td><div className="pill-row">{method.inputs?.map((v) => <Pill key={`i-${v}`} tone="blue">in:{v}</Pill>)}{method.outputs?.map((v) => <Pill key={`o-${v}`} tone="green">out:{v}</Pill>)}</div></td>
                  <td><Pill tone="gray">{method.source || '—'}</Pill></td>
                  <td>{formatDateTime(method.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Modal open={importOpen} title="导入工程方法 manifest" size="lg"
        onClose={() => { if (!busy) setImportOpen(false); }} closeOnOverlay={!busy}
        footer={<><Button variant="secondary" onClick={() => setImportOpen(false)} disabled={busy}>取消</Button>
          <Button variant="primary" loading={busy} onClick={() => void submitImport()}>导入</Button></>}>
        <div className="form-stack">
          {importError ? <Callout tone="error" title="导入失败">{importError}</Callout> : null}
          <Field label="manifest YAML" hint="ContractManifestGenerator 生成的 easytest-contract.yaml">
            <Textarea rows={16} className="yaml-editor" value={manifest} onChange={(e) => setManifest(e.target.value)} />
          </Field>
        </div>
      </Modal>
    </div>
  );
}
