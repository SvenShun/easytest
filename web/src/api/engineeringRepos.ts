import { request } from './client';

export interface EngineeringRepo {
  id: string;
  projectId?: string;
  name: string;
  repoType: 'API' | 'UI' | string;
  repoUrl: string;
  branchName: string;
  rootDirectory?: string;
  enabled: boolean;
  lastCommit?: string | null;
  lastScanAt?: string | null;
  lastError?: string | null;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  methodCount?: number;
}

export interface EngineeringRepoMethod {
  id: string;
  repoId?: string;
  kind?: string;
  contractId?: string;
  name: string;
  description?: string | null;
  methodPath: string;
  inputs?: string[];
  outputs?: string[];
  tags?: string[];
  payloadJson?: string | null;
  source?: string;
  updatedAt?: string;
}

export interface RepoScanResult {
  commit?: string;
  scanned: number;
  created: number;
  updated: number;
  removed: number;
  errors: string[];
  scannedAt?: string;
}

export const engineeringReposApi = {
  list: () => request<EngineeringRepo[]>('core', '/api/v1/engineering-repos'),
  create: (input: Partial<EngineeringRepo> & { name: string; repoUrl: string }) =>
    request<EngineeringRepo>('core', '/api/v1/engineering-repos', { method: 'POST', body: input }),
  update: (id: string, input: Partial<EngineeringRepo> & { name: string; repoUrl: string }) =>
    request<EngineeringRepo>('core', `/api/v1/engineering-repos/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  remove: (id: string) => request<void>('core', `/api/v1/engineering-repos/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  scan: (id: string) => request<RepoScanResult>('core', `/api/v1/engineering-repos/${encodeURIComponent(id)}/scan`, { method: 'POST' }),
  methods: (id: string) => request<EngineeringRepoMethod[]>('core', `/api/v1/engineering-repos/${encodeURIComponent(id)}/methods`),
  runMethod: (repoId: string, methodId: string, deviceId?: string) =>
    request<{ taskId: string; status?: string; deviceId?: string }>(
      'core', `/api/v1/engineering-repos/${encodeURIComponent(repoId)}/methods/${encodeURIComponent(methodId)}/run`,
      { method: 'POST', body: { deviceId: deviceId || null, timeoutSec: 120, stepTimeoutSec: 30 } }),
};
