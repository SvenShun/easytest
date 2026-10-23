import { asPage, queryString, request } from './client';
import type { AuditLog, ModelConfig, ModelConfigInput, User } from './types';

export const systemApi = {
  modelConfigs: () =>
    request<{ items: ModelConfig[] } | ModelConfig[]>('core', '/api/v1/system/model-configs'),
  updateModelConfig: (configType: string, input: ModelConfigInput) =>
    request<ModelConfig>('core', `/api/v1/system/model-configs/${encodeURIComponent(configType)}`, {
      method: 'PUT',
      body: input,
    }),
  auditLogs: (page = 1, size = 50, action = '') =>
    request<unknown>('core', `/api/v1/system/audit-logs${queryString({ page, size, action })}`).then(
      (data) => asPage<AuditLog>(data, page, size),
    ),
  users: (page = 1, size = 50) =>
    request<unknown>('core', `/api/v1/system/users${queryString({ page, size })}`).then((data) =>
      asPage<User>(data, page, size),
    ),
};
