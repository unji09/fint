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

// sources 가 어떤 형태로 와도(객체 또는 배열) {type, content}[] 로 평탄화
function parseSources(sources: unknown): { type: 'NEWS' | 'DART' | 'CRM'; content: string }[] {
  const allowed: Array<'NEWS' | 'DART' | 'CRM'> = ['NEWS', 'DART', 'CRM'];
  const normType = (s: string): 'NEWS' | 'DART' | 'CRM' => {
    const u = s.toUpperCase();
    return (allowed as string[]).includes(u) ? (u as 'NEWS' | 'DART' | 'CRM') : 'CRM';
  };
  if (!sources) return [];
  // 배열 형태: [{ type, content }]
  if (Array.isArray(sources)) {
    return sources
      .filter((x): x is { type: string; content: string } => !!x && typeof x === 'object' && 'type' in x && 'content' in x)
      .map((x) => ({ type: normType(String(x.type)), content: String(x.content) }));
  }
  // 객체 형태: { NEWS: ["..."] | "...", DART: [...], CRM: [...] }
  if (typeof sources === 'object') {
    const out: { type: 'NEWS' | 'DART' | 'CRM'; content: string }[] = [];
    for (const [k, v] of Object.entries(sources as Record<string, unknown>)) {
      const t = normType(k);
      if (Array.isArray(v)) {
        for (const c of v) out.push({ type: t, content: String(c) });
      } else if (v != null) {
        out.push({ type: t, content: String(v) });
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
