import { request } from './client';

export interface KbApp {
  id: string; name: string; packageName?: string | null; platform?: string | null;
  description?: string | null; iconUrl?: string | null; pageCount: number; controlCount: number;
  createdBy?: string; createdAt?: string; updatedAt?: string;
}
export interface KbPage {
  id: string; appId: string; name: string; route?: string | null; description?: string | null;
  screenshotUrl?: string | null; controlCount: number; createdAt?: string; updatedAt?: string;
}
export interface KbAction {
  id: string; controlId: string; actionType: string; label?: string | null; params?: string | null;
  source: string; enabled: boolean; createdAt?: string;
}
export interface KbControl {
  id: string; pageId: string; name?: string | null; text?: string | null; resourceId?: string | null;
  description?: string | null; desc?: string | null; intent?: string | null; controlType: string;
  screenshotUrl?: string | null;
  bounds?: string | null; source: string; confidence?: number | null; actions: KbAction[];
  createdAt?: string; updatedAt?: string;
}

export const kbApi = {
  apps: () => request<KbApp[]>('core', '/api/v1/kb/apps'),
  createApp: (input: object) =>
    request<KbApp>('core', '/api/v1/kb/apps', { method: 'POST', body: input }),
  updateApp: (id: string, input: object) =>
    request<KbApp>('core', `/api/v1/kb/apps/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  deleteApp: (id: string) =>
    request<void>('core', `/api/v1/kb/apps/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  pages: (appId: string) =>
    request<KbPage[]>('core', `/api/v1/kb/apps/${encodeURIComponent(appId)}/pages`),
  createPage: (appId: string, input: object) =>
    request<KbPage>('core', `/api/v1/kb/apps/${encodeURIComponent(appId)}/pages`, { method: 'POST', body: input }),
  updatePage: (id: string, input: object) =>
    request<KbPage>('core', `/api/v1/kb/pages/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  deletePage: (id: string) =>
    request<void>('core', `/api/v1/kb/pages/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  controls: (pageId: string) =>
    request<KbControl[]>('core', `/api/v1/kb/pages/${encodeURIComponent(pageId)}/controls`),
  batchControls: (pageId: string, controls: object[]) =>
    request<KbControl[]>('core', `/api/v1/kb/pages/${encodeURIComponent(pageId)}/controls/batch`, {
      method: 'POST', body: { controls },
    }),
  createControl: (pageId: string, input: object) =>
    request<KbControl>('core', `/api/v1/kb/pages/${encodeURIComponent(pageId)}/controls`, { method: 'POST', body: input }),
  updateControl: (id: string, input: object) =>
    request<KbControl>('core', `/api/v1/kb/controls/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  deleteControl: (id: string) =>
    request<void>('core', `/api/v1/kb/controls/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  actions: (controlId: string) =>
    request<KbAction[]>('core', `/api/v1/kb/controls/${encodeURIComponent(controlId)}/actions`),
  createAction: (controlId: string, input: object) =>
    request<KbAction>('core', `/api/v1/kb/controls/${encodeURIComponent(controlId)}/actions`, { method: 'POST', body: input }),
  deleteAction: (id: string) =>
    request<void>('core', `/api/v1/kb/actions/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  uploadScreenshot: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<{ url: string }>('core', '/api/v1/kb/screenshots', { method: 'POST', body: form });
  },
};
