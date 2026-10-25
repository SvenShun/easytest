import { useSyncExternalStore } from 'react';

export interface AppConfig {
  coreUrl: string;
  uexecUrl: string;
}

const CORE_KEY = 'testhub.config.coreUrl';
const UEXEC_KEY = 'testhub.config.uexecUrl';

const env = import.meta.env as Record<string, string | undefined>;

function pageHost(): string {
  return window.location.hostname || '127.0.0.1';
}

function isLoopback(hostname: string): boolean {
  return hostname === '127.0.0.1' || hostname === 'localhost' || hostname === '::1' || hostname === '[::1]';
}

/** 主服务/编排服务的默认地址：跟随当前页面主机名，支持局域网与其他设备访问。 */
function defaultBaseUrl(port: number): string {
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
  return `${protocol}//${pageHost()}:${port}`;
}

/** 旧版本保存过 127.0.0.1 时，若当前页面不是本机，则改写成页面所在主机。 */
function adaptSavedUrl(raw: string): string {
  if (!raw) return '';
  try {
    const url = new URL(raw);
    if (isLoopback(url.hostname) && !isLoopback(pageHost())) {
      url.hostname = pageHost();
      return url.toString().replace(/\/+$/, '');
    }
  } catch {
    // ignore invalid saved value
  }
  return raw;
}

export const DEFAULT_CONFIG: AppConfig = {
  coreUrl: normalizeUrl(env.VITE_CORE_URL) || defaultBaseUrl(8080),
  uexecUrl: normalizeUrl(env.VITE_UEXEC_URL) || defaultBaseUrl(8081),
};

function normalizeUrl(raw: string | undefined | null): string {
  if (!raw) return '';
  let url = String(raw).trim();
  if (!url) return '';
  if (!/^https?:\/\//i.test(url)) url = `http://${url}`;
  return url.replace(/\/+$/, '');
}

function readStorage(key: string): string {
  try {
    return normalizeUrl(window.localStorage.getItem(key));
  } catch {
    return '';
  }
}

function loadConfig(): AppConfig {
  return {
    coreUrl: adaptSavedUrl(readStorage(CORE_KEY)) || DEFAULT_CONFIG.coreUrl,
    uexecUrl: adaptSavedUrl(readStorage(UEXEC_KEY)) || DEFAULT_CONFIG.uexecUrl,
  };
}

let currentConfig: AppConfig = loadConfig();
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((fn) => fn());
}

export function getConfig(): AppConfig {
  return currentConfig;
}

export function setConfig(patch: Partial<AppConfig>): AppConfig {
  const next: AppConfig = {
    coreUrl: normalizeUrl(patch.coreUrl ?? currentConfig.coreUrl) || DEFAULT_CONFIG.coreUrl,
    uexecUrl: normalizeUrl(patch.uexecUrl ?? currentConfig.uexecUrl) || DEFAULT_CONFIG.uexecUrl,
  };
  currentConfig = next;
  try {
    window.localStorage.setItem(CORE_KEY, next.coreUrl);
    window.localStorage.setItem(UEXEC_KEY, next.uexecUrl);
  } catch {
    // localStorage 不可用时仅当前会话生效
  }
  emit();
  return next;
}

export function resetConfig(): AppConfig {
  try {
    window.localStorage.removeItem(CORE_KEY);
    window.localStorage.removeItem(UEXEC_KEY);
  } catch {
    // ignore
  }
  currentConfig = { ...DEFAULT_CONFIG };
  emit();
  return currentConfig;
}

export function subscribeConfig(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useAppConfig(): AppConfig {
  return useSyncExternalStore(subscribeConfig, getConfig, getConfig);
}

/** 主服务验收模式：隐藏设备池/执行任务/总览等依赖执行服务的页面与触发入口 */
export const MAIN_ONLY = import.meta.env.VITE_MAIN_ONLY === 'true';
