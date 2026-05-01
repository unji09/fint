import clsx from 'clsx';
import { getSignalSource, type SignalSourceCode } from '@/constants/signalSource';
import s from './Badge.module.css';

export interface SignalSourceBadgeProps {
  source: SignalSourceCode;
  size?: 'sm' | 'md';
  className?: string;
}

const TINT_BG: Record<SignalSourceCode, string> = {
  DART: 'rgba(3, 105, 161, 0.10)',
  NEWS: 'rgba(234, 88, 12, 0.10)',
  LOG: 'rgba(71, 85, 105, 0.10)',
  AI: 'rgba(124, 58, 237, 0.10)',
};

export default function SignalSourceBadge({
  source,
  size = 'md',
  className,
}: SignalSourceBadgeProps) {
  const meta = getSignalSource(source);
  return (
    <span
      className={clsx(s.root, s.tint, s[size], className)}
      style={{
        color: `var(${meta.colorVar})`,
        backgroundColor: TINT_BG[source],
      }}
    >
      {meta.label}
    </span>
  );
}
