import { request } from './client';

export interface DeviceGroup {
  id: string;
  name: string;
  description?: string | null;
  deviceIds: string[];
  deviceCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DeviceGroupInput {
  name: string;
  description?: string | null;
  deviceIds: string[];
}

export const deviceGroupsApi = {
  list: () => request<DeviceGroup[]>('uexec', '/api/v1/ui-execution/device-groups'),
  create: (input: DeviceGroupInput) =>
    request<DeviceGroup>('uexec', '/api/v1/ui-execution/device-groups', { method: 'POST', body: input }),
  update: (id: string, input: DeviceGroupInput) =>
    request<DeviceGroup>('uexec', `/api/v1/ui-execution/device-groups/${encodeURIComponent(id)}`, {
      method: 'PUT', body: input,
    }),
  remove: (id: string) =>
    request<void>('uexec', `/api/v1/ui-execution/device-groups/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
