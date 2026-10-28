import { useCallback, useEffect, useState } from 'react';
import { kbApi } from '../api';
import type { KbAction, KbApp, KbControl, KbPage } from '../api/kb';
import { canManageCases, useAuth } from '../auth';
import { formatDateTime, safeErrorMessage } from '../utils';
import {
  Button, Callout, Card, CardHead, EmptyState, ErrorPanel, Field, Input, PageHeading, Pill,
  Select, SkeletonRows, Textarea,
} from '../components/ui';
import { Modal } from '../components/Modal';
import { toastError, toastSuccess } from '../components/Toast';

const CONTROL_TYPES = ['BUTTON', 'TEXT', 'INPUT', 'LIST', 'IMAGE', 'CHECKBOX', 'SWITCH', 'TAB', 'OTHER'];
const ACTION_TYPES = ['CLICK', 'LONG_PRESS', 'SWIPE', 'INPUT', 'ASSERT_TEXT', 'SCROLL', 'CUSTOM'];
const SOURCES = ['MANUAL', 'AI', 'CRAWLER'];

interface AppForm { name: string; packageName: string; platform: string; description: string; iconUrl: string; }
interface PageForm { name: string; route: string; description: string; screenshotUrl: string; }
interface ControlForm {
  name: string; text: string; resourceId: string; description: string; desc: string; intent: string;
  controlType: string; screenshotUrl: string; bounds: string; source: string; confidence: string;
}
interface ActionForm { actionType: string; label: string; params: string; source: string; enabled: boolean; }

const EMPTY_APP: AppForm = { name: '', packageName: '', platform: 'android', description: '', iconUrl: '' };
const EMPTY_PAGE: PageForm = { name: '', route: '', description: '', screenshotUrl: '' };
const EMPTY_CONTROL: ControlForm = {
  name: '', text: '', resourceId: '', description: '', desc: '', intent: '', controlType: 'BUTTON',
  screenshotUrl: '', bounds: '', source: 'MANUAL', confidence: '',
};
const EMPTY_ACTION: ActionForm = { actionType: 'CLICK', label: '', params: '', source: 'MANUAL', enabled: true };

