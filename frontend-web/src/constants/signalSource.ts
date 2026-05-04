/**
 * 시그널 소스 — DART / NEWS / LOG / AI.
 * 컬러: --signal-* (tokens.css)
 */

export type SignalSourceCode = 'DART' | 'NEWS' | 'LOG' | 'AI';

export interface SignalSourceMeta {
  code: SignalSourceCode;
  label: string;
  colorVar: string;
}

export const SIGNAL_SOURCES: readonly SignalSourceMeta[] = [
  { code: 'DART', label: 'DART', colorVar: '--signal-dart' },
  { code: 'NEWS', label: 'NEWS', colorVar: '--signal-news' },
  { code: 'LOG', label: 'LOG', colorVar: '--signal-log' },
  { code: 'AI', label: 'AI 분석', colorVar: '--signal-ai' },
] as const;

const BY_CODE = new Map<SignalSourceCode, SignalSourceMeta>(
  SIGNAL_SOURCES.map((s) => [s.code, s]),
);

export function getSignalSource(code: SignalSourceCode): SignalSourceMeta {
  const s = BY_CODE.get(code);
  if (!s) throw new Error(`Unknown signal source: ${code}`);
  return s;
}
