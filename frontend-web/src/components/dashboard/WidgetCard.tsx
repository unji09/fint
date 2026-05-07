'use client';

import type { DashboardWidget } from '@/types/dashboard';

interface WidgetCardProps {
  widget: DashboardWidget;
}

/* ── SVG 라인차트 ── */
function LineChartSvg({ values }: { values: number[] }) {
  const W = 400,
    H = 140,
    pad = 16;
  const max = Math.max(...values, 1);
  const xs = values.map((_, i) => pad + (i / (values.length - 1)) * (W - pad * 2));
  const ys = values.map((v) => H - pad - (v / max) * (H - pad * 2));
  const line = xs.map((x, i) => `${i === 0 ? 'M' : 'L'}${x},${ys[i]}`).join(' ');
  const area = `${line} L${xs[xs.length - 1]},${H - pad} L${xs[0]},${H - pad} Z`;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: '100%' }}>
      <defs>
        <linearGradient id="lg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#06b6d4" stopOpacity="0.3" />
          <stop offset="100%" stopColor="#06b6d4" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#lg)" />
      <path
        d={line}
        fill="none"
        stroke="#06b6d4"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {xs.map((x, i) => (
        <circle key={i} cx={x} cy={ys[i]} r="4" fill="white" stroke="#06b6d4" strokeWidth="2" />
      ))}
    </svg>
  );
}

/* ── SVG 바차트 ── */
function BarChartSvg({ values, labels }: { values: number[]; labels: string[] }) {
  const W = 400,
    H = 140,
    pad = 20;
  const max = Math.max(...values, 1);
  const barW = Math.min(40, (W - pad * 2) / values.length - 8);
  const colors = ['#dbeafe', '#bfdbfe', '#60a5fa', '#3b82f6', '#2563eb', '#1d4ed8'];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: '100%' }}>
      {values.map((v, i) => {
        const x =
          pad + (i / values.length) * (W - pad * 2) + ((W - pad * 2) / values.length - barW) / 2;
        const bh = (v / max) * (H - pad * 2 - 20);
        return (
          <g key={i}>
            <rect
              x={x}
              y={H - pad - bh}
              width={barW}
              height={bh}
              fill={colors[i % colors.length]}
              rx={2}
            />
            <text
              x={x + barW / 2}
              y={H - 4}
              textAnchor="middle"
              fontSize="9"
              fill="#94a3b8"
              fontFamily="Pretendard"
            >
              {labels[i]}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

/* ── 세그먼트 목록 ── */
function SegmentList({
  segments,
}: {
  segments: { label: string; value: number; color: string }[];
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '8px 0' }}>
      {segments.map((s, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{ width: 12, height: 12, borderRadius: 3, background: s.color, flexShrink: 0 }}
          />
          <span
            style={{
              fontFamily: 'Pretendard, sans-serif',
              fontSize: 13,
              color: '#3d494c',
              flex: 1,
            }}
          >
            {s.label}
          </span>
          <span
            style={{
              fontFamily: 'Pretendard, sans-serif',
              fontSize: 13,
              fontWeight: 600,
              color: '#1d1a24',
            }}
          >
            {s.value}%
          </span>
          <div style={{ width: 80, height: 6, background: '#f1f5f9', borderRadius: 3 }}>
            <div
              style={{ width: `${s.value}%`, height: '100%', background: s.color, borderRadius: 3 }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

/* ── 위젯 카드 ── */
export default function WidgetCard({ widget }: WidgetCardProps) {
  const result = widget.result;
  const data = (result?.data as Record<string, unknown>) ?? {};
  const labels = (data.labels as string[]) ?? ['1월', '2월', '3월', '4월', '5월', '6월'];
  const values = (data.values as number[]) ?? [30, 45, 28, 60, 55, 78];

  /* 위젯 너비: position.w 기반 (6열 기준, 1열=180px) */
  const col = widget.position?.w ?? 6;
  const width = Math.max(280, col * 100);

  function renderContent() {
    switch (widget.widgetType) {
      case 'LINE_CHART':
        return <LineChartSvg values={values} />;
      case 'BAR_CHART':
        return <BarChartSvg values={values} labels={labels} />;
      case 'PIE':
      case 'SEGMENT':
        return (
          <SegmentList
            segments={[
              { label: 'Enterprise', value: 58, color: '#60a5fa' },
              { label: 'Mid-market', value: 30, color: '#34d399' },
              { label: 'SMB', value: 12, color: '#a78bfa' },
            ]}
          />
        );
      case 'TABLE':
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {labels.slice(0, 4).map((l, i) => (
              <div
                key={i}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '5px 8px',
                  background: i === 0 ? 'rgba(6,182,212,0.05)' : undefined,
                  borderRadius: 4,
                }}
              >
                <span style={{ fontSize: 12, color: '#64748b', fontFamily: 'Pretendard' }}>
                  {l}
                </span>
                <span
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: '#1d1a24',
                    fontFamily: 'Pretendard',
                  }}
                >
                  {values[i]}
                </span>
              </div>
            ))}
          </div>
        );
      default:
        return (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              color: '#94a3b8',
              fontSize: 13,
              fontFamily: 'Pretendard',
            }}
          >
            {widget.widgetType}
          </div>
        );
    }
  }

  return (
    <div
      style={{
        background: 'white',
        border: '1px solid #dee3e6',
        borderRadius: 8,
        boxShadow: '0px 2px 8px rgba(0,0,0,0.06)',
        width,
        minHeight: 240,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        flexShrink: 0,
      }}
    >
      {/* 헤더 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '16px 20px 14px',
          borderBottom: '1px solid #eff4f7',
          flexShrink: 0,
        }}
      >
        <span
          style={{
            fontFamily: 'Pretendard, sans-serif',
            fontWeight: 500,
            fontSize: 16,
            color: '#171d1e',
            letterSpacing: '-0.2px',
          }}
        >
          {widget.title}
        </span>
        {result?.insightText && (
          <span
            style={{
              background: '#e9eff1',
              borderRadius: 2,
              padding: '3px 8px',
              fontSize: 11,
              color: '#3d494c',
              fontFamily: 'Pretendard',
              fontWeight: 500,
            }}
          >
            {(data.range as string) ?? '최근 6개월'}
          </span>
        )}
      </div>

      {/* 차트 영역 */}
      <div style={{ flex: 1, padding: '12px 16px 8px' }}>{renderContent()}</div>

      {/* 인사이트 텍스트 */}
      {result?.insightText && (
        <div style={{ padding: '8px 16px 12px', borderTop: '1px solid #f1f5f9' }}>
          <p
            style={{
              fontFamily: 'Pretendard, sans-serif',
              fontSize: 11,
              color: '#94a3b8',
              margin: 0,
            }}
          >
            {result.insightText}
          </p>
        </div>
      )}
    </div>
  );
}
