import { request } from './client';

export interface NotificationChannel {
  id: string;
  name: string;
  channelType: 'GENERIC' | 'DINGTALK' | 'WECOM' | 'FEISHU' | string;
  webhookUrl: string;
  hasSecret: boolean;
  enabled: boolean;
  events: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  lastTestAt?: string | null;
  lastTestStatus?: string | null;
}

export interface NotificationInput {
  name: string;
  channelType: string;
  webhookUrl: string;
  secret?: string | null;
  enabled: boolean;
  events: string;
}

export const notificationsApi = {
  list: () => request<NotificationChannel[]>('core', '/api/v1/notification-channels'),
  create: (input: NotificationInput) =>
    request<NotificationChannel>('core', '/api/v1/notification-channels', { method: 'POST', body: input }),
  update: (id: string, input: NotificationInput) =>
    request<NotificationChannel>('core', `/api/v1/notification-channels/${encodeURIComponent(id)}`, {
      method: 'PUT', body: input,
    }),
  test: (id: string) =>
    request<{ ok: boolean; message: string; sentAt: string }>(
      'core', `/api/v1/notification-channels/${encodeURIComponent(id)}/test`, { method: 'POST' }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/notification-channels/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
