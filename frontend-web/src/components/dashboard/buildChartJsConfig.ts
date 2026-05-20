import type { ChartData, ChartOptions, ChartType } from 'chart.js';

function formatKoreanNumber(v: number): string {
  if (Math.abs(v) >= 1_0000_0000) return `${parseFloat((v / 1_0000_0000).toFixed(1))}억`;
  if (Math.abs(v) >= 1_0000) return `${parseFloat((v / 1_0000).toFixed(1))}만`;
  if (Number.isInteger(v)) return v.toLocaleString('ko-KR');
  return parseFloat(v.toFixed(2)).toLocaleString('ko-KR');
}

interface DatasetConfig {
  label: string;
  valueField: string;
}

interface PresetChartConfig {
  chart?: { type?: string };
  data?: { labelsField?: string; datasets?: DatasetConfig[] };
  options?: {
    xAxis?: { label?: string };
    yAxis?: { label?: string; unit?: string };
    legend?: boolean;
  };
  display?: { format?: string; emptyMessage?: string; koreanUnit?: boolean };
}

// 0-1 scale: preset widgets (CASE WHEN mood → 0/0.25/0.5/0.75/1.0)
const MOOD_TICK_LABELS_01: Record<number, string> = {
  0: '⛈️ 천둥',
  0.25: '🌧️ 비',
  0.5: '☁️ 흐림',
  0.75: '☀️ 맑음',
  1: '🌈 무지개',
};

// 0-100 scale: AI-generated widgets using AVG(mood_score)
function moodLabel100(v: number): string {
  if (v >= 80) return '🌈 무지개';
  if (v >= 60) return '☀️ 맑음';
  if (v >= 40) return '☁️ 흐림';
  if (v >= 20) return '🌧️ 비';
  return '⛈️ 천둥';
}

const COLORS = [
  '#06b6d4', '#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b',
  '#10b981', '#ef4444', '#6366f1',
];

