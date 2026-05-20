'use client';

import React from 'react';
import type { TemplateGroup } from '@/types/dashboard';
import useBreakpoint from '@/hooks/useBreakpoint';

interface TemplateCardGridProps {
  groups: TemplateGroup[];
  onSelect: (groupId: number) => void;
  onCreateNew?: () => void;
  loading?: boolean;
}

/* ─── 템플릿 1 프리뷰: 영업 성과 (파이프라인 퍼널 + 매출 선차트) ─── */
function SalesPreview() {
  const stages = [
    { label: '발굴', w: '88%', color: 'rgba(99,102,241,0.18)' },
    { label: '제안', w: '70%', color: 'rgba(99,102,241,0.30)' },
    { label: '협상', w: '50%', color: 'rgba(99,102,241,0.46)' },
    { label: '수주', w: '30%', color: 'rgba(99,102,241,0.70)' },
  ];
  const linePoints = [18, 32, 24, 44, 38, 56, 50];
  const maxY = 60;
  const pts = linePoints.map((v, i) => `${(i / (linePoints.length - 1)) * 120},${maxY - v}`).join(' ');
  return (
    <div style={{ padding: '8px 12px 6px', height: '100%', boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 6 }}>
      {/* 파이프라인 퍼널 */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {stages.map((s) => (
          <div key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 9, color: '#94a3b8', width: 22, flexShrink: 0 }}>{s.label}</span>
            <div style={{ flex: 1, height: 10, borderRadius: 2, background: '#f1f5f9', overflow: 'hidden' }}>
              <div style={{ width: s.w, height: '100%', background: s.color, borderRadius: 2 }} />
            </div>
          </div>
        ))}
      </div>
      {/* 매출 선차트 미니 */}
      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <svg width="100%" height="100%" viewBox="0 0 120 60" preserveAspectRatio="none" style={{ display: 'block' }}>
          <defs>
            <linearGradient id="salesGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="rgba(99,102,241,0.22)" />
              <stop offset="100%" stopColor="rgba(99,102,241,0)" />
            </linearGradient>
          </defs>
          <polygon points={`0,${maxY} ${pts} 120,${maxY}`} fill="url(#salesGrad)" />
          <polyline points={pts} fill="none" stroke="rgba(99,102,241,0.7)" strokeWidth="2" strokeLinejoin="round" />
        </svg>
        <span style={{ position: 'absolute', bottom: 2, right: 0, fontFamily: 'Pretendard,sans-serif', fontSize: 8, color: '#a5b4fc' }}>월별 매출</span>
      </div>
    </div>
  );
}

