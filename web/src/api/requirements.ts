import { asPage, queryString, request } from './client';
import type { Requirement } from './types';

export const requirementsApi = {
  list: (keyword = '', page = 1, size = 100) =>
    request<unknown>('core', `/api/v1/requirements${queryString({ keyword, page, size })}`).then(
      (data) => asPage<Requirement>(data, page, size),
    ),
  get: (id: string) => request<Requirement>('core', `/api/v1/requirements/${encodeURIComponent(id)}`),
  create: (input: { title: string; description?: string }) =>
    request<Requirement>('core', '/api/v1/requirements', { method: 'POST', body: input }),
  update: (id: string, input: { title: string; description?: string }) =>
    request<Requirement>('core', `/api/v1/requirements/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: input,
    }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/requirements/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
