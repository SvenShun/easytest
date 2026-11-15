import { request } from './client';

export interface ApiAgent {
  agentId: string;
  name: string;
  baseUrl: string;
  capabilities: string[];
  slots: number;
  activeTasks: number;
  status?: string;
  lastSeenAt: string;
}

export const apiAgentsApi = {
  list: () => request<ApiAgent[]>('core', '/api/v1/api-agents'),
  drain: (agentId: string) => request<{ agentId: string; status: string }>(
    'core', `/api/v1/api-agents/${encodeURIComponent(agentId)}/drain`, { method: 'POST' }),
};
