import { useSyncExternalStore } from 'react';

export interface AppConfig {
  coreUrl: string;
  uexecUrl: string;
}

const CORE_KEY = 'testhub.config.coreUrl';
const UEXEC_KEY = 'testhub.config.uexecUrl';

const env = import.meta.env as Record<string, string | undefined>;

export const DEFAULT_CONFIG: AppConfig = {
  coreUrl: normalizeUrl(env.VITE_CORE_URL) || 'http://127.0.0.1:8080',
  uexecUrl: normalizeUrl(env.VITE_UEXEC_URL) || 'http://127.0.0.1:8081',
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
    coreUrl: readStorage(CORE_KEY) || DEFAULT_CONFIG.coreUrl,
    uexecUrl: readStorage(UEXEC_KEY) || DEFAULT_CONFIG.uexecUrl,
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
