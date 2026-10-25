import { asPage, queryString, request } from './client';
import type { ApiCase, ApiCaseInput } from './types';

export const apiCasesApi = {
  list: (keyword = '', page = 1, size = 50) =>
    request<unknown>('core', `/api/v1/api-cases${queryString({ keyword, page, size })}`).then((data) =>
      asPage<ApiCase>(data, page, size),
    ),
  get: (id: string) => request<ApiCase>('core', `/api/v1/api-cases/${encodeURIComponent(id)}`),
  create: (input: ApiCaseInput) =>
    request<ApiCase>('core', '/api/v1/api-cases', { method: 'POST', body: input }),
  update: (id: string, input: ApiCaseInput) =>
    request<ApiCase>('core', `/api/v1/api-cases/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/api-cases/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
