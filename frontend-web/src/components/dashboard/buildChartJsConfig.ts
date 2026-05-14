import type { ChartData, ChartOptions, ChartType } from 'chart.js';

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
  display?: { format?: string; emptyMessage?: string };
}

const COLORS = [
  '#06b6d4', '#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b',
  '#10b981', '#ef4444', '#6366f1',
];

export function buildChartJsConfig(
  config: Record<string, unknown>,
  data: Record<string, unknown>[] | null,
): { type: ChartType; data: ChartData; options: ChartOptions } | null {
  const cfg = config as unknown as PresetChartConfig;
  if (!cfg.chart?.type || !cfg.data?.labelsField || !data || data.length === 0) {
    return null;
  }

  const chartType = mapChartType(cfg.chart.type);
  const labelsField = cfg.data.labelsField;
  const datasets = cfg.data.datasets ?? [];

  const labels = data.map((row) => String(row[labelsField] ?? ''));
  const chartDatasets = datasets.map((ds, i) => ({
    label: ds.label,
    data: data.map((row) => Number(row[ds.valueField] ?? 0)),
    backgroundColor: chartType === 'line'
      ? 'transparent'
      : COLORS[i % COLORS.length],
    borderColor: COLORS[i % COLORS.length],
    borderWidth: chartType === 'line' ? 2.5 : 0,
    tension: 0.3,
    fill: chartType === 'line',
    pointRadius: chartType === 'line' ? 3 : undefined,
  }));

  const isPie = chartType === 'doughnut' || chartType === 'pie';

  const options: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: cfg.options?.legend ?? !isPie ? true : isPie },
    },
    ...(isPie ? {} : {
      scales: {
        x: {
          title: cfg.options?.xAxis?.label
            ? { display: true, text: cfg.options.xAxis.label }
            : undefined,
        },
        y: {
          title: cfg.options?.yAxis?.label
            ? { display: true, text: `${cfg.options.yAxis.label}${cfg.options.yAxis.unit ? ` (${cfg.options.yAxis.unit})` : ''}` }
            : undefined,
          beginAtZero: true,
        },
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
