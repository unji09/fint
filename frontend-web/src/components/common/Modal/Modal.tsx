'use client';

import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import clsx from 'clsx';
import { CloseIcon } from '@/components/common/Icon';
import s from './Modal.module.css';

export type ModalVariant = 'centered' | 'fullscreen' | 'bottomSheet';
export type ModalSize = 'sm' | 'md' | 'lg';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  footer?: ReactNode;
  variant?: ModalVariant;
  size?: ModalSize;
  closeOnBackdrop?: boolean;
  closeOnEsc?: boolean;
  /** title이 없어도 dialog 의미를 위해 aria-label을 지정 */
  ariaLabel?: string;
  children: ReactNode;
}

export default function Modal({
  open,
  onClose,
  title,
  footer,
  variant = 'centered',
  size = 'md',
  closeOnBackdrop = true,
  closeOnEsc = true,
  ariaLabel,
  children,
}: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  // ESC 닫기
  useEffect(() => {
    if (!open || !closeOnEsc) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, closeOnEsc, onClose]);

  // 열렸을 때 dialog에 포커스 (간이 focus trap — 본격 trap은 후속 버전)
  useEffect(() => {
    if (open && dialogRef.current) {
      dialogRef.current.focus();
    }
  }, [open]);

  // body scroll lock
  useEffect(() => {
    if (!open) return;
    const original = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = original;
    };
  }, [open]);

  if (!open) return null;

  const handleBackdropClick = () => {
    if (closeOnBackdrop) onClose();
  };

  return createPortal(
    <div className={s.backdrop} onClick={handleBackdropClick} data-testid="modal-backdrop">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={ariaLabel}
        tabIndex={-1}
        className={clsx(
          s[variant],
          variant === 'centered' && s[size],
        )}
        onClick={(e) => e.stopPropagation()}
      >
        {(title || variant !== 'centered' || true) && (
          <div className={s.header}>
            {title ? <h2 className={s.title}>{title}</h2> : <span />}
            <button
              type="button"
              aria-label="닫기"
              className={s.closeButton}
              onClick={onClose}
            >
              <CloseIcon size={20} />
            </button>
          </div>
        )}
        <div className={s.body}>{children}</div>
        {footer && <div className={s.footer}>{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}