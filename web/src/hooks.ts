import { useEffect, useRef, useState } from 'react';

/**
 * 固定间隔轮询。callback 始终取最新闭包；delay 为 null 时暂停。
 * 页面隐藏时跳过执行，重新可见时立即补一次。
 */
export function usePolling(callback: () => void, delay: number | null, options: { immediate?: boolean } = {}) {
  const savedCallback = useRef(callback);
  const immediate = options.immediate ?? false;

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (delay === null || delay <= 0) return undefined;
    let timer: number | undefined;
    const tick = () => {
      if (document.visibilityState !== 'hidden') savedCallback.current();
    };
    if (immediate) tick();
    timer = window.setInterval(tick, delay);
    const onVisible = () => {
      if (document.visibilityState === 'visible') savedCallback.current();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      if (timer) window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [delay, immediate]);
}

export function useDebouncedValue<T>(value: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

export function useNow(intervalMs = 1000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}
