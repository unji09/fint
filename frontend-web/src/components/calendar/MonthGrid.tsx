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

import { useEffect, useRef, useState } from 'react';
import type { CalendarEvent } from './types';
import { getEventColor } from './types';
import { getCalendarDays, isToday, isSameDay } from './utils';
import { resizeActivity } from '@/hooks/useCalendarEvents';
import useBreakpoint from '@/hooks/useBreakpoint';

interface Props {
  currentDate: Date;
  events: CalendarEvent[];
  selectedDate: Date;
  onDayClick: (d: Date) => void;
  onEventClick: (e: CalendarEvent) => void;
  onMoreClick?: (d: Date) => void;
  /** 여러 날짜 드래그 시 호출 (start <= end, 각각 자정 기준). 단일 셀 클릭은 onDayClick 사용. */
  onDayRangeSelect?: (start: Date, end: Date) => void;
  /** 카드 가로 리사이즈로 endAt 이 변경되어 PATCH 성공한 직후 호출 (refetch 트리거). */
  onResized?: () => void;
  /** 알림 카드 드롭 시 해당 날짜와 드롭 데이터 전달. */
  onNotificationDrop?: (date: Date, data: string) => void;
}

function fmtKstIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${dd}T${h}:${min}:00+09:00`;
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

const DAY_HEADERS_SHORT = ['일', '월', '화', '수', '목', '금', '토'];

function fmtTime(iso: string) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

// Figma 719:7601 "월간 일정" 카드
// isMulti / isFirst / isLast: 다일 이벤트의 시각적 연결 표시용
// showContent: 시간/제목 텍스트를 이 셀에 표시할지 (다일 이벤트는 row 내 중앙 셀에만 true)
function MonthEventCard({ event, onClick, onMouseDownCard, isMoving, isMulti, isFirst, isLast, showContent, compact }: { event: CalendarEvent; onClick: () => void; onMouseDownCard?: (e: React.MouseEvent) => void; isMoving?: boolean; isMulti?: boolean; isFirst?: boolean; isLast?: boolean; showContent?: boolean; compact?: boolean }) {
  const { color: col, bg } = getEventColor(event);
  const showLeftEdge = !isMulti || isFirst;
  const showRightEdge = !isMulti || isLast;
  const extLeft = compact ? 0 : (showLeftEdge ? 0 : 6);
  const extRight = compact ? 0 : (showRightEdge ? 0 : 7);
  const extraWidth = extLeft + extRight;
  return (
    <button
      data-event="true"
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      onMouseDown={onMouseDownCard}
      style={{
        width: extraWidth > 0 ? `calc(100% + ${extraWidth}px)` : '100%',
        marginLeft: -extLeft,
        cursor: 'pointer',
        borderLeft: showLeftEdge ? `${compact ? 2 : 3}px solid ${col}` : 'none',
        borderTop: 'none',
        borderRight: 'none',
        borderBottom: 'none',
        borderTopLeftRadius: showLeftEdge ? 4 : 0,
        borderBottomLeftRadius: showLeftEdge ? 4 : 0,
        borderTopRightRadius: showRightEdge ? 4 : 0,
        borderBottomRightRadius: showRightEdge ? 4 : 0,
        backgroundColor: bg,
        padding: compact
          ? '2px 4px'
          : isMulti && showContent
          ? '3px 0'
          : showLeftEdge
          ? '3px 6px 3px 6px'
          : '3px 6px 3px 3px',
        display: 'flex',
        alignItems: compact ? 'flex-start' : 'center',
        justifyContent: !compact && isMulti && showContent ? 'center' : 'flex-start',
        textAlign: !compact && isMulti && showContent ? 'center' : 'left',
        gap: compact ? 2 : 5,
        overflow: 'hidden',
        fontFamily: FONT_EVENT,
        position: 'relative',
        opacity: isMoving ? 0.5 : 1,
        minHeight: compact ? 16 : 22,
      }}
    >
      {(showContent || compact) && (
        <>
          {!compact && !isMulti && (
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
          )}
          <span
            style={{
              fontSize: compact ? 9 : 11,
              fontWeight: 600,
              color: '#1F2126',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              flex: isMulti ? 'none' : 1,
              ...(compact
                ? { display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as const, whiteSpace: 'normal' as const, wordBreak: 'break-all' as const, lineHeight: '1.2' }
                : { whiteSpace: 'nowrap' as const }),
            }}
          >
            {event.title}
          </span>
        </>
      )}
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
  onResized,
  onNotificationDrop,
}: Props) {
  const bp = useBreakpoint();
  const isCompact = bp === 'mobile' || bp === 'tablet';

  const flat = getCalendarDays(currentDate.getFullYear(), currentDate.getMonth());
  const weeks = Array.from({ length: 6 }, (_, i) => flat.slice(i * 7, i * 7 + 7));
  const active = weeks.filter((w) => w.some((d) => d !== null));
  const cm = currentDate.getMonth();

  // 드래그 상태 — 다일 선택 (구글 캘린더 월간 뷰 패턴)
  const [drag, setDrag] = useState<{ start: Date; current: Date; moved: boolean } | null>(null);
  // 카드 좌측/우측 핸들 리사이즈. mode='start' → startAt 날짜 변경, 'end' → endAt 날짜 변경.
  const [resize, setResize] = useState<{
    event: CalendarEvent;
    mode: 'start' | 'end';
    startDate: Date;
    endDate: Date;
    currentDate: Date;
  } | null>(null);
  // 카드 본체 이동(move) — 4px 임계치 + 다른 셀 mouseenter 로 대상 날짜 추적.
  const [move, setMove] = useState<{
    event: CalendarEvent;
    origDate: Date;
    curDate: Date;
    startMouseX: number;
    startMouseY: number;
    moved: boolean;
  } | null>(null);

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

  // ref 로 최신 resize 추적 — setResize 마다 effect re-mount race 방지.
  const resizeRef = useRef(resize);
  resizeRef.current = resize;
  useEffect(() => {
    if (!resize) return;
    // mousemove: 마우스 좌표 → 현재 셀 → currentDate 업데이트
    const onMv = (e: MouseEvent) => {
      const r = resizeRef.current;
      if (!r) return;
      const el = (document.elementFromPoint(e.clientX, e.clientY) as HTMLElement | null);
      const cellEl = el?.closest('[data-cell-day]') as HTMLElement | null;
      if (!cellEl) return;
      const dayStr = cellEl.getAttribute('data-cell-day');
      if (!dayStr) return;
      const [y, m, d] = dayStr.split('-').map(Number);
      const day = new Date(y, m - 1, d);
      const dFloor = day.getTime();
      if (r.mode === 'end') {
        const sFloor = new Date(r.startDate.getFullYear(), r.startDate.getMonth(), r.startDate.getDate()).getTime();
        if (dFloor < sFloor) return;
      } else {
        const eFloor = new Date(r.endDate.getFullYear(), r.endDate.getMonth(), r.endDate.getDate()).getTime();
        if (dFloor > eFloor) return;
      }
      if (dayKey(r.currentDate) === dayKey(day)) return;
      setResize({ ...r, currentDate: day });
    };
    const onUp = async () => {
      document.body.style.cursor = '';
      const r = resizeRef.current;
      if (!r) return;
      setResize(null);
      let newStart: Date;
      let newEnd: Date;
      if (r.mode === 'end') {
        newStart = new Date(r.event.startAt);
        newEnd = new Date(r.event.endAt);
        newEnd.setFullYear(r.currentDate.getFullYear(), r.currentDate.getMonth(), r.currentDate.getDate());
      } else {
        newStart = new Date(r.event.startAt);
        newStart.setFullYear(r.currentDate.getFullYear(), r.currentDate.getMonth(), r.currentDate.getDate());
        newEnd = new Date(r.event.endAt);
      }
      const ok = await resizeActivity(r.event.eventId, fmtKstIso(newStart), fmtKstIso(newEnd));
      if (ok) onResized?.();
    };
    window.addEventListener('mousemove', onMv);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMv);
      window.removeEventListener('mouseup', onUp);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [!!resize]);

  // ─── 이동 mousemove(임계치) / mouseup ──────────────────────
  useEffect(() => {
    if (!move) return;
    const onMv = (e: MouseEvent) => {
      if (move.moved) return; // 이미 moved 면 mouseenter 가 처리
      if (Math.abs(e.clientX - move.startMouseX) > 4 || Math.abs(e.clientY - move.startMouseY) > 4) {
        setMove({ ...move, moved: true });
        document.body.style.cursor = 'grabbing';
      }
    };
    const onUp = async () => {
      document.body.style.cursor = '';
      const m = move;
      setMove(null);
      if (!m.moved) return; // 임계치 미만 → button onClick 으로 상세 열림
      const origFloor = new Date(m.origDate.getFullYear(), m.origDate.getMonth(), m.origDate.getDate()).getTime();
      const curFloor = new Date(m.curDate.getFullYear(), m.curDate.getMonth(), m.curDate.getDate()).getTime();
      const dayDelta = Math.round((curFloor - origFloor) / (24 * 60 * 60 * 1000));
      if (dayDelta === 0) return;
      const newStart = new Date(m.event.startAt);
      newStart.setDate(newStart.getDate() + dayDelta);
      const duration = new Date(m.event.endAt).getTime() - new Date(m.event.startAt).getTime();
      const newEnd = new Date(newStart.getTime() + duration);
      const ok = await resizeActivity(m.event.eventId, fmtKstIso(newStart), fmtKstIso(newEnd));
      if (ok) onResized?.();
    };
    window.addEventListener('mousemove', onMv);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMv);
      window.removeEventListener('mouseup', onUp);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [move, onResized]);

  const handleCellMouseDown = (e: React.MouseEvent<HTMLDivElement>, day: Date) => {
    if ((e.target as HTMLElement).closest('[data-event="true"]')) return;
    if ((e.target as HTMLElement).closest('[data-resize="true"]')) return;
    if (e.button !== 0) return;
    setDrag({ start: day, current: day, moved: false });
  };
  const handleCellMouseEnter = (day: Date) => {
    if (move) {
      if (dayKey(move.curDate) === dayKey(day)) return;
      setMove({ ...move, curDate: day });
      return;
    }
    if (resize) {
      const dFloor = new Date(day.getFullYear(), day.getMonth(), day.getDate()).getTime();
      if (resize.mode === 'end') {
        // endDate 변경: startDate 이전 셀은 무시
        const sFloor = new Date(resize.startDate.getFullYear(), resize.startDate.getMonth(), resize.startDate.getDate()).getTime();
        if (dFloor < sFloor) return;
      } else {
        // startDate 변경: endDate 이후 셀은 무시
        const eFloor = new Date(resize.endDate.getFullYear(), resize.endDate.getMonth(), resize.endDate.getDate()).getTime();
        if (dFloor > eFloor) return;
      }
      if (dayKey(resize.currentDate) === dayKey(day)) return;
      setResize({ ...resize, currentDate: day });
      return;
    }
    if (!drag) return;
    if (dayKey(drag.current) === dayKey(day)) return;
    setDrag({ ...drag, current: day, moved: true });
  };
  const startResize = (ev: CalendarEvent, mode: 'start' | 'end', e: React.MouseEvent) => {
    e.stopPropagation();
    if (e.button !== 0) return;
    if (!ev.eventId.startsWith('act-')) return;
    const startDate = new Date(ev.startAt);
    const endDate = new Date(ev.endAt);
    setResize({ event: ev, mode, startDate, endDate, currentDate: mode === 'end' ? endDate : startDate });
    document.body.style.cursor = 'ew-resize';
  };
  const startMove = (ev: CalendarEvent, day: Date, e: React.MouseEvent) => {
    if (e.button !== 0) return;
    if ((e.target as HTMLElement).closest('[data-resize="true"]')) return;
    if (!ev.eventId.startsWith('act-')) return;
    setMove({
      event: ev,
      origDate: day,
      curDate: day,
      startMouseX: e.clientX,
      startMouseY: e.clientY,
      moved: false,
    });
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
        {(isCompact ? DAY_HEADERS_SHORT : DAY_HEADERS).map((d, i) => (
          <div
            key={d}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: isCompact ? '5px 4px 6px' : '7px 8px 8px',
              color: i === 0 || i === 6 ? '#EE5555' : '#888888',
              fontWeight: 600,
              fontSize: isCompact ? 10 : 11,
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
              // 다일 이벤트는 startAt~endAt 범위의 모든 셀에 표시 (사용자가 늘린 결과를 시각적으로 인지)
              const dayFloor = new Date(day.getFullYear(), day.getMonth(), day.getDate()).getTime();
              const dayEvs = events.filter((ev) => {
                const s = new Date(ev.startAt);
                const en = new Date(ev.endAt);
                const sFloor = new Date(s.getFullYear(), s.getMonth(), s.getDate()).getTime();
                const eFloor = new Date(en.getFullYear(), en.getMonth(), en.getDate()).getTime();
                return dayFloor >= sFloor && dayFloor <= eFloor;
              });

              const inDragRange = drag && drag.moved && isInRange(day, drag.start, drag.current);
              // 리사이즈 중 미리보기 범위: end 모드면 startDate~currentDate, start 모드면 currentDate~endDate
              const inResizeRange = resize && (resize.mode === 'end'
                ? isInRange(day, resize.startDate, resize.currentDate)
                : isInRange(day, resize.currentDate, resize.endDate));
              // 이동 중 대상 셀 강조
              const isMoveTarget = move && move.moved && dayKey(day) === dayKey(move.curDate);
              const inHighlight = inDragRange || inResizeRange;

              return (
                <div
                  key={di}
                  data-cell-day={`${day.getFullYear()}-${day.getMonth() + 1}-${day.getDate()}`}
                  onMouseDown={(e) => handleCellMouseDown(e, day)}
                  onMouseEnter={() => handleCellMouseEnter(day)}
                  onDragOver={(e) => {
                    if (e.dataTransfer.types.includes('application/x-fint-notification')) {
                      e.preventDefault();
                      e.dataTransfer.dropEffect = 'copy';
                    }
                  }}
                  onDrop={(e) => {
                    const raw = e.dataTransfer.getData('application/x-fint-notification');
                    if (raw && onNotificationDrop) {
                      e.preventDefault();
                      const d = new Date(day);
                      d.setHours(9, 0, 0, 0);
                      onNotificationDrop(d, raw);
                    }
                  }}
                  style={{
                    // cell 우측 구분선을 borderRight 대신 inset boxShadow 로 cell 안쪽 1px 에 그림 →
                    // 다일 이벤트 카드가 negative margin 으로 이 1px 을 덮어 시각 연결.
                    backgroundColor: isMoveTarget
                      ? 'rgba(6, 182, 212, 0.18)'
                      : inHighlight
                      ? 'rgba(6, 182, 212, 0.10)'
                      : otherMonth
                      ? '#F5F7FA'
                      : '#FFFFFF',
                    opacity: otherMonth ? 0.5 : 1,
                    padding: isCompact ? '4px 3px' : '6px 7px 7px 6px',
                    overflow: 'hidden',
                    cursor: move ? 'grabbing' : resize ? 'ew-resize' : drag ? 'crosshair' : 'pointer',
                    userSelect: 'none',
                    display: 'flex',
                    flexDirection: 'column',
                    // 선택 / 드래그 / 리사이즈 / 이동대상 하이라이트 + cell 우측 구분선
                    boxShadow: [
                      selected ? 'inset 0 0 0 1.5px #06B6D4' : isMoveTarget ? 'inset 0 0 0 2px #06B6D4' : inHighlight ? 'inset 0 0 0 1px #06B6D4' : '',
                      di < 6 ? `inset -1px 0 0 ${BORDER_CELL}` : '',
                    ].filter(Boolean).join(', ') || 'none',
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

                  {/* 이벤트 목록 */}
                  {isCompact ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                      {dayEvs.slice(0, 2).map((ev) => (
                        <MonthEventCard
                          key={ev.eventId}
                          event={ev}
                          onClick={() => onEventClick(ev)}
                          compact
                        />
                      ))}
                      {dayEvs.length > 2 && (
                        <span style={{ fontSize: 8, color: '#0686D4', fontWeight: 600 }}>
                          +{dayEvs.length - 2}
                        </span>
                      )}
                    </div>
                  ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {dayEvs.slice(0, 2).map((ev) => {
                      const s = new Date(ev.startAt);
                      const en = new Date(ev.endAt);
                      const sFloor = new Date(s.getFullYear(), s.getMonth(), s.getDate()).getTime();
                      const eFloor = new Date(en.getFullYear(), en.getMonth(), en.getDate()).getTime();
                      const isMulti = sFloor !== eFloor;
                      const isFirst = dayFloor === sFloor;
                      const isLast = dayFloor === eFloor;
                      const DAY_MS = 24 * 60 * 60 * 1000;
                      const weekStart = week[0];
                      const weekEnd = week[6];
                      let isRowMid = !isMulti;
                      if (isMulti && weekStart && weekEnd) {
                        const wsFloor = new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate()).getTime();
                        const weFloor = new Date(weekEnd.getFullYear(), weekEnd.getMonth(), weekEnd.getDate()).getTime();
                        const evRowStart = Math.max(sFloor, wsFloor);
                        const evRowEnd = Math.min(eFloor, weFloor);
                        const evStartDi = Math.round((evRowStart - wsFloor) / DAY_MS);
                        const evEndDi = Math.round((evRowEnd - wsFloor) / DAY_MS);
                        const midDi = Math.floor((evStartDi + evEndDi) / 2);
                        isRowMid = di === midDi;
                      }
                      return (
                        <div key={ev.eventId} style={{ position: 'relative' }}>
                          <MonthEventCard
                            event={ev}
                            onClick={() => onEventClick(ev)}
                            onMouseDownCard={
                              ev.eventId.startsWith('act-')
                                ? (e) => startMove(ev, day, e)
                                : undefined
                            }
                            isMoving={move?.event.eventId === ev.eventId && move.moved}
                            isMulti={isMulti}
                            isFirst={isFirst}
                            isLast={isLast}
                            showContent={isRowMid}
                          />
                          {ev.eventId.startsWith('act-') && isFirst && (
                            <span
                              data-resize="true"
                              onMouseDown={(e) => startResize(ev, 'start', e)}
                              onMouseEnter={(e) => { (e.currentTarget as HTMLSpanElement).style.backgroundColor = 'rgba(6,182,212,0.18)'; }}
                              onMouseLeave={(e) => { (e.currentTarget as HTMLSpanElement).style.backgroundColor = 'transparent'; }}
                              style={{ position: 'absolute', top: 0, bottom: 0, left: 0, width: 12, cursor: 'ew-resize', zIndex: 6, transition: 'background-color .12s', borderRadius: '4px 0 0 4px' }}
                            />
                          )}
                          {ev.eventId.startsWith('act-') && isLast && (
                            <span
                              data-resize="true"
                              onMouseDown={(e) => startResize(ev, 'end', e)}
                              onMouseEnter={(e) => { (e.currentTarget as HTMLSpanElement).style.backgroundColor = 'rgba(6,182,212,0.18)'; }}
                              onMouseLeave={(e) => { (e.currentTarget as HTMLSpanElement).style.backgroundColor = 'transparent'; }}
                              style={{ position: 'absolute', top: 0, bottom: 0, right: isLast && !isFirst ? -7 : 0, width: 12, cursor: 'ew-resize', zIndex: 6, transition: 'background-color .12s', borderRadius: '0 4px 4px 0' }}
                            />
                          )}
                        </div>
                      );
                    })}
                  </div>
                  )}
                  {!isCompact && dayEvs.length > 2 && (
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
