'use client';
// src/components/calendar/MonthGrid.tsx
// ✅ 피그마 노드 719:7375 수치 기반
//
// 달력 셀: min-h-[100px], border-[#d6d6d6], border-b border-r
// 셀 패딩: pt-[6px] pl-[6px] pr-[7px] pb-[7px]
// 이전/다음달: opacity-50 bg-[#f5f7fa]
// 날짜 숫자 wrapper: size-[24px] rounded-[12px]
// 날짜 font: 12.7px, #16180f (평일) / #ee5555 (일/토)
// 오늘: bg-[#06b6d4] text-white rounded-[12px]
// 이벤트 카드: border-l-3px catColor, bg catBg, rounded-6px, pl-8px pr-6px py-5px, gap-3px
// 시간: Inter SemiBold 10px #737880  / 제목: Inter SemiBold 12px #1f2126
// Cat pill: bg-white rounded-8px px-6px py-2px gap-3px, dot 5px

import { useEffect, useState } from 'react';
import type { CalendarEvent } from './types';
import { CATEGORY_COLOR, CATEGORY_BG } from './types';
import { getCalendarDays, isToday, isSameDay } from './utils';

interface Props {
  currentDate: Date;
  events: CalendarEvent[];
  selectedDate: Date;
  onDayClick: (d: Date) => void;
  onEventClick: (e: CalendarEvent) => void;
  onMoreClick?: (d: Date) => void;
  /** 여러 날짜 드래그 시 호출 (start <= end, 각각 자정 기준). 단일 셀 클릭은 onDayClick 사용. */
  onDayRangeSelect?: (start: Date, end: Date) => void;
}

function dayKey(d: Date) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}
function isInRange(d: Date, a: Date, b: Date) {
  const [lo, hi] = a.getTime() <= b.getTime() ? [a, b] : [b, a];
  const t = d.getTime();
  return t >= new Date(lo.getFullYear(), lo.getMonth(), lo.getDate()).getTime()
    && t <= new Date(hi.getFullYear(), hi.getMonth(), hi.getDate(), 23, 59, 59).getTime();
}

// Figma: font-family/font-5 = Segoe UI Bold (달력 날짜)
// Figma: Inter:Semi_Bold (이벤트 카드)
const FONT_DATE = "'Pretendard', 'Segoe UI', sans-serif";
const FONT_EVENT = "'Pretendard', 'Inter', sans-serif";
const BORDER_CELL = '#D6D6D6'; // Figma: border-[#d6d6d6]
const BORDER_CAL = '#E5E6DE'; // Figma: var(--color/yellow/89)

const DAY_HEADERS = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];

function fmtTime(iso: string) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

// Figma 719:7601 "월간 일정" 카드
function MonthEventCard({ event, onClick }: { event: CalendarEvent; onClick: () => void }) {
  const col = event.category ? CATEGORY_COLOR[event.category] : '#7F77DD';
  const bg = event.category ? CATEGORY_BG[event.category] : '#ECEBFA';
  return (
    // 1줄 컴팩트: 높이 ~22px → 2개가 flex:1 행 안에 들어감
    <button
      data-event="true"
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      style={{
        width: '100%',
        textAlign: 'left',
        cursor: 'pointer',
        borderLeft: `3px solid ${col}`,
        borderTop: 'none',
        borderRight: 'none',
        borderBottom: 'none',
        borderRadius: 4,
        backgroundColor: bg,
        padding: '3px 6px 3px 6px',
        display: 'flex',
        alignItems: 'center',
        gap: 5,
        overflow: 'hidden',
        marginBottom: 2,
        fontFamily: FONT_EVENT,
      }}
    >
      <span
        style={{
          fontSize: 9,
          color: col,
          fontWeight: 700,
          flexShrink: 0,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {fmtTime(event.startAt)}
      </span>
      <span
        style={{ width: 5, height: 5, borderRadius: '50%', backgroundColor: col, flexShrink: 0 }}
      />
      <span
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: '#1F2126',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          flex: 1,
        }}
      >
        {event.title}
      </span>
    </button>
  );
}

