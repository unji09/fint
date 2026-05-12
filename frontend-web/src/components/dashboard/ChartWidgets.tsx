type BarChartProps = {
  size?: 'full' | 'mini';
  values?: number[];
  labels?: string[];
  xLabel?: string;
  yLabel?: string;
};

const BAR_FALLBACK_VALUES = [40, 48, 38, 50, 45, 62, 65, 70, 68, 74, 78, 80, 82, 90, 92, 95, 86, 84];
const BAR_FALLBACK_LABELS = ['W1', 'W4', 'W8', 'W12', 'W15'];
const SEGMENT_COLORS = ['#06b6d4', '#386570', '#6d797d', '#94a3b8', '#3b82f6', '#a78bfa', '#34d399'];

export function BarChartSvg({ size = 'full', values, labels, xLabel, yLabel }: BarChartProps) {
  const bars = values && values.length > 0 ? values : BAR_FALLBACK_VALUES;
  const max = Math.max(...bars);
  const h = size === 'mini' ? 90 : 150;
  const bw = size === 'mini' ? 10 : 18;
  const gap = size === 'mini' ? 3 : 6;
  const yAxisW = size === 'full' ? 36 : 0;
  const bottomPad = size === 'full' ? 28 : 14;
  const W = 300 + yAxisW;
  const H = h;
  const totalW = bars.length * (bw + gap) - gap;
  const chartLeft = yAxisW + (W - yAxisW - totalW) / 2;

  const axisLabels = labels && labels.length > 0 ? labels : BAR_FALLBACK_LABELS;
  const axisIndices =
    labels && labels.length > 0
      ? labels.map((_, i) => Math.round((i * (bars.length - 1)) / Math.max(1, labels.length - 1)))
      : [0, 3, 7, 11, 14];

  const yTicks = size === 'full' ? [0, Math.round(max / 2), max] : [];

  return (
    <svg viewBox={`0 0 ${W} ${H + bottomPad}`} style={{ width: '100%', height: size === 'mini' ? h : '100%' }}>
      {yTicks.map((tick) => {
        const y = H - (tick / max) * H + 4;
        return (
          <text key={tick} x={yAxisW - 4} y={y} textAnchor="end" fontSize="8" fill="#94a3b8" fontFamily="Pretendard">
            {tick.toLocaleString()}
          </text>
        );
      })}
      {bars.map((v, i) => {
        const bh = (v / max) * H;
        const x = chartLeft + i * (bw + gap);
        const isFuture = !values && i >= 15;
        return (
          <rect
            key={i}
            x={x}
            y={H - bh + 4}
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
        axisLabels.map((l, i) => {
          const idx = axisIndices[i];
          if (idx === undefined) return null;
          return (
            <text
              key={`${l}-${i}`}
              x={chartLeft + idx * (bw + gap) + bw / 2}
              y={H + 18}
              textAnchor="middle"
              fontSize="9"
              fill="#94a3b8"
              fontFamily="Pretendard"
            >
              {l}
            </text>
          );
        })}
      {size === 'full' && xLabel && (
        <text x={W / 2} y={H + bottomPad - 2} textAnchor="middle" fontSize="9" fill="#6b7280" fontFamily="Pretendard">
          {xLabel}
        </text>
      )}
      {size === 'full' && yLabel && (
        <text x={4} y={10} fontSize="9" fill="#6b7280" fontFamily="Pretendard">
          {yLabel}
        </text>
      )}
    </svg>
  );
}

type LineChartProps = {
  size?: 'full' | 'mini';
  values?: number[];
  labels?: string[];
  xLabel?: string;
  yLabel?: string;
};

const LINE_FALLBACK_VALUES = [20, 28, 35, 32, 45, 48, 52, 58, 55, 65, 70, 68, 75, 80, 76, 85, 90, 95];

export function LineChartSvg({ size = 'full', values, labels, xLabel, yLabel }: LineChartProps) {
  const pts = values && values.length > 0 ? values : LINE_FALLBACK_VALUES;
  const max = Math.max(...pts);
  const yAxisW = size === 'full' ? 36 : 0;
  const bottomPad = size === 'full' ? 28 : 12;
  const W = 300 + yAxisW;
  const H = size === 'mini' ? 80 : 140;
  const chartH = H - bottomPad;
  const pad = 12;

  const xs = pts.map((_, i) => yAxisW + pad + (i / (pts.length - 1)) * (W - yAxisW - pad * 2));
  const ys = pts.map((v) => pad + (1 - v / max) * (chartH - pad * 2));
  const line = xs.map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(' ');
  const area = `${line} L${xs[xs.length - 1].toFixed(1)},${chartH} L${xs[0].toFixed(1)},${chartH} Z`;

  const yTicks = size === 'full' ? [0, Math.round(max / 2), max] : [];

  const axisLabels = labels && labels.length > 0 ? labels : [];
  const sparseCount = Math.min(5, axisLabels.length);
  const sparseIndices = axisLabels.length > 0
    ? Array.from({ length: sparseCount }, (_, i) => Math.round((i * (pts.length - 1)) / Math.max(1, sparseCount - 1)))
    : [];

  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: size === 'mini' ? 80 : '100%' }}>
      <defs>
        <linearGradient id="lgg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#06b6d4" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#06b6d4" stopOpacity="0" />
        </linearGradient>
      </defs>
      {yTicks.map((tick) => {
        const y = pad + (1 - tick / max) * (chartH - pad * 2);
        return (
          <text key={tick} x={yAxisW - 4} y={y + 3} textAnchor="end" fontSize="8" fill="#94a3b8" fontFamily="Pretendard">
            {tick.toLocaleString()}
          </text>
        );
      })}
      <path d={area} fill="url(#lgg)" />
      <path d={line} fill="none" stroke="#06b6d4" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={xs[Math.min(7, xs.length - 1)]} cy={ys[Math.min(7, ys.length - 1)]} r="4" fill="white" stroke="#06b6d4" strokeWidth="2.5" />
      {sparseIndices.map((idx) => (
        <text key={idx} x={xs[idx]} y={chartH + 14} textAnchor="middle" fontSize="9" fill="#94a3b8" fontFamily="Pretendard">
          {axisLabels[idx]}
        </text>
      ))}
      {size === 'full' && xLabel && (
        <text x={W / 2} y={H - 2} textAnchor="middle" fontSize="9" fill="#6b7280" fontFamily="Pretendard">
          {xLabel}
        </text>
      )}
      {size === 'full' && yLabel && (
        <text x={yAxisW + 4} y={10} fontSize="9" fill="#6b7280" fontFamily="Pretendard">
          {yLabel}
        </text>
      )}
    </svg>
  );
}

