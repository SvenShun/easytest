import { request } from './client';

export interface ApiAgent {
  agentId: string;
  name: string;
  baseUrl: string;
  capabilities: string[];
  slots: number;
  activeTasks: number;
  lastSeenAt: string;
}

export const apiAgentsApi = {
  list: () => request<ApiAgent[]>('core', '/api/v1/api-agents'),
};
