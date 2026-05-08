'use client';
// src/components/calendar/WeekGrid.tsx
// 08:00~20:00 표시, borderLeft→boxShadow 통일로 파이프라인/요일 완벽 정렬

import { useState, useEffect, useRef } from 'react';
import type { CalendarEvent } from './types';
import { CATEGORY_COLOR, CATEGORY_BG } from './types';
import { getWeekDays, isToday } from './utils';

export interface PipelineItem {
  label: string;
  dot: string;
  cBg: string;
  cTxt: string;
  count: number;
}

interface Props {
  currentDate: Date;
  events: CalendarEvent[];
  selectedEvent: CalendarEvent | null;
  onEventClick: (e: CalendarEvent) => void;
  onTimeClick?: (dateWithTime: Date) => void;
  pipeline?: readonly PipelineItem[] | PipelineItem[];
}

export const WEEK_TIME_COL = 52;

const START = 8; // 08:00부터
const END = 20; // 20:00까지
const HOURS = Array.from({ length: END - START + 1 }, (_, i) => i + START);
const HOUR_H = 110; // 1시간 높이, 30분=55px → 이벤트 제목+태그 다 보임
const TOTAL = (END - START) * HOUR_H;

const BORDER = '#E5E6DE';
const RED = '#EE5555';
const TIME_C = '#9CA193';
const DAY_FULL = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
const DAY_COLOR = (i: number) => (i === 0 || i === 6 ? RED : '#888888');

function fmtTime(iso: string) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
function sameDay(ev: CalendarEvent, day: Date) {
  const d = new Date(ev.startAt);
  return (
    d.getDate() === day.getDate() &&
    d.getMonth() === day.getMonth() &&
    d.getFullYear() === day.getFullYear()
  );
}

// ─── 구분선: boxShadow(-1px) 방식 통일 ───
// 파이프라인·요일헤더·그리드 본문 모두 동일한 방식 → 완벽 정렬
const dividerShadow = (i: number) => (i > 0 ? `-1px 0 0 0 ${BORDER}` : 'none');

function WeekCard({
  event,
  onClick,
  selected,
}: {
  event: CalendarEvent;
  onClick: () => void;
  selected: boolean;
}) {
  const col = event.category ? CATEGORY_COLOR[event.category] : '#7F77DD';
  const bg = event.category ? CATEGORY_BG[event.category] : '#ECEBFA';
  return (
    <button
      data-event="true"
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      style={{
        position: 'absolute',
        inset: 0,
        width: '100%',
        height: '100%',
        textAlign: 'left',
        cursor: 'pointer',
        backgroundColor: '#F5F7FA',
        borderLeft: `3px solid ${col}`,
        borderTop: 'none',
        borderRight: 'none',
        borderBottom: 'none',
        borderRadius: 8,
        padding: '6px 10px',
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
        overflow: 'hidden',
        outline: selected ? `2px solid ${col}` : 'none',
        outlineOffset: -1,
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#ECEEF2';
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#F5F7FA';
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, overflow: 'hidden' }}>
        <span
          style={{
            fontSize: 10,
            color: col,
            fontWeight: 600,
            flexShrink: 0,
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {fmtTime(event.startAt)}
        </span>
        <span
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: '#1F2126',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {event.title}
        </span>
      </div>
      <div
        style={{
          display: 'flex',
          gap: 4,
          alignItems: 'center',
          overflow: 'hidden',
          flexWrap: 'nowrap',
        }}
      >
        {event.category && (
          <span
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              backgroundColor: bg,
              borderRadius: 8,
              padding: '2px 6px',
              flexShrink: 0,
            }}
          >
            <span style={{ width: 5, height: 5, borderRadius: '50%', backgroundColor: col }} />
            <span style={{ fontSize: 9, fontWeight: 600, color: col }}>{event.category}</span>
          </span>
        )}
        {event.pipelineStage && (
          <span
            style={{
              backgroundColor: '#F0EDF7',
              borderRadius: 4,
              padding: '2px 6px',
              fontSize: 8,
              fontWeight: 600,
              color: '#534AB7',
              flexShrink: 0,
              whiteSpace: 'nowrap',
            }}
          >
            {event.pipelineStage.stageName}
          </span>
        )}
        {event.accountName && (
          <span
            style={{
              backgroundColor: '#EBF2FF',
              borderRadius: 3,
              padding: '1px 4px',
              fontSize: 9,
              fontWeight: 500,
              color: '#3D78D9',
              flexShrink: 0,
              whiteSpace: 'nowrap',
            }}
          >
            {event.accountName}
          </span>
        )}
      </div>
    </button>
  );
}

