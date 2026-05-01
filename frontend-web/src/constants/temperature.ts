/**
 * 영업 온도 — HOT / WARM / COOL.
 * 컬러: --temp-* (tokens.css)
 */

export type TemperatureCode = 'HOT' | 'WARM' | 'COOL';

export interface TemperatureMeta {
  code: TemperatureCode;
  label: string;
  colorVar: string;
}

export const TEMPERATURES: readonly TemperatureMeta[] = [
  { code: 'HOT', label: 'HOT', colorVar: '--temp-hot' },
  { code: 'WARM', label: 'WARM', colorVar: '--temp-warm' },
  { code: 'COOL', label: 'COOL', colorVar: '--temp-cool' },
] as const;

const BY_CODE = new Map<TemperatureCode, TemperatureMeta>(
  TEMPERATURES.map((t) => [t.code, t]),
);

export function getTemperature(code: TemperatureCode): TemperatureMeta {
  const t = BY_CODE.get(code);
  if (!t) throw new Error(`Unknown temperature: ${code}`);
  return t;
}
