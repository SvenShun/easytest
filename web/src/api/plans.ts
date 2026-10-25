import { asPage, queryString, request } from './client';
import type {
  Plan,
  PlanDetail,
  PlanRun,
  RunItem,
  RunReport,
  TriggerPayload,
  TriggerResponse,
} from './types';

export const plansApi = {
  list: (keyword = '', page = 1, size = 10) =>
    request<unknown>('core', `/api/v1/plans${queryString({ keyword, page, size })}`).then((data) =>
      asPage<Plan>(data, page, size),
    ),
  get: (id: string) => request<PlanDetail>('core', `/api/v1/plans/${encodeURIComponent(id)}`),
  create: (input: { name: string; description: string }) =>
    request<Plan>('core', '/api/v1/plans', { method: 'POST', body: input }),
  update: (id: string, input: { name: string; description: string }) =>
    request<Plan>('core', `/api/v1/plans/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/plans/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  addCases: (id: string, caseIds: string[], executorTypeOverride?: string | null,
             apiCaseIds: string[] = []) =>
    request<unknown>('core', `/api/v1/plans/${encodeURIComponent(id)}/cases`, {
      method: 'POST',
      body: { caseIds, apiCaseIds, executorTypeOverride: executorTypeOverride ?? null },
    }),
  removeCase: (id: string, caseId: string) =>
    request<void>('core', `/api/v1/plans/${encodeURIComponent(id)}/cases/${encodeURIComponent(caseId)}`, {
      method: 'DELETE',
    }),
  trigger: (id: string, payload: TriggerPayload) =>
    request<TriggerResponse>('core', `/api/v1/plans/${encodeURIComponent(id)}/trigger`, {
      method: 'POST',
      body: payload,
    }),
  runs: (id: string, page = 1, size = 20) =>
    request<unknown>('core', `/api/v1/plans/${encodeURIComponent(id)}/runs${queryString({ page, size })}`).then(
      (data) => asPage<PlanRun>(data, page, size),
    ),
  run: (runId: string) =>
    request<{ run: PlanRun; items: RunItem[] }>('core', `/api/v1/plan-runs/${encodeURIComponent(runId)}`),
  report: (runId: string) =>
    request<RunReport>('core', `/api/v1/plan-runs/${encodeURIComponent(runId)}/report`),
};
