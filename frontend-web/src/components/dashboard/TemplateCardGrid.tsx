'use client';

import type { DashboardTemplate } from '@/types/dashboard';

interface TemplateCardGridProps {
  templates: DashboardTemplate[];
  onSelect: (templateId: number) => void;
  loading?: boolean;
}

/* ─── 미니 프리뷰 ─── */
function BarChartPreview() {
  const bars = [
    { h: 44, color: '#dbeafe' },
    { h: 65, color: '#bfdbfe' },
    { h: 92, color: '#60a5fa', label: true },
    { h: 33, color: '#dbeafe' },
  ] as const;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'center',
        gap: 12,
        padding: '20px 16px 16px',
        height: '100%',
        boxSizing: 'border-box',
      }}
    >
      {bars.map((bar, i) => (
        <div
          key={i}
          style={{
            position: 'relative',
            width: 28,
            height: bar.h,
            background: bar.color,
            borderRadius: '2px 2px 0 0',
            flexShrink: 0,
          }}
        >
          {'label' in bar && bar.label && (
            <div
              style={{
                position: 'absolute',
                top: -22,
                left: '50%',
                transform: 'translateX(-50%)',
                background: '#3b82f6',
                color: '#fff',
                fontSize: 9,
                lineHeight: '13px',
                padding: '0 4px',
                borderRadius: 4,
                whiteSpace: 'nowrap',
                fontFamily: 'Pretendard, sans-serif',
              }}
            >
              Max
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

function TablePreview() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        padding: '16px',
        height: '100%',
        boxSizing: 'border-box',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '5px 8px',
          borderRadius: 4,
          background: 'rgba(99,14,212,0.05)',
        }}
      >
        <div
          style={{ height: 9, width: 40, borderRadius: 9999, background: 'rgba(99,14,212,0.2)' }}
        />
        <div
          style={{ height: 9, width: 80, borderRadius: 9999, background: 'rgba(99,14,212,0.4)' }}
        />
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '5px 8px',
        }}
      >
        <div
          style={{ height: 9, width: 56, borderRadius: 9999, background: 'rgba(192,199,214,0.4)' }}
        />
        <div
          style={{ height: 9, width: 48, borderRadius: 9999, background: 'rgba(192,199,214,0.8)' }}
        />
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '5px 8px',
        }}
      >
        <div
          style={{ height: 9, width: 48, borderRadius: 9999, background: 'rgba(192,199,214,0.4)' }}
        />
        <div
          style={{ height: 9, width: 64, borderRadius: 9999, background: 'rgba(192,199,214,0.8)' }}
        />
      </div>
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: 40,
          background: 'linear-gradient(to top, white, transparent)',
        }}
      />
    </div>
  );
}

function CompanyPreview() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: 16 }}>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 4,
            background: '#d1fae5',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <div style={{ width: 18, height: 18, borderRadius: '50%', background: '#10b981' }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
          <div
            style={{
              height: 11,
              width: 72,
              borderRadius: 9999,
              background: 'rgba(16,185,129,0.2)',
            }}
          />
          <div
            style={{
              height: 7,
              width: 48,
              borderRadius: 9999,
              background: 'rgba(192,199,214,0.5)',
            }}
          />
        </div>
      </div>
      <div
        style={{
          height: 7,
          width: '100%',
          borderRadius: 9999,
          background: 'rgba(192,199,214,0.3)',
        }}
      />
      <div
        style={{ height: 7, width: '75%', borderRadius: 9999, background: 'rgba(192,199,214,0.3)' }}
      />
    </div>
  );
}

const PREVIEWS = [BarChartPreview, TablePreview, CompanyPreview];

