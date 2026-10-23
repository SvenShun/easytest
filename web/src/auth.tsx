import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { authApi } from './api';
import { USER_KEY, clearToken, getToken, saveToken, setUnauthorizedHandler } from './api/client';
import type { User } from './api/types';
import { toastWarning } from './components/Toast';

interface AuthContextValue {
  token: string | null;
  user: User | null;
  authenticated: boolean;
  login: (username: string, password: string) => Promise<User>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readStoredUser(): User | null {
  try {
    const raw = window.localStorage.getItem(USER_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as User;
    if (parsed && typeof parsed.username === 'string' && typeof parsed.role === 'string') return parsed;
    return null;
  } catch {
    return null;
  }
}

function decodeJwtUser(token: string): User | null {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return null;
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = decodeURIComponent(
      window
        .atob(padded)
        .split('')
        .map((char) => `%${`00${char.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join(''),
    );
    const payload = JSON.parse(json) as Record<string, unknown>;
    if (typeof payload.username !== 'string' || typeof payload.role !== 'string') return null;
    return {
      username: payload.username,
      displayName: typeof payload.name === 'string' ? payload.name : undefined,
      role: payload.role as User['role'],
      id: typeof payload.sub === 'string' ? payload.sub : undefined,
    };
  } catch {
    return null;
  }
}

function storeUser(user: User | null) {
  try {
    if (user) window.localStorage.setItem(USER_KEY, JSON.stringify(user));
    else window.localStorage.removeItem(USER_KEY);
  } catch {
    // ignore
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getToken() || null);
  const [user, setUser] = useState<User | null>(() => readStoredUser() || decodeJwtUser(getToken() || ''));

  const logout = useCallback(() => {
    clearToken();
    storeUser(null);
    setToken(null);
    setUser(null);
    if (!window.location.hash.startsWith('#/login')) window.location.hash = '#/login';
  }, []);

  const refreshUser = useCallback(async () => {
    if (!getToken()) return;
    try {
      const current = await authApi.me();
      setUser(current);
      storeUser(current);
      setToken(getToken() || null);
    } catch {
      // 网络异常时保留本地登录态；401 由全局 unauthorized 处理器清理
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearToken();
      storeUser(null);
      setToken(null);
      setUser(null);
      toastWarning('登录状态已失效，请重新登录', '会话过期');
      if (!window.location.hash.startsWith('#/login')) window.location.hash = '#/login';
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    if (!token) return;
    if (!user) {
      const decoded = decodeJwtUser(token);
      if (decoded) {
        setUser(decoded);
        storeUser(decoded);
      }
    }
    void refreshUser();
    // 仅首次挂载时校验
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username.trim(), password);
    saveToken(response.token);
    storeUser(response.user);
    setToken(response.token);
    setUser(response.user);
    return response.user;
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, authenticated: Boolean(token), login, logout, refreshUser }),
    [token, user, login, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return context;
}

export function hasAnyRole(user: User | null, roles: User['role'][]): boolean {
  if (!user) return false;
  return roles.includes(user.role);
}

export function isAdmin(user: User | null): boolean {
  return user?.role === 'ADMIN';
}

export function canReview(user: User | null): boolean {
  return user?.role === 'ADMIN' || user?.role === 'TESTER';
}

export function canManageCases(user: User | null): boolean {
  return user?.role === 'ADMIN' || user?.role === 'TESTER';
}
