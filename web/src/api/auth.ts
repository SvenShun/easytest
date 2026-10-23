import { request } from './client';
import type { LoginResponse, User } from './types';

export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResponse>('core', '/api/v1/auth/login', {
      method: 'POST',
      body: { username, password },
      skipAuthRedirect: true,
    }),
  me: () => request<User>('core', '/api/v1/auth/me'),
};