export default function TemplateCardGrid({ templates, onSelect, loading }: TemplateCardGridProps) {
  return (
    /* height: 100% → 부모 flex:1 영역을 꽉 채움 */
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <svg width="13" height="17" viewBox="0 0 13 17" fill="none">
          <path
            d="M6.5 1C3.46 1 1 3.46 1 6.5C1 10.75 6.5 16 6.5 16C6.5 16 12 10.75 12 6.5C12 3.46 9.54 1 6.5 1ZM6.5 8.5C5.4 8.5 4.5 7.6 4.5 6.5C4.5 5.4 5.4 4.5 6.5 4.5C7.6 4.5 8.5 5.4 8.5 6.5C8.5 7.6 7.6 8.5 6.5 8.5Z"
            fill="#06b6d4"
          />
        </svg>
        <h2
          style={{
            fontFamily: 'Pretendard, sans-serif',
            fontWeight: 600,
            fontSize: 18,
            lineHeight: '26px',
            letterSpacing: '-0.2px',
            color: '#1d1a24',
            margin: 0,
          }}
        >
          이런 대시보드를 만들 수 있어요
        </h2>
      </div>

      {/* 그리드 — flex:1 로 남은 높이 전부 차지, 카드는 height:100% */}
      <div
        style={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
          gridTemplateRows: '1fr' /* 카드가 행 높이를 꽉 채움 */,
          gap: 20,
          minHeight: 0,
        }}
      >
        {loading
          ? [0, 1, 2].map((i) => (
              <div
                key={i}
                style={{ background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: 12 }}
              />
            ))
          : templates.map((tpl, idx) => {
              const Preview = PREVIEWS[idx % PREVIEWS.length];
              return (
                <button
                  key={tpl.templateId}
                  onClick={() => onSelect(tpl.templateId)}
                  style={{
                    background: '#ffffff',
                    border: '1px solid rgba(226,232,240,0.8)',
                    borderRadius: 12,
                    overflow: 'hidden',
                    boxShadow:
                      '0 1px 0 rgba(255,255,255,0.9) inset, ' +
                      '0 1px 2px rgba(15,23,42,0.04), ' +
                      '0 6px 16px rgba(15,23,42,0.06)',
                    display: 'flex',
                    flexDirection: 'column',
                    textAlign: 'left',
                    cursor: 'pointer',
                    padding: 1,
                    transition: 'box-shadow 0.18s, border-color 0.18s, transform 0.18s',
                    height: '100%' /* 그리드 행을 꽉 채움 */,
                  }}
                  onMouseEnter={(e) => {
                    const el = e.currentTarget;
                    el.style.borderColor = 'rgba(6,182,212,0.5)';
                    el.style.boxShadow =
                      '0 1px 0 rgba(255,255,255,0.9) inset, 0 2px 4px rgba(15,23,42,0.06), 0 14px 32px rgba(6,182,212,0.16)';
                    el.style.transform = 'translateY(-2px)';
                  }}
                  onMouseLeave={(e) => {
                    const el = e.currentTarget;
                    el.style.borderColor = 'rgba(226,232,240,0.8)';
                    el.style.boxShadow =
                      '0 1px 0 rgba(255,255,255,0.9) inset, 0 1px 2px rgba(15,23,42,0.04), 0 6px 16px rgba(15,23,42,0.06)';
                    el.style.transform = 'translateY(0)';
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '14px 16px',
                      borderBottom: '1px solid #e5e7eb',
                      flexShrink: 0,
                    }}
                  >
                    <span
                      style={{
                        fontFamily: 'Pretendard, sans-serif',
                        fontWeight: 600,
                        fontSize: 13,
                        color: '#1d1a24',
                        letterSpacing: '0.13px',
                      }}
                    >
                      {tpl.title}
                    </span>
                    <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                      <path
                        d="M1 10L10 1M10 1H4M10 1V7"
                        stroke="#9CA3AF"
                        strokeWidth="1.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </div>
                  <div
                    style={{
                      flex: 1,
                      overflow: 'hidden',
                      background: '#ffffff',
                      position: 'relative',
                      minHeight: 0,
                    }}
                  >
                    <Preview />
                  </div>
                </button>
              );
            })}
      </div>
    </div>
  );
}
