import { asPage, queryString, request } from './client';
import type { ApiEnvironment, ApiEnvironmentInput } from './types';

export const apiEnvironmentsApi = {
  list: (keyword = '', page = 1, size = 50) =>
    request<unknown>('core', `/api/v1/api-environments${queryString({ keyword, page, size })}`).then((data) =>
      asPage<ApiEnvironment>(data, page, size),
    ),
  get: (id: string) => request<ApiEnvironment>('core', `/api/v1/api-environments/${encodeURIComponent(id)}`),
  create: (input: ApiEnvironmentInput) =>
    request<ApiEnvironment>('core', '/api/v1/api-environments', { method: 'POST', body: input }),
  update: (id: string, input: ApiEnvironmentInput) =>
    request<ApiEnvironment>('core', `/api/v1/api-environments/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: input,
    }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/api-environments/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