export function buildChartJsConfig(
  config: Record<string, unknown>,
  data: Record<string, unknown>[] | null,
  fontSize = 11,
): { type: ChartType; data: ChartData; options: ChartOptions } | null {
  const cfg = config as unknown as PresetChartConfig;
  if (!cfg.chart?.type || !cfg.data?.labelsField || !data || data.length === 0) {
    return null;
  }

  const chartType = mapChartType(cfg.chart.type);
  const labelsField = cfg.data.labelsField;
  const datasets = cfg.data.datasets ?? [];

  const resolved = needsPivot(data, datasets)
    ? pivotRows(data, labelsField, datasets)
    : data;

  const labels = resolved.map((row) => String(row[labelsField] ?? ''));
  const isPie = chartType === 'doughnut' || chartType === 'pie';
  const chartDatasets = datasets.map((ds, i) => {
    const values = resolved.map((row) => Number(row[ds.valueField] ?? 0));
    return {
      label: ds.label,
      data: values,
      backgroundColor: isPie
        ? values.map((_, j) => COLORS[j % COLORS.length])
        : chartType === 'line'
          ? 'transparent'
          : COLORS[i % COLORS.length],
      borderColor: isPie
        ? values.map((_, j) => COLORS[j % COLORS.length])
        : COLORS[i % COLORS.length],
      borderWidth: chartType === 'line' ? 2.5 : isPie ? 1 : 0,
      tension: 0.3,
      fill: chartType === 'line',
      pointRadius: chartType === 'line' ? 3 : undefined,
    };
  });

  // Detect mood y-axis and scale (0-1 preset vs 0-100 AI-generated)
  const yUnit = cfg.options?.yAxis?.unit ?? '';
  const yLabel = cfg.options?.yAxis?.label ?? '';
  const isMood = yUnit === 'mood' || /무드|감정|mood/i.test(yLabel) || cfg.display?.format === 'mood';
  const moodMax = isMood
    ? Math.max(0, ...chartDatasets.flatMap((d) => d.data as number[]))
    : 0;
  const isMood100 = isMood && Number.isInteger(moodMax) && moodMax > 1; // AVG(mood_score) 0-100 scale (정수 판별로 preset 0-1 스케일과 구분)

  /* eslint-disable @typescript-eslint/no-explicit-any */
  const options: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: { bottom: 8, top: 4 },
    },
    plugins: {
      legend: {
        display: cfg.options?.legend ?? !isPie ? true : isPie,
        labels: { font: { size: fontSize } },
      },
    },
    ...(isPie ? {} : {
      scales: {
        x: {
          title: cfg.options?.xAxis?.label
            ? { display: true, text: cfg.options.xAxis.label, font: { size: fontSize } }
            : undefined,
          ticks: { maxRotation: 45, font: { size: fontSize } },
        },
        y: (() => {
          const titleText = yLabel
            ? `${yLabel}${yUnit && yUnit !== 'mood' ? ` (${yUnit})` : ''}`
            : undefined;
          const titleObj = titleText
            ? { display: true, text: titleText, font: { size: fontSize } }
            : undefined;

          if (isMood100) {
            // 10-90 scale: mood_score values are 10/30/50/70/90 → ticks align exactly
            return {
              title: titleObj,
              beginAtZero: false,
              min: 10, max: 90,
              ticks: {
                stepSize: 20,
                callback: (v: any) => moodLabel100(Number(v)),
                font: { size: fontSize },
              },
            };
          }
          if (isMood) {
            // 0-1 scale: preset CASE WHEN approach
            return {
              title: titleObj,
              beginAtZero: true,
              min: 0, max: 1,
              ticks: {
                stepSize: 0.25,
                callback: (v: any) => MOOD_TICK_LABELS_01[Number(v)] ?? '',
                font: { size: fontSize },
              },
            };
          }
          return {
            title: titleObj,
            beginAtZero: true,
            ticks: {
              callback: (v: any) => formatKoreanNumber(Number(v)),
              font: { size: fontSize },
            },
          };
        })(),
      },
    }),
  };

  return {
    type: chartType,
    data: { labels, datasets: chartDatasets },
    options,
  };
}

function mapChartType(type: string): ChartType {
  switch (type.toLowerCase()) {
    case 'bar': return 'bar';
    case 'line': return 'line';
    case 'pie': return 'pie';
    case 'doughnut': return 'doughnut';
    default: return 'bar';
  }
}

function needsPivot(
  data: Record<string, unknown>[],
  datasets: DatasetConfig[],
): boolean {
  if (datasets.length <= 1 || data.length === 0) return false;
  const firstRow = data[0];
  return datasets.some((ds) => !(ds.valueField in firstRow));
}

function pivotRows(
  data: Record<string, unknown>[],
  labelsField: string,
  datasets: DatasetConfig[],
): Record<string, unknown>[] {
  const valueFieldNames = new Set(datasets.map((ds) => ds.valueField));
  const otherFields = Object.keys(data[0]).filter(
    (k) => k !== labelsField && !valueFieldNames.has(k),
  );
  const categoryField = otherFields.find((f) =>
    data.some((row) => valueFieldNames.has(String(row[f]))),
  );
  if (!categoryField) return data;

  const numericField = otherFields.find((f) =>
    f !== categoryField && data.some((row) => typeof row[f] === 'number'),
  );
  if (!numericField) return data;

  const grouped = new Map<string, Record<string, unknown>>();
  for (const row of data) {
    const label = String(row[labelsField] ?? '');
    if (!grouped.has(label)) {
      const entry: Record<string, unknown> = { [labelsField]: label };
      for (const ds of datasets) entry[ds.valueField] = 0;
      grouped.set(label, entry);
    }
    const cat = String(row[categoryField] ?? '');
    if (valueFieldNames.has(cat)) {
      grouped.get(label)![cat] = Number(row[numericField] ?? 0);
    }
  }
  return Array.from(grouped.values());
}
