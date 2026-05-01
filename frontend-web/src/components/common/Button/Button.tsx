'use client';

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import clsx from 'clsx';
import s from './Button.module.css';

export type ButtonVariant = 'primary' | 'sub' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  iconLeft?: ReactNode;
  iconRight?: ReactNode;
  fullWidth?: boolean;
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    disabled,
    iconLeft,
    iconRight,
    fullWidth,
    className,
    children,
    type = 'button',
    onClick,
    ...rest
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled}
      aria-busy={loading || undefined}
      onClick={loading ? undefined : onClick}
      className={clsx(s.root, s[variant], s[size], fullWidth && s.fullWidth, className)}
      {...rest}
    >
      {loading ? (
        <span className={s.spinner} aria-hidden="true" />
      ) : (
        iconLeft && <span className={s.icon}>{iconLeft}</span>
      )}
      {children}
      {!loading && iconRight && <span className={s.icon}>{iconRight}</span>}
    </button>
  );
});

export default Button;