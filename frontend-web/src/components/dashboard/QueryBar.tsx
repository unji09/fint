'use client';

import useBreakpoint from '@/hooks/useBreakpoint';

interface Props {
  value: string;
  onChange: (v: string) => void;
  onSubmit: (v: string) => void;
  loading: boolean;
}

export default function QueryBar({ value, onChange, onSubmit, loading }: Props) {
  const isMobile = useBreakpoint() === 'mobile';
  return (
    <div style={{ position: 'relative', width: isMobile ? '100%' : 390 }}>
      <div
        style={{
          position: 'absolute',
          inset: -6,
          borderRadius: 36,
          background:
            'linear-gradient(120deg, rgba(6,182,212,0.32) 0%, rgba(99,102,241,0.22) 50%, rgba(236,72,153,0.22) 100%)',
          filter: 'blur(14px)',
          opacity: 0.55,
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'relative',
          background:
            'linear-gradient(180deg, rgba(255,255,255,0.92) 0%, rgba(244,249,255,0.82) 100%)',
          backdropFilter: 'blur(20px) saturate(180%)',
          WebkitBackdropFilter: 'blur(20px) saturate(180%)',
          borderRadius: 32,
          display: 'flex',
          alignItems: 'center',
          padding: 7,
          border: '1px solid rgba(255,255,255,0.95)',
          boxShadow:
            '0 1px 0 rgba(255,255,255,0.95) inset, ' +
            '0 2px 4px rgba(15,23,42,0.04), ' +
            '0 10px 24px rgba(6,182,212,0.14), ' +
            '0 24px 48px -16px rgba(0,104,122,0.22)',
        }}
      >
        <div style={{ paddingLeft: 12, paddingRight: 8, flexShrink: 0 }}>
          <svg width="15" height="15" viewBox="0 0 18 18" fill="none">
            <circle cx="8" cy="8" r="5.5" stroke="#9CA3AF" strokeWidth="1.5" />
            <path d="M12.5 12.5L16 16" stroke="#9CA3AF" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </div>
        <input
          style={{
            flex: 1,
            height: 38,
            fontSize: 14,
            fontFamily: 'Pretendard,sans-serif',
            fontWeight: loading ? 600 : 500,
            outline: 'none',
            border: 'none',
            background: 'transparent',
            color: '#64748b',
            minWidth: 0,
          }}
          placeholder={loading ? '분석하고 있어요...' : '무엇을 분석할까요?'}
          value={loading ? '' : value}
          disabled={loading}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !loading) onSubmit(value);
          }}
        />
        <button
          onClick={() => !loading && onSubmit(value)}
          onMouseDown={(e) => e.stopPropagation()}
          style={{
            width: 42,
            height: 42,
            borderRadius: 14,
            flexShrink: 0,
            background:
              'linear-gradient(135deg, #22d3ee 0%, #06b6d4 45%, #0891b2 100%)',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow:
              '0 1px 0 rgba(255,255,255,0.4) inset, ' +
              '0 -1px 2px rgba(8,145,178,0.4) inset, ' +
              '0 4px 12px rgba(6,182,212,0.45), ' +
              '0 8px 18px -4px rgba(8,145,178,0.35)',
            transition: 'transform 0.12s, box-shadow 0.12s',
          }}
          onMouseOver={(e) => { e.currentTarget.style.transform = 'translateY(-1px)'; }}
          onMouseOut={(e) => { e.currentTarget.style.transform = 'translateY(0)'; }}
        >
          {loading ? (
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="white"
              style={{ animation: 'spin 1s linear infinite' }}
            >
              <path d="M12 2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8V2z" />
            </svg>
          ) : (
            <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
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
  );
}
