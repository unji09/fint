'use client';

import { useState } from 'react';
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
}

export default function StrategyCardComponent({ card, index, onExpand }: StrategyCardProps) {
  const [expanded, setExpanded] = useState(false);

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
          padding: '0 20px',
          flex: '0 0 auto',
          minHeight: 60,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <span
            style={{
              fontFamily: 'Manrope,sans-serif',
              fontWeight: 400,
              fontSize: expanded ? 38 : 26,
              color: expanded ? '#2bbad1' : 'rgba(0,104,119,0.2)',
              lineHeight: 1,
              minWidth: 36,
              transition: 'all 0.2s',
            }}
          >
            {String(index + 1).padStart(2, '0')}
          </span>
          <div>
            <div
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontWeight: expanded ? 400 : 500,
                fontSize: expanded ? 20 : 15,
                color: '#0d1c2e',
                lineHeight: '1.4',
              }}
            >
              {card.title}
            </div>
            <div
              style={{
                fontFamily: 'Inter,sans-serif',
                fontWeight: 600,
                fontSize: 12,
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
          <div style={{ textAlign: 'right' }}>
            <div
              style={{
                fontFamily: 'Inter,sans-serif',
                fontWeight: 600,
                fontSize: 18,
                color: '#0e7490',
              }}
            >
              {card.successRate}%
            </div>
            <div style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 11, color: '#3d494c' }}>
              성공 가능성
            </div>
          </div>
        )}
      </div>

      {/* 펼쳐진 본문 */}
      {expanded && card.basisData && (
        <div
          style={{ padding: '0 20px 20px', display: 'flex', flexDirection: 'column', gap: 10 }}
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
              return (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
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
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 500,
                      fontSize: 14,
                      color: '#0d1c2e',
                    }}
                  >
                    {d.content}
                  </span>
                </div>
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
        </div>
      )}
    </div>
  );
}
