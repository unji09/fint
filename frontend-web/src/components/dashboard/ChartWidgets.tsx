type BarChartProps = {
  size?: 'full' | 'mini';
  values?: number[];
  labels?: string[];
};

const BAR_FALLBACK_VALUES = [40, 48, 38, 50, 45, 62, 65, 70, 68, 74, 78, 80, 82, 90, 92, 95, 86, 84];
const BAR_FALLBACK_LABELS = ['W1', 'W4', 'W8', 'W12', 'W15'];

export function BarChartSvg({ size = 'full', values, labels }: BarChartProps) {
  const bars = values && values.length > 0 ? values : BAR_FALLBACK_VALUES;
  const max = Math.max(...bars);
  const h = size === 'mini' ? 90 : 150;
  const bw = size === 'mini' ? 10 : 18;
  const gap = size === 'mini' ? 3 : 6;
  const W = 300,
    H = h,
    pad = size === 'mini' ? 14 : 22;
  const totalW = bars.length * (bw + gap) - gap;
  const sx = (W - totalW) / 2;
  // labels 가 제공되면 그대로, 아니면 fallback 의 sparse 축 사용
  const axisLabels = labels && labels.length > 0 ? labels : BAR_FALLBACK_LABELS;
  const axisIndices = labels && labels.length > 0
    ? labels.map((_, i) => Math.round((i * (bars.length - 1)) / Math.max(1, labels.length - 1)))
    : [0, 3, 7, 11, 14];
  return (
    <svg viewBox={`0 0 ${W} ${H + pad}`} style={{ width: '100%', height: h }}>
      {bars.map((v, i) => {
        const bh = (v / max) * H;
        const x = sx + i * (bw + gap);
        const isFuture = !values && i >= 15;
        return (
          <rect
            key={i}
            x={x}
            y={H - bh + pad / 2}
            width={bw}
            height={bh}
            rx={2}
            fill={isFuture ? 'none' : '#7dd3fc'}
            stroke={isFuture ? '#06b6d4' : 'none'}
            strokeWidth={1.5}
            strokeDasharray={isFuture ? '3 2' : 'none'}
            opacity={isFuture ? 0.5 : 1}
          />
        );
      })}
      {size === 'full' &&
        axisLabels.map((l, i) => (
          <text
            key={`${l}-${i}`}
            x={sx + axisIndices[i] * (bw + gap) + bw / 2}
            y={H + pad / 2 + 14}
            textAnchor="middle"
            fontSize="9"
            fill="#94a3b8"
            fontFamily="Pretendard"
          >
            {l}
          </text>
        ))}
    </svg>
  );
}

type LineChartProps = {
  size?: 'full' | 'mini';
  values?: number[];
};

const LINE_FALLBACK_VALUES = [20, 28, 35, 32, 45, 48, 52, 58, 55, 65, 70, 68, 75, 80, 76, 85, 90, 95];

export function LineChartSvg({ size = 'full', values }: LineChartProps) {
  const pts = values && values.length > 0 ? values : LINE_FALLBACK_VALUES;
  const max = Math.max(...pts);
  const W = 300,
    H = size === 'mini' ? 80 : 140,
    pad = 12;
  const xs = pts.map((_, i) => pad + (i / (pts.length - 1)) * (W - pad * 2));
  const ys = pts.map((v) => H - pad - (v / max) * (H - pad * 2));
  const line = xs
    .map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${ys[i].toFixed(1)}`)
    .join(' ');
  const area = `${line} L${xs[xs.length - 1].toFixed(1)},${H} L${xs[0].toFixed(1)},${H} Z`;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: size === 'mini' ? 80 : 140 }}>
      <defs>
        <linearGradient id="lgg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#06b6d4" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#06b6d4" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#lgg)" />
      <path
        d={line}
        fill="none"
        stroke="#06b6d4"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={xs[Math.min(7, xs.length - 1)]} cy={ys[Math.min(7, ys.length - 1)]} r="4" fill="white" stroke="#06b6d4" strokeWidth="2.5" />
    </svg>
  );
}

export function SegmentChart() {
  const rows = [
    { label: 'Enterprise', pct: 58, color: '#06b6d4' },
    { label: 'Mid-market', pct: 30, color: '#386570' },
    { label: 'SMB', pct: 12, color: '#6d797d' },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '4px 0' }}>
      {rows.map((r) => (
        <div key={r.label}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
            <span
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontSize: 13,
                fontWeight: 500,
                color: '#171d1e',
              }}
            >
              {r.label}
            </span>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#3d494c' }}>
              {r.pct}%
            </span>
          </div>
          <div style={{ height: 7, background: '#eff4f7', borderRadius: 12, overflow: 'hidden' }}>
            <div
              style={{ height: '100%', width: `${r.pct}%`, background: r.color, borderRadius: 12 }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
