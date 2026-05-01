'use client';

import type { ReactNode } from 'react';
import { ChevronLeftIcon } from '@/components/common/Icon';
import s from './TopAppBar.module.css';

export interface TopAppBarProps {
  title?: ReactNode;
  /** 좌측 아이콘 (기본: 뒤로가기 chevron). null이면 노출 안 함 */
  leftIcon?: ReactNode;
  onLeftClick?: () => void;
  leftLabel?: string;
  /** 우측 액션들 (아이콘 버튼 등) */
  rightActions?: ReactNode;
}

export default function TopAppBar({
  title,
  leftIcon,
  onLeftClick,
  leftLabel = '뒤로',
  rightActions,
}: TopAppBarProps) {
  return (
    <header className={s.root}>
      {leftIcon !== null && (
        <button
          type="button"
          className={s.iconButton}
          onClick={onLeftClick}
          aria-label={leftLabel}
        >
          {leftIcon ?? <ChevronLeftIcon size={24} />}
        </button>
      )}
      <h1 className={s.title}>{title}</h1>
      {rightActions && <div className={s.actions}>{rightActions}</div>}
    </header>
  );
}