export default function MonthGrid({
  currentDate,
  events,
  selectedDate,
  onDayClick,
  onEventClick,
  onMoreClick,
  onDayRangeSelect,
}: Props) {
  const flat = getCalendarDays(currentDate.getFullYear(), currentDate.getMonth());
  const weeks = Array.from({ length: 6 }, (_, i) => flat.slice(i * 7, i * 7 + 7));
  const active = weeks.filter((w) => w.some((d) => d !== null));
  const cm = currentDate.getMonth();

  // 드래그 상태 — 다일 선택 (구글 캘린더 월간 뷰 패턴)
  const [drag, setDrag] = useState<{ start: Date; current: Date; moved: boolean } | null>(null);

  useEffect(() => {
    if (!drag) return;
    const onUp = () => {
      if (!drag) return;
      const { start, current, moved } = drag;
      if (!moved || dayKey(start) === dayKey(current)) {
        onDayClick(start);
      } else {
        const [a, b] = start.getTime() <= current.getTime() ? [start, current] : [current, start];
        onDayRangeSelect?.(a, b);
      }
      setDrag(null);
    };
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, [drag, onDayClick, onDayRangeSelect]);

  const handleCellMouseDown = (e: React.MouseEvent<HTMLDivElement>, day: Date) => {
    if ((e.target as HTMLElement).closest('[data-event="true"]')) return;
    if (e.button !== 0) return;
    setDrag({ start: day, current: day, moved: false });
  };
  const handleCellMouseEnter = (day: Date) => {
    if (!drag) return;
    if (dayKey(drag.current) === dayKey(day)) return;
    setDrag({ ...drag, current: day, moved: true });
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflow: 'hidden',
        fontFamily: FONT_DATE,
        WebkitFontSmoothing: 'antialiased',
        MozOsxFontSmoothing: 'grayscale',
      }}
    >
      {/* ── 요일 헤더 (Figma: backgroundhorizontalborder3, grid-rows:[27px]) ── */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(7,1fr)',
          gridTemplateRows: '27px',
          borderBottom: `1px solid ${BORDER_CAL}`,
          flexShrink: 0,
          backgroundColor: '#fff',
          textAlign: 'center',
          fontSize: 11,
        }}
      >
        {DAY_HEADERS.map((d, i) => (
          <div
            key={d}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              // Figma: pt-[7px] pb-[8px] px-[8px]
              padding: '7px 8px 8px',
              color: i === 0 || i === 6 ? '#EE5555' : '#888888',
              fontWeight: 600,
              borderRight: i < 6 ? `1px solid ${BORDER_CAL}` : 'none',
            }}
          >
            {d}
          </div>
        ))}
      </div>

      {/* ── 달력 그리드 (Figma: grid-cols-7, grid-rows-5, flex-[1_0_0]) ── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {active.map((week, wi) => (
          <div
            key={wi}
            style={{
              flex: 1,
              display: 'grid',
              gridTemplateColumns: 'repeat(7,1fr)',
              borderBottom: `1px solid ${BORDER_CAL}`,
            }}
          >
            {week.map((day, di) => {
              if (!day) {
                // 이전/다음달 빈 셀: Figma bg-[#f5f7fa] opacity-50 border-b border-r #d6d6d6
                return (
                  <div
                    key={di}
                    style={{
                      borderRight: di < 6 ? `1px solid ${BORDER_CELL}` : 'none',
                      backgroundColor: '#F5F7FA',
                      opacity: 0.5,
                    }}
                  />
                );
              }

              const otherMonth = day.getMonth() !== cm;
              const today = isToday(day);
              const selected = isSameDay(day, selectedDate);
              const dayEvs = events.filter((ev) => {
                const d = new Date(ev.startAt);
                return (
                  d.getFullYear() === day.getFullYear() &&
                  d.getMonth() === day.getMonth() &&
                  d.getDate() === day.getDate()
                );
              });

              const inDragRange = drag && drag.moved && isInRange(day, drag.start, drag.current);

              return (
                <div
                  key={di}
                  onMouseDown={(e) => handleCellMouseDown(e, day)}
                  onMouseEnter={() => handleCellMouseEnter(day)}
                  style={{
                    borderRight: di < 6 ? `1px solid ${BORDER_CELL}` : 'none',
                    backgroundColor: inDragRange ? 'rgba(6, 182, 212, 0.10)' : otherMonth ? '#F5F7FA' : '#FFFFFF',
                    opacity: otherMonth ? 0.5 : 1,
                    padding: '6px 7px 7px 6px',
                    overflow: 'hidden',
                    cursor: drag ? 'crosshair' : 'pointer',
                    userSelect: 'none',
                    display: 'flex',
                    flexDirection: 'column',
                    // 선택 셀 또는 드래그 중인 셀 하이라이트
                    boxShadow: selected
                      ? 'inset 0 0 0 1.5px #06B6D4'
                      : inDragRange
                      ? 'inset 0 0 0 1px #06B6D4'
                      : 'none',
                    transition: 'background-color 0.08s',
                  }}
                >
                  {/* 날짜 숫자: Figma size-[24px] rounded-[12px] */}
                  <div style={{ marginBottom: 4, flexShrink: 0 }}>
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 24,
                        height: 24,
                        borderRadius: 12,
                        // Figma: 오늘 bg-[#06b6d4] text-white / 평일 #16180f / 일토 #ee5555
                        backgroundColor: today ? '#06B6D4' : 'transparent',
                        color: today ? '#ffffff' : di === 0 || di === 6 ? '#EE5555' : '#16180F',
                        // Figma: font-size 12.7px, Segoe UI Bold
                        fontSize: 12.7,
                        fontWeight: 700,
                      }}
                    >
                      {day.getDate()}
                    </span>
                  </div>

                  {/* 이벤트 목록: 2개 표시, 더보기 버튼은 overflow 밖 → 항상 보임 */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {dayEvs.slice(0, 2).map((ev) => (
                      <MonthEventCard
                        key={ev.eventId}
                        event={ev}
                        onClick={() => onEventClick(ev)}
                      />
                    ))}
                  </div>
                  {dayEvs.length > 2 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onMoreClick?.(day);
                      }}
                      style={{
                        fontSize: 10,
                        color: '#0686D4',
                        padding: '1px 4px 2px',
                        fontWeight: 600,
                        flexShrink: 0,
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        textAlign: 'left',
                        lineHeight: 1.4,
                      }}
                    >
                      +{dayEvs.length - 2}개 더보기
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
