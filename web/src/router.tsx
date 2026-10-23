import { useEffect, useState } from 'react';

export interface RouteState {
  /** 形如 /plans/abc */
  path: string;
  /** path 拆段，如 ['plans','abc'] */
  segments: string[];
  query: URLSearchParams;
  raw: string;
}

function parseHash(): RouteState {
  const raw = window.location.hash || '#/dashboard';
  const withoutHash = raw.replace(/^#/, '');
  const queryIndex = withoutHash.indexOf('?');
  const rawPath = queryIndex >= 0 ? withoutHash.slice(0, queryIndex) : withoutHash;
  const queryText = queryIndex >= 0 ? withoutHash.slice(queryIndex + 1) : '';
  let path = rawPath || '/dashboard';
  if (!path.startsWith('/')) path = `/${path}`;
  if (path.length > 1) path = path.replace(/\/+$/, '');
  return {
    path,
    segments: path.split('/').filter(Boolean),
    query: new URLSearchParams(queryText),
    raw: withoutHash,
  };
}

export function useHashRoute(): RouteState {
  const [route, setRoute] = useState<RouteState>(() => parseHash());
  useEffect(() => {
    const onChange = () => setRoute(parseHash());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

export function navigate(to: string, options: { replace?: boolean } = {}) {
  const target = to.startsWith('#') ? to : `#${to.startsWith('/') ? to : `/${to}`}`;
  if (window.location.hash === target) {
    window.dispatchEvent(new Event('hashchange'));
    return;
  }
  if (options.replace) {
    window.history.replaceState(null, '', target);
    window.dispatchEvent(new Event('hashchange'));
  } else {
    window.location.hash = target;
  }
}

export function buildPath(path: string, query: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `${path}?${text}` : path;
}

/** 路由变化时滚动回页面顶部 */
export function useScrollTopOnRoute(path: string) {
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [path]);
}
