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

/** STT 전사 줄 단위 — 백엔드 응답 키는 timestamp/text 또는 ts/content 가능.
 *  화자 분리(diarize=true) 시 speakerId / startMs / endMs 가 함께 옴. */
export interface SttLine {
  timestamp?: string;
  text: string;
  speakerId?: string;
  startMs?: number;
  endMs?: number;
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
// 명세 흐름 (백엔드 PresignedUrlRequest / Response 와 일치):
//   1) POST /files/presigned-url
//        body: { fileName, contentType, fileType, purpose, fileSize, meetingId }
//        response: { uploadUrl, fileKey, expiresIn, uploadType }
//   2) PUT {uploadUrl}  (S3 직통, body=Blob, header Content-Type 매칭)
//   3) POST /activities/{activityId}/recording { fileKey }
//        → 202 Accepted (비동기 STT 시작)

export function useUploadRecording() {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = useCallback(async (activityId: number, blob: Blob, fileExt: string) => {
    setUploading(true);
    setError(null);
    try {
      // 1) Pre-signed URL 발급 — 백엔드 PresignedUrlRequest 풀스펙으로 전송
      const presRes = await fetchWithAuth('/files/presigned-url', {
        method: 'POST',
        body: JSON.stringify({
          fileName: `rec_${activityId}_${Date.now()}.${fileExt}`,
          contentType: blob.type || 'audio/webm',
          fileType: 'AUDIO',
          purpose: 'MEETING_RECORD',
          fileSize: blob.size,
          meetingId: activityId,
        }),
      });
      if (!presRes.ok) throw new Error(`presigned URL 실패 (${presRes.status})`);
      const presJson = await presRes.json();
      const pres = presJson?.data ?? presJson;
      // 백엔드는 uploadUrl 로 보냄. 과거 호환 위해 url 도 fallback.
      const uploadUrl: string | undefined = pres?.uploadUrl ?? pres?.url;
      const fileKey: string | undefined = pres?.fileKey;
      if (!uploadUrl || !fileKey) throw new Error('presigned URL 응답 형식 오류');

      // 2) S3 직통 업로드 — 백엔드가 SSE-KMS 강제로 presign 했으므로
      //    클라이언트도 동일한 암호화 헤더를 PUT 에 포함해야 서명 통과 (403 방지)
      const s3Res = await fetch(uploadUrl, {
        method: 'PUT',
        body: blob,
        headers: {
          'Content-Type': blob.type || 'audio/webm',
          'x-amz-server-side-encryption': 'aws:kms',
          'x-amz-server-side-encryption-aws-kms-key-id': 'alias/crm-fint-s3-key',
        },
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
      // 긴 녹음(1시간+) STT 대비 30분 timeout. 기존 2분은 GPU 처리 시간을 못 견딤.
      const timeout = opts?.timeoutMs ?? 30 * 60_000;
      const start = Date.now();
      setPolling(true);

      // transcript 정규화 — 배열 / Map / 문자열 / segments 모두 처리.
      // diarize 결과의 speaker_id / start_ms / end_ms 도 추출.
      const normalize = (raw: unknown): SttLine[] => {
        if (!raw) return [];
        if (Array.isArray(raw)) {
          return raw
            .map((t): SttLine | null => {
              if (typeof t === 'string') return t.trim() ? { text: t } : null;
              if (t && typeof t === 'object') {
                const o = t as Record<string, unknown>;
                const text = (o.text ?? o.content ?? '') as string;
                if (!text) return null;
                const line: SttLine = { text, timestamp: (o.timestamp ?? o.ts ?? '') as string };
                const sp = o.speaker_id ?? o.speakerId;
                if (typeof sp === 'string') line.speakerId = sp;
                const sm = o.start_ms ?? o.startMs;
                if (typeof sm === 'number') line.startMs = sm;
                const em = o.end_ms ?? o.endMs;
                if (typeof em === 'number') line.endMs = em;
                return line;
              }
              return null;
            })
            .filter((v): v is SttLine => v !== null);
        }
        if (typeof raw === 'string') return raw.trim() ? [{ text: raw }] : [];
        if (typeof raw === 'object') {
          const o = raw as Record<string, unknown>;
          if (Array.isArray(o.segments)) return normalize(o.segments);
          if (typeof o.text === 'string' && o.text.trim()) return [{ text: o.text }];
        }
        return [];
      };

      try {
        while (Date.now() - start < timeout) {
          try {
            const r = await fetchWithAuth(`/activities/${activityId}`);
            if (r.ok) {
              const j = await r.json();
              const d = j?.data ?? j;
              const status: SttStatus = d?.sttStatus ?? 'NONE';
              const transcript: SttLine[] = normalize(d?.sttTranscript ?? d?.transcript);
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

// ─── 활동 단위 분위기(날씨) 분석 결과 폴링 ───────────────────────────────
// 백엔드: GET /activities/{activityId}/ai/mood
//   응답: { activityId, moodStatus, mood, moodScore, reason, keySignals, analyzedAt }
//   moodStatus: PENDING | PROCESSING | COMPLETED | FAILED
//   mood: RAINBOW | SUNNY | CLOUDY | RAINY | THUNDER

export type MoodAnalysisStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface MoodAnalysisResult {
  activityId: number;
  moodStatus: MoodAnalysisStatus;
  mood: 'RAINBOW' | 'SUNNY' | 'CLOUDY' | 'RAINY' | 'THUNDER' | null;
  moodScore: number | null;
  reason: string | null;
  keySignals: string[];
  analyzedAt: string | null;
}

export function useMoodAnalysis(activityId: number | null) {
  const [data, setData] = useState<MoodAnalysisResult | null>(null);
  const [polling, setPolling] = useState(false);

  const fetchOnce = useCallback(async (id: number): Promise<MoodAnalysisResult | null> => {
    try {
      const r = await fetchWithAuth(`/activities/${id}/ai/mood`);
      if (!r.ok) return null;
      const j = await r.json();
      const d = j?.data ?? j;
      if (!d) return null;
      return {
        activityId: d.activityId ?? id,
        moodStatus: (d.moodStatus ?? 'PENDING') as MoodAnalysisStatus,
        mood: d.mood ?? null,
        moodScore: typeof d.moodScore === 'number' ? d.moodScore : null,
        reason: d.reason ?? null,
        keySignals: Array.isArray(d.keySignals) ? d.keySignals : [],
        analyzedAt: d.analyzedAt ?? null,
      };
    } catch {
      return null;
    }
  }, []);

  // 처음 1회 + 결과가 PENDING/PROCESSING 이면 폴링 (5초 간격, 5분 timeout)
  useEffect(() => {
    if (!activityId) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const start = Date.now();
    const TIMEOUT = 5 * 60_000;
    const INTERVAL = 5_000;

    const tick = async () => {
      if (cancelled) return;
      const result = await fetchOnce(activityId);
      if (cancelled) return;
      if (result) setData(result);
      const done = !result
        || result.moodStatus === 'COMPLETED'
        || result.moodStatus === 'FAILED'
        || Date.now() - start > TIMEOUT;
      if (done) { setPolling(false); return; }
      timer = setTimeout(tick, INTERVAL);
    };

    setPolling(true);
    tick();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [activityId, fetchOnce]);

  return { data, polling, refetch: () => activityId && fetchOnce(activityId).then((r) => { if (r) setData(r); }) };
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