/* ─── 템플릿 2 프리뷰: 활동 & 고객 (도넛 + 담당자 막대) ─── */
function ActivityPreview() {
  const slices = [
    { pct: 38, color: '#06b6d4' },
    { pct: 28, color: '#67e8f9' },
    { pct: 20, color: '#a5f3fc' },
    { pct: 14, color: '#cffafe' },
  ];
  const bars = [
    { w: '82%', label: '김민준' },
    { w: '64%', label: '이지은' },
    { w: '48%', label: '박서준' },
  ];
  // SVG 도넛 계산
  const r = 22, cx = 28, cy = 28, stroke = 12;
  const circ = 2 * Math.PI * r;
  let offset = 0;
  const segments = slices.map((s) => {
    const dash = (s.pct / 100) * circ;
    const gap = circ - dash;
    const seg = { ...s, dash, gap, offset };
    offset += dash;
    return seg;
  });

  return (
    <div style={{ padding: '8px 12px 6px', height: '100%', boxSizing: 'border-box', display: 'flex', gap: 10 }}>
      {/* 도넛 */}
      <svg width="56" height="56" viewBox="0 0 56 56" style={{ flexShrink: 0 }}>
        {segments.map((seg, i) => (
          <circle key={i} cx={cx} cy={cy} r={r}
            fill="none" stroke={seg.color} strokeWidth={stroke}
            strokeDasharray={`${seg.dash} ${seg.gap}`}
            strokeDashoffset={-seg.offset}
            style={{ transform: 'rotate(-90deg)', transformOrigin: '28px 28px' }}
          />
        ))}
        <text x={cx} y={cy + 4} textAnchor="middle" style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 9, fill: '#64748b' }}>활동</text>
      </svg>
      {/* 담당자 막대 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8, justifyContent: 'center' }}>
        {bars.map((b) => (
          <div key={b.label}>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 9, color: '#94a3b8' }}>{b.label}</span>
            <div style={{ marginTop: 2, height: 8, borderRadius: 2, background: '#f1f5f9', overflow: 'hidden' }}>
              <div style={{ width: b.w, height: '100%', background: 'rgba(6,182,212,0.45)', borderRadius: 2 }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

const PREVIEWS: React.ComponentType[] = [SalesPreview, ActivityPreview];

const TEMPLATE_META: Record<number, { name: string; subtitle: string; color: string }> = {
  1: { name: '영업 성과', subtitle: '딜·매출·파이프라인 중심', color: '99,102,241' },
  2: { name: '활동 & 고객', subtitle: '활동·고객사·담당자 분석', color: '6,182,212' },
};

const WIDGET_TYPE_ICON: Record<string, string> = {
  CHART: '📊',
  TABLE: '📋',
  CARD: '🔢',
  LIST: '📝',
};

function BlankDashboardPreview() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', flexDirection: 'column', gap: 12, padding: 20 }}>
      <div style={{ width: 56, height: 56, borderRadius: 16, border: '2px dashed rgba(6,182,212,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
          <path d="M12 5v14M5 12h14" stroke="rgba(6,182,212,0.6)" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </div>
    </div>
  );
}

export default function TemplateCardGrid({ groups, onSelect, onCreateNew, loading }: TemplateCardGridProps) {
  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';
  const isTablet = bp === 'tablet';
  const totalItems = (loading ? 3 : groups.length) + (onCreateNew ? 1 : 0);
  const maxCols = isMobile ? 1 : isTablet ? 2 : 3;
  const cols = Math.min(totalItems || 1, maxCols);

  const cardStyle: React.CSSProperties = {
    background: '#ffffff',
    border: '1px solid rgba(226,232,240,0.8)',
    borderRadius: 12,
    overflow: 'hidden',
    boxShadow: '0 1px 0 rgba(255,255,255,0.9) inset, 0 1px 2px rgba(15,23,42,0.04), 0 6px 16px rgba(15,23,42,0.06)',
    display: 'flex',
    flexDirection: 'column',
    textAlign: 'left',
    cursor: 'pointer',
    padding: 1,
    transition: 'box-shadow 0.18s, border-color 0.18s, transform 0.18s',
    height: '100%',
    minHeight: isMobile ? 160 : undefined,
  };

  const onHover = (e: React.MouseEvent<HTMLButtonElement>, color = '6,182,212') => {
    const el = e.currentTarget;
    el.style.borderColor = `rgba(${color},0.5)`;
    el.style.boxShadow = `0 1px 0 rgba(255,255,255,0.9) inset, 0 2px 4px rgba(15,23,42,0.06), 0 14px 32px rgba(${color},0.16)`;
    el.style.transform = 'translateY(-2px)';
  };
  const onLeave = (e: React.MouseEvent<HTMLButtonElement>) => {
    const el = e.currentTarget;
    el.style.borderColor = 'rgba(226,232,240,0.8)';
    el.style.boxShadow = '0 1px 0 rgba(255,255,255,0.9) inset, 0 1px 2px rgba(15,23,42,0.04), 0 6px 16px rgba(15,23,42,0.06)';
    el.style.transform = 'translateY(0)';
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <svg width="13" height="17" viewBox="0 0 13 17" fill="none">
          <path d="M6.5 1C3.46 1 1 3.46 1 6.5C1 10.75 6.5 16 6.5 16C6.5 16 12 10.75 12 6.5C12 3.46 9.54 1 6.5 1ZM6.5 8.5C5.4 8.5 4.5 7.6 4.5 6.5C4.5 5.4 5.4 4.5 6.5 4.5C7.6 4.5 8.5 5.4 8.5 6.5C8.5 7.6 7.6 8.5 6.5 8.5Z" fill="#06b6d4" />
        </svg>
        <h2 style={{ fontFamily: 'Pretendard, sans-serif', fontWeight: 600, fontSize: 18, lineHeight: '26px', letterSpacing: '-0.2px', color: '#1d1a24', margin: 0 }}>
          이런 대시보드를 만들 수 있어요
        </h2>
      </div>

      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, gridTemplateRows: isMobile ? 'auto' : '1fr', gap: isMobile ? 12 : 20, minHeight: 0 }}>
        {loading
          ? [0, 1, 2].slice(0, cols).map((i) => (
              <div key={i} style={{ background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: 12 }} />
            ))
          : groups.map((group, idx) => {
              const Preview = PREVIEWS[idx] ?? PREVIEWS[PREVIEWS.length - 1];
              const chartCount = group.widgets.filter((w) => w.widgetType === 'CHART').length;
              const tableCount = group.widgets.filter((w) => w.widgetType === 'TABLE').length;
              const meta = TEMPLATE_META[group.groupId] ?? { name: `템플릿 ${group.groupId}`, subtitle: `차트 ${chartCount}개 · 테이블 ${tableCount}개`, color: '99,118,183' };
              return (
                <button key={group.groupId} onClick={() => onSelect(group.groupId)} style={cardStyle}
                  onMouseEnter={(e) => onHover(e, meta.color)} onMouseLeave={onLeave}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: `1px solid rgba(${meta.color},0.12)`, flexShrink: 0, background: `rgba(${meta.color},0.04)`, borderRadius: '11px 11px 0 0' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                        <div style={{ width: 5, height: 5, borderRadius: '50%', background: `rgb(${meta.color})`, flexShrink: 0 }} />
                        <span style={{ fontFamily: 'Pretendard, sans-serif', fontWeight: 700, fontSize: 13, color: '#1d1a24', letterSpacing: '-0.1px' }}>
                          {meta.name}
                        </span>
                      </div>
                      <span style={{ fontFamily: 'Pretendard, sans-serif', fontSize: 10, color: '#94a3b8', paddingLeft: 10 }}>
                        {meta.subtitle} · 차트 {chartCount} 테이블 {tableCount}
                      </span>
                    </div>
                    <svg width="10" height="10" viewBox="0 0 11 11" fill="none">
                      <path d="M1 10L10 1M10 1H4M10 1V7" stroke={`rgba(${meta.color},0.7)`} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </div>
                  <div style={{ flex: 1, overflow: 'hidden', background: '#ffffff', position: 'relative', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                    <div style={{ flex: 1, minHeight: 0 }}><Preview /></div>
                    <div style={{ padding: '0 12px 8px', display: 'flex', flexWrap: 'wrap', gap: 3 }}>
                      {group.widgets.slice(0, 4).map((w) => (
                        <span key={w.templateId} style={{ fontFamily: 'Pretendard, sans-serif', fontSize: 9, color: '#64748b', background: '#f1f5f9', borderRadius: 4, padding: '1px 5px', whiteSpace: 'nowrap' }}>
                          {WIDGET_TYPE_ICON[w.widgetType] ?? '📊'} {w.title}
                        </span>
                      ))}
                      {group.widgets.length > 4 && (
                        <span style={{ fontFamily: 'Pretendard, sans-serif', fontSize: 9, color: '#94a3b8', padding: '1px 3px' }}>
                          +{group.widgets.length - 4}개
                        </span>
                      )}
                    </div>
                  </div>
                </button>
              );
            })}

        {/* 빈 대시보드 만들기 카드 */}
        {!loading && onCreateNew && (
          <button onClick={onCreateNew} style={{ ...cardStyle, border: '1.5px dashed rgba(6,182,212,0.3)', background: 'rgba(240,253,255,0.5)' }}
            onMouseEnter={(e) => onHover(e)} onMouseLeave={onLeave}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 16px', borderBottom: '1px solid rgba(6,182,212,0.1)', flexShrink: 0 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <span style={{ fontFamily: 'Pretendard, sans-serif', fontWeight: 600, fontSize: 13, color: '#0e7490', letterSpacing: '0.13px' }}>
                  빈 대시보드 만들기
                </span>
                <span style={{ fontFamily: 'Pretendard, sans-serif', fontSize: 11, color: '#94a3b8' }}>
                  처음부터 직접 구성
                </span>
              </div>
              <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                <path d="M1 10L10 1M10 1H4M10 1V7" stroke="#06b6d4" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
            <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
              <BlankDashboardPreview />
              <div style={{ padding: '0 14px 12px', display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {['AI 분석', '자유 배치', '커스텀 위젯'].map((tag) => (
                  <span key={tag} style={{ fontFamily: 'Pretendard, sans-serif', fontSize: 10, color: '#0e7490', background: 'rgba(6,182,212,0.08)', borderRadius: 4, padding: '2px 6px', whiteSpace: 'nowrap' }}>
                    {tag}
                  </span>
                ))}
              </div>
            </div>
          </button>
        )}
      </div>
    </div>
  );
}
