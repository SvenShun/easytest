import { asPage, queryString, request } from './client';
import type { ScriptInfo } from './types';

export const scriptsApi = {
  list: (page = 1, size = 100) =>
    request<unknown>('core', `/api/v1/scripts${queryString({ page, size })}`).then((data) =>
      asPage<ScriptInfo>(data, page, size),
    ),
  get: (id: string) => request<ScriptInfo>('core', `/api/v1/scripts/${encodeURIComponent(id)}`),
  upload: (input: { file: File; scriptType: string; entryCommand?: string; manifest?: string }) => {
    const form = new FormData();
    form.append('file', input.file);
    form.append('scriptType', input.scriptType);
    if (input.entryCommand) form.append('entryCommand', input.entryCommand);
    if (input.manifest) form.append('manifest', input.manifest);
    return request<ScriptInfo>('core', '/api/v1/scripts', { method: 'POST', body: form, timeoutMs: 120000 });
  },
  remove: (id: string) => request<void>('core', `/api/v1/scripts/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
