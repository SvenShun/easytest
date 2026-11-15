import { request } from './client';

export interface Project {
  id: string;
  name: string;
  key?: string | null;
  description?: string | null;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  memberCount?: number;
}

export interface ProjectMember {
  id: string;
  username: string;
  role: string;
  createdAt?: string;
}

export const projectsApi = {
  list: () => request<Project[]>('core', '/api/v1/projects'),
  create: (input: { name: string; key?: string; description?: string }) =>
    request<Project>('core', '/api/v1/projects', { method: 'POST', body: input }),
  update: (id: string, input: { name: string; key?: string; description?: string }) =>
    request<Project>('core', `/api/v1/projects/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  remove: (id: string) => request<void>('core', `/api/v1/projects/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  members: (id: string) => request<ProjectMember[]>('core', `/api/v1/projects/${encodeURIComponent(id)}/members`),
  addMember: (id: string, input: { username: string; role?: string }) =>
    request<ProjectMember>('core', `/api/v1/projects/${encodeURIComponent(id)}/members`, { method: 'POST', body: input }),
  removeMember: (id: string, memberId: string) =>
    request<void>('core', `/api/v1/projects/${encodeURIComponent(id)}/members/${encodeURIComponent(memberId)}`, { method: 'DELETE' }),
};
