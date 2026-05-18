'use client';

import { KeyboardEvent, useState } from 'react';
import useBreakpoint from '@/hooks/useBreakpoint';

interface DashboardHeroProps {
  onSubmit: (query: string) => void;
  loading?: boolean;
}

const CHIPS = [
  '이번 주 우선순위 고객',
  '멈춘 딜 있어?',
  '삼성 SDS 브리핑해줘',
  '이번 분기 매출 예측',
];

export default function DashboardHero({ onSubmit, loading = false }: DashboardHeroProps) {
  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';
  const isTablet = bp === 'tablet';
  const [input, setInput] = useState('');
  const handleSubmit = () => {
    if (!input.trim() || loading) return;
    onSubmit(input.trim());
    setInput('');
  };

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        maxWidth: 768,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          position: 'absolute',
          width: isMobile ? 300 : 800,
          height: isMobile ? 80 : 160,
          borderRadius: 12,
          background: 'rgba(76,215,246,0.05)',
          filter: 'blur(32px)',
          pointerEvents: 'none',
        }}
      />

      <h1
        style={{
          fontFamily: 'Pretendard, sans-serif',
          fontWeight: 500,
          fontSize: isMobile ? 28 : isTablet ? 36 : 52,
          lineHeight: isMobile ? '36px' : isTablet ? '46px' : '66px',
          letterSpacing: '-1.4px',
          color: '#141b2b',
          textAlign: 'center',
          margin: '0 0 8px',
          position: 'relative',
        }}
      >
        무엇을 <span style={{ color: '#06b6d4', fontWeight: 400 }}>분석</span>할까요?
      </h1>

      <p
        style={{
          fontFamily: 'Pretendard, sans-serif',
          fontWeight: 500,
          fontSize: isMobile ? 12 : 14,
          lineHeight: '20px',
          letterSpacing: '0.35px',
          color: 'rgba(61,73,76,0.8)',
          textAlign: 'center',
          margin: '0 0 20px',
          position: 'relative',
        }}
      >
        고객사 분석부터 다음 액션 추천까지, 자연어로 물어보세요
      </p>

      {/* 검색창 */}
      <div style={{ position: 'relative', width: '100%', maxWidth: 560, marginBottom: 16 }}>
        <div
          style={{
            position: 'absolute',
            inset: -2,
            borderRadius: 32,
            background: 'linear-gradient(to right, rgba(6,182,212,0.2), rgba(0,104,122,0.2))',
            filter: 'blur(4px)',
            opacity: 0.5,
            pointerEvents: 'none',
          }}
        />
        <div
          style={{
            position: 'relative',
            background: '#fff',
            borderRadius: 32,
            display: 'flex',
            alignItems: 'center',
            padding: 6,
            boxShadow: '0px 8px 15px rgba(0,104,122,0.08)',
          }}
        >
          <div
            style={{
              paddingLeft: 14,
              paddingRight: 8,
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <svg width="16" height="16" viewBox="0 0 18 18" fill="none">
              <circle cx="8" cy="8" r="5.5" stroke="#9CA3AF" strokeWidth="1.5" />
              <path d="M12.5 12.5L16 16" stroke="#9CA3AF" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </div>
          <input
            style={{
              flex: 1,
              height: 42,
              padding: '0 8px',
              fontSize: 15,
              fontFamily: 'Pretendard, sans-serif',
              fontWeight: 500,
              outline: 'none',
              border: 'none',
              background: 'transparent',
              color: '#1d1a24',
              minWidth: 0,
            }}
            placeholder="예: 3분기 목표 달성률을 보여주는 대시보드"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
              if (e.key === 'Enter') handleSubmit();
            }}
          />
          <button
            onClick={handleSubmit}
            disabled={loading || !input.trim()}
            style={{
              width: 38,
              height: 38,
              borderRadius: 10,
              flexShrink: 0,
              background: 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              opacity: loading || !input.trim() ? 0.5 : 1,
              transition: 'opacity 0.2s',
            }}
          >
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <path
                d="M8 13V3M8 3L4 7M8 3L12 7"
                stroke="white"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>

      {/* 제안 칩 */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 8,
          justifyContent: 'center',
          position: 'relative',
        }}
      >
        {CHIPS.map((chip) => (
          <button
            key={chip}
            onClick={() => {
              setInput(chip);
              onSubmit(chip);
            }}
            style={{
              padding: '5px 13px 6px',
              borderRadius: 9999,
              border: '1px solid rgba(14,166,212,0.2)',
              background: 'rgba(14,116,212,0.05)',
              color: '#06b6d4',
              fontSize: 13,
              fontFamily: 'Pretendard, sans-serif',
              fontWeight: 500,
              letterSpacing: '0.13px',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            {chip}
          </button>
        ))}
      </div>
    </div>
  );
}
