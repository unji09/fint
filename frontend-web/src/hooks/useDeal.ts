'use client';

import { useCallback, useEffect, useState } from 'react';
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
      return json.data as { dealId: number };
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
