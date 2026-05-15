'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
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
  speakerName?: string;
  isSelf?: boolean;
  startMs?: number;
  endMs?: number;
}

/** GET /activities/{id}/recordings 응답의 개별 녹음 아이템 */
export interface RecordingItem {
  recordingId: number;
  fileKey: string;
  title: string;
  duration: number;
  sttStatus: SttStatus;
  transcript: { segments: { speaker_id: string; text: string; start_ms: number; end_ms: number }[] } | null;
  createdAt: string;
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
//   1) POST /files/multipart/init  → uploadId, fileKey, partUploadUrls[]
//   2) PUT 각 partUploadUrl (S3 직통, 5 MB 청크)  → ETag 수집
//   3) POST /files/multipart/complete { uploadId, fileKey, parts[] }
//   4) POST /activities/{activityId}/recording { fileKey, duration, title }  → 202

const PART_SIZE = 5 * 1024 * 1024; // AWS multipart 최소 단위(마지막 파트 제외)

export interface UploadMeta {
  duration: number; // 녹음 길이(초), 0 허용
  title: string;    // 미팅 제목
}

export function useUploadRecording() {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = useCallback(async (activityId: number, blob: Blob, fileExt: string, meta?: UploadMeta) => {
    setUploading(true);
    setError(null);
    try {
      // A. Blob 분할
      const chunks: { partNumber: number; chunk: Blob }[] = [];
      let offset = 0;
      let partNumber = 1;
      while (offset < blob.size) {
        chunks.push({ partNumber, chunk: blob.slice(offset, offset + PART_SIZE) });
        offset += PART_SIZE;
        partNumber++;
      }
      if (chunks.length === 0) throw new Error('업로드할 데이터가 없습니다');

      // B. POST /files/multipart/init
      const initRes = await fetchWithAuth('/files/multipart/init', {
        method: 'POST',
        body: JSON.stringify({
          fileName: `rec_${activityId}_${Date.now()}.${fileExt}`,
          contentType: blob.type || 'audio/webm',
          fileType: 'AUDIO',
          purpose: 'MEETING_RECORD',
          fileSize: blob.size,
          partCount: chunks.length,
          meetingId: activityId,
        }),
      });
      if (!initRes.ok) throw new Error(`multipart init 실패 (${initRes.status})`);
      const initJson = await initRes.json();
      const init = initJson?.data ?? initJson;
      const uploadId: string | undefined = init?.uploadId;
      const fileKey: string | undefined = init?.fileKey;
      const partUploadUrls: { partNumber: number; uploadUrl: string }[] | undefined = init?.partUploadUrls;
      if (!uploadId || !fileKey || !Array.isArray(partUploadUrls)) throw new Error('multipart init 응답 형식 오류');

      // C. 파트별 S3 PUT (병렬)
      const parts = await Promise.all(
        chunks.map(async ({ partNumber: pn, chunk }) => {
          const urlEntry = partUploadUrls.find((p) => p.partNumber === pn);
          if (!urlEntry) throw new Error(`파트 ${pn} URL 없음`);
          const s3Res = await fetch(urlEntry.uploadUrl, {
            method: 'PUT',
            body: chunk,
            headers: { 'Content-Type': blob.type || 'audio/webm' },
          });
          if (!s3Res.ok) throw new Error(`S3 파트 ${pn} 업로드 실패 (${s3Res.status})`);
          const etag = s3Res.headers.get('ETag') ?? s3Res.headers.get('etag') ?? '';
          return { partNumber: pn, etag };
        }),
      );

      // D. POST /files/multipart/complete
      const completeRes = await fetchWithAuth('/files/multipart/complete', {
        method: 'POST',
        body: JSON.stringify({ uploadId, fileKey, parts }),
      });
      if (!completeRes.ok) throw new Error(`multipart complete 실패 (${completeRes.status})`);
      const completeJson = await completeRes.json();
      const finalFileKey: string = (completeJson?.data ?? completeJson)?.fileKey ?? fileKey;

      // E. STT 트리거 + 녹음 기록 생성
      const recRes = await fetchWithAuth(`/activities/${activityId}/recording`, {
        method: 'POST',
        body: JSON.stringify({
          fileKey: finalFileKey,
          duration: meta?.duration ?? 0,
          title: meta?.title ?? '',
        }),
      });
      if (!recRes.ok && recRes.status !== 202) {
        throw new Error(`STT 요청 실패 (${recRes.status})`);
      }
      const recJson = recRes.status !== 204 ? await recRes.json().catch(() => null) : null;
      const recordingId = (recJson?.data ?? recJson)?.recordingId as number | undefined;

      return { fileKey: finalFileKey, ok: true, recordingId } as const;
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
                const sp = o.speaker_id ?? o.speakerId ?? o.speaker;
                if (typeof sp === 'string') line.speakerId = sp;
                const sn = o.speaker_name ?? o.speakerName;
                if (typeof sn === 'string') line.speakerName = sn;
                const is = o.is_self ?? o.isSelf;
                if (typeof is === 'boolean') line.isSelf = is;
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

// ─── 실시간 STT 스트림 (WebSocket) ───────────────────────────────────────────
// 백엔드: ws(s)://host/ws/stt/{activityId}?token=JWT
//   - 클라이언트 → 서버: 바이너리 오디오 청크
//   - 서버 → 클라이언트: JSON 텍스트 { type, text, speaker_id, start_ms, end_ms }

/** 같은 화자 연속 청크를 모아뒀다가 한 번에 emit하는 버퍼 */
interface SegmentBuffer {
  speakerId: string | undefined;
  texts: string[];
  startMs: number;
  lastEndMs: number;
  /** 버퍼 시작 시점에 1회만 설정, 리셋 안 함 — 최대 대기 상한 */
  maxTimer: ReturnType<typeof setTimeout> | null;
}

/** 문장 종결 구두점 — 이 패턴이 나오면 즉시 flush */
const SENTENCE_END_RE = /[.!?。？！]\s*$/;
/** 녹음 타임라인 상 이 ms 이상 gap → 새 발화로 간주 (침묵 감지) */
const MAX_GAP_MS = 3000;
/** 버퍼 누적 최대 청크 수 — 초과 시 강제 flush */
const MAX_BUFFER_SEGMENTS = 5;
/** 버퍼 시작 후 최대 대기 시간 (리셋 안 함) — 긴 문장 강제 출력 */
const MAX_BUFFER_WALL_MS = 7000;

export function useTranscriptStream() {
  const wsRef = useRef<WebSocket | null>(null);
  const onSegmentRef = useRef<((line: SttLine) => void) | null>(null);
  const bufferRef = useRef<SegmentBuffer | null>(null);

  const flushBuffer = useCallback(() => {
    const buf = bufferRef.current;
    if (!buf || buf.texts.length === 0) return;
    if (buf.maxTimer) clearTimeout(buf.maxTimer);
    bufferRef.current = null;
    const merged: SttLine = {
      text: buf.texts.join(' '),
      speakerId: buf.speakerId,
      startMs: buf.startMs,
      endMs: buf.lastEndMs,
    };
    if (typeof merged.startMs === 'number') {
      const s = Math.floor(merged.startMs / 1000);
      merged.timestamp = `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
    }
    onSegmentRef.current?.(merged);
  }, []);

  const connect = useCallback((activityId: number, onSegment: (line: SttLine) => void) => {
    wsRef.current?.close();
    onSegmentRef.current = onSegment;
    // 이전 연결의 버퍼 정리
    if (bufferRef.current?.maxTimer) clearTimeout(bufferRef.current.maxTimer);
    bufferRef.current = null;

    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
    if (!token) return;

    const apiBase = process.env.NEXT_PUBLIC_API_URL ?? '';
    const wsBase = apiBase.replace(/\/api\/v1\/?$/, '').replace(/^http/, 'ws');
    const url = `${wsBase}/ws/stt/${activityId}?token=${token}`;

    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => console.info('[STT WS] 연결됨', url);
    ws.onclose = (e) => console.info('[STT WS] 종료', e.code, e.reason);

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data as string) as Record<string, unknown>;
        console.debug('[STT WS] 수신:', JSON.stringify(data));

        if (data.type === 'error') {
          console.warn('[STT WS] 서버 오류:', data.message);
          return;
        }

        // FastAPI: { type, segment: { text, speaker_id, start_ms, end_ms } }
        const seg = (data.segment && typeof data.segment === 'object'
          ? data.segment
          : data) as Record<string, unknown>;

        const text = ((seg.text ?? data.text) ?? '') as string;
        if (!text.trim()) return;

        const sp = (seg.speaker_id ?? seg.speakerId ?? data.speaker_id ?? data.speakerId) as string | undefined;
        const sm = typeof seg.start_ms === 'number' ? seg.start_ms
          : typeof data.start_ms === 'number' ? data.start_ms
          : (seg.startMs as number | undefined);
        const em = typeof seg.end_ms === 'number' ? seg.end_ms
          : typeof data.end_ms === 'number' ? data.end_ms
          : (seg.endMs as number | undefined);
        const startMs = typeof sm === 'number' ? sm : 0;
        const endMs = typeof em === 'number' ? em : startMs;

        const buf = bufferRef.current;
        const speakerChanged = buf !== null && buf.speakerId !== sp;
        const gapTooLarge = buf !== null && startMs - buf.lastEndMs > MAX_GAP_MS;
        const bufferFull = buf !== null && buf.texts.length >= MAX_BUFFER_SEGMENTS;

        if (speakerChanged || gapTooLarge || bufferFull) {
          flushBuffer();
        }

        if (bufferRef.current === null) {
          // 버퍼 시작 — maxTimer는 여기서 1회만 설정, 이후 리셋 없음
          bufferRef.current = {
            speakerId: sp,
            texts: [text],
            startMs,
            lastEndMs: endMs,
            maxTimer: setTimeout(flushBuffer, MAX_BUFFER_WALL_MS),
          };
        } else {
          // 같은 발화 계속 — texts만 누적, 타이머는 건드리지 않음
          bufferRef.current.texts.push(text);
          bufferRef.current.lastEndMs = endMs;
        }

        // 문장 종결 구두점이면 즉시 flush (문장 끝 감지)
        const combined = bufferRef.current?.texts.join(' ') ?? '';
        if (SENTENCE_END_RE.test(combined)) {
          flushBuffer();
        }
      } catch { /* non-JSON 무시 */ }
    };

    ws.onerror = () => console.warn('[STT WS] 연결 오류');
  }, [flushBuffer]);

  const sendChunk = useCallback((chunk: Blob) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(chunk);
    }
  }, []);

  const disconnect = useCallback(() => {
    flushBuffer();
    wsRef.current?.close();
    wsRef.current = null;
  }, [flushBuffer]);

  return { connect, sendChunk, disconnect };
}

// ─── 녹음 목록 조회 ──────────────────────────────────────────────────────────

export function useRecordingList(activityId: number | null) {
  const [recordings, setRecordings] = useState<RecordingItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch_ = useCallback(async () => {
    if (!activityId) return;
    setLoading(true);
    try {
      const r = await fetchWithAuth(`/activities/${activityId}/recordings`);
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const json = await r.json();
      setRecordings(json.data?.recordings ?? []);
    } catch {
      setError('녹음 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [activityId]);

  useEffect(() => {
    fetch_();
  }, [fetch_]);

  return { recordings, loading, error, refetch: fetch_ };
}

// ─── 녹음 제목 수정 ──────────────────────────────────────────────────────────

export function useUpdateRecordingTitle() {
  const [loading, setLoading] = useState(false);

  const update = useCallback(async (activityId: number, recordingId: number, title: string) => {
    setLoading(true);
    try {
      const r = await fetchWithAuth(`/activities/${activityId}/recordings/${recordingId}`, {
        method: 'PATCH',
        body: JSON.stringify({ title }),
      });
      return r.ok;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { update, loading };
}

// ─── 녹음 삭제 ───────────────────────────────────────────────────────────────

export function useDeleteRecording() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (activityId: number, recordingId: number) => {
    setLoading(true);
    try {
      const r = await fetchWithAuth(`/activities/${activityId}/recordings/${recordingId}`, { method: 'DELETE' });
      return r.ok || r.status === 204;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { remove, loading };
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
