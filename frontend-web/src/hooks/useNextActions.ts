'use client';

import { useCallback, useEffect, useState } from 'react';
import { fetchWithAuth } from '@/hooks/useAuth';

// ─── 타입 ────────────────────────────────────────────────────────────────────

export interface NextAction {
  suggestionId: number;
  title: string;
  category: string;
  /** 0-100. 백엔드 successProbability 매핑. UI 호환 위해 이름 유지. */
  successRate: number;
  /** 중요도 점수 (정렬용). 백엔드 importanceScore. */
  importanceScore?: number;
}

// API 명세 SourceItem
export interface NextActionBasis {
  type: 'NEWS' | 'DART' | 'CRM';
  title?: string;
  summary?: string;
  url?: string;
  /** 호환 폴백 — title/summary 없으면 사용 */
  content?: string;
}

export interface NextActionDetail extends NextAction {
  /** 백엔드 sources(Map<news|dart|crm, SourceItem[]>) 를 평탄화 */
  basisData?: NextActionBasis[];
  /** 백엔드 recommendedScript */
  aiComment?: string;
  /** 백엔드 caution — 주의사항 */
  warning?: string;
}

// ─── 백엔드 응답 → 프론트 타입 매핑 ─────────────────────────────────────────

interface RawNextActionList {
  suggestionId: number;
  title: string;
  category: string;
  successProbability?: number;
  importanceScore?: number;
}

interface RawNextActionDetail extends RawNextActionList {
  sources?: unknown;
  recommendedScript?: string;
  /** API 명세 data.caution — 주의사항 */
  caution?: string;
}

function mapList(r: RawNextActionList): NextAction {
  return {
    suggestionId: r.suggestionId,
    title: r.title,
    category: r.category,
    successRate: r.successProbability ?? 0,
    importanceScore: r.importanceScore,
  };
}

// 명세 sources = { news: SourceItem[], dart: SourceItem[], crm: SourceItem[] }
// SourceItem = { title?, summary?, url? } — 객체를 그대로 보존해 title/summary/url 모두 노출.
function parseSources(sources: unknown): NextActionBasis[] {
  const allowed: Array<'NEWS' | 'DART' | 'CRM'> = ['NEWS', 'DART', 'CRM'];
  const normType = (s: string): 'NEWS' | 'DART' | 'CRM' => {
    const u = s.toUpperCase();
    return (allowed as string[]).includes(u) ? (u as 'NEWS' | 'DART' | 'CRM') : 'CRM';
  };
  const pickStr = (v: unknown): string | undefined => (typeof v === 'string' && v.trim() ? v.trim() : undefined);
  // 단일 source(객체 또는 문자열) → NextActionBasis (type 제외)
  const toBasis = (c: unknown): Omit<NextActionBasis, 'type'> | null => {
    if (c == null) return null;
    if (typeof c === 'string') {
      const s = c.trim();
      return s ? { content: s } : null;
    }
    if (typeof c === 'object') {
      const item = c as Record<string, unknown>;
      const title = pickStr(item.title);
      const summary = pickStr(item.summary);
      const url = pickStr(item.url);
      const content = pickStr(item.content);
      if (!title && !summary && !url && !content) return null;
      return { title, summary, url, content };
    }
    return null;
  };
  if (!sources) return [];
  // 배열 형태: [{ type, ...SourceItem }]
  if (Array.isArray(sources)) {
    const out: NextActionBasis[] = [];
    for (const x of sources) {
      if (!x || typeof x !== 'object') continue;
      const obj = x as Record<string, unknown>;
      if (!obj.type) continue;
      const b = toBasis(obj);
      if (b) out.push({ type: normType(String(obj.type)), ...b });
    }
    return out;
  }
  // 객체 형태: { news: [SourceItem...], dart: [...], crm: [...] }
  if (typeof sources === 'object') {
    const out: NextActionBasis[] = [];
    for (const [k, v] of Object.entries(sources as Record<string, unknown>)) {
      const t = normType(k);
      const arr = Array.isArray(v) ? v : v != null ? [v] : [];
      for (const c of arr) {
        const b = toBasis(c);
        if (b) out.push({ type: t, ...b });
      }
    }
    return out;
  }
  return [];
}

// dedup — 같은 (type + url) 또는 (type + title + summary) 기준
function dedupBasisData(arr: NextActionBasis[]) {
  const seen = new Set<string>();
  const out: NextActionBasis[] = [];
  for (const item of arr) {
    const key = item.url
      ? `${item.type}::url::${item.url}`
      : `${item.type}::${item.title ?? ''}::${item.summary ?? ''}::${item.content ?? ''}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

function mapDetail(r: RawNextActionDetail): NextActionDetail {
  return {
    ...mapList(r),
    basisData: dedupBasisData(parseSources(r.sources)),
    aiComment: r.recommendedScript,
    warning: r.caution,
  };
}

// ─── AI 추천 전략 목록 ──────────────────────────────────────────────────────

export function useNextActions(accountId: string | number | null) {
  const [actions, setActions] = useState<NextAction[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch_ = useCallback(async () => {
    if (!accountId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetchWithAuth(`/accounts/${accountId}/ai/next-actions`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      const raw = (json.data ?? []) as RawNextActionList[];
      setActions(raw.map(mapList));
    } catch {
      setError('AI 추천 전략을 불러오지 못했습니다.');
      setActions([]);
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    fetch_();
  }, [fetch_]);

  return { actions, loading, error, refetch: fetch_ };
}

// ─── AI 추천 전략 상세 ──────────────────────────────────────────────────────

export async function fetchNextActionDetail(
  accountId: string | number,
  suggestionId: number,
): Promise<NextActionDetail | null> {
  try {
    const res = await fetchWithAuth(
      `/accounts/${accountId}/ai/next-actions/${suggestionId}`,
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    if (!json.data) return null;
    return mapDetail(json.data as RawNextActionDetail);
  } catch (err) {
    console.error('[fetchNextActionDetail]', err);
    return null;
  }
}
