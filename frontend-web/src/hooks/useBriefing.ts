'use client';

import { useCallback, useEffect, useState } from 'react';
import { fetchWithAuth } from '@/hooks/useAuth';

export interface BriefingData {
  key_points: string[];
  alerts: string[];
}

export function useBriefing(activityId: string | null) {
  const [briefing, setBriefing] = useState<BriefingData | null>(null);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // activityId 변경 시 상태 초기화
  useEffect(() => {
    setBriefing(null);
    setLoaded(false);
    setError(null);
  }, [activityId]);

  // GET /activities/{id} → data.briefing 추출
  const load = useCallback(async () => {
    if (!activityId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetchWithAuth(`/activities/${activityId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const j = await res.json();
      const data = j?.data ?? j;
      const b = data?.briefing;
      if (b && typeof b === 'object' && (Array.isArray(b.key_points) || Array.isArray(b.alerts))) {
        setBriefing({
          key_points: Array.isArray(b.key_points) ? b.key_points : [],
          alerts: Array.isArray(b.alerts) ? b.alerts : [],
        });
      } else {
        setBriefing(null);
      }
    } catch {
      setError('브리핑을 불러오지 못했어요.');
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  }, [activityId]);

  // POST /ai/briefing?activityId={id}
  const generate = useCallback(async () => {
    if (!activityId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetchWithAuth(`/ai/briefing?activityId=${activityId}`, { method: 'POST' });
      if (!res.ok) {
        const j = await res.json().catch(() => ({}));
        const code = j?.code ?? j?.errorCode ?? j?.data?.code;
        const msg = code === 'AC305'
          ? '미팅 유형의 일정에서만 브리핑을 생성할 수 있어요.'
          : code === 'AC306'
          ? '딜이 연결된 일정에서만 브리핑을 생성할 수 있어요.'
          : '브리핑 생성에 실패했어요. 잠시 후 다시 시도해 주세요.';
        setError(msg);
        return;
      }
      const j = await res.json();
      const data = j?.data ?? j;
      setBriefing({
        key_points: Array.isArray(data?.key_points) ? data.key_points : [],
        alerts: Array.isArray(data?.alerts) ? data.alerts : [],
      });
    } catch {
      setError('브리핑 생성에 실패했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  }, [activityId]);

  return { briefing, loading, loaded, error, load, generate };
}
