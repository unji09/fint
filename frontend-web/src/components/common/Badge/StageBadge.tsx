import clsx from 'clsx';
import { getDealStage, type DealStageCode } from '@/constants/dealStage';
import s from './Badge.module.css';

export interface StageBadgeProps {
  stage: DealStageCode;
  size?: 'sm' | 'md';
  className?: string;
}

export default function StageBadge({ stage, size = 'md', className }: StageBadgeProps) {
  const meta = getDealStage(stage);
  return (
    <span
      className={clsx(s.root, s.solid, s[size], className)}
      style={{ backgroundColor: `var(${meta.colorVar})` }}
    >
      {meta.label}
    </span>
  );
}
