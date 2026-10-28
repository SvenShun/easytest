import { asPage, queryString, request } from './client';

export interface ApiScenario {
  id: string;
  name: string;
  description?: string | null;
  yaml: string;
  environmentId?: string | null;
  enabled: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ScenarioStep {
  seq: number;
  name: string;
  type: string;
  status: string;
  durationMs?: number;
  request?: Record<string, unknown>;
  response?: Record<string, unknown>;
  assertions?: Array<{ type: string; path?: string; operator?: string; expected?: string; actual?: string; passed: boolean; message?: string }>;
  extracted?: Record<string, unknown>;
  error?: string | null;
}

export interface ScenarioRun {
  id: string;
  scenarioId: string;
  scenarioName?: string;
  taskId?: string;
  status: string;
  environmentId?: string | null;
  summary?: string | null;
  failureReason?: string | null;
  durationMs?: number | null;
  steps?: ScenarioStep[] | null;
  report?: unknown;
  variables?: Record<string, unknown>;
  createdBy?: string;
  createdAt?: string;
  finishedAt?: string | null;
}

export const apiScenariosApi = {
  list: (keyword = '', page = 1, size = 50) =>
    request<unknown>('core', `/api/v1/api-scenarios${queryString({ keyword, page, size })}`)
      .then((data) => asPage<ApiScenario>(data, page, size)),
  create: (input: { name: string; description?: string; yaml: string; environmentId?: string | null; enabled?: boolean }) =>
    request<ApiScenario>('core', '/api/v1/api-scenarios', { method: 'POST', body: input }),
  update: (id: string, input: { name: string; description?: string; yaml: string; environmentId?: string | null; enabled?: boolean }) =>
    request<ApiScenario>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  validate: (yaml: string) =>
    request<{ valid: boolean; message: string; stepCount: number; stepTypes: string[] }>(
      'core', '/api/v1/api-scenarios/validate', { method: 'POST', body: { yaml } }),
  run: (id: string, input: { environmentId?: string | null; variables?: Record<string, unknown>; timeoutSec?: number }) =>
    request<ScenarioRun>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}/run`, { method: 'POST', body: input }),
  runDetail: (runId: string) =>
    request<ScenarioRun>('core', `/api/v1/api-scenarios/runs/${encodeURIComponent(runId)}`),
  runs: (id: string, page = 1, size = 20) =>
    request<unknown>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}/runs${queryString({ page, size })}`)
      .then((data) => asPage<ScenarioRun>(data, page, size)),
};
