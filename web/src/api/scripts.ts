import { asPage, queryString, request } from './client';
import type { ScriptInfo } from './types';

export const scriptsApi = {
  list: (page = 1, size = 100) =>
    request<unknown>('core', `/api/v1/scripts${queryString({ page, size })}`).then((data) =>
      asPage<ScriptInfo>(data, page, size),
    ),
  get: (id: string) => request<ScriptInfo>('core', `/api/v1/scripts/${encodeURIComponent(id)}`),
};
