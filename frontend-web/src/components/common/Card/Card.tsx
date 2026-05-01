'use client';

import { forwardRef, type HTMLAttributes, type ReactNode } from 'react';
import clsx from 'clsx';
import s from './Card.module.css';

export type CardPadding = 'md' | 'lg';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  header?: ReactNode;
  footer?: ReactNode;
  /**
   * 'md' (기본 20px) — 대부분의 카드
   * 'lg' (24px) — 대시보드 KPI 카드처럼 여백을 강조할 때
   */
  padding?: CardPadding;
  /** 카드 전체를 클릭 가능하게 (button 역할) */
  clickable?: boolean;
}

const PADDING_CLASS: Record<CardPadding, string> = {
  md: s.paddingMd,
  lg: s.paddingLg,
};

const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { header, footer, padding = 'md', clickable, className, children, onClick, ...rest },
  ref,
) {
  const role = clickable ? 'button' : undefined;
  const tabIndex = clickable ? 0 : undefined;

  const handleKeyDown = clickable
    ? (e: React.KeyboardEvent<HTMLDivElement>) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          (e.currentTarget as HTMLDivElement).click();
        }
      }
    : undefined;

  return (
    <div
      ref={ref}
      role={role}
      tabIndex={tabIndex}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      className={clsx(
        s.root,
        PADDING_CLASS[padding],
        clickable && s.clickable,
        className,
      )}
      {...rest}
    >
      {header && <div className={s.header}>{header}</div>}
      <div className={s.body}>{children}</div>
      {footer && <div className={s.footer}>{footer}</div>}
    </div>
  );
});

export default Card;