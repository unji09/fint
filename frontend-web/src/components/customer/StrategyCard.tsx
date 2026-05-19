'use client';

import { useState } from 'react';
import useBreakpoint from '@/hooks/useBreakpoint';
import type { StrategyCard } from '@/types/customer';

const TAG_STYLES: Record<string, { bg: string; color: string }> = {
  NEWS: { bg: '#fdf7ee', color: '#ce622c' },
  DART: { bg: '#eaf2fc', color: '#506898' },
  CRM: { bg: '#f2f5f9', color: '#4c5567' },
};

function CircularGauge({ pct }: { pct: number }) {
  const r = 30,
    cx = 36,
    cy = 36;
  const circ = 2 * Math.PI * r;
  const dash = (pct / 100) * circ;
  return (
    <div style={{ position: 'relative', width: 72, height: 72, flexShrink: 0 }}>
      <svg width="72" height="72" viewBox="0 0 72 72">
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="#e2e8f0" strokeWidth="5" />
        <circle
          cx={cx}
          cy={cy}
          r={r}
          fill="none"
          stroke="#2bbad1"
          strokeWidth="5"
          strokeDasharray={`${dash} ${circ - dash}`}
          strokeLinecap="round"
          transform="rotate(-90 36 36)"
        />
      </svg>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <span
          style={{
            fontFamily: 'Inter,sans-serif',
            fontWeight: 600,
            fontSize: 15,
            color: '#0e7490',
          }}
        >
          {pct}%
        </span>
      </div>
      <div
        style={{
          position: 'absolute',
          top: 76,
          left: '50%',
          transform: 'translateX(-50%)',
          whiteSpace: 'nowrap',
        }}
      >
        <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 10, color: '#3d494c' }}>
          성공 가능성
        </span>
      </div>
    </div>
  );
}

interface StrategyCardProps {
  card: StrategyCard;
  index: number;
  onExpand?: () => void;
  onAddToCalendar?: (card: StrategyCard) => void;
}

