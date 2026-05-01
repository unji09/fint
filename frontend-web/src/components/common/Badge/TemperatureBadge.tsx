import clsx from 'clsx';
import { getTemperature, type TemperatureCode } from '@/constants/temperature';
import s from './Badge.module.css';

export interface TemperatureBadgeProps {
  temperature: TemperatureCode;
  size?: 'sm' | 'md';
  className?: string;
}

/**
 * 온도별 옅은 배경(tint) + 컬러 텍스트.
 * tint 배경은 컬러 시스템 시안 기준 — 단색 + 알파.
 */
const TINT_BG: Record<TemperatureCode, string> = {
  HOT: 'rgba(239, 68, 68, 0.12)',
  WARM: 'rgba(245, 158, 11, 0.16)',
  COOL: 'rgba(8, 145, 178, 0.12)',
};

export default function TemperatureBadge({
  temperature,
  size = 'md',
  className,
}: TemperatureBadgeProps) {
  const meta = getTemperature(temperature);
  return (
    <span
      className={clsx(s.root, s.tint, s[size], className)}
      style={{
        color: `var(${meta.colorVar})`,
        backgroundColor: TINT_BG[temperature],
      }}
    >
      {meta.label}
    </span>
  );
}