type SegmentChartProps = {
  labels?: string[];
  values?: number[];
};

const SEGMENT_FALLBACK = [
  { label: 'Enterprise', pct: 58, color: '#06b6d4' },
  { label: 'Mid-market', pct: 30, color: '#386570' },
  { label: 'SMB', pct: 12, color: '#6d797d' },
];

export function SegmentChart({ labels, values }: SegmentChartProps) {
  let rows: { label: string; pct: number; color: string }[];

  if (labels && labels.length > 0 && values && values.length > 0) {
    const total = values.reduce((a, b) => a + b, 0) || 1;
    rows = labels.map((l, i) => ({
      label: l,
      pct: Math.round((values[i] / total) * 100),
      color: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
    }));
  } else {
    rows = SEGMENT_FALLBACK;
  }

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

export function KpiCard({ value, label }: { value?: number; label?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 8 }}>
      <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 36, fontWeight: 700, color: '#06b6d4' }}>
        {value?.toLocaleString() ?? '-'}
      </span>
      {label && (
        <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#94a3b8' }}>
          {label}
        </span>
      )}
    </div>
  );
}

export function TableWidget({ data }: { data: Record<string, unknown> }) {
  const columns = Array.isArray(data.columns) ? (data.columns as string[]) : [];
  const rows = Array.isArray(data.rows) ? (data.rows as Record<string, unknown>[]) : [];
  const labels = Array.isArray(data.labels) ? (data.labels as string[]) : [];
  const values = Array.isArray(data.values) ? (data.values as number[]) : [];

  if (columns.length > 0 && rows.length > 0) {
    return (
      <div style={{ overflowX: 'auto', fontSize: 12, fontFamily: 'Pretendard,sans-serif' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              {columns.map((col) => (
                <th key={col} style={{ textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid #e2e8f0', color: '#64748b', fontWeight: 500 }}>
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, 10).map((row, i) => (
              <tr key={i}>
                {columns.map((col) => (
                  <td key={col} style={{ padding: '5px 8px', borderBottom: '1px solid #f1f5f9', color: '#1d1a24' }}>
                    {String(row[col] ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {labels.slice(0, 6).map((l, i) => (
        <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 8px', background: i === 0 ? 'rgba(6,182,212,0.05)' : undefined, borderRadius: 4 }}>
          <span style={{ fontSize: 12, color: '#64748b', fontFamily: 'Pretendard' }}>{l}</span>
          <span style={{ fontSize: 12, fontWeight: 600, color: '#1d1a24', fontFamily: 'Pretendard' }}>{values[i]?.toLocaleString()}</span>
        </div>
      ))}
    </div>
  );
}
