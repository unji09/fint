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
  const [briefingData, setBriefingData] = useState<any | null>(null);
  const [briefingLoading, setBriefingLoading] = useState(false);
  const [briefingError, setBriefingError] = useState<string | null>(null);

  // ── 메모 편집 ──
  const [memoText, setMemoText] = useState('');
  const [memoEditing, setMemoEditing] = useState(false);
  const [memoSaving, setMemoSaving] = useState(false);

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
      [timerRef, pollRef].forEach((r) => { if (r.current) { clearInterval(r.current); r.current = null; } });
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

  // 데모 시나리오 — 백엔드 STT 미구현 시 fallback. PENDING → PROCESSING → COMPLETED 순차 시뮬레이션.
  const runDemoStt = () => {
    setSttStatus('PENDING');
    setTimeout(() => setSttStatus('PROCESSING'), 1500);
    setTimeout(() => {
      setSttStatus('COMPLETED');
      setSttLines([
        { timestamp: '00:00', text: '안녕하세요, 오늘 미팅 시작하겠습니다.' },
        { timestamp: '00:08', text: '먼저 지난 주 진행 상황을 정리해 드릴게요.' },
        { timestamp: '00:23', text: '제안서 초안은 검토 완료했고, 가격 조건만 협의가 남았습니다.' },
        { timestamp: '00:41', text: '저희 쪽에서는 분기 내 계약 체결을 목표로 하고 있어요.' },
        { timestamp: '01:02', text: '예산 승인은 다음 주 임원 회의 후 확정될 예정입니다.' },
        { timestamp: '01:18', text: '추가 자료가 필요하시면 메일로 보내드릴게요.' },
      ]);
      setAiSummary([
        { label: '핵심 안건', text: '제안서 가격 조건 협상 진행, 분기 내 계약 체결 목표.', color: '#EF4444' },
        { label: '결정 사항', text: '추가 자료는 이메일로 전달 / 다음 주 임원 회의 후 예산 승인 확정.', color: '#F59E0B' },
        { label: 'Next Step', text: '가격 조건 최종안 작성 후 재미팅 일정 조율.', color: '#0686D4' },
        { label: '리스크', text: '예산 승인 지연 시 일정 한 주 이상 밀릴 가능성.', color: '#22C55E' },
      ]);
    }, 4500);
  };

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
      // 백엔드 미구현 시 자동으로 데모 시나리오로 fallback
      console.warn('[FINT] STT 백엔드 호출 실패 — 데모 시나리오로 진행:', result.error);
      runDemoStt();
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
    } catch { setRecError('마이크 접근 권한이 필요합니다.'); }
  };

  const stopRec = () => {
    if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null; }
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
    } catch (e: any) { setBriefingError(e?.message ?? '브리핑 조회 실패'); }
    finally { setBriefingLoading(false); }
  };

  const p = (n: number) => String(n).padStart(2, '0');
  const recTime = `${p(Math.floor(recordSec / 60))}:${p(recordSec % 60)}`;

  return (
    <div onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ width: 820, maxHeight: '88vh', borderRadius: 14, backgroundColor: '#fff', boxShadow: '0 20px 60px rgba(0,0,0,.2)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
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

        {/* 2-column body */}
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden', minHeight: 0 }}>
          {/* 좌: 메타 정보 */}
          <div style={{ width: 280, flexShrink: 0, padding: '20px 24px', borderRight: '1px solid #E5E6DE', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
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

          {/* 우: 탭+뷰 */}
          <div style={{ flex: 1, padding: '20px 24px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
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
                        border: '1px solid #06B6D4',
                        backgroundColor: 'transparent',
                        color: '#06B6D4',
                        fontSize: 13,
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      ⤴ 파일 업로드
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
                        border: '1px solid #EF4444',
                        backgroundColor: 'transparent',
                        color: '#EF4444',
                        fontSize: 13,
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      ● 녹음하기
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
                {sttStatus === 'COMPLETED' && aiSummary.length > 0 && (
                  <>
                    <SectionLabel>AI 요약</SectionLabel>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {aiSummary.map((r) => (
                        <div
                          key={r.label}
                          style={{
                            padding: '10px 12px',
                            borderRadius: 8,
                            backgroundColor: '#fff',
                            border: '1px solid #E2E8F0',
                            borderLeft: `3px solid ${r.color}`,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 6,
                          }}
                        >
                          <span
                            style={{
                              fontSize: 10,
                              color: r.color,
                              fontWeight: 700,
                              backgroundColor: r.color + '20',
                              padding: '2px 8px',
                              borderRadius: 4,
                              alignSelf: 'flex-start',
                              letterSpacing: '0.04em',
                            }}
                          >
                            {r.label.toUpperCase()}
                          </span>
                          <span style={{ fontSize: 13, color: '#1F2126', lineHeight: 1.6 }}>{r.text}</span>
                        </div>
                      ))}
                    </div>
                  </>
                )}
                {sttStatus === 'COMPLETED' && sttLines.length > 0 && (
                  <>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <SectionLabel>녹음 전사</SectionLabel>
                      <span style={{ fontSize: 11, color: '#94A3B8' }}>{sttLines.length} 줄</span>
                    </div>
                    <div
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2,
                        maxHeight: 220,
                        overflowY: 'auto',
                        padding: '4px 0',
                        borderRadius: 8,
                        backgroundColor: '#F8F8F5',
                      }}
                    >
                      {sttLines.map((line, i) => (
                        <div
                          key={i}
                          style={{
                            display: 'flex',
                            gap: 10,
                            padding: '8px 12px',
                            borderRadius: 6,
                            transition: 'background-color 0.12s',
                          }}
                          onMouseEnter={(e) => {
                            (e.currentTarget as HTMLDivElement).style.backgroundColor = '#EEF1F5';
                          }}
                          onMouseLeave={(e) => {
                            (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent';
                          }}
                        >
                          {line.timestamp && (
                            <span
                              style={{
                                fontSize: 10,
                                color: '#06B6D4',
                                fontFamily: 'ui-monospace, SFMono-Regular, monospace',
                                fontWeight: 600,
                                whiteSpace: 'nowrap',
                                paddingTop: 2,
                                minWidth: 44,
                              }}
                            >
                              {line.timestamp}
                            </span>
                          )}
                          <span style={{ fontSize: 12, color: '#1F2126', lineHeight: 1.6, flex: 1 }}>
                            {line.text}
                          </span>
                        </div>
                      ))}
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

            {/* 녹음 뷰 */}
            {rightView === 'recording' && (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: '#EF4444', display: 'inline-block' }} />
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#EF4444' }}>녹음 중</span>
                </div>
                <div style={{ backgroundColor: '#F8F8F5', borderRadius: 10, padding: '24px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
                  <div style={{ fontSize: 36, fontWeight: 700, color: '#EF4444', fontVariantNumeric: 'tabular-nums' }}>{recTime}</div>
                  <div style={{ display: 'flex', gap: 2, alignItems: 'center', height: 32 }}>
                    {Array.from({ length: 22 }, (_, i) => (<div key={i} style={{ width: 3, height: `${8 + Math.abs(Math.sin(i * 0.9)) * 12}px`, backgroundColor: '#EF4444', borderRadius: 2, opacity: 0.85 }} />))}
                  </div>
                  <button onClick={stopRec} style={{ padding: '8px 24px', borderRadius: 20, border: 'none', backgroundColor: '#EF4444', color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>■ 녹음 중지</button>
                </div>
                <div style={{ fontSize: 11, color: '#9CA193', textAlign: 'center' }}>녹음 중지 시 자동으로 STT 분석이 시작됩니다.</div>
              </>
            )}

            {/* AI 브리핑 탭 */}
            {rightView === 'briefing' && (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <SectionLabel>AI 브리핑</SectionLabel>
                  <span style={{ fontSize: 11, color: '#9CA3AF', fontWeight: 400 }}>— 고객사 시그널 기반 미팅 준비 자료 (선택)</span>
                </div>
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
                      {briefingData.signals.map((s: any, i: number) => (<div key={i} style={{ padding: '8px 10px', backgroundColor: '#F8F8F5', borderRadius: 6, borderLeft: '3px solid #06B6D4', marginBottom: 4 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: '#1F2126', marginBottom: 2 }}>{s.title ?? s.name ?? ''}</div>
                        <div style={{ fontSize: 11, color: '#737880', lineHeight: 1.5 }}>{s.content ?? s.description ?? ''}</div>
                      </div>))}
                    </div>)}
                    {briefingData.nextActions?.length > 0 && (<div>
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#9CA193', marginBottom: 6 }}>Next Actions</div>
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
          <div style={{ padding: '10px 24px', borderTop: '1px solid #E5E6DE', textAlign: 'center', fontSize: 12, color: '#EF4444', fontWeight: 600 }}>녹음 진행 중...</div>
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

// ─── STT 진행 상태 시각화 ───────────────────────────────────────────
// 업로드 → PENDING → PROCESSING → COMPLETED 4단계 progress bar.
function SttProgress({ status, uploading }: { status: string | null; uploading: boolean }) {
  const STEPS = [
    { key: 'UPLOAD', label: '업로드' },
    { key: 'PENDING', label: '대기' },
    { key: 'PROCESSING', label: '분석' },
    { key: 'COMPLETED', label: '완료' },
  ] as const;

  const isFailed = status === 'FAILED' || status === 'TIMEOUT';
  let activeIdx = uploading ? 0 : 1;
  if (status === 'PENDING') activeIdx = 1;
  else if (status === 'PROCESSING') activeIdx = 2;
  else if (status === 'COMPLETED') activeIdx = 3;

  if (isFailed) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '8px 12px',
          borderRadius: 8,
          backgroundColor: '#FEF2F2',
          border: '1px solid #FECACA',
          color: '#DC2626',
          fontSize: 12,
          fontWeight: 600,
        }}
      >
        <span style={{ fontSize: 14 }}>{status === 'TIMEOUT' ? '⏱' : '❌'}</span>
        <span>{status === 'TIMEOUT' ? '분석 시간이 초과되었어요.' : '분석에 실패했어요.'}</span>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        padding: '10px 12px',
        borderRadius: 8,
        backgroundColor: '#F8FAFC',
        border: '1px solid #E2E8F0',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: '#0F172A' }}>
          {status === 'COMPLETED' ? '✅ STT 분석 완료' : '🔄 STT 분석 진행 중'}
        </span>
        <span style={{ fontSize: 11, color: '#64748B' }}>
          {Math.min(activeIdx + 1, STEPS.length)} / {STEPS.length}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        {STEPS.map((s, i) => {
          const done = i < activeIdx;
          const active = i === activeIdx;
          const color = done ? '#06B6D4' : active ? '#06B6D4' : '#CBD5E1';
          return (
            <div key={s.key} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <div
                style={{
                  width: '100%',
                  height: 4,
                  borderRadius: 2,
                  backgroundColor: color,
                  opacity: done ? 1 : active ? 1 : 0.4,
                }}
              />
              <span
                style={{
                  fontSize: 10,
                  color: active || done ? '#0F172A' : '#94A3B8',
                  fontWeight: active ? 600 : 400,
                }}
              >
                {s.label}
                {active && i < 3 && '...'}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
