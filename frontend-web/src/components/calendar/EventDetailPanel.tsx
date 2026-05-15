'use client';
// src/components/calendar/EventDetailPanel.tsx

import { useState, useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import type { CalendarEvent } from './types';
import { CATEGORY_COLOR, CATEGORY_BG, SOURCE_STYLE } from './types';
import { formatTime, formatFullDate } from './utils';
import { useUploadRecording, usePollSttStatus, type SttLine } from '@/hooks/useActivity';

interface Props {
  event: CalendarEvent | null;
  onClose: () => void;
  onDeleted?: () => void;
  onEdit?: (event: CalendarEvent) => void;
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';
function authHeader(): HeadersInit {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : {};
}

type RightView = 'memo' | 'recording' | 'stt' | 'briefing';

// 화자 라벨: speakerName > speakerId의 끝 숫자 추출해 "화자 N" > fallbackIndex 기반 "화자 N" > "화자"
export function formatSpeakerLabel(line: SttLine, fallbackIndex?: number): string {
  if (line.speakerName) return line.speakerName;
  if (line.speakerId) {
    const m = line.speakerId.match(/(\d+)$/);
    if (m) return `화자 ${Number(m[1]) + 1}`;
    return line.speakerId;
  }
  if (fallbackIndex !== undefined) return `화자 ${fallbackIndex + 1}`;
  return '화자';
}

// 백엔드 WS 미연결 상태에서 녹음 중 실시간 전사 UI를 보여주기 위한 데모 라인.
// 실제 WS 연결은 후속 이슈에서 useTranscriptStream hook으로 교체.
// 실제로는 화자 식별 정보(이름)를 알 수 없으므로 speakerId만 두고
// formatSpeakerLabel이 "화자 1", "화자 2" 형태로 변환한다.
const DEMO_LIVE_LINES: SttLine[] = [
  { timestamp: '00:03', text: '안녕하십니까. 이 과장님 소개로 뵙게 되어 반갑습니다.', speakerId: 'SPEAKER_00', isSelf: false },
  { timestamp: '00:09', text: '네, 반갑습니다. 저희 솔루션에 관심을 가져주셔서 감사합니다.', speakerId: 'SPEAKER_01', isSelf: true },
  { timestamp: '00:21', text: '저희가 지금 MES 90% 이상, IOC 30% 도입 중이고 2028년 자가 기능적 제조로 사업 목표를 갖고 있어요.', speakerId: 'SPEAKER_00', isSelf: false },
  { timestamp: '00:38', text: '저희 플랫폼이 실시간 대시보드와 알림 기능을 제공합니다. 기존 ERP와도 연동 가능하고요.', speakerId: 'SPEAKER_01', isSelf: true },
  { timestamp: '00:55', text: '데이터 연동은 어떻게 되나요? 기존 ERP와 호환이 되어야 합니다.', speakerId: 'SPEAKER_00', isSelf: false },
  { timestamp: '01:12', text: '표준 REST API와 메시지 큐 양쪽 다 지원합니다. 자세한 스펙은 자료로 보내드리겠습니다.', speakerId: 'SPEAKER_01', isSelf: true },
];

export default function EventDetailPanel({ event, onClose, onDeleted, onEdit }: Props) {
  const [rightView, setRightView] = useState<RightView>('memo');
  const [deleting, setDeleting] = useState(false);
  const [recordSec, setRecordSec] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mediaRecRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [recError, setRecError] = useState<string | null>(null);
  const [sttStatus, setSttStatus] = useState<string | null>(null);
  const [sttLines, setSttLines] = useState<SttLine[]>([]);
  const [aiSummary, setAiSummary] = useState<{ label: string; text: string; color: string }[]>([]);
  const { upload: uploadRecording, uploading: uploadingRec } = useUploadRecording();
  const { poll: pollStt } = usePollSttStatus();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [briefingData, setBriefingData] = useState<any | null>(null);
  const [briefingLoading, setBriefingLoading] = useState(false);
  const [briefingError, setBriefingError] = useState<string | null>(null);

  // ── 메모 편집 ──
  const [memoText, setMemoText] = useState('');
  const [memoEditing, setMemoEditing] = useState(false);
  const [memoSaving, setMemoSaving] = useState(false);

  // ── 녹음 중 실시간 전사 (mock) — 후속 이슈에서 WS hook으로 교체 ──
  const liveSegRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [liveSegments, setLiveSegments] = useState<SttLine[]>([]);
  const liveScrollRef = useRef<HTMLDivElement | null>(null);

  // ── 모바일 반응형 (< 768px) ──
  const [isMobile, setIsMobile] = useState(false);
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const mq = window.matchMedia('(max-width: 767px)');
    const handler = () => setIsMobile(mq.matches);
    handler();
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  // 새 라인 추가 시 실시간 전사 컨테이너 하단으로 자동 스크롤
  useEffect(() => {
    if (liveScrollRef.current) {
      liveScrollRef.current.scrollTop = liveScrollRef.current.scrollHeight;
    }
  }, [liveSegments.length]);

  useEffect(() => {
    if (event) {
      setMemoText(event.memo ?? '');
      setMemoEditing(false);
    }
  }, [event]);

  useEffect(() => {
    if (!event) {
      setRightView('memo'); setRecordSec(0);
      setSttStatus(null); setSttLines([]); setAiSummary([]);
      setBriefingData(null); setBriefingError(null); setRecError(null);
      setMemoText(''); setMemoEditing(false); setMemoSaving(false);
      setLiveSegments([]);
      [timerRef, pollRef, liveSegRef].forEach((r) => { if (r.current) { clearInterval(r.current); r.current = null; } });
      if (mediaRecRef.current && mediaRecRef.current.state !== 'inactive') mediaRecRef.current.stop();
      mediaRecRef.current = null;
    }
  }, [event]);

  if (!event) return null;

  const isFint = event.source === 'FINT';
  const activityId = isFint ? event.eventId.replace(/^act-/, '') : null;
  const catColor = event.category ? CATEGORY_COLOR[event.category] : '#7F77DD';
  const catBg = event.category ? CATEGORY_BG[event.category] : '#ECEBFA';

  const handleDelete = async () => {
    if (!isFint || !activityId) return;
    if (!window.confirm('이 일정을 삭제할까요?')) return;
    setDeleting(true);
    try {
      const res = await fetch(`${API_BASE}/activities/${activityId}`, { method: 'DELETE', headers: authHeader() });
      if (!res.ok && res.status !== 204) throw new Error(`HTTP ${res.status}`);
      onDeleted?.(); onClose();
    } catch (e) { console.error('[EDP] 삭제 실패', e); }
    finally { setDeleting(false); }
  };

  // ── 메모 저장 ──
  const handleMemoSave = async () => {
    if (!activityId) return;
    setMemoSaving(true);
    try {
      const res = await fetch(`${API_BASE}/activities/${activityId}`, {
        method: 'PATCH',
        headers: authHeader(),
        body: JSON.stringify({ memo: memoText.trim() || null }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setMemoEditing(false);
    } catch (e) {
      console.error('[EDP] 메모 저장 실패', e);
    } finally {
      setMemoSaving(false);
    }
  };

  const SUMMARY_COLORS = ['#EF4444', '#F59E0B', '#0686D4', '#22C55E'];

  const handleUpload = async (blob: Blob) => {
    if (!activityId) return;
    setRecError(null);
    setSttLines([]);
    setAiSummary([]);
    setSttStatus('PENDING');
    // 파일 확장자 결정 (Blob 의 type 우선, 없으면 webm)
    const ext = blob.type.includes('mp4') || blob.type.includes('m4a')
      ? 'm4a'
      : blob.type.includes('mpeg')
      ? 'mp3'
      : blob.type.includes('wav')
      ? 'wav'
      : 'webm';
    const result = await uploadRecording(Number(activityId), blob, ext);
    if (!result.ok) {
      // 백엔드 호출 실패 — 실제 에러를 사용자에게 노출 (데모 fallback 제거)
      console.error('[FINT] STT 업로드/호출 실패:', result.error);
      setSttStatus('FAILED');
      setRecError(`녹음 업로드 실패: ${result.error ?? '알 수 없는 오류'}`);
      return;
    }
    // 폴링 시작 — 매 업데이트 콜백으로 상태 반영
    pollStt(
      Number(activityId),
      ({ status, transcript, summary }) => {
        setSttStatus(status);
        if (transcript.length > 0) setSttLines(transcript);
        if (summary) {
          setAiSummary(
            Object.entries(summary).map(([k, v], i) => ({
              label: k,
              text: String(v),
              color: SUMMARY_COLORS[i % SUMMARY_COLORS.length],
            })),
          );
        }
      },
    );
  };

  const startRec = async () => {
    if (!isFint) return;
    setRecError(null); setSttStatus(null); setSttLines([]); setAiSummary([]);
    setLiveSegments([]);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mt = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm';
      const rec = new MediaRecorder(stream, { mimeType: mt });
      chunksRef.current = [];
      rec.ondataavailable = (e) => { if (e.data.size > 0) chunksRef.current.push(e.data); };
      rec.onstop = () => { stream.getTracks().forEach((t) => t.stop()); handleUpload(new Blob(chunksRef.current, { type: mt })); };
      rec.start(1000); mediaRecRef.current = rec;
      setRightView('recording');
      timerRef.current = setInterval(() => setRecordSec((s) => s + 1), 1000);

      // Mock 실시간 전사 — 2초마다 DEMO_LIVE_LINES에서 한 라인씩 추가.
      // 후속 이슈에서 useTranscriptStream WS hook으로 교체.
      let li = 0;
      liveSegRef.current = setInterval(() => {
        if (li >= DEMO_LIVE_LINES.length) {
          if (liveSegRef.current) { clearInterval(liveSegRef.current); liveSegRef.current = null; }
          return;
        }
        setLiveSegments((prev) => [...prev, DEMO_LIVE_LINES[li]]);
        li += 1;
      }, 2000);
    } catch { setRecError('마이크 접근 권한이 필요합니다.'); }
  };

  const stopRec = () => {
    if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null; }
    if (liveSegRef.current) { clearInterval(liveSegRef.current); liveSegRef.current = null; }
    if (mediaRecRef.current && mediaRecRef.current.state !== 'inactive') { mediaRecRef.current.stop(); mediaRecRef.current = null; }
    setRecordSec(0); setRightView('stt');
  };

  const fetchBriefing = async () => {
    if (!activityId) return;
    setBriefingLoading(true); setBriefingError(null);
    try {
      const r = await fetch(`${API_BASE}/activities/${activityId}/ai/briefing`, { headers: authHeader() });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      setBriefingData((await r.json()).data ?? {});
    } catch (e: unknown) { setBriefingError(e instanceof Error ? e.message : '브리핑 조회 실패'); }
    finally { setBriefingLoading(false); }
  };

  const p = (n: number) => String(n).padStart(2, '0');
  const recTime = `${p(Math.floor(recordSec / 60))}:${p(recordSec % 60)}`;

  return (
    <div onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,.5)', display: 'flex', alignItems: isMobile ? 'stretch' : 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div
        data-testid="event-detail-modal"
        data-mobile={isMobile ? 'true' : 'false'}
        style={{
          width: isMobile ? '100%' : 820,
          height: isMobile ? '100%' : 'auto',
          maxHeight: isMobile ? '100%' : '88vh',
          borderRadius: isMobile ? 0 : 14,
          backgroundColor: '#fff',
          boxShadow: isMobile ? 'none' : '0 20px 60px rgba(0,0,0,.2)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {/* 헤더 */}
        <div style={{ padding: '16px 24px', borderBottom: '1px solid #E5E6DE', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: '#1F2126', margin: 0, flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {event.title}
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
            {event.pipelineStage && <Chip label={event.pipelineStage.stageName} color={catColor} bg={catBg} />}
            {isFint && (
              <button onClick={() => onEdit?.(event)}
                style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 12px', borderRadius: 6, border: '1px solid #E5E6DE', backgroundColor: '#fff', color: '#475569', fontSize: 12, fontWeight: 500, cursor: 'pointer' }}>
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M8.5 1.5l2 2-7 7H1.5V8.5l7-7z" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/></svg>
                수정
              </button>
            )}
            {isFint && (
              <button onClick={handleDelete} disabled={deleting}
                style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 12px', borderRadius: 6, border: '1px solid #E5E6DE', backgroundColor: '#fff', color: '#94A3B8', fontSize: 12, fontWeight: 500, cursor: deleting ? 'default' : 'pointer' }}>
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M1.5 3h9M4.5 3V2a1 1 0 011-1h1a1 1 0 011 1v1m1.5 0v7a1 1 0 01-1 1h-4a1 1 0 01-1-1V3h6z" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/></svg>
                {deleting ? '삭제 중' : '삭제'}
              </button>
            )}
            <button onClick={onClose}
              style={{ width: 28, height: 28, borderRadius: 6, border: '1px solid #E5E6DE', backgroundColor: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: '#9CA193' }}
              aria-label="닫기">
              ✕
            </button>
          </div>
        </div>

        {/* 2-column body (모바일에서는 세로 스택) */}
        <div
          data-testid="event-detail-body"
          style={{
            display: 'flex',
            flexDirection: isMobile ? 'column' : 'row',
            flex: 1,
            overflow: 'hidden',
            minHeight: 0,
          }}
        >
          {/* 좌(또는 상단): 메타 정보 */}
          <div
            style={{
              width: isMobile ? '100%' : 280,
              flexShrink: 0,
              padding: isMobile ? '16px 20px' : '20px 24px',
              borderRight: isMobile ? 'none' : '1px solid #E5E6DE',
              borderBottom: isMobile ? '1px solid #E5E6DE' : 'none',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
          >
            <MetaRow label="영업 목표">{event.dealTitle ?? <Muted>없음</Muted>}</MetaRow>
            <MetaRow label="고객">
              {event.accountName ? (
                `${event.accountName}${event.attendees?.external[0] ? ` — ${event.attendees.external[0]}` : ''}`
              ) : <Muted>없음</Muted>}
            </MetaRow>
            <MetaRow label="카테고리">
              {event.category ? (
                <span style={{ fontSize: 12, color: catColor, backgroundColor: catBg, padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>{event.category}</span>
              ) : <Muted>—</Muted>}
            </MetaRow>
            <MetaRow label="파이프라인">
              {event.pipelineStage ? <span style={{ fontSize: 12, color: '#534AB7' }}>{event.pipelineStage.stageName}</span> : <Muted>없음</Muted>}
            </MetaRow>
            <MetaRow label="일정">
              <span style={{ fontSize: 12, color: '#737880' }}>{formatFullDate(new Date(event.startAt))} — {formatTime(event.endAt)}</span>
            </MetaRow>
            <MetaRow label="생성일">
              <span style={{ fontSize: 12, color: '#737880' }}>{new Date(event.startAt).toLocaleDateString('ko-KR')}</span>
            </MetaRow>
            <MetaRow label="출처">
              <span style={{ fontSize: 11, color: SOURCE_STYLE[event.source].dot, backgroundColor: SOURCE_STYLE[event.source].bg, padding: '2px 7px', borderRadius: 4, fontWeight: 500 }}>
                {SOURCE_STYLE[event.source].label}
              </span>
            </MetaRow>
          </div>

          {/* 우(또는 하단): 탭+뷰 */}
          <div style={{ flex: 1, padding: isMobile ? '16px 20px' : '20px 24px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
            {rightView !== 'recording' && (
              <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E5E6DE', paddingBottom: 10 }}>
                {(['memo', 'briefing'] as const).map((v) => {
                  const LABELS: Record<string, string> = { memo: '메모', briefing: 'AI 브리핑' };
                  const active = rightView === v || (v === 'memo' && rightView === 'stt');
                  return (
                    <button key={v} onClick={() => { setRightView(v); if (v === 'briefing' && !briefingData && !briefingLoading) fetchBriefing(); }}
                      style={{ padding: '6px 14px', borderRadius: 6, border: 'none', backgroundColor: active ? '#06B6D4' : 'transparent', color: active ? '#fff' : '#737880', fontSize: 13, fontWeight: active ? 600 : 400, cursor: 'pointer', transition: 'border-color 0.15s' }}>
                      {LABELS[v]}
                    </button>
                  );
                })}
                {isFint && (
                  <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
                    <label
                      style={{
                        padding: '6px 14px',
                        borderRadius: 6,
                        border: '1px solid #E5E6DE',
                        backgroundColor: '#fff',
                        color: '#475569',
                        fontSize: 12,
                        fontWeight: 500,
                        cursor: 'pointer',
                      }}
                    >
                      파일 업로드
                      <input
                        type="file"
                        accept="audio/m4a,audio/mp4,audio/mpeg,audio/wav,audio/x-m4a,audio/webm"
                        style={{ display: 'none' }}
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) handleUpload(file);
                          e.currentTarget.value = '';
                        }}
                      />
                    </label>
                    <button
                      onClick={startRec}
                      style={{
                        padding: '6px 14px',
                        borderRadius: 6,
                        border: '1px solid #E5E6DE',
                        backgroundColor: '#fff',
                        color: '#475569',
                        fontSize: 12,
                        fontWeight: 500,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                      }}
                    >
                      <span style={{ width: 7, height: 7, borderRadius: '50%', backgroundColor: '#EF4444' }} />
                      녹음하기
                    </button>
                  </div>
                )}
              </div>
            )}
            {recError && <div style={{ fontSize: 12, color: '#EF4444', backgroundColor: '#FEF2F2', padding: '8px 12px', borderRadius: 6 }}>{recError}</div>}

            {/* 메모 탭 */}
            {(rightView === 'memo' || rightView === 'stt') && (
              <>
                {(sttStatus || uploadingRec) && (
                  <SttProgress status={sttStatus} uploading={uploadingRec} />
                )}
                {sttStatus === 'COMPLETED' && (aiSummary.length > 0 || sttLines.length > 0) && (
                  <>
                    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8 }}>
                      <SectionLabel>녹음 기록</SectionLabel>
                      <span style={{ fontSize: 11, color: '#9CA193' }}>
                        {(() => {
                          const t = sttLines.reduce((s, l) => s + (l.text?.length ?? 0), 0);
                          if (t === 0) return '';
                          return `약 ${Math.max(1, Math.round(t / 240))}분`;
                        })()}
                      </span>
                    </div>
                    <div
                      style={{
                        backgroundColor: '#F8F8F5',
                        borderRadius: 10,
                        padding: '22px 24px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 18,
                      }}
                    >
                      {/* 요약 — 4분할 라벨 / 글머리 점 제거.
                          여러 항목을 자연어 단락으로 결합. 사람이 쓴 회의록처럼. */}
                      {aiSummary.length > 0 && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                          {aiSummary
                            .map((r) => r.text)
                            .filter((t) => t && t.trim())
                            .map((text, i) => (
                              <p
                                key={i}
                                style={{
                                  margin: 0,
                                  fontSize: 15,
                                  lineHeight: 1.9,
                                  color: '#1F2126',
                                  letterSpacing: '-0.005em',
                                  whiteSpace: 'pre-wrap',
                                }}
                              >
                                {text}
                              </p>
                            ))}
                        </div>
                      )}

                      {sttLines.length > 0 && (
                        <details
                          style={{
                            marginTop: aiSummary.length > 0 ? 4 : 0,
                            borderTop: aiSummary.length > 0 ? '1px solid #ECEDE5' : 'none',
                            paddingTop: aiSummary.length > 0 ? 16 : 0,
                          }}
                        >
                          <summary
                            style={{
                              fontSize: 12,
                              color: '#737880',
                              cursor: 'pointer',
                              listStyle: 'none',
                              userSelect: 'none',
                              fontWeight: 500,
                              display: 'flex',
                              alignItems: 'center',
                              gap: 6,
                            }}
                          >
                            <span aria-hidden style={{ fontSize: 10, color: '#9CA193' }}>▸</span>
                            원본 대화 보기
                          </summary>
                          <div
                            data-testid="transcript-scroll"
                            style={{
                              marginTop: 14,
                              maxHeight: isMobile ? '40vh' : 360,
                              overflowY: 'auto',
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 12,
                            }}
                          >
                            {sttLines
                              .filter((l) => l.text && l.text.trim())
                              .map((line, i) => (
                                <SpeakerLine key={i} line={line} fallbackIndex={i % 2} />
                              ))}
                          </div>
                        </details>
                      )}
                    </div>
                  </>
                )}

                {/* 메모 — 편집 가능 */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <SectionLabel>메모</SectionLabel>
                  {isFint && !memoEditing && (
                    <button onClick={() => setMemoEditing(true)}
                      style={{ border: 'none', background: 'none', color: '#06B6D4', fontSize: 12, cursor: 'pointer', fontWeight: 500 }}>
                      편집
                    </button>
                  )}
                </div>
                {memoEditing ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <textarea value={memoText} onChange={(e) => setMemoText(e.target.value)}
                      placeholder="메모를 입력하세요..."
                      rows={5}
                      style={{ fontSize: 13, lineHeight: 1.7, color: '#475569', backgroundColor: '#fff', borderRadius: 8, padding: '12px 14px', border: '1.5px solid #06B6D4', outline: 'none', resize: 'vertical', fontFamily: 'inherit', minHeight: 100 }} />
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button onClick={() => { setMemoEditing(false); setMemoText(event.memo ?? ''); }}
                        style={{ padding: '5px 12px', borderRadius: 6, border: '1px solid #E5E6DE', backgroundColor: '#fff', color: '#737880', fontSize: 12, cursor: 'pointer' }}>
                        취소
                      </button>
                      <button onClick={handleMemoSave} disabled={memoSaving}
                        style={{ padding: '5px 14px', borderRadius: 6, border: 'none', backgroundColor: '#06B6D4', color: '#fff', fontSize: 12, fontWeight: 600, cursor: memoSaving ? 'default' : 'pointer' }}>
                        {memoSaving ? '저장 중...' : '저장'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div onClick={isFint ? () => setMemoEditing(true) : undefined}
                    style={{ fontSize: 13, lineHeight: 1.7, color: memoText ? '#475569' : '#9CA193', backgroundColor: '#F8F8F5', borderRadius: 8, padding: '12px 14px', minHeight: 100, cursor: isFint ? 'text' : 'default', transition: 'border-color 0.15s', border: '1.5px solid transparent' }}
                    onMouseEnter={isFint ? (e) => { (e.currentTarget as HTMLElement).style.borderColor = '#E5E6DE'; } : undefined}
                    onMouseLeave={isFint ? (e) => { (e.currentTarget as HTMLElement).style.borderColor = 'transparent'; } : undefined}>
                    {memoText || '메모 없음 — 클릭하여 추가'}
                  </div>
                )}
              </>
            )}

            {/* 녹음 뷰 — 단순 시간 표시 + 중지 (사인파/빨간 강조 톤다운) */}
            {rightView === 'recording' && (
              <>
                {/* REQ-ACT-01: AI 기록 고지 */}
                <div style={{ fontSize: 11, color: '#9CA193', textAlign: 'center' }} data-testid="ai-notice">
                  AI가 자동으로 기록·요약합니다
                </div>
                <div
                  style={{
                    backgroundColor: '#F8F8F5',
                    borderRadius: 10,
                    padding: '24px 20px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 16,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        backgroundColor: '#EF4444',
                        animation: 'recordingPulse 1.2s ease-in-out infinite',
                      }}
                    />
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#475569' }}>녹음 중</span>
                  </div>
                  <div
                    style={{
                      fontSize: 32,
                      fontWeight: 600,
                      color: '#1F2126',
                      fontVariantNumeric: 'tabular-nums',
                      letterSpacing: '0.02em',
                    }}
                  >
                    {recTime}
                  </div>
                  <button
                    onClick={stopRec}
                    style={{
                      padding: '7px 20px',
                      borderRadius: 6,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#fff',
                      color: '#1F2126',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    녹음 중지
                  </button>
                  <style>{`@keyframes recordingPulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }`}</style>
                </div>
                <div style={{ fontSize: 11, color: '#9CA193', textAlign: 'center' }}>녹음 중지 시 자동으로 STT 분석이 시작됩니다.</div>

                {/* 실시간 전사 — 컨트롤 박스 아래에 표시. 일정 높이 이상이면 스크롤. */}
                <SectionLabel>실시간 전사</SectionLabel>
                <div
                  ref={liveScrollRef}
                  data-testid="live-transcript-scroll"
                  style={{
                    maxHeight: isMobile ? '40vh' : 280,
                    overflowY: 'auto',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 10,
                    padding: '4px 2px',
                  }}
                >
                  {liveSegments.length === 0 ? (
                    <div style={{ fontSize: 12, color: '#9CA193', textAlign: 'center', padding: '12px 0' }}>
                      전사 대기 중…
                    </div>
                  ) : (
                    liveSegments.map((line, i) => (
                      <SpeakerLine key={i} line={line} fallbackIndex={i % 2} />
                    ))
                  )}
                </div>
              </>
            )}

            {/* AI 브리핑 탭 */}
            {rightView === 'briefing' && (
              <>
                <SectionLabel>AI 브리핑</SectionLabel>
                {briefingLoading && (<div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {[80,60,90,50].map((w,i) => (<div key={i} style={{ height: 14, borderRadius: 6, backgroundColor: '#F0F0EE', width: `${w}%` }} />))}
                  <div style={{ fontSize: 11, color: '#9CA193', marginTop: 4 }}>브리핑 생성 중...</div>
                </div>)}
                {briefingError && (<div style={{ fontSize: 12, color: '#EF4444', backgroundColor: '#FEF2F2', padding: '10px 12px', borderRadius: 6 }}>
                  {briefingError}<button onClick={fetchBriefing} style={{ marginLeft: 8, fontSize: 11, color: '#0686D4', border: 'none', background: 'none', cursor: 'pointer', textDecoration: 'underline' }}>재시도</button>
                </div>)}
                {briefingData && !briefingLoading && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                    {briefingData.summary && (<div>
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#9CA193', marginBottom: 6 }}>고객 요약</div>
                      <div style={{ fontSize: 13, lineHeight: 1.7, color: '#475569', backgroundColor: '#F8F8F5', borderRadius: 8, padding: '10px 12px' }}>
                        {typeof briefingData.summary === 'string' ? briefingData.summary : JSON.stringify(briefingData.summary)}
                      </div>
                    </div>)}
                    {briefingData.signals?.length > 0 && (<div>
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#9CA193', marginBottom: 6 }}>시그널</div>
                      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
                      {briefingData.signals.map((s: any, i: number) => (<div key={i} style={{ padding: '8px 10px', backgroundColor: '#F8F8F5', borderRadius: 6, borderLeft: '3px solid #06B6D4', marginBottom: 4 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: '#1F2126', marginBottom: 2 }}>{s.title ?? s.name ?? ''}</div>
                        <div style={{ fontSize: 11, color: '#737880', lineHeight: 1.5 }}>{s.content ?? s.description ?? ''}</div>
                      </div>))}
                    </div>)}
                    {briefingData.nextActions?.length > 0 && (<div>
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#9CA193', marginBottom: 6 }}>Next Actions</div>
                      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
                      {briefingData.nextActions.map((a: any, i: number) => (<div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 4 }}>
                        <span style={{ fontSize: 11, color: '#06B6D4', fontWeight: 700, marginTop: 1 }}>{i + 1}.</span>
                        <span style={{ fontSize: 12, color: '#475569', lineHeight: 1.5 }}>{typeof a === 'string' ? a : (a.action ?? a.content ?? '')}</span>
                      </div>))}
                    </div>)}
                    {!briefingData.summary && !briefingData.signals && !briefingData.nextActions && (<div style={{ fontSize: 13, color: '#9CA193' }}>브리핑 데이터가 없습니다.</div>)}
                  </div>
                )}
                {!briefingData && !briefingLoading && !briefingError && (
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, padding: '20px 0' }}>
                    <div style={{ fontSize: 13, color: '#9CA193', textAlign: 'center' }}>고객사 시그널(뉴스/DART)을 기반으로<br/>미팅 준비 자료를 AI가 생성합니다.</div>
                    <button onClick={fetchBriefing} style={{ padding: '10px 20px', borderRadius: 8, border: '1px solid #06B6D4', backgroundColor: 'transparent', color: '#06B6D4', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>AI 브리핑 생성하기</button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
        {rightView === 'recording' && (
          <div style={{ padding: '10px 24px', borderTop: '1px solid #E5E6DE', textAlign: 'center', fontSize: 11, color: '#9CA193' }}>녹음 진행 중</div>
        )}
      </div>
    </div>
  );
}

function MetaRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
      <span style={{ fontSize: 12, color: '#9CA193', width: 68, flexShrink: 0, paddingTop: 1 }}>{label}</span>
      <div style={{ flex: 1, fontSize: 13, color: '#1F2126' }}>{children}</div>
    </div>
  );
}
function Muted({ children }: { children: ReactNode }) {
  return <span style={{ fontSize: 13, color: '#9CA193' }}>{children}</span>;
}
function SectionLabel({ children }: { children: ReactNode }) {
  return <div style={{ fontSize: 12, fontWeight: 600, color: '#9CA193', letterSpacing: '0.03em' }}>{children}</div>;
}
function Chip({ label, color, bg }: { label: string; color: string; bg: string }) {
  return <span style={{ fontSize: 11, color, backgroundColor: bg, padding: '2px 8px', borderRadius: 4, fontWeight: 500 }}>{label}</span>;
}

// 화자별 채팅 버블 — isSelf면 우측 정렬 + 청록 옅은 배경, 아니면 좌측 + 회색
export function SpeakerLine({ line, fallbackIndex }: { line: SttLine; fallbackIndex?: number }) {
  const label = formatSpeakerLabel(line, fallbackIndex);
  const self = line.isSelf === true;
  return (
    <div
      data-testid="speaker-line"
      data-self={self ? 'true' : 'false'}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: self ? 'flex-end' : 'flex-start',
        gap: 4,
      }}
    >
      <span style={{ fontSize: 11, color: '#737880', fontWeight: 500 }}>{label}</span>
      <div
        style={{
          maxWidth: '85%',
          backgroundColor: self ? '#ECF9FC' : '#F8F8F5',
          borderRadius: 8,
          padding: '8px 12px',
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {line.timestamp && (
          <span style={{ fontSize: 10, color: '#9CA193', fontVariantNumeric: 'tabular-nums' }}>{line.timestamp}</span>
        )}
        <span style={{ fontSize: 13, lineHeight: 1.7, color: '#1F2126', whiteSpace: 'pre-wrap' }}>{line.text}</span>
      </div>
    </div>
  );
}

// ─── STT 진행 상태 — 한 줄 텍스트. 완료 시에는 결과 자체가 신호이므로 노출하지 않음.
function SttProgress({ status, uploading }: { status: string | null; uploading: boolean }) {
  const isFailed = status === 'FAILED' || status === 'TIMEOUT';
  if (status === 'COMPLETED' && !uploading) return null;

  if (isFailed) {
    return (
      <span style={{ fontSize: 11, color: '#B45309', alignSelf: 'flex-start' }}>
        {status === 'TIMEOUT' ? '분석 시간이 초과되었어요' : '분석에 실패했어요'}
      </span>
    );
  }

  let label = '준비 중';
  if (uploading) label = '파일 업로드 중...';
  else if (status === 'PENDING') label = '대기 중...';
  else if (status === 'PROCESSING') label = '분석 중...';

  return <span style={{ fontSize: 11, color: '#9CA193', alignSelf: 'flex-start' }}>{label}</span>;
}
