import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';
import clsx from 'clsx';
import s from './Input.module.css';

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string;
  error?: string;
  helperText?: string;
  leftAdornment?: ReactNode;
  rightAdornment?: ReactNode;
  /** 라벨 옆 빨간 별표 */
  required?: boolean;
}

const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    id,
    label,
    error,
    helperText,
    leftAdornment,
    rightAdornment,
    required,
    disabled,
    className,
    ...rest
  },
  ref,
) {
  const reactId = useId();
  const inputId = id ?? `input-${reactId}`;
  const messageId = `${inputId}-message`;
  const message = error ?? helperText;

  return (
    <div className={clsx(s.field, className)}>
      {label && (
        <label htmlFor={inputId} className={s.label}>
          {label}
          {required && <span className={s.required}>*</span>}
        </label>
      )}
      <div
        className={clsx(s.inputWrap, error && s.error, disabled && s.disabled)}
      >
        {leftAdornment && (
          <span className={clsx(s.adornment, s.adornmentLeft)}>{leftAdornment}</span>
        )}
        <input
          ref={ref}
          id={inputId}
          className={s.input}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
          {...rest}
        />
        {rightAdornment && (
          <span className={clsx(s.adornment, s.adornmentRight)}>{rightAdornment}</span>
        )}
      </div>
      {message && (
        <span id={messageId} className={clsx(s.message, error && s.errorMessage)}>
          {message}
        </span>
      )}
    </div>
  );
});

export default Input;
