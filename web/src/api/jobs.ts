import { asPage, queryString, request } from './client';
import type { TestJob, TestJobInput } from './types';

export const jobsApi = {
  list: (keyword = '', page = 1, size = 20) =>
    request<unknown>('core', `/api/v1/jobs${queryString({ keyword, page, size })}`).then((data) =>
      asPage<TestJob>(data, page, size),
    ),
  create: (input: TestJobInput) => request<TestJob>('core', '/api/v1/jobs', { method: 'POST', body: input }),
  update: (id: string, input: TestJobInput) =>
    request<TestJob>('core', `/api/v1/jobs/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  toggle: (id: string, enabled: boolean) =>
    request<TestJob>('core', `/api/v1/jobs/${encodeURIComponent(id)}/toggle?enabled=${enabled}`, { method: 'POST' }),
  run: (id: string) => request<TestJob>('core', `/api/v1/jobs/${encodeURIComponent(id)}/run`, { method: 'POST' }),
  remove: (id: string) => request<void>('core', `/api/v1/jobs/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
