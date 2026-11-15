import { request } from './client';

export interface EngineeringMethod {
  id: string;
  name: string;
  description?: string | null;
  methodPath: string;
  inputs?: string[];
  outputs?: string[];
  tags?: string[];
}

export const engineeringMethodsApi = {
  list: () => request<EngineeringMethod[]>('core', '/api/v1/engineering-methods'),
};
