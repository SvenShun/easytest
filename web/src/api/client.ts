import { getConfig } from '../config';
import type { Page } from './types';

export const TOKEN_KEY = 'testhub.token';
export const USER_KEY = 'testhub.user';

export class ApiError extends Error {
  status: number;
  code: string;
  details?: unknown;
  path?: string;

  constructor(message: string, options: { status?: number; code?: string; details?: unknown; path?: string } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = options.status ?? 0;
    this.code = options.code ?? 'REQUEST_FAILED';
    this.details = options.details;
    this.path = options.path;
  }
}

export function getToken(): string {
  try {
    return window.localStorage.getItem(TOKEN_KEY) || '';
  } catch {
    return '';
  }
}

export function saveToken(token: string) {
  try {
    window.localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // ignore
  }
}

export function clearToken() {
  try {
    window.localStorage.removeItem(TOKEN_KEY);
    window.localStorage.removeItem(USER_KEY);
  } catch {
    // ignore
  }
}

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

export type ApiBase = 'core' | 'uexec' | string;

function resolveBase(base: ApiBase): string {
  const config = getConfig();
  if (base === 'core') return config.coreUrl;
  if (base === 'uexec') return config.uexecUrl;
  return String(base || '').replace(/\/+$/, '');
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body?: unknown;
  raw?: boolean;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  timeoutMs?: number;
  /** 登录等公开接口：401 时不触发全局登出跳转 */
  skipAuthRedirect?: boolean;
}

const DEFAULT_TIMEOUT = 20000;

function parseErrorMessage(payload: unknown, fallback: string): { message: string; code?: string; details?: unknown } {
  if (payload && typeof payload === 'object') {
    const obj = payload as Record<string, unknown>;
    const message =
      (typeof obj.message === 'string' && obj.message) ||
      (typeof obj.error === 'string' && obj.error) ||
      fallback;
    return {
      message,
      code: typeof obj.code === 'string' ? obj.code : undefined,
      details: obj.details,
    };
  }
  if (typeof payload === 'string' && payload.trim()) {
    return { message: payload.slice(0, 500) };
  }
  return { message: fallback };
}

export async function request<T>(
  base: ApiBase,
  path: string,
  options?: RequestOptions & { raw?: false },
): Promise<T>;
export async function request(
  base: ApiBase,
  path: string,
  options: RequestOptions & { raw: true },
): Promise<Response>;
export async function request<T>(
  base: ApiBase,
  path: string,
  options: RequestOptions = {},
): Promise<T | Response> {
  const root = resolveBase(base);
  const url = `${root}${path.startsWith('/') ? path : `/${path}`}`;
  const controller = new AbortController();
  let timedOut = false;
  const timer = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, options.timeoutMs ?? DEFAULT_TIMEOUT);

  const onExternalAbort = () => controller.abort();
  if (options.signal) {
    if (options.signal.aborted) controller.abort();
    else options.signal.addEventListener('abort', onExternalAbort, { once: true });
  }

  const headers: Record<string, string> = { Accept: 'application/json', ...(options.headers || {}) };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let payload: BodyInit | undefined;
  if (options.body !== undefined && options.body !== null) {
    if (options.body instanceof FormData) {
      payload = options.body;
    } else {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(options.body);
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? (payload ? 'POST' : 'GET'),
      headers,
      body: payload,
      signal: controller.signal,
    });
  } catch (error) {
    if (timedOut) {
      throw new ApiError('请求超时（20 秒），请稍后重试', {
        code: 'TIMEOUT',
        path,
      });
    }
    if (options.signal?.aborted) {
      throw new ApiError('请求已取消', { code: 'ABORTED', path });
    }
    throw new ApiError(`无法连接服务 ${root}，请确认服务已启动或检查服务地址配置`, {
      code: 'NETWORK_ERROR',
      path,
      details: error instanceof Error ? error.message : String(error),
    });
  } finally {
    window.clearTimeout(timer);
    if (options.signal) options.signal.removeEventListener('abort', onExternalAbort);
  }

  if (options.raw) {
    if (!response.ok) {
      let data: unknown = null;
      try {
        data = await response.clone().json();
      } catch {
        // ignore
      }
      if (response.status === 401 && !options.skipAuthRedirect) {
        const hadToken = Boolean(getToken());
        clearToken();
        if (hadToken) unauthorizedHandler?.();
      }
      const parsed = parseErrorMessage(data, `请求失败（HTTP ${response.status}）`);
      throw new ApiError(parsed.message, {
        status: response.status,
        code: parsed.code || `HTTP_${response.status}`,
        details: parsed.details,
        path,
      });
    }
    return response;
  }

  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && !options.skipAuthRedirect) {
      const hadToken = Boolean(getToken());
      clearToken();
      if (hadToken) unauthorizedHandler?.();
    }
    const parsed = parseErrorMessage(data, `请求失败（HTTP ${response.status}）`);
    throw new ApiError(parsed.message, {
      status: response.status,
      code: parsed.code || `HTTP_${response.status}`,
      details: parsed.details,
      path,
    });
  }

  return data as T;
}

export function queryString(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

/** 兼容后端返回数组或分页对象两种形态 */
export function asPage<T>(data: unknown, fallbackPage = 1, fallbackSize = 20): Page<T> {
  if (Array.isArray(data)) {
    return { items: data as T[], total: data.length, page: fallbackPage, size: fallbackSize };
  }
  if (data && typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    const items = Array.isArray(obj.items) ? (obj.items as T[]) : [];
    return {
      items,
      total: typeof obj.total === 'number' ? obj.total : items.length,
      page: typeof obj.page === 'number' ? obj.page : fallbackPage,
      size: typeof obj.size === 'number' ? obj.size : fallbackSize,
    };
  }
  return { items: [], total: 0, page: fallbackPage, size: fallbackSize };
}
