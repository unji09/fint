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
import { BarChartSvg, LineChartSvg, SegmentChart, KpiCard, TableWidget } from './ChartWidgets';

ChartJS.register(
  CategoryScale, LinearScale, BarElement, LineElement,
  PointElement, ArcElement, Tooltip, Legend, Filler,
);

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

  // data가 빈 배열인 경우 → 빈 상태 메시지
  if (resolvedData !== null && resolvedData.length === 0 && config.display) {
    const display = config.display as { emptyMessage?: string };
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#94a3b8', fontSize: 13, fontFamily: 'Pretendard,sans-serif' }}>
        {display.emptyMessage ?? '데이터가 없습니다'}
      </div>
    );
  }

  // config.chart/columns 없는 레거시 AI 위젯 → SVG 폴백
  return <LegacyRenderer widgetType={widgetType} config={config} result={result} />;
}

function PresetChartRenderer({ config, data }: { config: Record<string, unknown>; data: Record<string, unknown>[] }) {
  const chartConfig = buildChartJsConfig(config, data);
  if (!chartConfig) return null;

  const { type, data: chartData, options } = chartConfig;

  // chart.js 의 ChartData/ChartOptions 가 generic chart type 별로 narrow 되어 있어,
  // 동적 분기 시 'keyof ChartTypeRegistry' 와 'bar'/'line' 등이 호환되지 않음.
  // 런타임 동작은 동일하므로 react-chartjs-2 컴포넌트 prop 타입에 맞춰 캐스팅.
  /* eslint-disable @typescript-eslint/no-explicit-any */
  const cd = chartData as any;
  const op = options as any;
  return (
    <div style={{ width: '100%', height: '100%', position: 'relative' }}>
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
}

function PresetTableRenderer({ config, data }: { config: Record<string, unknown>; data: Record<string, unknown>[] }) {
  const columns = (config.columns ?? []) as ColumnDef[];

  const formatValue = (val: unknown, col: ColumnDef): string => {
    if (val == null) return '-';
    if (col.format === 'currency') {
      return `${Number(val).toLocaleString()}${col.unit ? ` ${col.unit}` : ''}`;
    }
    if (col.format === 'number') return Number(val).toLocaleString();
    if (col.format === 'date') {
      const d = new Date(String(val));
      return isNaN(d.getTime()) ? String(val) : d.toLocaleDateString('ko-KR');
    }
    return String(val);
  };

  return (
    <div style={{ overflowX: 'auto', overflowY: 'auto', fontSize: 12, fontFamily: 'Pretendard,sans-serif', height: '100%' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.field} style={{ textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid #e2e8f0', color: '#64748b', fontWeight: 500, whiteSpace: 'nowrap', position: 'sticky', top: 0, background: 'white' }}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row, i) => (
            <tr key={i}>
              {columns.map((col) => (
                <td key={col.field} style={{ padding: '5px 8px', borderBottom: '1px solid #f1f5f9', color: '#1d1a24', whiteSpace: 'nowrap' }}>
                  {formatValue(row[col.field], col)}
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

  if (widgetType === 'LINE_CHART') return <LineChartSvg values={values} labels={labels} xLabel={xLabel} yLabel={yLabel} />;
  if (widgetType === 'PIE' || widgetType === 'SEGMENT') return <SegmentChart labels={labels} values={values} />;
  if (widgetType === 'KPI') {
    const kpiVal = values?.[0] ?? (typeof data.value === 'number' ? data.value : undefined);
    return <KpiCard value={kpiVal} label={labels?.[0]} />;
  }
  if (widgetType === 'TABLE') return <TableWidget data={data} />;
  if (widgetType === 'NL_QUERY' || widgetType === 'BAR_CHART') return <BarChartSvg values={values} labels={labels} xLabel={xLabel} yLabel={yLabel} />;

  // CHART 타입이지만 data가 없는 경우 (AI 생성 위젯)
  return <BarChartSvg values={values} labels={labels} xLabel={xLabel} yLabel={yLabel} />;
}