export default function WeekGrid({
  currentDate,
  events,
  selectedEvent,
  onEventClick,
  onTimeClick,
  pipeline,
}: Props) {
  const [now, setNow] = useState(new Date());
  const [scrollTop, setST] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 30_000);
    if (scrollRef.current) {
      scrollRef.current.scrollTop = 0; // 08:00부터 보임
    }
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const weekDays = getWeekDays(currentDate);
  const redTop = (now.getHours() - START + now.getMinutes() / 60) * HOUR_H;

  const handleColClick = (e: React.MouseEvent<HTMLDivElement>, day: Date) => {
    if ((e.target as HTMLElement).closest('[data-event="true"]')) return;
    if (!onTimeClick) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const y = e.clientY - rect.top + scrollTop;
    const h = Math.min(Math.floor(y / HOUR_H) + START, 23);
    const rawM = Math.round(((y % HOUR_H) / HOUR_H) * 4) * 15;
    const d = new Date(day);
    d.setHours(h, rawM >= 60 ? 45 : rawM, 0, 0);
    onTimeClick(d);
  };

  return (
    <div
      ref={scrollRef}
      onScroll={(e) => setST(e.currentTarget.scrollTop)}
      style={{
        flex: 1,
        height: '100%',
        overflowY: 'auto',
        overflowX: 'hidden',
        backgroundColor: '#fff',
      }}
    >
      {/* ── sticky 헤더 블록 ─────────────────────────────────────── */}
      <div style={{ position: 'sticky', top: 0, zIndex: 10, backgroundColor: '#fff' }}>
        {/* 파이프라인: 시간컬럼 + 7컬럼, boxShadow 구분선 */}
        {pipeline && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: `${WEEK_TIME_COL}px repeat(7, 1fr)`,
              borderBottom: `1px solid ${BORDER}`,
            }}
          >
            <div style={{ borderRight: `1px solid ${BORDER}` }} />
            {pipeline.map((s, i) => (
              <div
                key={s.label}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '6px 10px',
                  boxShadow: dividerShadow(i),
                  overflow: 'hidden',
                  minWidth: 0,
                }}
              >
                <span
                  style={{
                    width: 7,
                    height: 7,
                    borderRadius: '50%',
                    backgroundColor: s.dot,
                    flexShrink: 0,
                  }}
                />
                <span
                  style={{
                    fontSize: 12,
                    fontWeight: 500,
                    color: '#1F2126',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    flex: 1,
                  }}
                >
                  {s.label}
                </span>
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    color: s.cTxt,
                    backgroundColor: s.cBg,
                    padding: '1px 6px',
                    borderRadius: 10,
                    flexShrink: 0,
                  }}
                >
                  {s.count}
                </span>
              </div>
            ))}
          </div>
        )}

        {/* 요일 헤더: 동일한 grid + 동일한 boxShadow */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `${WEEK_TIME_COL}px repeat(7, 1fr)`,
            borderBottom: `1px solid ${BORDER}`,
          }}
        >
          <div style={{ borderRight: `1px solid ${BORDER}` }} />
          {weekDays.map((day, i) => {
            const today = isToday(day);
            const cnt = events.filter((ev) => sameDay(ev, day)).length;
            return (
              <div
                key={i}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '6px 8px 7px',
                  boxShadow: dividerShadow(i), // ← 파이프라인과 동일
                  gap: 5,
                }}
              >
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: DAY_COLOR(i),
                    letterSpacing: '0.02em',
                  }}
                >
                  {DAY_FULL[i]}
                </span>
                <span
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: today ? '#06B6D4' : 'transparent',
                    color: today ? '#fff' : i === 0 || i === 6 ? RED : '#1F2126',
                    fontSize: 13,
                    fontWeight: 700,
                    flexShrink: 0,
                  }}
                >
                  {day.getDate()}
                </span>
                {cnt > 0 && (
                  <span
                    style={{
                      fontSize: 9,
                      color: '#0686D4',
                      fontWeight: 600,
                      backgroundColor: '#EEF6FF',
                      borderRadius: 8,
                      padding: '1px 5px',
                      flexShrink: 0,
                    }}
                  >
                    {cnt}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* ── 그리드 본문 ─────────────────────────────────────────── */}
      <div style={{ display: 'flex', width: '100%' }}>
        {/* 시간 레이블 컬럼 */}
        <div style={{ width: WEEK_TIME_COL, flexShrink: 0, borderRight: `1px solid ${BORDER}` }}>
          {HOURS.map((h) => (
            <div
              key={h}
              style={{ height: HOUR_H, borderTop: `1px solid ${BORDER}`, position: 'relative' }}
            >
              {h < END && (
                <span
                  style={{
                    position: 'absolute',
                    top: 3,
                    right: 6,
                    fontSize: 9,
                    color: TIME_C,
                    fontVariantNumeric: 'tabular-nums',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {`${String(h).padStart(2, '0')}:00`}
                </span>
              )}
            </div>
          ))}
        </div>

        {/* 7 요일 컬럼: boxShadow(-1px) → 파이프라인·요일헤더와 완벽 정렬 */}
        {weekDays.map((day, colIdx) => {
          const dayEvs = events.filter((ev) => sameDay(ev, day));
          return (
            <div
              key={colIdx}
              onClick={(e) => handleColClick(e, day)}
              style={{
                flex: 1,
                boxShadow: dividerShadow(colIdx), // ← borderLeft 대신
                position: 'relative',
                minWidth: 0,
                cursor: 'default',
              }}
            >
              {HOURS.map((h) => (
                <div
                  key={h}
                  style={{ height: HOUR_H, borderTop: `1px solid ${BORDER}`, position: 'relative' }}
                >
                  {/* 30분 구분선 */}
                  {h < END && (
                    <div
                      style={{
                        position: 'absolute',
                        top: HOUR_H / 2,
                        left: 0,
                        right: 0,
                        height: 1,
                        backgroundColor: '#F0F0EE',
                      }}
                    />
                  )}
                </div>
              ))}

              {/* 현재 시각 표시기 */}
              {isToday(day) && redTop >= 0 && redTop <= TOTAL && (
                <div
                  style={{
                    position: 'absolute',
                    top: redTop,
                    left: -4,
                    right: 0,
                    zIndex: 4,
                    display: 'flex',
                    alignItems: 'center',
                    pointerEvents: 'none',
                  }}
                >
                  <div
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 4,
                      backgroundColor: RED,
                      flexShrink: 0,
                    }}
                  />
                  <div style={{ flex: 1, height: 2, backgroundColor: RED }} />
                </div>
              )}

              {/* 이벤트 카드 */}
              {dayEvs.map((ev) => {
                const s = new Date(ev.startAt);
                const end = new Date(ev.endAt);
                const hStart = s.getHours() + s.getMinutes() / 60;
                const hEnd = end.getHours() + end.getMinutes() / 60;
                if (hStart >= END || hEnd <= START) return null; // 08~20 밖 숨김
                const topPx = (Math.max(hStart, START) - START) * HOUR_H;
                const hPx = Math.max(
                  50,
                  (Math.min(hEnd, END) - Math.max(hStart, START)) * HOUR_H - 2,
                );
                return (
                  <div
                    key={ev.eventId}
                    data-event="true"
                    style={{
                      position: 'absolute',
                      top: topPx + 1,
                      left: 1,
                      right: 1,
                      height: hPx,
                      zIndex: 2,
                    }}
                  >
                    <WeekCard
                      event={ev}
                      onClick={() => onEventClick(ev)}
                      selected={selectedEvent?.eventId === ev.eventId}
                    />
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
