'use client';
// src/components/customer/WeatherPanel.tsx
//
// 분위기(날씨) 인라인 한 줄 — AI 감정 분석 결과를 read-only 로 표시.
// 사용자가 직접 편집하지 않으며, 백엔드 AI 분석 응답(account.moodReason)을 그대로 노출.

import type { MoodLevel } from '@/types/customer';
import WeatherIcon from '@/components/customer/WeatherIcon';

interface Props {
  mood: MoodLevel;
  /** AI 감정 분석 결과의 이유 텍스트. 백엔드가 채워주면 표시, 없으면 안내. */
  reason?: string | null;
}

const F = 'Pretendard, sans-serif';

export default function WeatherPanel({ mood, reason }: Props) {
  const hasReason = !!(reason && reason.trim());

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        fontFamily: F,
        padding: '2px 0',
        minHeight: 32,
      }}
    >
      <WeatherIcon mood={mood} size="sm" />
      <span
        style={{
          flex: 1,
          fontSize: 12,
          color: hasReason ? '#475569' : '#9CA193',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          minWidth: 0,
        }}
        title={reason ?? ''}
      >
        {hasReason ? reason : '고객사 동향이 수집되면 여기에 표시됩니다.'}
      </span>
    </div>
  );
}
