'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

type DialogVariant = 'primary' | 'danger';
type DialogMode = 'confirm' | 'alert' | 'prompt';

interface DialogOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: DialogVariant;
  placeholder?: string;
}

interface DialogState extends DialogOptions {
  mode: DialogMode;
}

type ConfirmFn = (options: string | DialogOptions) => Promise<boolean>;
type AlertFn = (options: string | Omit<DialogOptions, 'cancelText'>) => Promise<void>;
type PromptFn = (options: string | DialogOptions) => Promise<string | null>;

interface DialogContextValue {
  confirm: ConfirmFn;
  alert: AlertFn;
  prompt: PromptFn;
}

const DialogContext = createContext<DialogContextValue | null>(null);

export function useConfirm(): ConfirmFn {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('useConfirm must be used within ConfirmDialogProvider');
  return ctx.confirm;
}

export function useAlert(): AlertFn {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('useAlert must be used within ConfirmDialogProvider');
  return ctx.alert;
}

export function usePrompt(): PromptFn {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('usePrompt must be used within ConfirmDialogProvider');
  return ctx.prompt;
}

export function useDialog(): DialogContextValue {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('useDialog must be used within ConfirmDialogProvider');
  return ctx;
}

const VARIANT_COLORS: Record<DialogVariant, string> = {
  primary: '#06b6d4',
  danger: '#ef4444',
};

const F = "'Pretendard', -apple-system, sans-serif";

export default function ConfirmDialogProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<DialogState | null>(null);
  const [inputValue, setInputValue] = useState('');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const resolveRef = useRef<((value: any) => void) | null>(null);
  const confirmBtnRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const normalize = (options: string | DialogOptions): DialogOptions =>
    typeof options === 'string' ? { message: options } : options;

  const confirm: ConfirmFn = useCallback((options) => {
    setState({ ...normalize(options), mode: 'confirm' });
    return new Promise<boolean>((resolve) => { resolveRef.current = resolve; });
  }, []);

  const alert: AlertFn = useCallback((options) => {
    setState({ ...normalize(options), mode: 'alert' });
    return new Promise<void>((resolve) => { resolveRef.current = resolve; });
  }, []);

  const prompt: PromptFn = useCallback((options) => {
    setInputValue('');
    setState({ ...normalize(options), mode: 'prompt' });
    return new Promise<string | null>((resolve) => { resolveRef.current = resolve; });
  }, []);

  const close = useCallback((result: boolean) => {
    if (!state) return;
    const resolve = resolveRef.current;
    resolveRef.current = null;
    setState(null);

    if (state.mode === 'alert') {
      resolve?.(undefined);
    } else if (state.mode === 'prompt') {
      resolve?.(result ? inputValue : null);
    } else {
      resolve?.(result);
    }
  }, [state, inputValue]);

  useEffect(() => {
    if (!state) return;
    if (state.mode === 'prompt') {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      confirmBtnRef.current?.focus();
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [state, close]);

  const bg = VARIANT_COLORS[state?.variant ?? 'primary'];

  const value: DialogContextValue = { confirm, alert, prompt };

  return (
    <DialogContext.Provider value={value}>
      {children}
      {state && createPortal(
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1100,
            animation: 'dlgFadeIn 200ms ease-out',
          }}
          onClick={() => close(false)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={state.title ?? '확인'}
            style={{
              backgroundColor: '#ffffff',
              borderRadius: 14,
              boxShadow: '0 20px 60px rgba(0, 0, 0, 0.2)',
              width: 380,
              maxWidth: 'calc(100vw - 32px)',
              overflow: 'hidden',
              fontFamily: F,
              animation: 'dlgSlideUp 200ms ease-out',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div style={{
              padding: '18px 22px 0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#1e293b' }}>
                {state.title ?? '확인'}
              </h3>
              <button
                type="button"
                aria-label="닫기"
                onClick={() => close(false)}
                style={{
                  width: 28, height: 28, borderRadius: 6,
                  border: '1px solid #e2eaf0', backgroundColor: '#fff',
                  cursor: 'pointer', display: 'flex', alignItems: 'center',
                  justifyContent: 'center', fontSize: 14, color: '#94a3b8',
                }}
              >
                ✕
              </button>
            </div>

            {/* Body */}
            <div style={{ padding: '14px 22px 20px' }}>
              <p style={{
                margin: 0, fontSize: 14, lineHeight: 1.6,
                color: '#475569', whiteSpace: 'pre-line',
              }}>
                {state.message}
              </p>
              {state.mode === 'prompt' && (
                <input
                  ref={inputRef}
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') close(true); }}
                  placeholder={state.placeholder}
                  style={{
                    marginTop: 12, width: '100%', padding: '10px 12px',
                    borderRadius: 8, border: '1px solid #e2eaf0',
                    fontSize: 13, color: '#1e293b', outline: 'none',
                    fontFamily: F, boxSizing: 'border-box',
                  }}
                />
              )}
            </div>

            {/* Footer */}
            <div style={{
              padding: '0 22px 18px',
              display: 'flex', justifyContent: 'flex-end', gap: 8,
            }}>
              {state.mode !== 'alert' && (
                <button
                  type="button"
                  onClick={() => close(false)}
                  style={{
                    padding: '8px 18px', borderRadius: 8,
                    border: '1px solid #e2eaf0', backgroundColor: '#fff',
                    color: '#475569', fontSize: 13, fontWeight: 600,
                    cursor: 'pointer', fontFamily: 'inherit',
                  }}
                >
                  {state.cancelText ?? '취소'}
                </button>
              )}
              <button
                ref={confirmBtnRef}
                type="button"
                onClick={() => close(true)}
                style={{
                  padding: '8px 18px', borderRadius: 8, border: 'none',
                  backgroundColor: bg, color: '#ffffff',
                  fontSize: 13, fontWeight: 600,
                  cursor: 'pointer', fontFamily: 'inherit',
                }}
              >
                {state.confirmText ?? '확인'}
              </button>
            </div>
          </div>

          <style>{`
            @keyframes dlgFadeIn { from { opacity: 0 } to { opacity: 1 } }
            @keyframes dlgSlideUp { from { transform: translateY(16px); opacity: 0 } to { transform: translateY(0); opacity: 1 } }
          `}</style>
        </div>,
        document.body,
      )}
    </DialogContext.Provider>
  );
}
