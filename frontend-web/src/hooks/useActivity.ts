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

/** STT 진행 상태 — 명세: NONE → PENDING → PROCESSING → COMPLETED / FAILED */
export type SttStatus = 'NONE' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/** STT 전사 줄 단위 — 백엔드 응답 키는 timestamp/text 또는 ts/content 가능 */
export interface SttLine {
  timestamp?: string;
  text: string;
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
  // ── STT/AI 분석 결과 (명세: GET /activities/{id} 가 함께 포함)
  sttStatus?: SttStatus | null;
  sttTranscript?: SttLine[] | null;
  aiSummary?: Record<string, string> | null;
  recordingFileKey?: string | null;
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

// ─── 녹음 업로드 + STT 트리거 ────────────────────────────────────────────────
// 명세 흐름:
//   1) POST /files/presigned-url { fileName, contentType }
//        → { data: { url, fileKey } }
//   2) PUT {url}  (S3 직통, body=Blob, header Content-Type 매칭)
//   3) POST /activities/{activityId}/recording { fileKey }
//        → 202 Accepted (비동기 STT 시작)

export function useUploadRecording() {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = useCallback(async (activityId: number, blob: Blob, fileExt: string) => {
    setUploading(true);
    setError(null);
    try {
      // 1) Pre-signed URL 발급
      const presRes = await fetchWithAuth('/files/presigned-url', {
        method: 'POST',
        body: JSON.stringify({
          fileName: `rec_${activityId}_${Date.now()}.${fileExt}`,
          contentType: blob.type,
        }),
      });
      if (!presRes.ok) throw new Error(`presigned URL 실패 (${presRes.status})`);
      const presJson = await presRes.json();
      const pres = presJson?.data ?? presJson;
      const url: string | undefined = pres?.url;
      const fileKey: string | undefined = pres?.fileKey;
      if (!url || !fileKey) throw new Error('presigned URL 응답 형식 오류');

      // 2) S3 직통 업로드
      const s3Res = await fetch(url, {
        method: 'PUT',
        body: blob,
        headers: { 'Content-Type': blob.type },
      });
      if (!s3Res.ok) throw new Error(`S3 업로드 실패 (${s3Res.status})`);

      // 3) STT 트리거
      const recRes = await fetchWithAuth(`/activities/${activityId}/recording`, {
        method: 'POST',
        body: JSON.stringify({ fileKey }),
      });
      if (!recRes.ok && recRes.status !== 202) {
        throw new Error(`STT 요청 실패 (${recRes.status})`);
      }

      return { fileKey, ok: true } as const;
    } catch (e) {
      const msg = e instanceof Error ? e.message : '업로드 실패';
      setError(msg);
      return { fileKey: null, ok: false, error: msg } as const;
    } finally {
      setUploading(false);
    }
  }, []);

  return { upload, uploading, error };
}

// ─── STT 진행 상태 폴링 ──────────────────────────────────────────────────────
// 5초 간격으로 GET /activities/{id} 호출, COMPLETED/FAILED 도달 또는 timeout(기본 2분) 까지.

export interface SttPollResult {
  status: SttStatus;
  transcript: SttLine[];
  summary: Record<string, string> | null;
}

export function usePollSttStatus() {
  const [polling, setPolling] = useState(false);

  const poll = useCallback(
    async (
      activityId: number,
      onUpdate: (r: SttPollResult) => void,
      opts?: { intervalMs?: number; timeoutMs?: number },
    ) => {
      const interval = opts?.intervalMs ?? 5_000;
      const timeout = opts?.timeoutMs ?? 120_000;
      const start = Date.now();
      setPolling(true);
      try {
        while (Date.now() - start < timeout) {
          try {
            const r = await fetchWithAuth(`/activities/${activityId}`);
            if (r.ok) {
              const j = await r.json();
              const d = j?.data ?? j;
              const status: SttStatus = d?.sttStatus ?? 'NONE';
              const rawTr = d?.sttTranscript ?? d?.transcript ?? [];
              const transcript: SttLine[] = Array.isArray(rawTr)
                ? rawTr.map((t: { timestamp?: string; ts?: string; text?: string; content?: string }) => ({
                    timestamp: t.timestamp ?? t.ts ?? '',
                    text: t.text ?? t.content ?? '',
                  }))
                : [];
              const summary =
                d?.aiSummary && typeof d.aiSummary === 'object' ? (d.aiSummary as Record<string, string>) : null;
              onUpdate({ status, transcript, summary });
              if (status === 'COMPLETED' || status === 'FAILED') return { status, transcript, summary };
            }
          } catch { /* 단발성 실패는 무시 (다음 폴링) */ }
          await new Promise((resolve) => setTimeout(resolve, interval));
        }
        return { status: 'FAILED' as SttStatus, transcript: [], summary: null };
      } finally {
        setPolling(false);
      }
    },
    [],
  );

  return { poll, polling };
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
