import { useEffect, useId, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from './icons';
import { Button, Textarea } from './ui';
import { cx } from '../utils';

let openCount = 0;

function useModalLifecycle(open: boolean, onClose: () => void) {
  const titleId = useId();
  useEffect(() => {
    if (!open) return undefined;
    openCount += 1;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      openCount = Math.max(0, openCount - 1);
      if (openCount === 0) document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, onClose]);
  return titleId;
}

export interface ModalProps {
  open: boolean;
  title: ReactNode;
  description?: ReactNode;
  onClose: () => void;
  children?: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  closeOnOverlay?: boolean;
  hideClose?: boolean;
  className?: string;
}

export function Modal({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
  closeOnOverlay = true,
  hideClose,
  className,
}: ModalProps) {
  const titleId = useModalLifecycle(open, onClose);
  if (!open) return null;
  return createPortal(
    <div
      className="modal-overlay"
      onMouseDown={(event) => {
        if (closeOnOverlay && event.target === event.currentTarget) onClose();
      }}
    >
      <div className={cx('modal', `modal-${size}`, className)} role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="modal-head">
          <div className="modal-head-main">
            <h2 className="modal-title" id={titleId}>
              {title}
            </h2>
            {description ? <p className="modal-desc">{description}</p> : null}
          </div>
          {hideClose ? null : (
            <button type="button" className="modal-close" onClick={onClose} aria-label="关闭">
              <Icon name="close" size={16} />
            </button>
          )}
        </div>
        <div className="modal-body">{children}</div>
        {footer ? <div className="modal-foot">{footer}</div> : null}
      </div>
    </div>,
    document.body,
  );
}

export interface DrawerProps {
  open: boolean;
  title: ReactNode;
  description?: ReactNode;
  onClose: () => void;
  children?: ReactNode;
  footer?: ReactNode;
  width?: number;
}

export function Drawer({ open, title, description, onClose, children, footer, width = 680 }: DrawerProps) {
  const titleId = useModalLifecycle(open, onClose);
  if (!open) return null;
  return createPortal(
    <div
      className="drawer-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <aside
        className="drawer"
        style={{ width: `min(${width}px, 96vw)` }}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="drawer-head">
          <div>
            <h2 className="drawer-title" id={titleId}>
              {title}
            </h2>
            {description ? <p className="drawer-desc">{description}</p> : null}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="关闭">
            <Icon name="close" size={16} />
          </button>
        </div>
        <div className="drawer-body">{children}</div>
        {footer ? <div className="drawer-foot">{footer}</div> : null}
      </aside>
    </div>,
    document.body,
  );
}

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message?: ReactNode;
  confirmText?: string;
  cancelText?: string;
  tone?: 'danger' | 'primary';
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: ReactNode;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmText = '确认',
  cancelText = '取消',
  tone = 'danger',
  loading,
  onConfirm,
  onClose,
  children,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={loading ? () => undefined : onClose}
      size="sm"
      closeOnOverlay={!loading}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            {cancelText}
          </Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>
            {confirmText}
          </Button>
        </>
      }
    >
      {message ? <div className="confirm-message">{message}</div> : null}
      {children}
    </Modal>
  );
}

export interface PromptDialogProps {
  open: boolean;
  title: string;
  message?: ReactNode;
  label: string;
  placeholder?: string;
  required?: boolean;
  confirmText?: string;
  tone?: 'danger' | 'primary';
  loading?: boolean;
  hint?: ReactNode;
  mono?: boolean;
  onConfirm: (value: string) => void;
  onClose: () => void;
}

export function PromptDialog({
  open,
  title,
  message,
  label,
  placeholder,
  required = true,
  confirmText = '确认',
  tone = 'danger',
  loading,
  hint,
  mono,
  onConfirm,
  onClose,
}: PromptDialogProps) {
  const [value, setValue] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setValue('');
      setError('');
    }
  }, [open]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const next = value.trim();
    if (required && !next) {
      setError(`请填写${label}`);
      return;
    }
    setError('');
    onConfirm(next);
  };

  return (
    <Modal
      open={open}
      title={title}
      onClose={loading ? () => undefined : onClose}
      size="sm"
      closeOnOverlay={!loading}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            取消
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger' : 'primary'}
            loading={loading}
            onClick={() => {
              const next = value.trim();
              if (required && !next) {
                setError(`请填写${label}`);
                return;
              }
              setError('');
              onConfirm(next);
            }}
          >
            {confirmText}
          </Button>
        </>
      }
    >
      <form className="prompt-form" onSubmit={submit}>
        {message ? <div className="confirm-message">{message}</div> : null}
        <label className="field-label" htmlFor="prompt-dialog-input">
          {label}
          {required ? <span className="field-required">*</span> : null}
        </label>
        <Textarea
          id="prompt-dialog-input"
          rows={4}
          autoFocus
          className={cx(mono && 'mono')}
          placeholder={placeholder}
          value={value}
          invalid={Boolean(error)}
          onChange={(event) => setValue(event.target.value)}
        />
        {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
        <button type="submit" hidden />
      </form>
    </Modal>
  );
}

export function ImageLightbox({
  open,
  src,
  title,
  onClose,
}: {
  open: boolean;
  src?: string | null;
  title?: string;
  onClose: () => void;
}) {
  return (
    <Modal open={open} title={title || '截图预览'} onClose={onClose} size="xl" className="lightbox-modal">
      {src ? (
        <div className="lightbox-body">
          <img src={src} alt={title || '任务截图'} />
        </div>
      ) : (
        <div className="image-fallback large">
          <Icon name="image" size={26} />
          <span>截图不可用</span>
        </div>
      )}
    </Modal>
  );
}
