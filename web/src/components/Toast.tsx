import { useSyncExternalStore } from 'react';
import { Icon } from './icons';
import type { IconName } from './icons';

export type ToastTone = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
  title?: string;
}

const TOAST_TTL = 4200;
let items: ToastItem[] = [];
let seq = 0;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((fn) => fn());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getSnapshot() {
  return items;
}

export function dismissToast(id: number) {
  items = items.filter((item) => item.id !== id);
  emit();
}

export function toast(message: string, tone: ToastTone = 'info', title?: string): number {
  const id = ++seq;
  items = [...items.slice(-3), { id, tone, message, title }];
  emit();
  window.setTimeout(() => dismissToast(id), TOAST_TTL);
  return id;
}

export const toastSuccess = (message: string, title?: string) => toast(message, 'success', title);
export const toastError = (message: string, title?: string) => toast(message, 'error', title);
export const toastWarning = (message: string, title?: string) => toast(message, 'warning', title);
export const toastInfo = (message: string, title?: string) => toast(message, 'info', title);

const TONE_META: Record<ToastTone, { icon: IconName; title: string }> = {
  success: { icon: 'check-circle', title: '操作成功' },
  error: { icon: 'x-circle', title: '操作失败' },
  warning: { icon: 'warning', title: '注意' },
  info: { icon: 'info', title: '提示' },
};

export function ToastHost() {
  const list = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
  return (
    <div className="toast-stack" role="status" aria-live="polite">
      {list.map((item) => {
        const meta = TONE_META[item.tone];
        return (
          <div key={item.id} className={`toast toast-${item.tone}`}>
            <span className="toast-icon">
              <Icon name={meta.icon} size={18} />
            </span>
            <div className="toast-body">
              <div className="toast-title">{item.title || meta.title}</div>
              <div className="toast-message">{item.message}</div>
            </div>
            <button
              type="button"
              className="toast-close"
              aria-label="关闭通知"
              onClick={() => dismissToast(item.id)}
            >
              <Icon name="close" size={14} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
