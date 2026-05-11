'use client';

import { useCallback, useEffect, useState } from 'react';
import { fetchWithAuth } from '@/hooks/useAuth';

// ─── 타입 ────────────────────────────────────────────────────────────────────

export type ActivityType = 'CALL' | 'EMAIL' | 'MEETING' | 'NOTE';

export interface ActivityListItem {
  activityId: number;
  type: ActivityType;
  title: string;
  startAt: string;
  endAt: string;
  dealId: number | null;
  dealTitle: string | null;
  accountName: string | null;
}

export interface ActivityDetail {
  activityId: number;
  type: ActivityType;
  title: string;
  startAt: string;
  endAt: string;
  memo: string | null;
  dealId: number | null;
  dealTitle: string | null;
  accountId: number | null;
  accountName: string | null;
  attendees: { internal: string[]; external: string[] } | null;
  pipelineStage: { stageId: number; stageName: string; stageCode: string } | null;
}

export interface ActivityCreateRequest {
  dealId?: number;
  type: string;
  title: string;
  startAt: string;
  endAt: string;
  attendees?: { key: string }[];
  pipelineStageId?: number;
  memo?: string;
}

export interface ActivityUpdateRequest {
  type?: string;
  title?: string;
  startAt?: string;
  endAt?: string;
  memo?: string;
  dealId?: number;
}

export interface ActivityListFilter {
  accountId?: number;
  dealId?: number;
  type?: ActivityType;
  page?: number;
  size?: number;
}

// ─── 활동 목록 조회 ──────────────────────────────────────────────────────────

export function useActivityList(filter?: ActivityListFilter) {
  const [activities, setActivities] = useState<ActivityListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetch_ = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (filter?.accountId) params.set('accountId', String(filter.accountId));
      if (filter?.dealId) params.set('dealId', String(filter.dealId));
      if (filter?.type) params.set('type', filter.type);
      if (filter?.page !== undefined) params.set('page', String(filter.page));
      params.set('size', String(filter?.size ?? 20));

      const qs = params.toString();
      const res = await fetchWithAuth(`/activities${qs ? `?${qs}` : ''}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setActivities(json.data?.content ?? json.data ?? []);
    } catch {
      setError('활동 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [filter?.accountId, filter?.dealId, filter?.type, filter?.page, filter?.size]);

  useEffect(() => {
    fetch_();
  }, [fetch_]);

  return { activities, loading, error, refetch: fetch_ };
}

// ─── 활동 상세 조회 ──────────────────────────────────────────────────────────

export function useActivityDetail(activityId: number | null) {
  const [activity, setActivity] = useState<ActivityDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch_ = useCallback(async () => {
    if (!activityId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetchWithAuth(`/activities/${activityId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setActivity(json.data);
    } catch {
      setError('활동 상세를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [activityId]);

  useEffect(() => {
    fetch_();
  }, [fetch_]);

  return { activity, loading, error, refetch: fetch_ };
}

// ─── 활동 생성 ───────────────────────────────────────────────────────────────

export function useCreateActivity() {
  const [loading, setLoading] = useState(false);

  const create = useCallback(async (req: ActivityCreateRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth('/activities', {
        method: 'POST',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      return json.data as { activityId: number };
    } finally {
      setLoading(false);
    }
  }, []);

  return { create, loading };
}

// ─── 활동 수정 ───────────────────────────────────────────────────────────────

export function useUpdateActivity() {
  const [loading, setLoading] = useState(false);

  const update = useCallback(async (activityId: number, req: ActivityUpdateRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/activities/${activityId}`, {
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

// ─── 활동 삭제 ───────────────────────────────────────────────────────────────

export function useDeleteActivity() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (activityId: number) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/activities/${activityId}`, {
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
