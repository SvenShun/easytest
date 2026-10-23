import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { useCallback, useId, useState } from 'react';
import { Icon } from './icons';
import type { IconName } from './icons';
import { cx, formatDateTime, toText } from '../utils';
import { FAILURE_CATEGORY_META, metaOf } from '../constants';
import type { StatusMeta, Tone } from '../constants';
import { toastSuccess, toastError } from './Toast';

/* ------------------------------------------------------------------ */
/* 基础控件                                                            */
/* ------------------------------------------------------------------ */

export function Spinner({ size = 14, className }: { size?: number; className?: string }) {
  return (
    <span
      className={cx('spinner', className)}
      style={{ width: size, height: size }}
      aria-hidden="true"
    />
  );
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'success';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  icon?: IconName;
  iconRight?: IconName;
  block?: boolean;
  loadingText?: string;
}

export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  iconRight,
  block,
  loadingText,
  className,
  children,
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cx('btn', `btn-${variant}`, `btn-${size}`, block && 'btn-block', className)}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Spinner size={14} /> : icon ? <Icon name={icon} size={size === 'sm' ? 14 : 16} /> : null}
      <span>{loading && loadingText ? loadingText : children}</span>
      {!loading && iconRight ? <Icon name={iconRight} size={size === 'sm' ? 14 : 16} /> : null}
    </button>
  );
}

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: IconName;
  label: string;
  size?: number;
  tone?: 'default' | 'danger';
}

export function IconButton({ icon, label, size = 16, tone = 'default', className, ...rest }: IconButtonProps) {
  return (
    <button
      type="button"
      className={cx('icon-btn', tone === 'danger' && 'icon-btn-danger', className)}
      title={label}
      aria-label={label}
      {...rest}
    >
      <Icon name={icon} size={size} />
    </button>
  );
}

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  icon?: IconName;
  suffix?: ReactNode;
}