export default function StrategyCardComponent({ card, index, onExpand, onAddToCalendar }: StrategyCardProps) {
  const [expanded, setExpanded] = useState(false);
  const bp = useBreakpoint();
  const isCompact = bp !== 'desktop';

  const handleToggle = () => {
    if (onExpand) {
      onExpand();
      setExpanded(!expanded);
    } else {
      setExpanded(!expanded);
    }
  };

  return (
    <div
      onClick={handleToggle}
      style={{
        background: 'white',
        border: expanded ? '2px solid #2bbad1' : '1px solid #f1f5f9',
        borderRadius: 8,
        overflow: 'hidden',
        boxShadow: expanded ? 'none' : '0 1px 1px rgba(0,0,0,0.05)',
        cursor: 'pointer',
        /* 접힌 상태: 고정 높이로 뭉개짐 방지 / 펼친 상태: 자연 높이 */
        flex: '0 0 auto',
        minHeight: !expanded ? 65 : undefined,
        display: 'flex',
        flexDirection: 'column',
        transition: 'border-color 0.2s',
      }}
    >
      {/* 헤더 (항상 보임) */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: isCompact ? '0 12px' : '0 20px',
          flex: '0 0 auto',
          minHeight: 60,
          gap: isCompact ? 8 : 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: isCompact ? 8 : 14, flex: 1, minWidth: 0 }}>
          <span
            style={{
              fontFamily: 'Manrope,sans-serif',
              fontWeight: 400,
              fontSize: isCompact ? (expanded ? 28 : 20) : (expanded ? 38 : 26),
              color: expanded ? '#2bbad1' : 'rgba(0,104,119,0.2)',
              lineHeight: 1,
              minWidth: isCompact ? 28 : 36,
              transition: 'all 0.2s',
            }}
          >
            {String(index + 1).padStart(2, '0')}
          </span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontWeight: expanded ? 400 : 500,
                fontSize: isCompact ? (expanded ? 16 : 13) : (expanded ? 20 : 15),
                color: '#0d1c2e',
                lineHeight: '1.4',
                wordBreak: 'break-word',
              }}
            >
              {card.title}
            </div>
            <div
              style={{
                fontFamily: 'Inter,sans-serif',
                fontWeight: 600,
                fontSize: isCompact ? 10 : 12,
                color: '#0e7490',
                textTransform: 'uppercase',
                letterSpacing: '0.02em',
              }}
            >
              {card.category}
            </div>
          </div>
        </div>
        {expanded ? (
          <div onClick={(e) => e.stopPropagation()}>
            <CircularGauge pct={card.successRate} />
          </div>
        ) : (
          <div style={{ textAlign: 'right', flexShrink: 0 }}>
            <div
              style={{
                fontFamily: 'Inter,sans-serif',
                fontWeight: 600,
                fontSize: isCompact ? 15 : 18,
                color: '#0e7490',
              }}
            >
              {card.successRate}%
            </div>
            <div style={{ fontFamily: 'Pretendard,sans-serif', fontSize: isCompact ? 10 : 11, color: '#3d494c', whiteSpace: 'nowrap' }}>
              성공 가능성
            </div>
          </div>
        )}
      </div>

      {/* 펼쳐진 본문 */}
      {expanded && card.basisData && (
        <div
          style={{ padding: isCompact ? '0 12px 16px' : '0 20px 20px', display: 'flex', flexDirection: 'column', gap: 10 }}
          onClick={(e) => e.stopPropagation()}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 11 }}>📊</span>
            <span
              style={{
                fontFamily: 'Inter,sans-serif',
                fontWeight: 700,
                fontSize: 12,
                color: '#3d494c',
                letterSpacing: '0.02em',
              }}
            >
              근거 데이터
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {card.basisData.map((d, i) => {
              const s = TAG_STYLES[d.type] ?? TAG_STYLES.CRM;
              const primary = d.title || d.summary || d.content || '';
              const secondary = d.title && d.summary && d.title !== d.summary ? d.summary : undefined;
              const hasUrl = !!d.url;
              const Tag = hasUrl ? 'a' : 'div';
              return (
                <Tag
                  key={i}
                  href={hasUrl ? d.url : undefined}
                  target={hasUrl ? '_blank' : undefined}
                  rel={hasUrl ? 'noopener noreferrer' : undefined}
                  onClick={(e: React.MouseEvent) => e.stopPropagation()}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 4,
                    padding: '8px 10px',
                    borderRadius: 6,
                    backgroundColor: '#F8FAFC',
                    border: '1px solid #ECEDE5',
                    textDecoration: 'none',
                    color: 'inherit',
                    cursor: hasUrl ? 'pointer' : 'default',
                    transition: 'background-color 0.12s, border-color 0.12s',
                  }}
                  onMouseEnter={(e: React.MouseEvent<HTMLElement>) => {
                    if (hasUrl) {
                      (e.currentTarget as HTMLElement).style.backgroundColor = '#fff';
                      (e.currentTarget as HTMLElement).style.borderColor = '#06B6D4';
                    }
                  }}
                  onMouseLeave={(e: React.MouseEvent<HTMLElement>) => {
                    (e.currentTarget as HTMLElement).style.backgroundColor = '#F8FAFC';
                    (e.currentTarget as HTMLElement).style.borderColor = '#ECEDE5';
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                    <span
                      style={{
                        background: s.bg,
                        color: s.color,
                        fontFamily: 'Inter,sans-serif',
                        fontWeight: 700,
                        fontSize: 10,
                        padding: '2px 7px',
                        borderRadius: 2,
                        flexShrink: 0,
                      }}
                    >
                      {d.type}
                    </span>
                    <span
                      style={{
                        flex: 1,
                        minWidth: 0,
                        fontFamily: 'Pretendard,sans-serif',
                        fontWeight: 600,
                        fontSize: 13,
                        color: '#0d1c2e',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {primary}
                    </span>
                    {hasUrl && (
                      <span style={{ fontSize: 10, color: '#0686d4', fontWeight: 500, flexShrink: 0 }}>원문 →</span>
                    )}
                  </div>
                  {secondary && (
                    <p style={{ margin: 0, fontSize: 12, lineHeight: 1.5, color: '#475569', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', wordBreak: 'break-word' }}>
                      {secondary}
                    </p>
                  )}
                </Tag>
              );
            })}
          </div>

          {card.aiComment && (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 2 }}>
                <span style={{ fontSize: 10 }}>✦</span>
                <span
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontSize: 11,
                    color: '#06b6d4',
                    textTransform: 'uppercase',
                    letterSpacing: '-0.03em',
                  }}
                >
                  AI 멘트 추천
                </span>
              </div>
              <div
                style={{
                  background: 'white',
                  border: '2px solid #cffafe',
                  borderRadius: 4,
                  padding: '14px 16px',
                  fontFamily: 'Pretendard,sans-serif',
                  fontSize: 14,
                  color: '#1e293b',
                  lineHeight: 1.6,
                }}
              >
                {card.aiComment}
              </div>
            </>
          )}

          {card.warning && (
            <div
              style={{
                background: '#fffbeb',
                border: '1px solid #fef3c7',
                borderRadius: 4,
                padding: '10px 12px',
                display: 'flex',
                gap: 8,
                alignItems: 'flex-start',
              }}
            >
              <span style={{ fontSize: 13, flexShrink: 0 }}>⚠️</span>
              <div>
                <div
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontSize: 11,
                    color: '#d97706',
                    textTransform: 'uppercase',
                    marginBottom: 2,
                  }}
                >
                  주의
                </div>
                <div
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontSize: 12,
                    color: '#92400e',
                    letterSpacing: '0.01em',
                  }}
                >
                  {card.warning}
                </div>
              </div>
            </div>
          )}

          {/* 일정에 추가 버튼 — Next Action 을 캘린더 활동으로 연결 */}
          {onAddToCalendar && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onAddToCalendar(card);
              }}
              style={{
                marginTop: 2,
                alignSelf: 'flex-start',
                padding: '8px 16px',
                borderRadius: 6,
                border: '1px solid #06B6D4',
                backgroundColor: '#fff',
                color: '#06B6D4',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                fontFamily: 'Pretendard,sans-serif',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                transition: 'background-color 0.12s',
              }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#ECFEFF';
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#fff';
              }}
            >
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
                <rect x="1.5" y="2.5" width="11" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.2" />
                <path d="M4.5 1v3M9.5 1v3M1.5 5.5h11M7 7.5v3M5.5 9h3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
              </svg>
              일정에 추가
            </button>
          )}
        </div>
      )}
    </div>
  );
}
