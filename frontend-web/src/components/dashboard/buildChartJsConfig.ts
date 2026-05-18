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
