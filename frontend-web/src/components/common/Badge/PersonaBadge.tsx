import clsx from 'clsx';
import { getPersona, type PersonaCode } from '@/constants/persona';
import s from './Badge.module.css';

export interface PersonaBadgeProps {
  persona: PersonaCode;
  size?: 'sm' | 'md';
  className?: string;
}

export default function PersonaBadge({ persona, size = 'md', className }: PersonaBadgeProps) {
  const meta = getPersona(persona);
  return (
    <span
      className={clsx(s.root, s.tint, s[size], className)}
      style={{
        color: `var(${meta.fgVar})`,
        backgroundColor: `var(${meta.bgVar})`,
      }}
    >
      {meta.label}
    </span>
  );
}
