import { forwardRef, useId, type TextareaHTMLAttributes } from 'react';
import clsx from 'clsx';
import s from './Textarea.module.css';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  helperText?: string;
  required?: boolean;
}

const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { id, label, error, helperText, required, rows = 4, className, ...rest },
  ref,
) {
  const reactId = useId();
  const textareaId = id ?? `ta-${reactId}`;
  const messageId = `${textareaId}-message`;
  const message = error ?? helperText;

  return (
    <div className={clsx(s.field, className)}>
      {label && (
        <label htmlFor={textareaId} className={s.label}>
          {label}
          {required && <span className={s.required}>*</span>}
        </label>
      )}
      <textarea
        ref={ref}
        id={textareaId}
        rows={rows}
        className={clsx(s.textarea, error && s.error)}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        {...rest}
      />
      {message && (
        <span id={messageId} className={clsx(s.message, error && s.errorMessage)}>
          {message}
        </span>
      )}
    </div>
  );
});

export default Textarea;
