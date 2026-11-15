import { request } from './client';

export interface EngineeringMethod {
  id: string;
  name: string;
  description?: string | null;
  methodPath: string;
  inputs?: string[];
  outputs?: string[];
  tags?: string[];
  source?: string;
  updatedAt?: string;
}

export const engineeringMethodsApi = {
  list: () => request<EngineeringMethod[]>('core', '/api/v1/engineering-methods'),
  sync: () => request<{ imported: number; total: number; errors: string[] }>(
    'core', '/api/v1/engineering-methods/sync', { method: 'POST' }),
  importManifest: (yaml: string) => request<{ imported: number; total: number; errors: string[] }>(
    'core', '/api/v1/engineering-methods/import', { method: 'POST', body: { yaml } }),
};
