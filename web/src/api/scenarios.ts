import { asPage, queryString, request } from './client';

export interface ApiScenario {
  id: string;
  name: string;
  description?: string | null;
  yaml: string;
  environmentId?: string | null;
  enabled: boolean;
  sourcePath?: string | null;
  sourceCommit?: string | null;
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

export interface ScenarioGraphNode {
  id: string;
  name: string;
  type: string;
  summary?: string | null;
  consumes?: string[];
  produces?: string[];
  warnings?: string[];
  children?: ScenarioGraphNode[];
}

export interface ScenarioGitConfig {
  repoUrl?: string | null;
  branch: string;
  yamlDirectory: string;
  enabled: boolean;
  webhookSecretConfigured: boolean;
  lastCommit?: string | null;
  lastSyncAt?: string | null;
  lastError?: string | null;
  updatedAt?: string | null;
}

export interface ScenarioGitSyncResult {
  commit: string;
  scanned: number;
  created: number;
  updated: number;
  errors: string[];
  syncedAt: string;
}

export interface ScenarioGraph {
  stepCount: number;
  warnings: string[];
  variables: Array<{ name: string; producer: string; consumers: string[] }>;
  steps: ScenarioGraphNode[];
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
  analyze: (yaml: string) =>
    request<ScenarioGraph>('core', '/api/v1/api-scenarios/analyze', { method: 'POST', body: { yaml } }),
  gitConfig: () =>
    request<ScenarioGitConfig>('core', '/api/v1/api-scenarios/git/config'),
  updateGitConfig: (input: { repoUrl?: string; branch?: string; yamlDirectory?: string; webhookSecret?: string; enabled?: boolean }) =>
    request<ScenarioGitConfig>('core', '/api/v1/api-scenarios/git/config', { method: 'PUT', body: input }),
  syncGit: () =>
    request<ScenarioGitSyncResult>('core', '/api/v1/api-scenarios/git/sync', { method: 'POST' }),
  graph: (id: string) =>
    request<ScenarioGraph>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}/graph`),
  validate: (yaml: string) =>
    request<{ valid: boolean; message: string; stepCount: number; stepTypes: string[] }>(
      'core', '/api/v1/api-scenarios/validate', { method: 'POST', body: { yaml } }),
  run: (id: string, input: { environmentId?: string | null; variables?: Record<string, unknown>; timeoutSec?: number }) =>
    request<ScenarioRun>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}/run`, { method: 'POST', body: input }),
  runDetail: (runId: string) =>
    request<ScenarioRun>('core', `/api/v1/api-scenarios/runs/${encodeURIComponent(runId)}`),
  batchRun: (input: { scenarioIds: string[]; environmentId?: string | null; timeoutSec?: number }) =>
    request<{ runs: ScenarioRun[]; started: number; failed: number }>(
      'core', '/api/v1/api-scenarios/batch-run', { method: 'POST', body: input }),
  exportScenario: (id: string) =>
    request<{ name: string; description?: string | null; yaml: string; environmentId?: string | null }>(
      'core', `/api/v1/api-scenarios/${encodeURIComponent(id)}/export`),
  importScenario: (input: { name: string; description?: string; yaml: string; environmentId?: string | null; enabled?: boolean }) =>
    request<ApiScenario>('core', '/api/v1/api-scenarios/import', { method: 'POST', body: input }),
  runs: (id: string, page = 1, size = 20) =>
    request<unknown>('core', `/api/v1/api-scenarios/${encodeURIComponent(id)}/runs${queryString({ page, size })}`)
      .then((data) => asPage<ScenarioRun>(data, page, size)),
};
