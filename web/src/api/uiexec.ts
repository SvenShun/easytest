import { asPage, queryString, request } from './client';
import type {
  Agent,
  AuditLog,
  Device,
  DeviceQueue,
  DeviceStats,
  SkillsPointer,
  TaskDetail,
  TaskSummary,
} from './types';

export interface TaskListParams {
  page?: number;
  size?: number;
  status?: string;
  planRunId?: string;
  planId?: string;
  deviceId?: string;
  caseId?: string;
}

export const uiexecApi = {
  stats: () => request<DeviceStats>('uexec', '/api/v1/ui-execution/stats'),
  tasks: (params: TaskListParams = {}) => {
    const page = params.page ?? 1;
    const size = params.size ?? 20;
    return request<unknown>(
      'uexec',
      `/api/v1/ui-execution/tasks${queryString({
        page,
        size,
        status: params.status,
        planRunId: params.planRunId,
        planId: params.planId,
        deviceId: params.deviceId,
        caseId: params.caseId,
      })}`,
    ).then((data) => asPage<TaskSummary>(data, page, size));
  },
  task: (taskId: string) =>
    request<TaskDetail>('uexec', `/api/v1/ui-execution/tasks/${encodeURIComponent(taskId)}`),
  cancelTask: (taskId: string) =>
    request<{ taskId: string; status: string }>(
      'uexec',
      `/api/v1/ui-execution/tasks/${encodeURIComponent(taskId)}/cancel`,
      { method: 'POST' },
    ),
  devices: (params: { status?: string; keyword?: string } = {}) =>
    request<unknown>(
      'uexec',
      `/api/v1/ui-execution/devices${queryString({ status: params.status, keyword: params.keyword })}`,
    ).then((data) => asPage<Device>(data)),
  agents: () =>
    request<Agent[] | { items?: Agent[] }>('uexec', '/api/v1/ui-execution/agents').then((data) =>
      Array.isArray(data) ? data : data.items || [],
    ),
  deviceQueue: (deviceId: string) =>
    request<DeviceQueue>('uexec', `/api/v1/ui-execution/devices/${encodeURIComponent(deviceId)}/queue`),
  deviceScreenshot: (deviceId: string) =>
    request('uexec', `/api/v1/ui-execution/devices/${encodeURIComponent(deviceId)}/screenshot`, { raw: true }),
  deviceInstall: (deviceId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<{ ok?: boolean; message?: string }>(
      'uexec',
      `/api/v1/ui-execution/devices/${encodeURIComponent(deviceId)}/install`,
      { method: 'POST', body: form, timeoutMs: 180000 },
    );
  },
  deviceAction: (deviceId: string, action: Record<string, unknown>) =>
    request<{ ok?: boolean; message?: string }>(
      'uexec',
      `/api/v1/ui-execution/devices/${encodeURIComponent(deviceId)}/action`,
      { method: 'POST', body: action },
    ),
  skillsPointer: () => request<SkillsPointer>('uexec', '/api/v1/ui-execution/skills/pointer'),
  updateSkillsPointer: (input: { gitUrl: string; branch: string }) =>
    request<SkillsPointer>('uexec', '/api/v1/ui-execution/skills/pointer', {
      method: 'PUT',
      body: input,
    }),
  syncSkills: () =>
    request<{ activeCommit: string; syncedAt: string }>(
      'uexec',
      '/api/v1/ui-execution/skills/pointer/sync',
      { method: 'POST' },
    ),
  rollbackSkills: (commit: string) =>
    request<SkillsPointer>('uexec', '/api/v1/ui-execution/skills/pointer/rollback', {
      method: 'POST',
      body: { commit },
    }),
  auditLogs: (page = 1, size = 50) =>
    request<unknown>('uexec', `/api/v1/ui-execution/audit-logs${queryString({ page, size })}`).then(
      (data) => asPage<AuditLog>(data, page, size),
    ),
};
