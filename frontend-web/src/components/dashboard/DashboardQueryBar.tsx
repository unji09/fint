'use client';

import { KeyboardEvent } from 'react';

interface DashboardQueryBarProps {
  value: string;
  onChange: (v: string) => void;
  onSubmit: (v: string) => void;
  loading: boolean;
}

export default function DashboardQueryBar({
  value,
  onChange,
  onSubmit,
  loading,
}: DashboardQueryBarProps) {
  return (
    /* 피그마: 하단 고정 바, gradient border cyan, shadow */
    <div
      style={{
        flexShrink: 0,
        padding: '12px 32px 16px',
        background: 'rgba(255,255,255,0.85)',
        backdropFilter: 'blur(8px)',
        borderTop: '1px solid rgba(6,182,212,0.2)',
      }}
    >
      <div style={{ maxWidth: 700, margin: '0 auto', position: 'relative' }}>
        {/* glow */}
        <div
          style={{
            position: 'absolute',
            inset: -2,
            borderRadius: 32,
            background: 'linear-gradient(to right, rgba(6,182,212,0.2), rgba(0,104,122,0.2))',
            filter: 'blur(4px)',
            opacity: loading ? 0.8 : 0.5,
            pointerEvents: 'none',
            transition: 'opacity 0.3s',
          }}
        />

        {/* pill */}
        <div
          style={{
            position: 'relative',
            background: loading ? 'linear-gradient(to right, #ffffff, #ecfeff)' : '#ffffff',
            borderRadius: 32,
            display: 'flex',
            alignItems: 'center',
            padding: 8,
            border: `2px solid ${loading ? '#06b6d4' : 'transparent'}`,
            boxShadow: '0px 8px 15px rgba(0,104,122,0.08)',
            transition: 'border-color 0.3s, background 0.3s',
          }}
        >
          {/* 검색 아이콘 */}
          <div
            style={{
              paddingLeft: 12,
              paddingRight: 10,
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

          {/* 입력창 */}
          <input
            style={{
              flex: 1,
              height: 44,
              padding: '0 8px',
              fontSize: 15,
              fontFamily: 'Pretendard, sans-serif',
              fontWeight: loading ? 600 : 500,
              outline: 'none',
              border: 'none',
              background: 'transparent',
              color: loading ? '#818181' : '#1d1a24',
              minWidth: 0,
            }}
            placeholder={loading ? '분석 중...' : '추가로 분석할 내용을 입력하세요'}
            value={loading ? '' : value}
            disabled={loading}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
              if (e.key === 'Enter' && !loading) onSubmit(value);
            }}
          />

          {/* 버튼 */}
          <button
            onClick={() => !loading && onSubmit(value)}
            disabled={loading && !value}
            style={{
              width: 48,
              height: 48,
              borderRadius: 12,
              flexShrink: 0,
              background: 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)',
              boxShadow: '0px 4px 6px -1px rgba(0,0,0,0.1)',
              border: 'none',
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {loading ? (
              /* 피그마: eos-icons:loading 스피너 */
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="white"
                style={{ animation: 'spin 1s linear infinite' }}
              >
                <path d="M12 2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8V2z" />
              </svg>
            ) : (
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path
                  d="M8 13V3M8 3L4 7M8 3L12 7"
                  stroke="white"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </button>
        </div>
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