export function KnowledgeBasePage() {
  const { user } = useAuth();
  const canWrite = canManageCases(user);
  const [apps, setApps] = useState<KbApp[]>([]);
  const [selectedApp, setSelectedApp] = useState<KbApp | null>(null);
  const [pages, setPages] = useState<KbPage[]>([]);
  const [selectedPage, setSelectedPage] = useState<KbPage | null>(null);
  const [controls, setControls] = useState<KbControl[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [appOpen, setAppOpen] = useState(false);
  const [appEditing, setAppEditing] = useState<KbApp | null>(null);
  const [appForm, setAppForm] = useState<AppForm>(EMPTY_APP);
  const [pageOpen, setPageOpen] = useState(false);
  const [pageEditing, setPageEditing] = useState<KbPage | null>(null);
  const [pageForm, setPageForm] = useState<PageForm>(EMPTY_PAGE);
  const [controlOpen, setControlOpen] = useState(false);
  const [controlEditing, setControlEditing] = useState<KbControl | null>(null);
  const [controlForm, setControlForm] = useState<ControlForm>(EMPTY_CONTROL);
  const [actionControl, setActionControl] = useState<KbControl | null>(null);
  const [actions, setActions] = useState<KbAction[]>([]);
  const [actionForm, setActionForm] = useState<ActionForm>(EMPTY_ACTION);
  const [busy, setBusy] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchText, setBatchText] = useState('{\n  "controls": [\n    {"name":"示例控件","text":"示例","controlType":"TEXT","source":"CRAWLER"}\n  ]\n}');
  const [batchError, setBatchError] = useState('');
  const [modalError, setModalError] = useState('');
  const [uploading, setUploading] = useState(false);

  const loadApps = useCallback(async (selectId?: string) => {
    setLoading(true);
    setError('');
    try {
      const data = await kbApi.apps();
      setApps(data);
      const target = data.find((app) => app.id === (selectId || selectedApp?.id)) || data[0] || null;
      setSelectedApp(target);
    } catch (err) {
      setError(safeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [selectedApp?.id]);

  const loadPages = useCallback(async (appId: string, selectId?: string) => {
    try {
      const data = await kbApi.pages(appId);
      setPages(data);
      setSelectedPage(data.find((page) => page.id === selectId) || data[0] || null);
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  }, []);

  const loadControls = useCallback(async (pageId: string) => {
    try {
      setControls(await kbApi.controls(pageId));
    } catch (err) {
      toastError(safeErrorMessage(err));
    }
  }, []);

  useEffect(() => { void loadApps(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (selectedApp) void loadPages(selectedApp.id);
    else { setPages([]); setSelectedPage(null); }
  }, [selectedApp?.id]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (selectedPage) void loadControls(selectedPage.id);
    else setControls([]);
  }, [selectedPage?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const reloadAll = async (appId = selectedApp?.id, pageId = selectedPage?.id) => {
    await loadApps(appId);
    if (appId) await loadPages(appId, pageId);
    if (pageId) await loadControls(pageId);
  };

  const uploadImage = async (file: File, target: 'page' | 'control') => {
    setUploading(true);
    try {
      const result = await kbApi.uploadScreenshot(file);
      if (target === 'page') setPageForm((prev) => ({ ...prev, screenshotUrl: result.url }));
      else setControlForm((prev) => ({ ...prev, screenshotUrl: result.url }));
    } catch (err) {
      toastError(safeErrorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  const saveApp = async () => {
    if (!appForm.name.trim()) { setModalError('应用名称不能为空'); return; }
    setBusy(true); setModalError('');
    try {
      if (appEditing) await kbApi.updateApp(appEditing.id, appForm);
      else await kbApi.createApp(appForm);
      toastSuccess(appEditing ? '应用已更新' : '应用已创建');
      setAppOpen(false);
      await loadApps(appEditing?.id);
    } catch (err) { setModalError(safeErrorMessage(err)); } finally { setBusy(false); }
  };

  const savePage = async () => {
    if (!selectedApp || !pageForm.name.trim()) { setModalError('页面名称不能为空'); return; }
    setBusy(true); setModalError('');
    try {
      if (pageEditing) await kbApi.updatePage(pageEditing.id, pageForm);
      else await kbApi.createPage(selectedApp.id, pageForm);
      toastSuccess(pageEditing ? '页面已更新' : '页面已创建');
      setPageOpen(false);
      await loadPages(selectedApp.id, pageEditing?.id);
      await loadApps(selectedApp.id);
    } catch (err) { setModalError(safeErrorMessage(err)); } finally { setBusy(false); }
  };

  const saveControl = async () => {
    if (!selectedPage) return;
    if (!controlForm.name.trim() && !controlForm.text.trim() && !controlForm.resourceId.trim()) {
      setModalError('name/text/resourceId 至少填一个');
      return;
    }
    setBusy(true); setModalError('');
    try {
      const payload = {
        ...controlForm,
        confidence: controlForm.confidence ? Number(controlForm.confidence) : null,
      };
      if (controlEditing) await kbApi.updateControl(controlEditing.id, payload);
      else await kbApi.createControl(selectedPage.id, payload);
      toastSuccess(controlEditing ? '控件已更新' : '控件已创建');
      setControlOpen(false);
      await loadControls(selectedPage.id);
    } catch (err) { setModalError(safeErrorMessage(err)); } finally { setBusy(false); }
  };

  const importBatch = async () => {
    if (!selectedPage) return;
    setBusy(true); setBatchError('');
    try {
      const parsed = JSON.parse(batchText);
      const controls = Array.isArray(parsed) ? parsed : parsed.controls;
      if (!Array.isArray(controls) || controls.length === 0) throw new Error('JSON 需要 controls 数组');
      await kbApi.batchControls(selectedPage.id, controls);
      toastSuccess(`已批量导入 ${controls.length} 个控件`);
      setBatchOpen(false);
      await loadControls(selectedPage.id);
    } catch (err) {
      setBatchError(safeErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const openActions = async (control: KbControl) => {
    setActionControl(control);
    setActionForm(EMPTY_ACTION);
    try { setActions(await kbApi.actions(control.id)); } catch (err) { toastError(safeErrorMessage(err)); }
  };

  const addAction = async () => {
    if (!actionControl) return;
    try {
      await kbApi.createAction(actionControl.id, actionForm);
      setActionForm(EMPTY_ACTION);
      setActions(await kbApi.actions(actionControl.id));
      await loadControls(actionControl.pageId);
      toastSuccess('操作已关联');
    } catch (err) { toastError(safeErrorMessage(err)); }
  };

  const removeAction = async (action: KbAction) => {
    if (!actionControl) return;
    try {
      await kbApi.deleteAction(action.id);
      setActions(await kbApi.actions(actionControl.id));
      await loadControls(actionControl.pageId);
    } catch (err) { toastError(safeErrorMessage(err)); }
  };

  const deleteApp = async (app: KbApp) => {
    if (!window.confirm(`删除应用“${app.name}”及其页面/控件？`)) return;
    await kbApi.deleteApp(app.id);
    toastSuccess('应用已删除');
    await loadApps();
  };

  const deletePage = async (page: KbPage) => {
    if (!window.confirm(`删除页面“${page.name}”及其控件？`)) return;
    await kbApi.deletePage(page.id);
    toastSuccess('页面已删除');
    if (selectedApp) await loadPages(selectedApp.id);
  };

  const deleteControl = async (control: KbControl) => {
    if (!window.confirm('删除该控件及关联操作？')) return;
    await kbApi.deleteControl(control.id);
    toastSuccess('控件已删除');
    if (selectedPage) await loadControls(selectedPage.id);
  };

  return (
    <div className="page-stack">
      <PageHeading
        title="UI 知识库"
        description="按 APP → 页面 → 控件建模，维护 text/id/desc/intent/截图，并关联基本操作。"
        actions={canWrite ? <Button variant="primary" icon="plus" onClick={() => { setAppEditing(null); setAppForm(EMPTY_APP); setModalError(''); setAppOpen(true); }}>新建应用</Button> : undefined}
      />
      {error ? <ErrorPanel message={error} onRetry={() => void loadApps()} /> : null}
      <div className="kb-layout">
        <aside className="kb-sidebar">
          <Card>
            <CardHead title="应用" description="知识库的根节点" icon="devices" />
            <div className="kb-app-list">
              {loading ? <SkeletonRows rows={3} cols={1} /> : apps.length === 0 ? (
                <EmptyState compact icon="devices" title="暂无应用" description="新建一个 APP 知识库" />
              ) : apps.map((app) => (
                <button key={app.id} type="button" className={`kb-app-item ${selectedApp?.id === app.id ? 'is-active' : ''}`}
                        onClick={() => setSelectedApp(app)}>
                  <strong>{app.name}</strong>
                  <span className="mono">{app.packageName || '—'}</span>
                  <span className="text-muted">{app.pageCount} 页面 · {app.controlCount} 控件</span>
                </button>
              ))}
            </div>
          </Card>
        </aside>
        <main className="kb-main">
          {!selectedApp ? <EmptyState icon="devices" title="选择或新建应用" description="一个应用下可建立多个页面与控件" /> : (
            <>
              <Card>
                <CardHead title={selectedApp.name} description={selectedApp.description || 'APP 知识库'}
                  icon="devices"
                  actions={<div className="row-actions">
                    <Button size="sm" variant="ghost" onClick={() => { setAppEditing(selectedApp); setAppForm({ name: selectedApp.name, packageName: selectedApp.packageName || '', platform: selectedApp.platform || 'android', description: selectedApp.description || '', iconUrl: selectedApp.iconUrl || '' }); setModalError(''); setAppOpen(true); }} disabled={!canWrite}>编辑应用</Button>
                    <Button size="sm" variant="ghost" onClick={() => void deleteApp(selectedApp)} disabled={!canWrite}>删除</Button>
                  </div>} />
                <div className="card-body">
                  <div className="kb-page-bar">
                    {pages.map((page) => (
                      <button key={page.id} type="button" className={`group-chip ${selectedPage?.id === page.id ? 'is-active' : ''}`}
                              onClick={() => setSelectedPage(page)}>
                        {page.name} <span>{page.controlCount}</span>
                      </button>
                    ))}
                    {canWrite ? <Button size="sm" variant="ghost" icon="plus" onClick={() => { setPageEditing(null); setPageForm(EMPTY_PAGE); setModalError(''); setPageOpen(true); }}>新建页面</Button> : null}
                  </div>
                </div>
              </Card>

              {selectedPage ? (
                <Card>
                  <CardHead title={selectedPage.name} description={selectedPage.route || '页面控件'} icon="cases"
                    actions={<div className="row-actions">
                      <Button size="sm" variant="ghost" onClick={() => { setPageEditing(selectedPage); setPageForm({ name: selectedPage.name, route: selectedPage.route || '', description: selectedPage.description || '', screenshotUrl: selectedPage.screenshotUrl || '' }); setModalError(''); setPageOpen(true); }} disabled={!canWrite}>编辑页面</Button>
                      <Button size="sm" variant="ghost" onClick={() => void deletePage(selectedPage)} disabled={!canWrite}>删除页面</Button>
                      <Button size="sm" variant="secondary" onClick={() => { setBatchError(''); setBatchOpen(true); }} disabled={!canWrite}>批量导入</Button>
                      <Button size="sm" variant="primary" icon="plus" onClick={() => { setControlEditing(null); setControlForm(EMPTY_CONTROL); setModalError(''); setControlOpen(true); }} disabled={!canWrite}>新建控件</Button>
                    </div>} />
                  <div className="card-body">
                    {selectedPage.screenshotUrl ? <img className="kb-page-shot" src={selectedPage.screenshotUrl} alt="页面截图" /> : null}
                    {controls.length === 0 ? <EmptyState compact icon="cases" title="暂无控件" description="新建控件，或后续通过爬虫/OCR 批量导入" /> : (
                      <div className="kb-control-grid">
                        {controls.map((control) => (
                          <div key={control.id} className="kb-control-card">
                            {control.screenshotUrl ? <img src={control.screenshotUrl} alt="控件截图" /> : <div className="kb-control-placeholder">{control.controlType}</div>}
                            <div className="kb-control-body">
                              <div className="kb-control-title">{control.name || control.text || control.resourceId}</div>
                              <div className="cell-sub mono">{control.text || '—'} · {control.resourceId || 'no id'}</div>
                              <div className="text-muted">意图：{control.intent || '—'}</div>
                              <div className="row-actions">
                                <Pill tone="blue">{control.controlType}</Pill>
                                <Pill tone={control.source === 'AI' ? 'purple' : control.source === 'CRAWLER' ? 'orange' : 'gray'}>{control.source}</Pill>
                                <span className="text-muted">{control.actions.length} 操作</span>
                              </div>
                              <div className="row-actions">
                                <Button size="sm" variant="ghost" onClick={() => void openActions(control)}>操作</Button>
                                <Button size="sm" variant="ghost" onClick={() => { setControlEditing(control); setControlForm({ name: control.name || '', text: control.text || '', resourceId: control.resourceId || '', description: control.description || '', desc: control.desc || '', intent: control.intent || '', controlType: control.controlType, screenshotUrl: control.screenshotUrl || '', bounds: control.bounds || '', source: control.source, confidence: control.confidence ? String(control.confidence) : '' }); setModalError(''); setControlOpen(true); }} disabled={!canWrite}>编辑</Button>
                                <Button size="sm" variant="ghost" onClick={() => void deleteControl(control)} disabled={!canWrite}>删除</Button>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </Card>
              ) : <EmptyState icon="cases" title="选择或新建页面" description="页面是控件的容器" />}
            </>
          )}
        </main>
      </div>

      <Modal open={appOpen} title={appEditing ? '编辑应用' : '新建应用'} size="md"
        onClose={() => !busy && setAppOpen(false)}
        footer={<><Button variant="secondary" onClick={() => setAppOpen(false)} disabled={busy}>取消</Button>
          <Button variant="primary" loading={busy} onClick={() => void saveApp()}>{appEditing ? '保存' : '创建'}</Button></>}>
        <div className="form-stack">
          {modalError ? <Callout tone="error" title="操作失败">{modalError}</Callout> : null}
          <Field label="应用名称" htmlFor="kb-app-name" required><Input id="kb-app-name" value={appForm.name} onChange={(e) => setAppForm({ ...appForm, name: e.target.value })} /></Field>
          <Field label="包名" htmlFor="kb-app-pkg"><Input id="kb-app-pkg" value={appForm.packageName} onChange={(e) => setAppForm({ ...appForm, packageName: e.target.value })} /></Field>
          <Field label="平台" htmlFor="kb-app-platform"><Select id="kb-app-platform" value={appForm.platform} onChange={(e) => setAppForm({ ...appForm, platform: e.target.value })}><option value="android">Android</option><option value="ios">iOS</option><option value="web">Web</option></Select></Field>
          <Field label="描述" htmlFor="kb-app-desc"><Textarea id="kb-app-desc" rows={2} value={appForm.description} onChange={(e) => setAppForm({ ...appForm, description: e.target.value })} /></Field>
        </div>
      </Modal>

      <Modal open={pageOpen} title={pageEditing ? '编辑页面' : '新建页面'} size="md"
        onClose={() => !busy && setPageOpen(false)}
        footer={<><Button variant="secondary" onClick={() => setPageOpen(false)} disabled={busy}>取消</Button>
          <Button variant="primary" loading={busy} onClick={() => void savePage()}>{pageEditing ? '保存' : '创建'}</Button></>}>
        <div className="form-stack">
          {modalError ? <Callout tone="error" title="操作失败">{modalError}</Callout> : null}
          <Field label="页面名称" htmlFor="kb-page-name" required><Input id="kb-page-name" value={pageForm.name} onChange={(e) => setPageForm({ ...pageForm, name: e.target.value })} /></Field>
          <Field label="路由/Activity" htmlFor="kb-page-route"><Input id="kb-page-route" value={pageForm.route} onChange={(e) => setPageForm({ ...pageForm, route: e.target.value })} /></Field>
          <Field label="描述" htmlFor="kb-page-desc"><Textarea id="kb-page-desc" rows={2} value={pageForm.description} onChange={(e) => setPageForm({ ...pageForm, description: e.target.value })} /></Field>
          <Field label="页面截图" htmlFor="kb-page-shot"><input id="kb-page-shot" type="file" accept="image/png,image/jpeg" onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadImage(f, 'page'); }} />{uploading ? ' 上传中…' : ''}{pageForm.screenshotUrl ? <img className="kb-form-shot" src={pageForm.screenshotUrl} alt="页面截图" /> : null}</Field>
        </div>
      </Modal>

      <Modal open={controlOpen} title={controlEditing ? '编辑控件' : '新建控件'} size="lg"
        onClose={() => !busy && setControlOpen(false)}
        footer={<><Button variant="secondary" onClick={() => setControlOpen(false)} disabled={busy}>取消</Button>
          <Button variant="primary" loading={busy} onClick={() => void saveControl()}>{controlEditing ? '保存' : '创建'}</Button></>}>
        <div className="form-stack">
          {modalError ? <Callout tone="error" title="操作失败">{modalError}</Callout> : null}
          <div className="form-grid-2">
            <Field label="控件名称" htmlFor="kb-ctl-name"><Input id="kb-ctl-name" value={controlForm.name} onChange={(e) => setControlForm({ ...controlForm, name: e.target.value })} /></Field>
            <Field label="控件类型" htmlFor="kb-ctl-type"><Select id="kb-ctl-type" value={controlForm.controlType} onChange={(e) => setControlForm({ ...controlForm, controlType: e.target.value })}>{CONTROL_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}</Select></Field>
          </div>
          <div className="form-grid-2">
            <Field label="显示文本 text" htmlFor="kb-ctl-text"><Input id="kb-ctl-text" value={controlForm.text} onChange={(e) => setControlForm({ ...controlForm, text: e.target.value })} /></Field>
            <Field label="定位 id" htmlFor="kb-ctl-id"><Input id="kb-ctl-id" value={controlForm.resourceId} onChange={(e) => setControlForm({ ...controlForm, resourceId: e.target.value })} /></Field>
          </div>
          <Field label="功能意图 intent" htmlFor="kb-ctl-intent"><Input id="kb-ctl-intent" value={controlForm.intent} onChange={(e) => setControlForm({ ...controlForm, intent: e.target.value })} placeholder="如：提交登录、打开设置" /></Field>
          <div className="form-grid-2">
            <Field label="bounds" htmlFor="kb-ctl-bounds"><Input id="kb-ctl-bounds" value={controlForm.bounds} onChange={(e) => setControlForm({ ...controlForm, bounds: e.target.value })} placeholder="x,y,w,h" /></Field>
            <Field label="来源" htmlFor="kb-ctl-source"><Select id="kb-ctl-source" value={controlForm.source} onChange={(e) => setControlForm({ ...controlForm, source: e.target.value })}>{SOURCES.map((source) => <option key={source} value={source}>{source}</option>)}</Select></Field>
          </div>
          <Field label="desc 定位描述" htmlFor="kb-ctl-desc-locator" hint="不显示在界面，但可用于定位/语义说明">
            <Input id="kb-ctl-desc-locator" value={controlForm.desc} onChange={(e) => setControlForm({ ...controlForm, desc: e.target.value })} />
          </Field>
          <Field label="备注" htmlFor="kb-ctl-desc"><Textarea id="kb-ctl-desc" rows={2} value={controlForm.description} onChange={(e) => setControlForm({ ...controlForm, description: e.target.value })} /></Field>
          <Field label="控件截图" htmlFor="kb-ctl-shot"><input id="kb-ctl-shot" type="file" accept="image/png,image/jpeg" onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadImage(f, 'control'); }} />{uploading ? ' 上传中…' : ''}{controlForm.screenshotUrl ? <img className="kb-form-shot" src={controlForm.screenshotUrl} alt="控件截图" /> : null}</Field>
        </div>
      </Modal>

      <Modal open={batchOpen} title="批量导入控件" size="lg"
        onClose={() => !busy && setBatchOpen(false)}
        footer={<><Button variant="secondary" onClick={() => setBatchOpen(false)} disabled={busy}>取消</Button>
          <Button variant="primary" loading={busy} onClick={() => void importBatch()}>导入</Button></>}>
        <div className="form-stack">
          {batchError ? <Callout tone="error" title="导入失败">{batchError}</Callout> : null}
          <Field label="JSON" htmlFor="kb-batch-json" hint='支持 {"controls":[...]} 或直接数组；字段同单控件创建'>
            <Textarea id="kb-batch-json" rows={12} value={batchText} onChange={(e) => setBatchText(e.target.value)} />
          </Field>
        </div>
      </Modal>

      <Modal open={!!actionControl} title={`操作关联 · ${actionControl?.name || actionControl?.text || ''}`} size="md"
        onClose={() => setActionControl(null)}
        footer={<Button variant="secondary" onClick={() => setActionControl(null)}>关闭</Button>}>
        <div className="form-stack">
          <div className="kb-action-list">
            {actions.length === 0 ? <span className="text-muted">尚未关联操作</span> : actions.map((action) => (
              <div key={action.id} className="kb-action-row">
                <Pill tone="blue">{action.actionType}</Pill>
                <span>{action.label || '—'}</span>
                <span className="text-muted">{action.params || ''}</span>
                <Button size="sm" variant="ghost" onClick={() => void removeAction(action)} disabled={!canWrite}>删除</Button>
              </div>
            ))}
          </div>
          {canWrite ? (
            <>
              <div className="form-grid-2">
                <Field label="操作类型" htmlFor="kb-action-type"><Select id="kb-action-type" value={actionForm.actionType} onChange={(e) => setActionForm({ ...actionForm, actionType: e.target.value })}>{ACTION_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}</Select></Field>
                <Field label="标签" htmlFor="kb-action-label"><Input id="kb-action-label" value={actionForm.label} onChange={(e) => setActionForm({ ...actionForm, label: e.target.value })} /></Field>
              </div>
              <Field label="参数 JSON" htmlFor="kb-action-params"><Input id="kb-action-params" value={actionForm.params} onChange={(e) => setActionForm({ ...actionForm, params: e.target.value })} placeholder='{"text":"登录"}' /></Field>
              <Button size="sm" variant="primary" onClick={() => void addAction()}>添加操作</Button>
            </>
          ) : null}
        </div>
      </Modal>
    </div>
  );
}
