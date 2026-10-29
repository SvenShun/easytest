import { useEffect, useState } from 'react';
import { Button, Field, Input, Select, Textarea } from './ui';

export type ScenarioStep = Record<string, any>;

const STEP_TYPES = [
  ['http', 'HTTP'], ['sql', 'SQL'], ['groovy', 'Groovy'], ['set', 'set'], ['assert', 'assert'],
  ['wait', 'wait'], ['if', 'if'], ['loop', 'loop'], ['once', 'once'], ['while', 'while'],
];

const DEFAULTS: Record<string, ScenarioStep> = {
  http: { type: 'http', name: 'HTTP 步骤', method: 'GET', path: '/api/health', assertions: [{ type: 'STATUS_CODE', operator: 'EQ', expected: 200 }] },
  sql: { type: 'sql', name: 'SQL 步骤', datasource: 'main', query: 'SELECT 1 AS n', extract: { n: 'rows[0].n' } },
  groovy: { type: 'groovy', name: 'Groovy 步骤', script: 'vars.put("key", "value")' },
  set: { type: 'set', name: '设置变量', variables: { key: 'value' } },
  assert: { type: 'assert', name: '断言', assertions: [{ type: 'VARIABLE', path: 'key', operator: 'EQ', expected: 'value' }] },
  wait: { type: 'wait', name: '等待', ms: 500 },
  if: { type: 'if', name: '条件分支', condition: '${flag} == yes', steps: [] },
  loop: { type: 'loop', name: '循环', dataset: 'rows', steps: [] },
  once: { type: 'once', name: '执行一次', steps: [] },
  while: { type: 'while', name: '条件循环', condition: '${i} < 3', maxIterations: 10, steps: [] },
};

function rows(value: unknown): ScenarioStep[] {
  return Array.isArray(value) ? value.filter((item) => item && typeof item === 'object') as ScenarioStep[] : [];
}

export function JsonField({ label, value, onChange }: { label: string; value: unknown; onChange: (next: unknown) => void }) {
  const pretty = value == null ? '' : JSON.stringify(value, null, 2);
  const [text, setText] = useState(pretty);
  const [error, setError] = useState('');
  useEffect(() => { setText(pretty); setError(''); }, [pretty]);
  return (
    <Field label={label} hint={error || 'JSON 格式；留空表示不配置'}>
      <Textarea rows={Math.min(8, Math.max(2, text.split('\n').length))} value={text}
        invalid={Boolean(error)}
        onChange={(event) => {
          const next = event.target.value;
          setText(next);
          if (!next.trim()) { setError(''); onChange(undefined); return; }
          try { onChange(JSON.parse(next)); setError(''); }
          catch { setError('JSON 格式错误，暂未写回 YAML'); }
        }} />
    </Field>
  );
}

function StepFields({ step, update }: { step: ScenarioStep; update: (patch: ScenarioStep) => void }) {
  const type = String(step.type || '').toLowerCase();
  switch (type) {
    case 'http':
      return (
        <div className="step-fields">
          <div className="form-grid-2">
            <Field label="方法"><Select value={step.method || 'GET'} onChange={(e) => update({ method: e.target.value })}>
              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'].map((method) => <option key={method}>{method}</option>)}
            </Select></Field>
            <Field label="路径 / URL"><Input value={step.path || step.url || ''} onChange={(e) => update({ path: e.target.value, url: undefined })} /></Field>
          </div>
          <div className="form-grid-2">
            <JsonField label="Headers" value={step.headers} onChange={(headers) => update({ headers })} />
            <JsonField label="Query" value={step.query} onChange={(query) => update({ query })} />
          </div>
          <JsonField label="Body" value={step.body} onChange={(body) => update({ body })} />
          <div className="form-grid-2">
            <JsonField label="提取变量 extract" value={step.extract} onChange={(extract) => update({ extract })} />
            <Field label="超时（秒）"><Input type="number" value={step.timeoutSec || ''} onChange={(e) => update({ timeoutSec: e.target.value ? Number(e.target.value) : undefined })} /></Field>
          </div>
          <JsonField label="断言 assertions" value={step.assertions} onChange={(assertions) => update({ assertions })} />
        </div>
      );
    case 'sql':
      return (
        <div className="step-fields">
          <div className="form-grid-2">
            <Field label="数据源"><Input value={step.datasource || ''} onChange={(e) => update({ datasource: e.target.value })} /></Field>
            <Field label="参数 params"><Input value={Array.isArray(step.params) ? JSON.stringify(step.params) : step.params || ''} onChange={(e) => {
              try { update({ params: e.target.value ? JSON.parse(e.target.value) : undefined }); } catch { update({ params: e.target.value }); }
            }} /></Field>
          </div>
          <Field label="SQL"><Textarea rows={5} value={step.query || ''} onChange={(e) => update({ query: e.target.value })} /></Field>
          <div className="form-grid-2">
            <JsonField label="提取变量 extract" value={step.extract} onChange={(extract) => update({ extract })} />
            <JsonField label="断言 assertions" value={step.assertions} onChange={(assertions) => update({ assertions })} />
          </div>
        </div>
      );
    case 'groovy':
      return <Field label="Groovy 脚本"><Textarea rows={8} className="yaml-editor" value={step.script || ''} onChange={(e) => update({ script: e.target.value })} /></Field>;
    case 'set':
      return <JsonField label="变量 variables" value={step.variables} onChange={(variables) => update({ variables })} />;
    case 'assert':
      return <JsonField label="断言 assertions" value={step.assertions} onChange={(assertions) => update({ assertions })} />;
    case 'wait':
      return <Field label="等待毫秒"><Input type="number" value={step.ms ?? 500} onChange={(e) => update({ ms: Number(e.target.value) })} /></Field>;
    case 'if':
      return (
        <div className="step-fields">
          <Field label="条件 condition" hint="支持 ${var} == value，或 left/operator/right"><Input value={step.condition || ''} onChange={(e) => update({ condition: e.target.value })} /></Field>
        </div>
      );
    case 'loop':
      return (
        <div className="step-fields">
          <div className="form-grid-2">
            <Field label="dataset"><Input value={step.dataset || ''} onChange={(e) => update({ dataset: e.target.value || undefined })} /></Field>
            <Field label="times"><Input type="number" value={step.times || ''} onChange={(e) => update({ times: e.target.value ? Number(e.target.value) : undefined })} /></Field>
            <Field label="indexVar"><Input value={step.indexVar || ''} onChange={(e) => update({ indexVar: e.target.value || undefined })} /></Field>
            <Field label="itemVar"><Input value={step.itemVar || ''} onChange={(e) => update({ itemVar: e.target.value || undefined })} /></Field>
          </div>
          <JsonField label="items（与 dataset/times 三选一）" value={step.items} onChange={(items) => update({ items })} />
        </div>
      );
    case 'while':
      return (
        <div className="form-grid-2">
          <Field label="条件 condition"><Input value={step.condition || ''} onChange={(e) => update({ condition: e.target.value })} /></Field>
          <Field label="最大次数"><Input type="number" value={step.maxIterations ?? 20} onChange={(e) => update({ maxIterations: Number(e.target.value) })} /></Field>
        </div>
      );
    case 'once':
      return <div className="field-hint">once 控制器只执行一次其子步骤。</div>;
    default:
      return <JsonField label="步骤参数" value={Object.fromEntries(Object.entries(step).filter(([key]) => !['type', 'name'].includes(key)))} onChange={(fields) => update(fields as ScenarioStep)} />;
  }
}

