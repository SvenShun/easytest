import { useCallback, useEffect, useState } from 'react';
import { apiEnvironmentsApi, apiScenariosApi } from '../api';
import type { ApiEnvironment } from '../api/types';
import type { ApiScenario, ScenarioRun, ScenarioStep } from '../api/scenarios';
import { canManageCases, useAuth } from '../auth';
import { formatDateTime, safeErrorMessage } from '../utils';
import {
  Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill,
  Select, SkeletonRows, StatusPill, Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const DEFAULT_YAML = `name: login-and-query
variables:
  username: tester
  password: test123
environment:
  baseUrl: http://127.0.0.1:8080
  datasources:
    main:
      url: jdbc:h2:mem:api_scenario;DB_CLOSE_DELAY=-1
      username: sa
      password: ""
steps:
  - name: 登录
    type: http
    method: POST
    path: /api/v1/auth/login
    headers:
      Content-Type: application/json
    body:
      username: "\${username}"
      password: "\${password}"
    extract:
      token: $.token
      userId: $.user.id
    assertions:
      - type: STATUS_CODE
        operator: EQ
        expected: 200
      - type: JSON_PATH
        path: $.user.username
        operator: EQ
        expected: tester
  - name: 查询数据库
    type: sql
    datasource: main
    query: SELECT 1 AS n
    extract:
      n: rows[0].n
    assertions:
      - type: SQL_ROW_COUNT
        operator: GT
        expected: 0
  - name: 组合变量
    type: groovy
    script: |
      vars.put("combo", vars.get("n") + "-" + vars.get("userId"))
  - name: 断言变量
    type: assert
    assertions:
      - type: VARIABLE
        path: combo
        operator: CONTAINS
        expected: "1-"
`;

interface EditorState {
  name: string;
  description: string;
  yaml: string;
  environmentId: string;
  enabled: boolean;
}

const EMPTY_EDITOR: EditorState = {
  name: '', description: '', yaml: DEFAULT_YAML, environmentId: '', enabled: true,
};

export function ApiScenariosPage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [items, setItems] = useState<ApiScenario[]>([]);
  const [environments, setEnvironments] = useState<ApiEnvironment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<ApiScenario | null>(null);
  const [editor, setEditor] = useState<EditorState>(EMPTY_EDITOR);
  const [saving, setSaving] = useState(false);
  const [editorError, setEditorError] = useState('');
  const [validating, setValidating] = useState(false);
  const [validation, setValidation] = useState<{ valid: boolean; message: string; stepCount: number; stepTypes: string[] } | null>(null);
  const [runOpen, setRunOpen] = useState(false);
  const [running, setRunning] = useState(false);
  const [run, setRun] = useState<ScenarioRun | null>(null);
  const [runError, setRunError] = useState('');
  const [runEnv, setRunEnv] = useState('');
  const [runTimeout, setRunTimeout] = useState('120');
  const [runVariables, setRunVariables] = useState('{}');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [history, setHistory] = useState<ScenarioRun[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [deleting, setDeleting] = useState<ApiScenario | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await apiScenariosApi.list('', 1, 100);
      setItems(data.items);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    void apiEnvironmentsApi.list('', 1, 100).then((data) => setEnvironments(data.items)).catch(() => undefined);
  }, [load]);

  useEffect(() => {
    if (!run || !runOpen) return;
    if (!['PENDING', 'RUNNING', 'DISPATCHED'].includes(run.status)) return;
    const timer = window.setInterval(() => {
      apiScenariosApi.runDetail(run.id).then(setRun).catch(() => undefined);
    }, 1500);
    return () => window.clearInterval(timer);
  }, [run?.id, run?.status, runOpen]);

  const openCreate = () => {
    setEditing(null);
    setEditor({ ...EMPTY_EDITOR });
    setValidation(null);
    setEditorError('');
    setEditorOpen(true);
  };

  const openEdit = (item: ApiScenario) => {
    setEditing(item);
    setEditor({
      name: item.name,
      description: item.description || '',
      yaml: item.yaml,
      environmentId: item.environmentId || '',
      enabled: item.enabled,
    });
    setValidation(null);
    setEditorError('');
    setEditorOpen(true);
  };

  const validate = async () => {
    setValidating(true);
    setValidation(null);
    try {
      setValidation(await apiScenariosApi.validate(editor.yaml));
    } catch (err) {
      setEditorError(safeErrorMessage(err));
    } finally {
      setValidating(false);
    }
  };

  const save = async () => {
    if (!editor.name.trim() || !editor.yaml.trim()) {
      setEditorError('名称和 YAML 不能为空');
      return;
    }
    setSaving(true);
    setEditorError('');
    try {
      const input = {
        name: editor.name.trim(),
        description: editor.description,
        yaml: editor.yaml,
        environmentId: editor.environmentId || null,
        enabled: editor.enabled,
      };
      if (editing) await apiScenariosApi.update(editing.id, input);
      else await apiScenariosApi.create(input);
      toastSuccess(editing ? '场景已更新' : '场景已创建');
      setEditorOpen(false);
      await load();
    } catch (err) {
      setEditorError(safeErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const openRun = (item: ApiScenario) => {
    setRun(null);
    setRunError('');
    setRunEnv(item.environmentId || '');
    setRunTimeout('120');
    setRunVariables('{}');
    setEditor({ ...EMPTY_EDITOR, name: item.name });
    setEditing(item);
    setRunOpen(true);
  };

  const execute = async () => {
    if (!editing) return;
    setRunning(true);
    setRunError('');
    let variables: Record<string, unknown> = {};
    try {
      variables = JSON.parse(runVariables || '{}');
    } catch {
      setRunError('变量 JSON 不合法');
      setRunning(false);
      return;
    }
    try {
      const result = await apiScenariosApi.run(editing.id, {
        environmentId: runEnv || null,
        variables,
        timeoutSec: Number(runTimeout) || 120,
      });
      setRun(result);
    } catch (err) {
      setRunError(safeErrorMessage(err));
    } finally {
      setRunning(false);
    }
  };

  const openHistory = async (item: ApiScenario) => {
    setEditing(item);
    setHistoryOpen(true);
    setHistoryLoading(true);
    try {
      const data = await apiScenariosApi.runs(item.id, 1, 50);
      setHistory(data.items);
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setHistoryLoading(false);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await apiScenariosApi.remove(deleting.id);
      toastSuccess('场景已删除');
      setDeleting(null);
      await load();
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setDeleteBusy(false);
    }
  };

  const steps: ScenarioStep[] = Array.isArray(run?.steps) ? run.steps as ScenarioStep[] : [];

  return (
    <div className="page-stack">
      <PageHeading
        title="API 场景"
        description="YAML 驱动多步骤接口自动化：HTTP、SQL、Groovy、变量传递、断言。"
        actions={canWrite ? <Button variant="primary" icon="plus" onClick={openCreate}>新建场景</Button> : undefined}
      />
      <Card>
        <CardHead title="场景列表" description="YAML 是唯一事实来源；运行报告包含每步请求/响应/断言/提取变量" icon="code"
          actions={<Button size="sm" variant="ghost" icon="refresh" loading={loading} onClick={() => void load()}>刷新</Button>} />
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>名称</th><th>环境</th><th>状态</th><th>更新时间</th><th>创建人</th><th className="th-right">操作</th></tr></thead>
            <tbody>
              {loading ? <SkeletonRows rows={4} cols={6} /> : error ? (
                <tr><td colSpan={6}><ErrorPanel message={error} onRetry={() => void load()} /></td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={6}><EmptyState icon="code" title="暂无 API 场景" description="新建一个 YAML 场景，组合 HTTP/SQL/Groovy/断言步骤" /></td></tr>
              ) : items.map((item) => (
                <tr key={item.id}>
                  <td>{item.name}<div className="cell-sub mono">{item.id}</div></td>
                  <td>{environments.find((env) => env.id === item.environmentId)?.name || item.environmentId || 'YAML 内配置'}</td>
                  <td><Pill tone={item.enabled ? 'green' : 'red'} dot>{item.enabled ? '启用' : '停用'}</Pill></td>
                  <td>{formatDateTime(item.updatedAt)}</td>
                  <td>{item.createdBy || '—'}</td>
                  <td><div className="row-actions">
                    <Button size="sm" variant="primary" onClick={() => openRun(item)} disabled={!canWrite}>运行</Button>
                    <Button size="sm" variant="ghost" onClick={() => openEdit(item)} disabled={!canWrite}>编辑</Button>
                    <Button size="sm" variant="ghost" onClick={() => void openHistory(item)}>历史</Button>
                    <Button size="sm" variant="ghost" onClick={() => setDeleting(item)} disabled={!canWrite}>删除</Button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal open={editorOpen} title={editing ? '编辑 API 场景' : '新建 API 场景'} size="xl"
        onClose={() => { if (!saving) setEditorOpen(false); }} closeOnOverlay={!saving}
        footer={<><Button variant="secondary" onClick={() => setEditorOpen(false)} disabled={saving}>取消</Button>
          <Button variant="secondary" loading={validating} onClick={() => void validate()}>校验 YAML</Button>
          <Button variant="primary" loading={saving} onClick={() => void save()}>{editing ? '保存' : '创建'}</Button></>}>
        <div className="form-stack">
          {editorError ? <Callout tone="error" title="操作失败">{editorError}</Callout> : null}
          {validation ? (
            <Callout tone={validation.valid ? 'success' : 'error'} title={validation.valid ? 'YAML 有效' : 'YAML 无效'}>
              {validation.stepCount} 个步骤 · {validation.stepTypes.join(' / ')}
            </Callout>
          ) : null}
          <div className="form-grid-2">
            <Field label="场景名称" htmlFor="scenario-name" required><Input id="scenario-name" value={editor.name} onChange={(e) => setEditor({ ...editor, name: e.target.value })} /></Field>
            <Field label="环境" htmlFor="scenario-env"><Select id="scenario-env" value={editor.environmentId} onChange={(e) => setEditor({ ...editor, environmentId: e.target.value })}>
              <option value="">不绑定环境（使用 YAML 内配置）</option>
              {environments.map((env) => <option key={env.id} value={env.id}>{env.name}</option>)}
            </Select></Field>
          </div>
          <Field label="描述" htmlFor="scenario-desc"><Input id="scenario-desc" value={editor.description} onChange={(e) => setEditor({ ...editor, description: e.target.value })} /></Field>
          <Field label="YAML" htmlFor="scenario-yaml" hint="支持 http / sql / groovy / set / assert / wait；变量用 ${var} 或 {{var}}">
            <Textarea id="scenario-yaml" className="yaml-editor" rows={26} value={editor.yaml} onChange={(e) => setEditor({ ...editor, yaml: e.target.value })} />
          </Field>
          <Field label="状态" htmlFor="scenario-enabled"><Select id="scenario-enabled" value={editor.enabled ? '1' : '0'} onChange={(e) => setEditor({ ...editor, enabled: e.target.value === '1' })}><option value="1">启用</option><option value="0">停用</option></Select></Field>
        </div>
      </Modal>

      <Modal open={runOpen} title={`运行场景 · ${editing?.name || ''}`} size="xl"
        onClose={() => { if (!running) setRunOpen(false); }} closeOnOverlay={!running}
        footer={<><Button variant="secondary" onClick={() => setRunOpen(false)} disabled={running}>关闭</Button>
          <Button variant="primary" loading={running} onClick={() => void execute()}>开始运行</Button></>}>
        <div className="form-stack">
          {runError ? <Callout tone="error" title="运行失败">{runError}</Callout> : null}
          {!run ? (
            <>
              <Field label="环境覆盖" htmlFor="run-env"><Select id="run-env" value={runEnv} onChange={(e) => setRunEnv(e.target.value)}>
                <option value="">跟随场景配置</option>
                {environments.map((env) => <option key={env.id} value={env.id}>{env.name}</option>)}
              </Select></Field>
              <Field label="运行变量 JSON" htmlFor="run-vars"><Textarea id="run-vars" rows={4} value={runVariables} onChange={(e) => setRunVariables(e.target.value)} /></Field>
              <Field label="超时（秒）" htmlFor="run-timeout"><Input id="run-timeout" type="number" value={runTimeout} onChange={(e) => setRunTimeout(e.target.value)} /></Field>
            </>
          ) : (
            <>
              <div className="run-meta-strip">
                <div className="summary-chip"><span className="summary-chip-label">状态</span><span className="summary-chip-value"><StatusPill meta={{ label: run.status, tone: run.status === 'SUCCESS' ? 'green' : run.status === 'FAILED' ? 'red' : 'blue' }} /></span></div>
                <div className="summary-chip"><span className="summary-chip-label">摘要</span><span className="summary-chip-value">{run.summary || '—'}</span></div>
                <div className="summary-chip"><span className="summary-chip-label">耗时</span><span className="summary-chip-value">{run.durationMs ?? '—'} ms</span></div>
              </div>
              {steps.map((step) => (
                <details key={step.seq} className="scenario-step" open={step.status !== 'OK'}>
                  <summary>
                    <span className="step-seq">{step.seq}</span>
                    <Pill tone={step.status === 'OK' ? 'green' : 'red'}>{step.status}</Pill>
                    <strong>{step.name}</strong>
                    <span className="text-muted">{step.type} · {step.durationMs ?? 0}ms</span>
                  </summary>
                  <div className="scenario-step-body">
                    {step.request && Object.keys(step.request).length > 0 ? <><div className="meta-label">请求</div><pre>{JSON.stringify(step.request, null, 2)}</pre></> : null}
                    {step.response && Object.keys(step.response).length > 0 ? <><div className="meta-label">响应</div><pre>{JSON.stringify(step.response, null, 2)}</pre></> : null}
                    {step.assertions && step.assertions.length > 0 ? <><div className="meta-label">断言</div><ul>{step.assertions.map((item, index) => <li key={index} className={item.passed ? 'text-success' : 'text-danger'}>{item.type} {item.path} {item.operator} {item.expected} → {item.actual} {item.message || ''}</li>)}</ul></> : null}
                    {step.extracted && Object.keys(step.extracted).length > 0 ? <><div className="meta-label">提取变量</div><pre>{JSON.stringify(step.extracted, null, 2)}</pre></> : null}
                    {step.error ? <Callout tone="error" title="步骤错误">{step.error}</Callout> : null}
                  </div>
                </details>
              ))}
            </>
          )}
        </div>
      </Modal>

      <Modal open={historyOpen} title={`运行历史 · ${editing?.name || ''}`} size="lg"
        onClose={() => setHistoryOpen(false)} footer={<Button variant="secondary" onClick={() => setHistoryOpen(false)}>关闭</Button>}>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>运行 ID</th><th>状态</th><th>摘要</th><th>耗时</th><th>时间</th><th></th></tr></thead>
            <tbody>
              {historyLoading ? <SkeletonRows rows={4} cols={6} /> : history.length === 0 ? <tr><td colSpan={6}><EmptyState compact icon="play" title="暂无运行记录" /></td></tr> : history.map((item) => (
                <tr key={item.id}>
                  <td className="mono">{item.id}</td>
                  <td><Pill tone={item.status === 'SUCCESS' ? 'green' : item.status === 'FAILED' ? 'red' : 'blue'}>{item.status}</Pill></td>
                  <td>{item.summary || '—'}</td><td>{item.durationMs ?? '—'} ms</td><td>{formatDateTime(item.createdAt)}</td>
                  <td><Button size="sm" variant="ghost" onClick={() => { setHistoryOpen(false); setRun(item); setEditing(items.find((s) => s.id === item.scenarioId) || editing); setRunOpen(true); }}>查看</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Modal>

      <ConfirmDialog open={!!deleting} title="删除 API 场景" message={`确认删除“${deleting?.name || ''}”？历史运行保留。`}
        confirmText="删除" tone="danger" loading={deleteBusy} onConfirm={() => void remove()} onClose={() => setDeleting(null)} />
    </div>
  );
}
