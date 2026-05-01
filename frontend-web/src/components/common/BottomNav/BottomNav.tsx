'use client';

import clsx from 'clsx';
import type { ReactNode } from 'react';
import s from './BottomNav.module.css';

export interface BottomNavItem {
  key: string;
  label: string;
  icon: ReactNode;
}

export interface BottomNavProps {
  items: BottomNavItem[];
  activeKey: string;
  onChange: (key: string) => void;
  className?: string;
}

export default function BottomNav({ items, activeKey, onChange, className }: BottomNavProps) {
  return (
    <nav className={clsx(s.root, className)} aria-label="주요 탐색">
      {items.map((item) => {
        const active = item.key === activeKey;
        return (
          <button
            key={item.key}
            type="button"
            className={clsx(s.tab, active && s.active)}
            aria-current={active ? 'page' : undefined}
            onClick={() => onChange(item.key)}
          >
            <span className={s.icon}>{item.icon}</span>
            <span className={s.label}>{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}