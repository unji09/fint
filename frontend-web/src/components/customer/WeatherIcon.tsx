'use client';
// src/components/customer/WeatherIcon.tsx
//
// CRM 비즈니스 톤의 분위기 표현 — OS 네이티브 이모지 기반.
// 시스템 이모지는 OS 가 자체 입체 렌더링하여 자연스러운 깊이감.

import type { MoodLevel } from '@/types/customer';

interface Props {
  mood: MoodLevel;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZE: Record<NonNullable<Props['size']>, number> = { sm: 22, md: 32, lg: 48 };

const META: Record<MoodLevel, { label: string; emoji: string; accent: string }> = {
  RAINBOW: { label: '무지개', emoji: '🌈', accent: '#7C3AED' },
  SUNNY:   { label: '맑음',   emoji: '☀️', accent: '#D97706' },
  CLOUDY:  { label: '흐림',   emoji: '☁️', accent: '#64748B' },
  RAINY:   { label: '비',     emoji: '🌧️', accent: '#1D4ED8' },
  THUNDER: { label: '천둥',   emoji: '⛈️', accent: '#B45309' },
};

export function moodMeta(mood: MoodLevel) {
  return META[mood] ?? META.CLOUDY;
}

export default function WeatherIcon({ mood, size = 'md', className }: Props) {
  const px = SIZE[size];
  const meta = META[mood] ?? META.CLOUDY;

  return (
    <span
      className={className}
      role="img"
      aria-label={meta.label}
      style={{
        width: px,
        height: px,
        flexShrink: 0,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: px * 0.92,
        lineHeight: 1,
        // 이모지 폰트 우선순위: Apple/Segoe/Noto Color
        fontFamily: '"Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", system-ui, sans-serif',
        // 미세한 떠있는 느낌
        filter: 'drop-shadow(0 1px 1.5px rgba(15, 23, 42, 0.18))',
        userSelect: 'none',
      }}
    >
      {meta.emoji}
    </span>
  );
}
