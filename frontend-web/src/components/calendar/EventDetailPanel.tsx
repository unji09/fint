'use client';
// src/components/calendar/EventDetailPanel.tsx

import { useState, useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import type { CalendarEvent } from './types';
import { CATEGORY_COLOR, CATEGORY_BG, SOURCE_STYLE } from './types';
import { formatTime, formatFullDate } from './utils';

interface Props {
  event: CalendarEvent | null;
  onClose: () => void;
}

type RightView = 'memo' | 'recording' | 'stt';

export default function EventDetailPanel({ event, onClose }: Props) {
  const [rightView, setRightView] = useState<RightView>('memo');
  const [recordSec, setRecordSec] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!event) {
      setRightView('memo');
      setRecordSec(0);
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }
  }, [event]);

  if (!event) return null;

  const catColor = event.category ? CATEGORY_COLOR[event.category] : '#7F77DD';
  const catBg = event.category ? CATEGORY_BG[event.category] : '#ECEBFA';

  const startRec = () => {
    setRightView('recording');
    timerRef.current = setInterval(() => setRecordSec((s) => s + 1), 1000);
  };

  const stopRec = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setRightView('stt');
  };

  const p = (n: number) => String(n).padStart(2, '0');
  const recTime = `${p(Math.floor(recordSec / 60))}:${p(recordSec % 60)}`;

  return (
    <div
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
    >
      <div
        style={{
          width: 680,
          maxHeight: '85vh',
          borderRadius: 12,
          backgroundColor: '#fff',
          boxShadow: '0 20px 60px rgba(0,0,0,.2)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {/* 헤더 */}
        <div
          style={{
            padding: '14px 20px',
            borderBottom: '1px solid #E5E6DE',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}
        >
          <h3
            style={{
              fontSize: 15,
              fontWeight: 700,
              color: '#1F2126',
              margin: 0,
              flex: 1,
              minWidth: 0,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {event.title}
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
            <Chip label="저장" color="#475569" bg="#F1F5F9" />
            {event.pipelineStage && (
              <Chip label={event.pipelineStage.stageName} color={catColor} bg={catBg} />
            )}
            <IconBtn label="수정" onClick={() => {}}>
              ✏
            </IconBtn>
            <IconBtn label="닫기" onClick={onClose}>
              ✕
            </IconBtn>
          </div>
        </div>

        {/* 2-column body */}
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden', minHeight: 0 }}>
          {/* 좌: 메타 정보 */}
          <div
            style={{
              width: 260,
              flexShrink: 0,
              padding: '16px 20px',
              borderRight: '1px solid #E5E6DE',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
            }}
          >
            <MetaRow label="영업 목표">{event.dealTitle ?? <Muted>없음</Muted>}</MetaRow>
            <MetaRow label="고객">
              {event.accountName ? (
                `${event.accountName}${event.attendees?.external[0] ? ` — ${event.attendees.external[0]}` : ''}`
              ) : (
                <Muted>없음</Muted>
              )}
            </MetaRow>
            <MetaRow label="카테고리">
              {event.category ? (
                <span
                  style={{
                    fontSize: 12,
                    color: catColor,
                    backgroundColor: catBg,
                    padding: '2px 8px',
                    borderRadius: 4,
                    fontWeight: 600,
                  }}
                >
                  {event.category}
                </span>
              ) : (
                <Muted>—</Muted>
              )}
            </MetaRow>
            <MetaRow label="파이프라인">
              {event.pipelineStage ? (
                <span style={{ fontSize: 12, color: '#534AB7' }}>
                  {event.pipelineStage.stageName}
                </span>
              ) : (
                <Muted>없음</Muted>
              )}
            </MetaRow>
            <MetaRow label="일정">
              <span style={{ fontSize: 12, color: '#737880' }}>
                {formatFullDate(new Date(event.startAt))} — {formatTime(event.endAt)}
              </span>
            </MetaRow>
            <MetaRow label="생성일">
              <span style={{ fontSize: 12, color: '#737880' }}>
                {new Date(event.startAt).toLocaleDateString('ko-KR')}
              </span>
            </MetaRow>
            <MetaRow label="출처">
              <span
                style={{
                  fontSize: 11,
                  color: SOURCE_STYLE[event.source].dot,
                  backgroundColor: SOURCE_STYLE[event.source].bg,
                  padding: '2px 7px',
                  borderRadius: 4,
                  fontWeight: 500,
                }}
              >
                {SOURCE_STYLE[event.source].label}
              </span>
            </MetaRow>
          </div>

          {/* 우: 뷰 전환 */}
          <div
            style={{
              flex: 1,
              padding: '16px 20px',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}
          >
            {/* 메모 */}
            {rightView === 'memo' && (
              <>
                <SectionLabel>메모</SectionLabel>
                <div
                  style={{
                    fontSize: 13,
                    lineHeight: 1.7,
                    color: event.memo ? '#475569' : '#9CA193',
                    backgroundColor: '#F8F8F5',
                    borderRadius: 8,
                    padding: '12px 14px',
                    minHeight: 120,
                  }}
                >
                  {event.memo ?? '메모 없음'}
                </div>
              </>
            )}

            {/* 녹음 중 */}
            {rightView === 'recording' && (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      backgroundColor: '#EF4444',
                      display: 'inline-block',
                    }}
                  />
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#EF4444' }}>녹음 중</span>
                </div>
                <div
                  style={{
                    backgroundColor: '#F8F8F5',
                    borderRadius: 10,
                    padding: '20px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 12,
                  }}
                >
                  <div
                    style={{
                      fontSize: 36,
                      fontWeight: 700,
                      color: '#EF4444',
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {recTime}
                  </div>
                  <div style={{ display: 'flex', gap: 2, alignItems: 'center', height: 32 }}>
                    {Array.from({ length: 22 }, (_, i) => (
                      <div
                        key={i}
                        style={{
                          width: 3,
                          height: `${8 + Math.abs(Math.sin(i * 0.9)) * 12}px`,
                          backgroundColor: '#EF4444',
                          borderRadius: 2,
                          opacity: 0.85,
                        }}
                      />
                    ))}
                  </div>
                  <button
                    onClick={stopRec}
                    style={{
                      padding: '7px 20px',
                      borderRadius: 20,
                      border: 'none',
                      backgroundColor: '#EF4444',
                      color: '#fff',
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    ● 녹음 중지
                  </button>
                </div>
                <div>
                  <div
                    style={{
                      fontSize: 11,
                      fontWeight: 600,
                      color: '#9CA193',
                      marginBottom: 6,
                      display: 'flex',
                      gap: 6,
                      alignItems: 'center',
                    }}
                  >
                    실시간 전사
                    <span
                      style={{
                        fontSize: 9,
                        color: '#EF4444',
                        fontWeight: 700,
                        border: '1px solid #EF4444',
                        borderRadius: 3,
                        padding: '0 4px',
                      }}
                    >
                      LIVE
                    </span>
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      lineHeight: 1.6,
                      color: '#737880',
                      backgroundColor: '#F8F8F5',
                      padding: '10px 12px',
                      borderRadius: 8,
                      minHeight: 60,
                    }}
                  >
                    <span style={{ color: '#0686D4' }}>
                      안녕하세요, 오늘 30% 매출이요? 분기 전략을 검토중이신가요?
                    </span>
                  </div>
                </div>
              </>
            )}

            {/* STT 완료 */}
            {rightView === 'stt' && (
              <>
                <SectionLabel>딜 요구 사항</SectionLabel>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {[
                    {
                      label: '매출 목표',
                      text: 'AWS 70%, IDC 30% 전환 → 클라우드 이전 완료',
                      color: '#EF4444',
                    },
                    {
                      label: '고객 니즈',
                      text: '비용 절감 이후 채택, 비공개 패밀리 커뮤니케이션',
                      color: '#F59E0B',
                    },
                    { label: '6/30 이후', text: '기한 이후 계약 → 계약 완료', color: '#0686D4' },
                    {
                      label: 'Next step',
                      text: '기술 요구사항 정리 (4월 9일내 제안)',
                      color: '#22C55E',
                    },
                  ].map((r) => (
                    <div
                      key={r.label}
                      style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}
                    >
                      <span
                        style={{
                          fontSize: 10,
                          color: r.color,
                          fontWeight: 600,
                          backgroundColor: r.color + '20',
                          padding: '1px 6px',
                          borderRadius: 3,
                          whiteSpace: 'nowrap',
                          marginTop: 2,
                        }}
                      >
                        {r.label}
                      </span>
                      <span style={{ fontSize: 12, color: '#737880', lineHeight: 1.5 }}>
                        {r.text}
                      </span>
                    </div>
                  ))}
                </div>
                <SectionLabel>녹음 전사</SectionLabel>
                <div
                  style={{
                    backgroundColor: '#F8F8F5',
                    borderRadius: 8,
                    padding: '8px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                >
                  <div style={{ display: 'flex', gap: 1, alignItems: 'center', flex: 1 }}>
                    {Array.from({ length: 30 }, (_, i) => (
                      <div
                        key={i}
                        style={{
                          width: 2,
                          height: `${4 + Math.abs(Math.sin(i * 0.5)) * 5}px`,
                          backgroundColor: '#0686D4',
                          borderRadius: 1,
                        }}
                      />
                    ))}
                  </div>
                  <span style={{ fontSize: 11, color: '#9CA193', whiteSpace: 'nowrap' }}>
                    32:15
                  </span>
                </div>
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                    maxHeight: 160,
                    overflowY: 'auto',
                  }}
                >
                  {[
                    {
                      t: '00:01',
                      hi: true,
                      text: '안녕하세요, 오늘 30% 매출이요? 분기 전략을 검토중이신가요?',
                    },
                    { t: '00:20', hi: false, text: '이 플랫폼은 제품 구조로 세워지실 건가요?' },
                    {
                      t: '00:41',
                      hi: false,
                      text: 'AWS 70%, IDC 30% 구조로, 2026 하반기 최료 최종 3개월 30% 클라우드 이전 완료..',
                    },
                  ].map((line, i) => (
                    <div key={i}>
                      <div style={{ fontSize: 10, color: '#9CA193', marginBottom: 2 }}>
                        {line.t}
                      </div>
                      <div
                        style={{
                          fontSize: 12,
                          lineHeight: 1.5,
                          color: '#737880',
                          backgroundColor: line.hi ? '#E1EDFA' : 'transparent',
                          padding: line.hi ? '6px 8px' : '0',
                          borderRadius: 6,
                        }}
                      >
                        {line.text}
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>

        {/* ✅ AI 브리핑 제거 — 녹음하기만 남김 */}
        <div
          style={{ padding: '10px 20px', borderTop: '1px solid #E5E6DE', display: 'flex', gap: 8 }}
        >
          {rightView === 'memo' && (
            <button
              onClick={startRec}
              style={{
                flex: 1,
                padding: '9px',
                borderRadius: 8,
                border: '1px solid #EF4444',
                backgroundColor: 'transparent',
                color: '#EF4444',
                fontSize: 12,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              ● 녹음하기
            </button>
          )}
          {rightView === 'stt' && (
            <button
              style={{
                flex: 1,
                padding: '9px',
                borderRadius: 8,
                border: '1px solid #E5E6DE',
                backgroundColor: 'transparent',
                color: '#737880',
                fontSize: 12,
                cursor: 'pointer',
              }}
            >
              녹화 작업이 없으면 이거
            </button>
          )}
          {rightView === 'recording' && (
            <div
              style={{
                flex: 1,
                textAlign: 'center',
                fontSize: 12,
                color: '#EF4444',
                fontWeight: 600,
                padding: '9px',
              }}
            >
              녹음 진행 중...
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function MetaRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
      <span style={{ fontSize: 12, color: '#9CA193', width: 64, flexShrink: 0, paddingTop: 1 }}>
        {label}
      </span>
      <div style={{ flex: 1, fontSize: 13, color: '#1F2126' }}>{children}</div>
    </div>
  );
}
function Muted({ children }: { children: ReactNode }) {
  return <span style={{ fontSize: 13, color: '#9CA193' }}>{children}</span>;
}
function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        fontSize: 11,
        fontWeight: 600,
        color: '#9CA193',
        textTransform: 'uppercase',
        letterSpacing: '0.05em',
      }}
    >
      {children}
    </div>
  );
}
function Chip({ label, color, bg }: { label: string; color: string; bg: string }) {
  return (
    <span
      style={{
        fontSize: 11,
        color,
        backgroundColor: bg,
        padding: '2px 8px',
        borderRadius: 4,
        fontWeight: 500,
      }}
    >
      {label}
    </span>
  );
}
function IconBtn({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      style={{
        width: 26,
        height: 26,
        borderRadius: 5,
        border: '1px solid #E5E6DE',
        backgroundColor: 'transparent',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 12,
        color: '#9CA193',
      }}
    >
      {children}
    </button>
  );
}
