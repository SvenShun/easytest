import { useCallback, useEffect, useState } from 'react';
import { apiEnvironmentsApi, apiScenariosApi } from '../api';
import type { ApiEnvironment } from '../api/types';
import type { ApiScenario, ScenarioRun, ScenarioStep } from '../api/scenarios';
import { canManageCases, useAuth } from '../auth';
import { cx, formatDateTime, safeErrorMessage } from '../utils';
import {
  Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill,
  Select, SkeletonRows, StatusPill, Textarea,
} from '../components/ui';
import { ConfirmDialog, Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (ch === '"') quoted = false;
      else cell += ch;
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ',') {
      row.push(cell); cell = '';
    } else if (ch === '\n') {
      row.push(cell); rows.push(row); row = []; cell = '';
    } else if (ch !== '\r') {
      cell += ch;
    }
  }
  if (cell !== '' || row.length > 0) { row.push(cell); rows.push(row); }
  return rows;
}

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

const STEP_SNIPPETS: Array<{ label: string; group: string; yaml: string }> = [
  { label: 'HTTP', group: '步骤', yaml: '- name: http-step\n  type: http\n  method: GET\n  path: /api/health\n  headers: {}\n  assertions:\n    - {type: STATUS_CODE, operator: EQ, expected: 200}' },
  { label: 'SQL', group: '步骤', yaml: '- name: sql-step\n  type: sql\n  datasource: main\n  query: SELECT 1 AS n\n  extract: {n: rows[0].n}\n  assertions:\n    - {type: SQL_ROW_COUNT, operator: GT, expected: 0}' },
  { label: 'Groovy', group: '步骤', yaml: '- name: script-step\n  type: groovy\n  script: |\n    vars.put("computed", "ok")' },
  { label: 'set', group: '步骤', yaml: '- name: set-step\n  type: set\n  variables:\n    key: value' },
  { label: 'assert', group: '步骤', yaml: '- name: assert-step\n  type: assert\n  assertions:\n    - {type: VARIABLE, path: key, operator: EQ, expected: value}' },
  { label: 'wait', group: '步骤', yaml: '- name: wait-step\n  type: wait\n  ms: 500' },
  { label: 'if / else', group: '控制器', yaml: '- name: branch\n  type: if\n  condition: "${flag} == yes"\n  steps:\n    - {name: then-step, type: set, variables: {branch: yes}}\n  elseSteps:\n    - {name: else-step, type: set, variables: {branch: no}}' },
  { label: 'loop / dataset', group: '控制器', yaml: '- name: iterate\n  type: loop\n  dataset: users\n  indexVar: idx\n  itemVar: row\n  steps:\n    - {name: use-row, type: set, variables: {current: "${username}"}}' },
  { label: 'once', group: '控制器', yaml: '- name: once\n  type: once\n  steps:\n    - {name: once-set, type: set, variables: {once: ok}}' },
  { label: 'while', group: '控制器', yaml: '- name: retry\n  type: while\n  condition: "${i} < 3"\n  maxIterations: 10\n  steps:\n    - name: increase\n      type: groovy\n      script: |\n        vars.put("i", ((Number) vars.get("i")).intValue() + 1)' },
  { label: '前置 / 后置', group: '钩子', yaml: '- name: step-with-hooks\n  type: http\n  method: GET\n  path: /api/health\n  before:\n    - {name: prepare, type: set, variables: {marker: before-ok}}\n  after:\n    - {name: verify, type: assert, assertions: [{type: VARIABLE, path: marker, operator: EQ, expected: before-ok}]}' },
];

