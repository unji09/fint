'use client';

import { useCallback, useEffect, useState } from 'react';
import { fetchWithAuth } from '@/hooks/useAuth';

// ─── 타입 ────────────────────────────────────────────────────────────────────

export interface NextAction {
  suggestionId: number;
  title: string;
  category: string;
  successRate: number;
}

export interface NextActionDetail extends NextAction {
  basisData?: { type: 'NEWS' | 'DART' | 'CRM'; content: string }[];
  aiComment?: string;
  warning?: string;
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
      setActions(json.data ?? []);
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
    return json.data;
  } catch (err) {
    console.error('[fetchNextActionDetail]', err);
    return null;
  }
}