function StepCard({ step, index, total, depth, onChange, onRemove, onMove }: {
  step: ScenarioStep; index: number; total: number; depth: number;
  onChange: (next: ScenarioStep) => void; onRemove: () => void; onMove: (offset: number) => void;
}) {
  const type = String(step.type || 'http').toLowerCase();
  const update = (patch: ScenarioStep) => {
    const next: ScenarioStep = { ...step, ...patch };
    Object.keys(next).forEach((key) => { if (next[key] === undefined) delete next[key]; });
    onChange(next);
  };
  const childKey = type === 'if' ? ['steps', 'elseSteps'] : ['steps'];
  return (
    <div className={`structured-step ${depth > 0 ? 'is-child' : ''}`}>
      <div className="structured-step-head">
        <span className="structured-step-index">{index + 1}</span>
        <Select value={type} onChange={(event) => {
          const nextType = event.target.value;
          onChange({ ...DEFAULTS[nextType], name: step.name || DEFAULTS[nextType].name });
        }}>
          {STEP_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </Select>
        <Input value={step.name || ''} placeholder="步骤名称" onChange={(event) => update({ name: event.target.value })} />
        <div className="row-actions">
          <Button size="sm" variant="ghost" disabled={index === 0} onClick={() => onMove(-1)}>上移</Button>
          <Button size="sm" variant="ghost" disabled={index === total - 1} onClick={() => onMove(1)}>下移</Button>
          <Button size="sm" variant="ghost" onClick={onRemove}>删除</Button>
        </div>
      </div>
      <StepFields step={step} update={update} />
      {childKey.map((key) => rows(step[key]).length || type === 'if' || type === 'loop' || type === 'once' || type === 'while' ? (
        <div className="structured-children" key={key}>
          <div className="structured-children-title">{key === 'elseSteps' ? 'else 分支' : '子步骤'}</div>
          <StepList steps={rows(step[key])} depth={depth + 1}
            onChange={(children) => update({ [key]: children })}
            allowHooks={false} />
        </div>
      ) : null)}
      {depth === 0 ? (
        <div className="structured-hooks">
          {(['before', 'after'] as const).map((key) => (
            <details key={key}>
              <summary>{key === 'before' ? '前置处理器' : '后置处理器'}（{rows(step[key]).length}）</summary>
              <StepList steps={rows(step[key])} depth={depth + 1}
                onChange={(hooks) => update({ [key]: hooks.length ? hooks : undefined })} allowHooks={false} />
            </details>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function StepList({ steps, depth, onChange, allowHooks = true }: {
  steps: ScenarioStep[]; depth: number; onChange: (next: ScenarioStep[]) => void; allowHooks?: boolean;
}) {
  const [newType, setNewType] = useState('http');
  const update = (index: number, next: ScenarioStep) => onChange(steps.map((item, i) => i === index ? next : item));
  const remove = (index: number) => onChange(steps.filter((_, i) => i !== index));
  const move = (index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= steps.length) return;
    const next = [...steps];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };
  return (
    <div className="structured-step-list">
      {steps.map((step, index) => (
        <StepCard key={`${depth}-${index}`} step={step} index={index} total={steps.length} depth={depth}
          onChange={(next) => update(index, next)} onRemove={() => remove(index)} onMove={(offset) => move(index, offset)} />
      ))}
      <div className="row-actions">
        <Select value={newType} onChange={(event) => setNewType(event.target.value)}>
          {STEP_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </Select>
        <Button size="sm" variant="secondary" onClick={() => onChange([...steps, { ...DEFAULTS[newType] }])}>+ 添加步骤</Button>
      </div>
      {allowHooks && steps.length === 0 ? <div className="field-hint">还没有步骤，选择类型后点击“添加步骤”。</div> : null}
    </div>
  );
}

export function ScenarioStepEditor({ steps, onChange }: { steps: ScenarioStep[]; onChange: (steps: ScenarioStep[]) => void }) {
  return <StepList steps={steps} depth={0} onChange={onChange} />;
}