const DATASET_SNIPPET = `datasets:
  users:
    - {username: tester, password: test123}
    - {username: admin, password: admin123}`;

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
  const [datasetName, setDatasetName] = useState('rows');
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
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchSelected, setBatchSelected] = useState<Set<string>>(new Set());
  const [batchEnv, setBatchEnv] = useState('');
  const [batchTimeout, setBatchTimeout] = useState('120');
  const [batchBusy, setBatchBusy] = useState(false);
  const [batchError, setBatchError] = useState('');
  const [batchResults, setBatchResults] = useState<Array<{ id: string; scenarioId: string; scenarioName?: string; status: string; summary?: string | null }>>([]);
  const [importOpen, setImportOpen] = useState(false);
  const [importName, setImportName] = useState('');
  const [importDescription, setImportDescription] = useState('');
  const [importYaml, setImportYaml] = useState('');
  const [importEnv, setImportEnv] = useState('');
  const [importBusy, setImportBusy] = useState(false);
  const [importError, setImportError] = useState('');
  const [capabilitiesOpen, setCapabilitiesOpen] = useState(false);

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

  const openBatch = () => {
    setBatchSelected(new Set());
    setBatchEnv('');
    setBatchTimeout('120');
    setBatchError('');
    setBatchResults([]);
    setBatchOpen(true);
  };

  const runBatch = async () => {
    if (batchSelected.size === 0) {
      setBatchError('请选择至少一个场景');
      return;
    }
    setBatchBusy(true);
    setBatchError('');
    try {
      const result = await apiScenariosApi.batchRun({
        scenarioIds: Array.from(batchSelected),
        environmentId: batchEnv || null,
        timeoutSec: Number(batchTimeout) || 120,
      });
      setBatchResults(result.runs);
      toastSuccess(`批量运行已创建 ${result.started} 个，失败 ${result.failed} 个`);
    } catch (err) {
      setBatchError(safeErrorMessage(err));
    } finally {
      setBatchBusy(false);
    }
  };

  const exportScenario = async (item: ApiScenario) => {
    try {
      const data = await apiScenariosApi.exportScenario(item.id);
      const blob = new Blob([data.yaml], { type: 'text/yaml;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${item.name.replace(/[^A-Za-z0-9_\-\u4e00-\u9fa5]/g, '_')}.yaml`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  };

  const importYamlFile = async (file: File) => {
    setImportYaml(await file.text());
    if (!importName) setImportName(file.name.replace(/\.(ya?ml)$/i, ''));
  };

  const submitImport = async () => {
    if (!importName.trim() || !importYaml.trim()) {
      setImportError('名称和 YAML 不能为空');
      return;
    }
    setImportBusy(true);
    setImportError('');
    try {
      await apiScenariosApi.importScenario({
        name: importName.trim(),
        description: importDescription,
        yaml: importYaml,
        environmentId: importEnv || null,
        enabled: true,
      });
      toastSuccess('场景已导入');
      setImportOpen(false);
      await load();
    } catch (err) {
      setImportError(safeErrorMessage(err));
    } finally {
      setImportBusy(false);
    }
  };

  const insertSnippet = (snippet: string) => {
    if (!editorOpen) {
      setEditing(null);
      setEditor({ ...EMPTY_EDITOR, name: '新场景' });
      setEditorOpen(true);
    }
    setEditor((prev) => ({
      ...prev,
      yaml: `${prev.yaml.trimEnd()}${prev.yaml.trim() ? '\n\n' : ''}${snippet}`,
    }));
    toastSuccess('已插入模板到 YAML 编辑器');
  };

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

  const importCsv = async (file: File) => {
    const text = await file.text();
    const rows = parseCsv(text);
    if (rows.length < 2) {
      setEditorError('CSV 至少需要表头和一行数据');
      return;
    }
    const headers = rows[0].map((header) => header.trim()).filter(Boolean);
    const objects = rows.slice(1).filter((row) => row.some((cell) => cell.trim() !== '')).map((row) => {
      const object: Record<string, string> = {};
      headers.forEach((header, index) => { object[header] = row[index] ?? ''; });
      return object;
    });
    const name = (datasetName.trim() || 'rows').replace(/[^A-Za-z0-9_]/g, '_');
    const block = `datasets:\n  ${name}:\n${objects.map((row) => `    - ${JSON.stringify(row)}`).join('\n')}\n`;
    let yaml = editor.yaml;
    if (/^datasets:/m.test(yaml)) {
      yaml = yaml.replace(/^datasets:[\s\S]*?(?=^steps:)/m, block);
    } else if (/^steps:/m.test(yaml)) {
      yaml = yaml.replace(/^steps:/m, `${block}steps:`);
    } else {
      yaml = `${block}${yaml}`;
    }
    setEditor({ ...editor, yaml });
    toastSuccess(`已导入 ${objects.length} 行到 dataset "${name}"`);
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
        actions={canWrite ? (
          <div className="row-actions">
            <Button variant="secondary" icon="queue" onClick={openBatch}>批量运行</Button>
            <Button variant="secondary" icon="external" onClick={() => { setImportName(''); setImportDescription(''); setImportYaml(''); setImportEnv(''); setImportError(''); setImportOpen(true); }}>导入</Button>
            <Button variant="primary" icon="plus" onClick={openCreate}>新建场景</Button>
          </div>
        ) : undefined}
      />
      <Card>
        <CardHead title="YAML 能力入口" description="所有步骤/控制器都在 YAML 中；点击按钮可直接插入模板，再编辑成你的场景" icon="code"
          actions={<Button size="sm" variant="ghost" onClick={() => setCapabilitiesOpen(true)}>语法说明</Button>} />
        <div className="card-body">
          <div className="capability-row">
            {['步骤', '控制器', '钩子'].map((group) => (
              <div key={group} className="capability-row" style={{ flexWrap: 'wrap' }}>
                <span className="capability-label">{group}</span>
                <div className="pill-row">
                  {STEP_SNIPPETS.filter((item) => item.group === group).map((item) => (
                    <Button key={item.label} size="sm" variant="ghost" onClick={() => insertSnippet(item.yaml)}>
                      + {item.label}
                    </Button>
                  ))}
                </div>
              </div>
            ))}
          </div>
          <div className="row-actions">
            <Button size="sm" variant="secondary" onClick={() => insertSnippet(DATASET_SNIPPET)}>+ 数据集 datasets</Button>
            <Button size="sm" variant="secondary" onClick={() => { setEditor({ ...EMPTY_EDITOR, name: '新场景' }); setEditing(null); setEditorOpen(true); }}>打开 YAML 编辑器</Button>
            <Button size="sm" variant="secondary" onClick={openBatch}>批量运行</Button>
            <Button size="sm" variant="secondary" onClick={() => { setImportName(''); setImportDescription(''); setImportYaml(''); setImportEnv(''); setImportError(''); setImportOpen(true); }}>导入 YAML</Button>
          </div>
          <div className="field-hint">支持 HTTP / SQL / Groovy / set / assert / wait / if-else / loop(times/items/dataset) / once / while / before-after / 变量提取；CSV 导入按钮在编辑器内。</div>
        </div>
      </Card>

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
                    <Button size="sm" variant="ghost" onClick={() => void exportScenario(item)}>导出</Button>
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
          <div className="row-actions" style={{ flexWrap: 'wrap' }}>
            <span className="capability-label">插入：</span>
            {STEP_SNIPPETS.map((item) => (
              <Button key={item.label} size="sm" variant="ghost" onClick={() => insertSnippet(item.yaml)}>{item.label}</Button>
            ))}
            <Button size="sm" variant="ghost" onClick={() => insertSnippet(DATASET_SNIPPET)}>datasets</Button>
          </div>
          <Field label="YAML" htmlFor="scenario-yaml" hint="支持 HTTP/SQL/Groovy/set/assert/wait、if/loop/once/while、before/after、变量 ${var} 或 {{var}}">
            <Textarea id="scenario-yaml" className="yaml-editor" rows={26} value={editor.yaml} onChange={(e) => setEditor({ ...editor, yaml: e.target.value })} />
          </Field>
          <div className="row-actions">
            <Field label="CSV dataset 名称" htmlFor="scenario-dataset">
              <Input id="scenario-dataset" value={datasetName} onChange={(e) => setDatasetName(e.target.value)} />
            </Field>
            <label className="button button-secondary" style={{ cursor: 'pointer' }}>
              导入 CSV 数据集
              <input type="file" accept=".csv,text/csv" hidden
                     onChange={(event) => {
                       const file = event.target.files?.[0];
                       if (file) void importCsv(file);
                       event.currentTarget.value = '';
                     }} />
            </label>
          </div>
          <div className="field-hint">CSV 会写入 YAML 的 datasets 段；loop 步骤用 <code>dataset: 名称</code> 引用。</div>
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

      <Modal open={capabilitiesOpen} title="YAML 场景语法说明" size="lg"
        onClose={() => setCapabilitiesOpen(false)} footer={<Button variant="secondary" onClick={() => setCapabilitiesOpen(false)}>关闭</Button>}>
        <div className="form-stack">
          <Callout tone="info" title="入口说明">
            「API 场景」页 → 新建/编辑 → YAML 编辑器。批量运行/导入在页面顶部，CSV 导入在编辑器内。
          </Callout>
          <table className="table">
            <thead><tr><th>能力</th><th>写法</th></tr></thead>
            <tbody>
              <tr><td>HTTP</td><td><code>type: http</code> + method/path/headers/body/extract/assertions</td></tr>
              <tr><td>SQL</td><td><code>type: sql</code> + datasource/query/params/extract</td></tr>
              <tr><td>Groovy</td><td><code>type: groovy</code> + script；使用 vars/env/log</td></tr>
              <tr><td>变量传递</td><td><code>{'${var}'}</code> 或 <code>{'{{var}}'}</code>；extract 写入后续步骤</td></tr>
              <tr><td>控制器</td><td><code>type: if/loop/once/while</code> + steps；loop 支持 times/items/dataset</td></tr>
              <tr><td>数据集</td><td>根节点 <code>datasets:</code>；编辑器「导入 CSV 数据集」自动生成</td></tr>
              <tr><td>前置/后置</td><td>任意步骤加 <code>before:</code> / <code>after:</code> 子步骤</td></tr>
              <tr><td>断言</td><td>STATUS_CODE / JSON_PATH / XML_PATH / JSON_SCHEMA / SQL_ROW_COUNT / SQL_VALUE / VARIABLE</td></tr>
            </tbody>
          </table>
          <div className="field-hint">完整格式见 docs/API-SCENARIO.md。</div>
        </div>
      </Modal>

      <Modal open={batchOpen} title="批量运行 API 场景" size="lg"
        onClose={() => { if (!batchBusy) setBatchOpen(false); }} closeOnOverlay={!batchBusy}
        footer={<><Button variant="secondary" onClick={() => setBatchOpen(false)} disabled={batchBusy}>关闭</Button>
          <Button variant="primary" loading={batchBusy} onClick={() => void runBatch()}>开始批量运行</Button></>}>
        <div className="form-stack">
          {batchError ? <Callout tone="error" title="运行失败">{batchError}</Callout> : null}
          <div className="add-case-list" style={{ maxHeight: 260 }}>
            {items.filter((item) => item.enabled).map((item) => (
              <label key={item.id} className={cx('add-case-row', batchSelected.has(item.id) && 'is-checked')}>
                <input type="checkbox" checked={batchSelected.has(item.id)} onChange={() => setBatchSelected((prev) => {
                  const next = new Set(prev); if (next.has(item.id)) next.delete(item.id); else next.add(item.id); return next;
                })} />
                <span className="add-case-main"><span className="add-case-title">{item.name}</span><span className="add-case-sub mono">{item.id}</span></span>
              </label>
            ))}
          </div>
          <div className="form-grid-2">
            <Field label="环境覆盖" htmlFor="batch-env"><Select id="batch-env" value={batchEnv} onChange={(e) => setBatchEnv(e.target.value)}>
              <option value="">跟随场景</option>
              {environments.map((env) => <option key={env.id} value={env.id}>{env.name}</option>)}
            </Select></Field>
            <Field label="超时（秒）" htmlFor="batch-timeout"><Input id="batch-timeout" type="number" value={batchTimeout} onChange={(e) => setBatchTimeout(e.target.value)} /></Field>
          </div>
          {batchResults.length > 0 ? (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>场景</th><th>运行 ID</th><th>状态</th><th>摘要</th></tr></thead>
                <tbody>{batchResults.map((run) => (
                  <tr key={run.id}><td>{run.scenarioName || run.scenarioId}</td><td className="mono">{run.id}</td>
                    <td><Pill tone={run.status === 'SUCCESS' ? 'green' : run.status === 'FAILED' ? 'red' : 'blue'}>{run.status}</Pill></td>
                    <td>{run.summary || '—'}</td></tr>
                ))}</tbody>
              </table>
            </div>
          ) : null}
        </div>
      </Modal>

      <Modal open={importOpen} title="导入 API 场景" size="lg"
        onClose={() => { if (!importBusy) setImportOpen(false); }} closeOnOverlay={!importBusy}
        footer={<><Button variant="secondary" onClick={() => setImportOpen(false)} disabled={importBusy}>取消</Button>
          <Button variant="primary" loading={importBusy} onClick={() => void submitImport()}>导入</Button></>}>
        <div className="form-stack">
          {importError ? <Callout tone="error" title="导入失败">{importError}</Callout> : null}
          <Field label="YAML 文件" htmlFor="scenario-import-file"><input id="scenario-import-file" type="file" accept=".yaml,.yml,text/yaml" onChange={(e) => { const file = e.target.files?.[0]; if (file) void importYamlFile(file); }} /></Field>
          <div className="form-grid-2">
            <Field label="场景名称" htmlFor="scenario-import-name" required><Input id="scenario-import-name" value={importName} onChange={(e) => setImportName(e.target.value)} /></Field>
            <Field label="环境" htmlFor="scenario-import-env"><Select id="scenario-import-env" value={importEnv} onChange={(e) => setImportEnv(e.target.value)}>
              <option value="">不绑定环境</option>
              {environments.map((env) => <option key={env.id} value={env.id}>{env.name}</option>)}
            </Select></Field>
          </div>
          <Field label="描述" htmlFor="scenario-import-desc"><Input id="scenario-import-desc" value={importDescription} onChange={(e) => setImportDescription(e.target.value)} /></Field>
          <Field label="YAML" htmlFor="scenario-import-yaml"><Textarea id="scenario-import-yaml" className="yaml-editor" rows={14} value={importYaml} onChange={(e) => setImportYaml(e.target.value)} /></Field>
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
