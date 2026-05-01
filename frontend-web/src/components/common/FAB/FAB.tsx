'use client';

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import clsx from 'clsx';
import { PlusIcon } from '@/components/common/Icon';
import s from './FAB.module.css';

export interface FABProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** 접근성 라벨 (필수). 시각적으로 아이콘만 보이므로 명시 필요. */
  ariaLabel: string;
  /** 아이콘 (기본: PlusIcon) */
  icon?: ReactNode;
}

const FAB = forwardRef<HTMLButtonElement, FABProps>(function FAB(
  { ariaLabel, icon, className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={ariaLabel}
      className={clsx(s.root, className)}
      {...rest}
    >
      {icon ?? <PlusIcon size={24} />}
    </button>
  );
});

export default FAB;