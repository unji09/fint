'use client';

import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  ArcElement,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Bar, Line, Doughnut, Pie } from 'react-chartjs-2';
import { buildChartJsConfig } from './buildChartJsConfig';
import { BarChartSvg, LineChartSvg, SegmentChart, KpiCard, TableWidget, COLUMN_KO } from './ChartWidgets';

ChartJS.register(
  CategoryScale, LinearScale, BarElement, LineElement,
  PointElement, ArcElement, Tooltip, Legend, Filler,
);

// 날씨(감정) 5단계 레이블
const MOOD_LABELS: Record<string, string> = {
  RAINBOW: '무지개 🌈',
  SUNNY: '맑음 ☀️',
  CLOUDY: '흐림 ☁️',
  RAINY: '비 🌧️',
  THUNDER: '천둥 ⛈️',
};

// 숫자 포맷: 정수/소수 자동 판별, KPI용 큰 수 한국어 단위
export function formatNumber(v: number, useKoreanUnit = false): string {
  if (useKoreanUnit) {
    if (Math.abs(v) >= 1_0000_0000) return `${parseFloat((v / 1_0000_0000).toFixed(1))}억`;
    if (Math.abs(v) >= 1_0000) return `${parseFloat((v / 1_0000).toFixed(1))}만`;
  }
  if (Number.isInteger(v)) return v.toLocaleString('ko-KR');
  return parseFloat(v.toFixed(2)).toLocaleString('ko-KR');
}

// 날짜/시간 포맷
function formatDatetime(val: string, dateOnly = false): string {
  const d = new Date(val);
  if (isNaN(d.getTime())) return val;
  if (dateOnly) {
    return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
  }
  return d.toLocaleString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
  });
}

interface WidgetRendererProps {
  widgetType: string;
  config: Record<string, unknown>;
  data: Record<string, unknown>[] | null;
  result: { data: unknown; insightText: string } | null;
}

export default function WidgetRenderer({ widgetType, config, data, result }: WidgetRendererProps) {
  const resultData = result?.data as Record<string, unknown> | unknown[] | null | undefined;
  const extractedRows = Array.isArray(resultData)
    ? (resultData as Record<string, unknown>[])
    : (resultData && typeof resultData === 'object' && Array.isArray((resultData as Record<string, unknown>).rows))
      ? ((resultData as Record<string, unknown>).rows as Record<string, unknown>[])
      : null;
  const resolvedData = (data && data.length > 0) ? data : extractedRows;

  // config.chart 존재 → Chart.js (프리셋 + AI 공통)
  if (config.chart && resolvedData && resolvedData.length > 0) {
    return <PresetChartRenderer config={config} data={resolvedData} />;
  }

  // config.columns 존재 → 테이블 렌더링 (프리셋 + AI 공통)
  if (config.columns && resolvedData && resolvedData.length > 0) {
    return <PresetTableRenderer config={config} data={resolvedData} />;
  }

  // 데이터가 빈 배열인 경우 → 빈 상태 메시지 (진짜 빈 데이터)
  if (resolvedData !== null && resolvedData !== undefined && resolvedData.length === 0) {
    const display = (config.display ?? {}) as { emptyMessage?: string };
    return <EmptyState message={display.emptyMessage} />;
  }

  // config 없지만 rows 데이터 있음 → 키 기반 자동 테이블 폴백
  if (resolvedData && resolvedData.length > 0) {
    return <AutoTableFallback data={resolvedData} />;
  }

  // config.chart/columns 없는 레거시 AI 위젯 → SVG 폴백 (values/labels 구조)
  return <LegacyRenderer widgetType={widgetType} config={config} result={result} />;
}

function EmptyState({ message }: { message?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', width: '100%', height: '100%', gap: 8, color: '#94a3b8' }}>
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
        <rect x="4" y="8" width="24" height="18" rx="3" stroke="#cbd5e1" strokeWidth="1.5" fill="none" />
        <path d="M4 13h24" stroke="#cbd5e1" strokeWidth="1.5" />
        <circle cx="10" cy="10.5" r="1" fill="#cbd5e1" />
        <circle cx="14" cy="10.5" r="1" fill="#cbd5e1" />
        <circle cx="18" cy="10.5" r="1" fill="#cbd5e1" />
      </svg>
      <span style={{ fontSize: 13, fontFamily: 'Pretendard,sans-serif', textAlign: 'center', lineHeight: 1.5 }}>
        {message ?? '자료 없음'}
      </span>
    </div>
  );
}

