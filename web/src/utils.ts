export function cx(...values: Array<string | false | null | undefined>): string {
  return values.filter(Boolean).join(' ');
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** 本地时间 YYYY-MM-DD HH:mm:ss */
export function formatDateTime(value?: string | number | Date | null): string {
  if (value === null || value === undefined || value === '') return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/** 本地时间 HH:mm:ss */
export function formatTime(value?: string | number | Date | null): string {
  if (!value) return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function isSameLocalDay(value: string | number | Date | null | undefined, reference = new Date()): boolean {
  if (!value) return false;
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return false;
  return (
    date.getFullYear() === reference.getFullYear() &&
    date.getMonth() === reference.getMonth() &&
    date.getDate() === reference.getDate()
  );
}

export function formatDurationMs(ms?: number | null): string {
  if (ms === null || ms === undefined || Number.isNaN(ms)) return '—';
  const total = Math.max(0, Math.round(ms));
  if (total < 1000) return `${total} ms`;
  const seconds = total / 1000;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.floor(seconds % 60);
  if (minutes < 60) return `${minutes} 分 ${pad(rest)} 秒`;
  const hours = Math.floor(minutes / 60);
  return `${hours} 时 ${pad(minutes % 60)} 分`;
}

export function durationBetween(
  start?: string | null,
  end?: string | null,
  fallbackMs?: number | null,
): number | null {
  if (fallbackMs !== null && fallbackMs !== undefined) return fallbackMs;
  if (!start) return null;
  const startAt = new Date(start).getTime();
  if (Number.isNaN(startAt)) return null;
  const endAt = end ? new Date(end).getTime() : Date.now();
  if (Number.isNaN(endAt)) return null;
  return Math.max(0, endAt - startAt);
}

export function truncate(text: string | null | undefined, max = 120): string {
  if (!text) return '';
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

export function toText(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function formatDetail(value: unknown, max = 160): string {
  if (typeof value === 'string') return truncate(value, max);
  if (!value) return '—';
  const text = toText(value);
  return text === '{}' ? '—' : truncate(text, max);
}

export function parseTags(raw: string): string[] {
  return raw
    .split(/[,，\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

export function isTerminalTask(status?: string | null): boolean {
  return ['SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED', 'SKIPPED'].includes(String(status || ''));
}

export function isTerminalRun(status?: string | null): boolean {
  return ['COMPLETED', 'PARTIAL', 'FAILED', 'SUCCESS', 'TIMEOUT', 'CANCELLED'].includes(
    String(status || ''),
  );
}

export function percent(part: number, total: number): string {
  if (!total) return '0%';
  return `${Math.round((part / total) * 100)}%`;
}

export function userDisplayName(user?: { displayName?: string; name?: string; username?: string } | null): string {
  if (!user) return '';
  return user.displayName || user.name || user.username || '';
}

export function safeErrorMessage(error: unknown): string {
  if (!error) return '未知错误';
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message || '请求失败';
  return String(error);
}

export function shortId(value?: string | null, head = 8): string {
  if (!value) return '—';
  return value.length > head + 4 ? `${value.slice(0, head)}…${value.slice(-4)}` : value;
}
