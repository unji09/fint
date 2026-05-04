import clsx from 'clsx';
import { CheckIcon } from '@/components/common/Icon';
import s from './Stepper.module.css';

export interface StepperStep {
  label: string;
  description?: string;
}

export interface StepperProps {
  steps: StepperStep[];
  /** 현재 진행 중인 인덱스 (0-based). 이 인덱스 이전은 completed, 이후는 pending */
  current: number;
  orientation?: 'vertical' | 'horizontal';
  className?: string;
}

export default function Stepper({
  steps,
  current,
  orientation = 'vertical',
  className,
}: StepperProps) {
  return (
    <ol
      className={clsx(s.root, orientation === 'horizontal' && s.horizontal, className)}
      aria-label="진행 단계"
    >
      {steps.map((step, idx) => {
        const completed = idx < current;
        const active = idx === current;
        return (
          <li
            key={`${idx}-${step.label}`}
            className={clsx(s.step, completed && s.completed, active && s.active)}
            aria-current={active ? 'step' : undefined}
          >
            <span className={s.indicator}>
              {completed ? <CheckIcon size={16} /> : idx + 1}
            </span>
            <div className={s.content}>
              <span className={s.label}>{step.label}</span>
              {step.description && <span className={s.description}>{step.description}</span>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
