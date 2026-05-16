'use client';

import { useState } from 'react';
import type { Signal } from '@/types/customer';

const SIGNAL_TAG: Record<string, { bg: string; border: string; color: string }> = {
  DART: { bg: '#f2fcff', border: '#bfdbfe', color: '#06b6d4' },
  NEWS: { bg: '#f8fafc', border: '#e2e8f0', color: '#64748b' },
  NEWS_WARN: { bg: '#fff7ed', border: '#fed7aa', color: '#ea580c' },
};

const F = 'Pretendard,sans-serif';

interface SignalItemProps {
  signal: Signal;
  accent: string;
}

export default function SignalItem({ signal, accent }: SignalItemProps) {
  const tagKey = signal.type === 'DART' ? 'DART' : accent === '#fb923c' ? 'NEWS_WARN' : 'NEWS';
  const tag = SIGNAL_TAG[tagKey];

  const [hovered, setHovered] = useState(false);

  const hasContent = !!signal.content && signal.content.length > 0;
  const hasUrl = !!signal.url;

  const openUrl = () => {
    if (hasUrl) window.open(signal.url, '_blank', 'noopener,noreferrer');
  };

  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={openUrl}
      style={{
        display: 'flex',
        gap: 0,
        alignItems: 'flex-start',
        cursor: hasUrl ? 'pointer' : 'default',
        padding: '4px 6px',
        marginLeft: -6,
        marginRight: -6,
        borderRadius: 6,
        backgroundColor: hovered ? '#f8fafc' : 'transparent',
        transition: 'background-color 0.12s',
      }}
    >
      <div style={{ width: 16, paddingRight: 12, paddingTop: 3, flexShrink: 0 }}>
        <div style={{ width: 3, height: 38, background: accent, borderRadius: 9999 }} />
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span
            style={{
              background: tag.bg,
              border: `1px solid ${tag.border}`,
              borderRadius: 4,
              padding: '2px 6px',
              fontSize: 11,
              color: tag.color,
              fontFamily: F,
            }}
          >
            {signal.type}
          </span>
          <span style={{ fontSize: 11, fontWeight: 300, color: '#94a3b8', fontFamily: F }}>
            {signal.time}
          </span>
        </div>
        {/* 평소: 제목 1줄 ellipsis */}
        <div
          style={{
            fontSize: 14,
            color: '#1e293b',
            lineHeight: '18px',
            fontFamily: F,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {signal.title}
        </div>
        {/* 호버: 요약 미리보기 + 자세히 보기 링크 */}
        {hovered && (
          <>
            {hasContent && (
              <div
                style={{
                  fontSize: 12,
                  color: '#475569',
                  lineHeight: '16px',
                  fontFamily: F,
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                }}
              >
                {signal.content}
              </div>
            )}
            {hasUrl && (
              <a
                href={signal.url}
                target="_blank"
                rel="noopener noreferrer"
                onClick={(e) => e.stopPropagation()}
                style={{
                  fontSize: 12,
                  color: '#0686d4',
                  fontFamily: F,
                  textDecoration: 'none',
                  marginTop: 2,
                  alignSelf: 'flex-start',
                  fontWeight: 500,
                }}
              >
                자세히 보기 →
              </a>
            )}
          </>
        )}
      </div>
    </div>
  );
}