export function Input({ invalid, icon, suffix, className, ...rest }: InputProps) {
  return (
    <div className={cx('input-wrap', invalid && 'is-invalid', className)}>
      {icon ? (
        <span className="input-icon">
          <Icon name={icon} size={15} />
        </span>
      ) : null}
      <input className={cx('input', icon && 'has-icon')} {...rest} />
      {suffix ? <span className="input-suffix">{suffix}</span> : null}
    </div>
  );
}

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export function Textarea({ invalid, className, ...rest }: TextareaProps) {
  return <textarea className={cx('textarea', invalid && 'is-invalid', className)} {...rest} />;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

export function Select({ invalid, className, children, ...rest }: SelectProps) {
  return (
    <div className={cx('select-wrap', invalid && 'is-invalid', className)}>
      <select className="select" {...rest}>
        {children}
      </select>
      <span className="select-caret">
        <Icon name="chevron-down" size={14} />
      </span>
    </div>
  );
}

export interface FieldProps {
  label?: ReactNode;
  htmlFor?: string;
  required?: boolean;
  error?: string | null;
  hint?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function Field({ label, htmlFor, required, error, hint, children, className }: FieldProps) {
  return (
    <div className={cx('field', className)}>
      {label ? (
        <label className="field-label" htmlFor={htmlFor}>
          {label}
          {required ? <span className="field-required">*</span> : null}
        </label>
      ) : null}
      {children}
      {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* 展示组件                                                            */
/* ------------------------------------------------------------------ */

export function Card({
  children,
  className,
  ...rest
}: {
  children: ReactNode;
  className?: string;
  [key: string]: unknown;
}) {
  return (
    <section className={cx('card', className)} {...rest}>
      {children}
    </section>
  );
}

export function CardHead({
  title,
  description,
  actions,
  icon,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  icon?: IconName;
}) {
  return (
    <div className="card-head">
      <div className="card-head-main">
        {icon ? (
          <span className="card-head-icon">
            <Icon name={icon} size={16} />
          </span>
        ) : null}
        <div>
          <h3 className="card-title">{title}</h3>
          {description ? <p className="card-desc">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="card-actions">{actions}</div> : null}
    </div>
  );
}

export function PageHeading({
  title,
  description,
  actions,
  eyebrow,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  eyebrow?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow ? <div className="page-eyebrow">{eyebrow}</div> : null}
        <h1 className="page-title">{title}</h1>
        {description ? <p className="page-desc">{description}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </div>
  );
}

export function Pill({
  tone = 'gray',
  children,
  dot = false,
  className,
}: {
  tone?: Tone;
  children: ReactNode;
  dot?: boolean;
  className?: string;
}) {
  return (
    <span className={cx('pill', `tone-${tone}`, className)}>
      {dot ? <span className="pill-dot" /> : null}
      {children}
    </span>
  );
}

export function StatusPill({
  meta,
  dot = true,
  className,
}: {
  meta: StatusMeta;
  dot?: boolean;
  className?: string;
}) {
  return (
    <Pill tone={meta.tone} dot={dot} className={className}>
      {meta.label}
    </Pill>
  );
}

export function ExecutorPill({ value }: { value?: string | null }) {
  return <StatusPill meta={metaOf({ SCRIPT: { label: '传统脚本', tone: 'blue' }, AI_RUNTIME: { label: 'AI Runtime', tone: 'purple' }, API: { label: '接口执行', tone: 'slate' } }, value)} />;
}

export function EmptyState({
  title = '暂无数据',
  description,
  icon = 'empty',
  action,
  compact,
}: {
  title?: ReactNode;
  description?: ReactNode;
  icon?: IconName;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={cx('empty-state', compact && 'is-compact')}>
      <span className="empty-icon">
        <Icon name={icon} size={compact ? 22 : 28} />
      </span>
      <div className="empty-title">{title}</div>
      {description ? <div className="empty-desc">{description}</div> : null}
      {action ? <div className="empty-action">{action}</div> : null}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <span className={cx('skeleton', className)} />;
}

export function SkeletonRows({ rows = 4, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <tr key={rowIndex} className="skeleton-row">
          {Array.from({ length: cols }).map((__, colIndex) => (
            <td key={colIndex}>
              <Skeleton className={colIndex === 0 ? 'sk-w-70' : 'sk-w-40'} />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

export function LoadingPanel({ text = '加载中…' }: { text?: string }) {
  return (
    <div className="loading-panel">
      <Spinner size={18} />
      <span>{text}</span>
    </div>
  );
}

export function ErrorPanel({
  message,
  hint,
  onRetry,
  compact,
}: {
  message: string;
  hint?: ReactNode;
  onRetry?: () => void;
  compact?: boolean;
}) {
  return (
    <div className={cx('error-panel', compact && 'is-compact')}>
      <span className="error-panel-icon">
        <Icon name="warning" size={20} />
      </span>
      <div className="error-panel-body">
        <div className="error-panel-title">加载失败</div>
        <div className="error-panel-message">{message}</div>
        {hint ? <div className="error-panel-hint">{hint}</div> : null}
      </div>
      {onRetry ? (
        <Button variant="secondary" size="sm" icon="refresh" onClick={onRetry}>
          重试
        </Button>
      ) : null}
    </div>
  );
}

export function Callout({
  tone = 'info',
  title,
  children,
  icon,
}: {
  tone?: 'info' | 'warning' | 'error' | 'success';
  title?: ReactNode;
  children?: ReactNode;
  icon?: IconName;
}) {
  const iconMap: Record<string, IconName> = {
    info: 'info',
    warning: 'warning',
    error: 'error',
    success: 'check-circle',
  };
  return (
    <div className={cx('callout', `callout-${tone}`)}>
      <span className="callout-icon">
        <Icon name={icon || iconMap[tone]} size={17} />
      </span>
      <div className="callout-body">
        {title ? <div className="callout-title">{title}</div> : null}
        {children ? <div className="callout-text">{children}</div> : null}
      </div>
    </div>
  );
}

export function Tabs({
  items,
  value,
  onChange,
}: {
  items: Array<{ key: string; label: string; count?: number }>;
  value: string;
  onChange: (key: string) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map((item) => (
        <button
          key={item.key}
          role="tab"
          type="button"
          aria-selected={value === item.key}
          className={cx('tab', value === item.key && 'is-active')}
          onClick={() => onChange(item.key)}
        >
          {item.label}
          {item.count !== undefined ? <span className="tab-count">{item.count}</span> : null}
        </button>
      ))}
    </div>
  );
}

export function Pagination({
  page,
  size,
  total,
  onChange,
  disabled,
}: {
  page: number;
  size: number;
  total: number;
  onChange: (page: number) => void;
  disabled?: boolean;
}) {
  const pages = Math.max(1, Math.ceil(total / Math.max(1, size)));
  const current = Math.min(Math.max(1, page), pages);
  if (total <= 0) return null;

  const list: Array<number | '...'> = [];
  if (pages <= 7) {
    for (let i = 1; i <= pages; i += 1) list.push(i);
  } else {
    list.push(1);
    const start = Math.max(2, current - 1);
    const end = Math.min(pages - 1, current + 1);
    if (start > 2) list.push('...');
    for (let i = start; i <= end; i += 1) list.push(i);
    if (end < pages - 1) list.push('...');
    list.push(pages);
  }

  return (
    <div className="pagination">
      <span className="pagination-total">
        共 {total} 条 · 第 {current} / {pages} 页
      </span>
      <div className="pagination-controls">
        <button
          type="button"
          className="page-btn"
          disabled={disabled || current <= 1}
          onClick={() => onChange(current - 1)}
          aria-label="上一页"
        >
          <Icon name="chevron-left" size={15} />
        </button>
        {list.map((item, index) =>
          item === '...' ? (
            <span key={`gap-${index}`} className="page-gap">
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              className={cx('page-btn', item === current && 'is-active')}
              disabled={disabled}
              onClick={() => onChange(item)}
            >
              {item}
            </button>
          ),
        )}
        <button
          type="button"
          className="page-btn"
          disabled={disabled || current >= pages}
          onClick={() => onChange(current + 1)}
          aria-label="下一页"
        >
          <Icon name="chevron-right" size={15} />
        </button>
      </div>
    </div>
  );
}

export function ProgressBar({
  value,
  max = 100,
  tone = 'blue',
  className,
}: {
  value: number;
  max?: number;
  tone?: Tone;
  className?: string;
}) {
  const width = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  return (
    <div className={cx('progress', `progress-${tone}`, className)}>
      <div className="progress-fill" style={{ width: `${width}%` }} />
    </div>
  );
}

export function CodeBlock({
  code,
  title,
  maxHeight,
  wrap,
}: {
  code: string;
  title?: ReactNode;
  maxHeight?: number;
  wrap?: boolean;
}) {
  const [copied, setCopied] = useState(false);
  const copy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      toastSuccess('已复制到剪贴板');
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      toastError('复制失败，请手动选择文本');
    }
  }, [code]);

  return (
    <div className="code-block">
      <div className="code-head">
        <span className="code-title">{title || 'code'}</span>
        <button type="button" className="code-copy" onClick={copy}>
          <Icon name={copied ? 'check' : 'copy'} size={13} />
          {copied ? '已复制' : '复制'}
        </button>
      </div>
      <pre
        className={cx('code-body', wrap && 'is-wrap')}
        style={maxHeight ? { maxHeight, overflow: 'auto' } : undefined}
      >
        <code>{code}</code>
      </pre>
    </div>
  );
}

export function Mono({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={cx('mono', className)}>{children}</span>;
}

export function Avatar({ name, size = 32 }: { name: string; size?: number }) {
  const initial = (name || '?').trim().slice(0, 1).toUpperCase();
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.42 }} aria-hidden="true">
      {initial}
    </span>
  );
}

export interface StatCardProps {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon: IconName;
  tone?: Tone;
  loading?: boolean;
}

export function StatCard({ label, value, hint, icon, tone = 'blue', loading }: StatCardProps) {
  return (
    <div className="stat-card">
      <span className={cx('stat-icon', `tone-${tone}`)}>
        <Icon name={icon} size={20} />
      </span>
      <div className="stat-main">
        <div className="stat-label">{label}</div>
        <div className="stat-value">{loading ? <Skeleton className="sk-value" /> : value}</div>
        {hint ? <div className="stat-hint">{hint}</div> : null}
      </div>
    </div>
  );
}

export function InfoGrid({
  items,
  columns = 2,
}: {
  items: Array<{ label: ReactNode; value: ReactNode; full?: boolean }>;
  columns?: number;
}) {
  return (
    <dl className={cx('info-grid', `cols-${columns}`)}>
      {items.map((item, index) => (
        <div key={index} className={cx('info-item', item.full && 'is-full')}>
          <dt>{item.label}</dt>
          <dd>{item.value === null || item.value === undefined || item.value === '' ? '—' : item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function FailureBars({
  data,
  emptyText = '暂无失败数据',
}: {
  data?: Array<{ category: string; count: number }>;
  emptyText?: string;
}) {
  const items = (data || []).filter((item) => item.count > 0);
  if (!items.length) {
    return <EmptyState compact icon="check-circle" title={emptyText} description="任务执行成功或尚未产生失败样本" />;
  }
  const max = Math.max(...items.map((item) => item.count), 1);
  const sorted = [...items].sort((a, b) => b.count - a.count);
  return (
    <div className="bars">
      {sorted.map((item) => {
        const meta = metaOf(FAILURE_CATEGORY_META, item.category);
        return (
          <div className="bar-row" key={item.category}>
            <div className="bar-label">
              <span>{meta.label}</span>
              <span className="bar-count">{item.count}</span>
            </div>
            <div className="bar-track">
              <div
                className={cx('bar-fill', `tone-bg-${meta.tone}`)}
                style={{ width: `${Math.max(4, (item.count / max) * 100)}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function DefinitionRow({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <div className="definition-row">
      <span className="definition-label">{label}</span>
      <span className="definition-value">{children}</span>
    </div>
  );
}

export function TimeText({ value }: { value?: string | null }) {
  return <span title={value ? toText(value) : ''}>{formatDateTime(value)}</span>;
}

export function LinkButton({
  children,
  onClick,
  tone,
  icon,
}: {
  children: ReactNode;
  onClick?: () => void;
  tone?: 'danger' | 'muted';
  icon?: IconName;
}) {
  return (
    <button type="button" className={cx('link-btn', tone === 'danger' && 'is-danger', tone === 'muted' && 'is-muted')} onClick={onClick}>
      {icon ? <Icon name={icon} size={13} /> : null}
      {children}
    </button>
  );
}
