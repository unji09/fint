'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchWithAuth } from '@/hooks/useAuth';

// ─── 타입 ────────────────────────────────────────────────────────────────────

export interface DealListItem {
  dealId: number;
  title: string;
  stage: string | null;
  probability: number | null;
  amount: number | null;
  expectedClose: string | null;
  accountName: string | null;
}

export interface DealDetail {
  dealId: number;
  title: string;
  stage: string | null;
  probability: number | null;
  amount: number | null;
  expectedClose: string | null;
  accountId: number | null;
  accountName: string | null;
  contacts: { contactId: number; name: string; title: string | null }[];
  activities: { activityId: number; type: string; title: string; startAt: string }[];
}

export interface DealCreateRequest {
  accountId: number;
  teamId?: number;
  title: string;
  expectedClose?: string;
  amount?: number;
  contacts?: { contactId: number }[];
}

export interface DealUpdateRequest {
  title?: string;
  expectedClose?: string;
  amount?: number;
  stage?: string;
}

export interface DealSearchItem {
  dealId: number;
  title: string;
  amount: number | null;
  expectedClose: string | null; // "YYYY-MM-DD"
}

// ─── 딜 목록 검색 (GET /deals) ───────────────────────────────────────────────

export function useDealSearch() {
  const [deals, setDeals] = useState<DealSearchItem[]>([]);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const search = useCallback((params: {
    keyword?: string;
    accountId?: number;
    size?: number;
  }) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const qs = new URLSearchParams();
        if (params.keyword?.trim()) qs.set('keyword', params.keyword.trim());
        if (params.accountId) qs.set('accountId', String(params.accountId));
        qs.set('size', String(params.size ?? 10));

        const res = await fetchWithAuth(`/deals?${qs.toString()}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();
        const listResponse = json.data ?? {};
        setDeals(
          (listResponse.data ?? []).map((d: {
            dealId: number; title: string;
            amount?: number | null; expectedClose?: string | null;
          }) => ({
            dealId: d.dealId,
            title: d.title,
            amount: d.amount ?? null,
            expectedClose: d.expectedClose ?? null,
          }))
        );
        setHasNext(listResponse.hasNext ?? false);
      } catch {
        setError('딜 목록을 불러오지 못했습니다.');
        setDeals([]);
      } finally {
        setLoading(false);
      }
    }, 250);
  }, []);

  return { deals, hasNext, loading, error, search };
}

// ─── 딜 상세 조회 ────────────────────────────────────────────────────────────

export function useDealDetail(dealId: number | null) {
  const [deal, setDeal] = useState<DealDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch_ = useCallback(async () => {
    if (!dealId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetchWithAuth(`/deals/${dealId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setDeal(json.data);
    } catch {
      setError('딜 상세를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [dealId]);

  useEffect(() => {
    fetch_();
  }, [fetch_]);

  return { deal, loading, error, refetch: fetch_ };
}

// ─── 딜 생성 ─────────────────────────────────────────────────────────────────

export function useCreateDeal() {
  const [loading, setLoading] = useState(false);

  const create = useCallback(async (req: DealCreateRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth('/deals', {
        method: 'POST',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      return json.data as Record<string, unknown>;
    } finally {
      setLoading(false);
    }
  }, []);

  return { create, loading };
}

// ─── 딜 수정 ─────────────────────────────────────────────────────────────────

export function useUpdateDeal() {
  const [loading, setLoading] = useState(false);

  const update = useCallback(async (dealId: number, req: DealUpdateRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/deals/${dealId}`, {
        method: 'PATCH',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      return json.data;
    } finally {
      setLoading(false);
    }
  }, []);

  return { update, loading };
}

// ─── 딜 삭제 ─────────────────────────────────────────────────────────────────

export function useDeleteDeal() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (dealId: number) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/deals/${dealId}`, {
        method: 'DELETE',
      });
      if (!res.ok && res.status !== 204) throw new Error(`HTTP ${res.status}`);
      return true;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { remove, loading };
}
