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

export interface NextActionDetail extends NextAction {
  /** 백엔드 sources(Map) 를 평탄화한 근거 데이터 */
  basisData?: { type: 'NEWS' | 'DART' | 'CRM'; content: string }[];
  /** 백엔드 recommendedScript */
  aiComment?: string;
  /** 백엔드 미제공 (구 UI 호환용) */
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

// sources 평탄화 — {type, content}[]
//   - SourceItem 객체 { title, summary, url } 이면 summary || title 추출 (이전: String(obj) → "[object Object]")
//   - 문자열이면 그대로
//   - { type, content } 형태도 지원
function parseSources(sources: unknown): { type: 'NEWS' | 'DART' | 'CRM'; content: string }[] {
  const allowed: Array<'NEWS' | 'DART' | 'CRM'> = ['NEWS', 'DART', 'CRM'];
  const normType = (s: string): 'NEWS' | 'DART' | 'CRM' => {
    const u = s.toUpperCase();
    return (allowed as string[]).includes(u) ? (u as 'NEWS' | 'DART' | 'CRM') : 'CRM';
  };
  // 단일 source 객체/문자열을 표시용 content 문자열로 변환
  const toContent = (c: unknown): string | null => {
    if (c == null) return null;
    if (typeof c === 'string') return c.trim() || null;
    if (typeof c === 'object') {
      const item = c as { title?: unknown; summary?: unknown; content?: unknown };
      const candidates = [item.summary, item.title, item.content];
      for (const v of candidates) {
        if (typeof v === 'string' && v.trim()) return v.trim();
      }
      return null;
    }
    return String(c);
  };
  if (!sources) return [];
  // 배열 형태: [{ type, content }]
  if (Array.isArray(sources)) {
    return sources
      .filter((x): x is { type: string; content: unknown } => !!x && typeof x === 'object' && 'type' in x)
      .map((x) => ({ type: normType(String(x.type)), content: toContent(x.content) ?? '' }))
      .filter((x) => x.content !== '');
  }
  // 객체 형태: { news: [SourceItem...], dart: [...], crm: [...] } (소문자 키, 명세)
  //          또는  { NEWS: ["..."], DART: [...] } (구버전)
  if (typeof sources === 'object') {
    const out: { type: 'NEWS' | 'DART' | 'CRM'; content: string }[] = [];
    for (const [k, v] of Object.entries(sources as Record<string, unknown>)) {
      const t = normType(k);
      if (Array.isArray(v)) {
        for (const c of v) {
          const text = toContent(c);
          if (text) out.push({ type: t, content: text });
        }
      } else {
        const text = toContent(v);
        if (text) out.push({ type: t, content: text });
      }
    }
    return out;
  }
  return [];
}

function mapDetail(r: RawNextActionDetail): NextActionDetail {
  return {
    ...mapList(r),
    basisData: parseSources(r.sources),
    aiComment: r.recommendedScript,
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