function AutoTableFallback({ data }: { data: Record<string, unknown>[] }) {
  const keys = Object.keys(data[0] ?? {});
  const [colWidths, setColWidths] = React.useState<Record<string, number>>({});
  const resizingRef = React.useRef<{ field: string; startX: number; startW: number } | null>(null);

  const handleResizeStart = (e: React.MouseEvent, field: string, currentW: number) => {
    e.preventDefault();
    e.stopPropagation();
    resizingRef.current = { field, startX: e.clientX, startW: currentW };
    const onMove = (ev: MouseEvent) => {
      if (!resizingRef.current) return;
      const newW = Math.max(60, resizingRef.current.startW + ev.clientX - resizingRef.current.startX);
      setColWidths((prev) => ({ ...prev, [resizingRef.current!.field]: newW }));
    };
    const onUp = () => { resizingRef.current = null; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  return (
    <div style={{ width: '100%', height: '100%', overflowX: 'auto', overflowY: 'auto', fontSize: 12, fontFamily: 'Pretendard,sans-serif' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed' }}>
        <thead>
          <tr>
            {keys.map((k) => {
              const w = colWidths[k];
              return (
                <th key={k} style={{ textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid #e2e8f0', color: '#64748b', fontWeight: 500, whiteSpace: 'nowrap', position: 'sticky', top: 0, background: 'white', width: w, userSelect: 'none' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>{COLUMN_KO[k] ?? k}</span>
                    <div
                      onMouseDown={(e) => handleResizeStart(e, k, w ?? (e.currentTarget.closest('th')?.offsetWidth ?? 100))}
                      style={{ width: 4, height: 16, background: 'rgba(100,116,139,0.25)', borderRadius: 2, cursor: 'col-resize', flexShrink: 0 }}
                    />
                  </div>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {data.map((row, i) => (
            <tr key={i} style={{ background: i % 2 === 0 ? 'white' : 'rgba(248,250,252,0.8)' }}>
              {keys.map((k) => (
                <td key={k} style={{ padding: '5px 8px', borderBottom: '1px solid #f1f5f9', color: '#1d1a24', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {formatCellValue(row[k], { label: COLUMN_KO[k] ?? k, field: k })}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PresetChartRenderer({ config, data }: { config: Record<string, unknown>; data: Record<string, unknown>[] }) {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [fontSize, setFontSize] = React.useState(11);

  React.useEffect(() => {
    const el = containerRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(([entry]) => {
      const w = entry.contentRect.width;
      setFontSize(Math.round(Math.max(9, Math.min(16, 10 + (w - 200) / 80))));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const chartConfig = buildChartJsConfig(config, data, fontSize);
  if (!chartConfig) return null;

  const { type, data: chartData, options } = chartConfig;

  /* eslint-disable @typescript-eslint/no-explicit-any */
  const cd = chartData as any;
  const op = options as any;
  return (
    <div ref={containerRef} style={{ width: '100%', height: '100%', position: 'relative' }}>
      {type === 'bar' && <Bar data={cd} options={op} />}
      {type === 'line' && <Line data={cd} options={op} />}
      {type === 'doughnut' && <Doughnut data={cd} options={op} />}
      {type === 'pie' && <Pie data={cd} options={op} />}
    </div>
  );
}

interface ColumnDef {
  label: string;
  field: string;
  format?: string;
  unit?: string;
  koreanUnit?: boolean;
}

export function formatCellValue(val: unknown, col: ColumnDef): string {
  if (val == null) return '-';
  // Mood enum auto-detect — must run before numeric format checks (Number("RAINBOW") = NaN)
  const rawStr = String(val);
  if (MOOD_LABELS[rawStr]) return MOOD_LABELS[rawStr];
  if (col.format === 'currency') {
    const n = Number(val);
    return `${formatNumber(n, col.koreanUnit)}${col.unit ? ` ${col.unit}` : ''}`;
  }
  if (col.format === 'number') return formatNumber(Number(val));
  if (col.format === 'date') return formatDatetime(String(val), true);
  if (col.format === 'datetime') return formatDatetime(String(val), false);
  if (col.format === 'mood') return MOOD_LABELS[rawStr] ?? rawStr;
  // ISO 날짜 문자열 자동 감지 (created_at 등 raw ISO)
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(rawStr)) return formatDatetime(rawStr, false);
  return rawStr;
}

function PresetTableRenderer({ config, data }: { config: Record<string, unknown>; data: Record<string, unknown>[] }) {
  const rawColumns = (config.columns ?? []) as ColumnDef[];
  const columns = rawColumns.filter((col, idx) => rawColumns.findIndex((c) => c.field === col.field) === idx);
  const [colWidths, setColWidths] = React.useState<Record<string, number>>({});
  const resizingRef = React.useRef<{ field: string; startX: number; startW: number } | null>(null);
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [fontSize, setFontSize] = React.useState(12);

  React.useEffect(() => {
    const el = containerRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(([entry]) => {
      const w = entry.contentRect.width;
      // 280px 기준 12px, 60px 당 1px 증가, 11~20px 범위
      setFontSize(Math.round(Math.max(11, Math.min(20, 12 + (w - 280) / 60))));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const handleResizeStart = (e: React.MouseEvent, field: string, currentW: number) => {
    e.preventDefault();
    e.stopPropagation();
    resizingRef.current = { field, startX: e.clientX, startW: currentW };
    const onMove = (ev: MouseEvent) => {
      if (!resizingRef.current) return;
      const delta = ev.clientX - resizingRef.current.startX;
      const newW = Math.max(60, resizingRef.current.startW + delta);
      setColWidths((prev) => ({ ...prev, [resizingRef.current!.field]: newW }));
    };
    const onUp = () => {
      resizingRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  return (
    <div ref={containerRef} style={{ width: '100%', height: '100%', overflowX: 'auto', overflowY: 'auto', fontSize, fontFamily: 'Pretendard,sans-serif' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed' }}>
        <thead>
          <tr>
            {columns.map((col) => {
              const w = colWidths[col.field];
              return (
                <th key={col.field} style={{ textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid #e2e8f0', color: '#64748b', fontWeight: 500, whiteSpace: 'nowrap', position: 'sticky', top: 0, background: 'white', width: w, userSelect: 'none' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>{COLUMN_KO[col.label] ?? COLUMN_KO[col.field] ?? col.label}</span>
                    <div
                      onMouseDown={(e) => handleResizeStart(e, col.field, w ?? (e.currentTarget.closest('th')?.offsetWidth ?? 100))}
                      style={{ width: 4, height: 16, background: 'rgba(100,116,139,0.25)', borderRadius: 2, cursor: 'col-resize', flexShrink: 0 }}
                    />
                  </div>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {data.map((row, i) => (
            <tr key={i} style={{ background: i % 2 === 0 ? 'white' : 'rgba(248,250,252,0.8)' }}>
              {columns.map((col) => (
                <td key={col.field} style={{ padding: '5px 8px', borderBottom: '1px solid #f1f5f9', color: '#1d1a24', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {formatCellValue(row[col.field], col)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LegacyRenderer({ widgetType, config, result }: { widgetType: string; config: Record<string, unknown>; result: { data: unknown; insightText: string } | null }) {
  const data = (result?.data as Record<string, unknown> | undefined) ?? {};
  const labels = Array.isArray(data.labels) ? (data.labels as string[]) : undefined;
  const values = Array.isArray(data.values) ? (data.values as number[]) : undefined;
  const cfg = config ?? {};
  const xLabel = cfg.x_label as string | undefined;
  const yLabel = cfg.y_label as string | undefined;

  // 실제 데이터가 없는 경우 빈 상태 표시
  const hasData = (labels && labels.length > 0) || (values && values.length > 0) ||
    (Array.isArray(data.rows) && (data.rows as unknown[]).length > 0) ||
    (Array.isArray(data.columns) && (data.columns as unknown[]).length > 0);

  if (!hasData) return <EmptyState />;

  // 차트 계열은 values가 실제로 있을 때만 렌더. 없으면 더미 데이터 대신 빈 상태 표시.
  if (widgetType === 'LINE_CHART') {
    if (!values || values.length === 0) return <EmptyState />;
    return <LineChartSvg values={values} labels={labels} xLabel={xLabel} yLabel={yLabel} />;
  }
  if (widgetType === 'PIE' || widgetType === 'SEGMENT') return <SegmentChart labels={labels} values={values} />;
  if (widgetType === 'KPI') {
    const kpiVal = values?.[0] ?? (typeof data.value === 'number' ? data.value : undefined);
    return <KpiCard value={kpiVal} label={labels?.[0]} />;
  }
  if (widgetType === 'TABLE') return <TableWidget data={data} />;
  if (widgetType === 'NL_QUERY' || widgetType === 'BAR_CHART') {
    if (!values || values.length === 0) return <EmptyState />;
    return <BarChartSvg values={values} labels={labels} xLabel={xLabel} yLabel={yLabel} />;
  }

  // 나머지 widgetType — values 없으면 EmptyState
  if (!values || values.length === 0) return <EmptyState />;
  return <BarChartSvg values={values} labels={labels} xLabel={xLabel} yLabel={yLabel} />;
}
