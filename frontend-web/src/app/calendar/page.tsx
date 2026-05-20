'use client';
// src/app/calendar/page.tsx
// ✅ 피그마 노드 719:7375 수치 기반
//
// GNB: h-[90px] bg-white border-b #e2e8f0
// Aside: w-[300px] border-r #e5e6de
// 파이프라인: 각 항목 w-[229.571px] px-[16px] gap-[8px] / divider h-[28px] bg-[#ebedf0]
// 달력헤더: h-[52px] / 월이름 Segoe_UI:Bold 16px #16180f / 연도 Segoe_UI:Regular #888
// 뷰토글: border-[#e5e6de] rounded-[8px] / 활성 bg-[#06b6d4] / 비활성 Arial 12px #888
// 오늘버튼: bg-[#f5f7fa] border-[#e5e6de] rounded-[6px] pt-[6px] pb-[7px] px-[11px]
// ‹ › : Pretendard 18px #6d7164

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import MonthGrid from '@/components/calendar/MonthGrid';
import WeekGrid, { PipelineItem } from '@/components/calendar/WeekGrid';
import EventDetailPanel from '@/components/calendar/EventDetailPanel';
import AddEventModal from '@/components/calendar/AddEventModal';
import type { CalendarEvent, ViewMode } from '@/components/calendar/types';
import { getEventColor } from '@/components/calendar/types';
import { useCalendarEvents, fetchEventDetail, resizeActivity } from '@/hooks/useCalendarEvents';
import useBreakpoint, { TABLET_MAX } from '@/hooks/useBreakpoint';
import {
  addMonths,
  addWeeks,
  getEventsForDay,
  getWeekDays,
  isToday,
  isSameDay,
} from '@/components/calendar/utils';

// ── 피그마 수치 상수 ─────────────────────────────────────────
const BG = '#F8F8FA'; // 페이지 배경 (neutral gray)
const WHITE = '#FFFFFF';
const BORDER = '#E5E6DE'; // Figma: var(--color/yellow/89, #e5e6de)

const TXT1 = '#1F2126';
const TXT2 = '#737880';
const TEAL = '#06B6D4'; // 활성 선택
const BRAND = '#0686D4'; // FAB
const MUTED = '#BBBBBB';
const RED = '#EE5555';

// Figma fonts
const F_PRETENDARD = "'Pretendard', -apple-system, sans-serif";
const F_INTER = "'Inter', 'Noto Sans KR', sans-serif";
const F_SEGOE = "'Pretendard', 'Segoe UI', sans-serif"; // 달력 날짜 폰트

// ── 파이프라인 스타일 — DB pipeline_stages 와 1:1 매칭 (한글 stage 이름 사용)
const PIPELINE_STYLES = [
  { label: '발굴',        code: '발굴',        dot: '#8CB2E5', cBg: '#EEF4FB', cTxt: '#8CB2E5' },
  { label: '가치 제안',   code: '가치 제안',   dot: '#738CD9', cBg: '#EAEEF9', cTxt: '#738CD9' },
  { label: '솔루션 설계', code: '솔루션 설계', dot: '#806BC7', cBg: '#ECE9F7', cTxt: '#806BC7' },
  { label: '제안 제출',   code: '제안 제출',   dot: '#6B5CBF', cBg: '#E9E7F5', cTxt: '#6B5CBF' },
  { label: '협상',        code: '협상',        dot: '#997333', cBg: '#F0EAE0', cTxt: '#997333' },
  { label: '계약 대기',   code: '계약 대기',   dot: '#339E80', cBg: '#E0F0EC', cTxt: '#339E80' },
  { label: '수주',        code: '수주',        dot: '#268C66', cBg: '#DEEEE8', cTxt: '#268C66' },
];

// ── 미니 주간 (일~토) ─────────────────────────────────────────
const WK = ['일', '월', '화', '수', '목', '금', '토'];
const MONTHS_KO = [
  '1월',
  '2월',
  '3월',
  '4월',
  '5월',
  '6월',
  '7월',
  '8월',
  '9월',
  '10월',
  '11월',
  '12월',
];


// ── Aside 이벤트 카드 (Figma 719:7431)
// bg-[#f6f7f9] border-[catBg] rounded-[10px] px-[14px] py-[12px] gap-[8px]
// 제목: Inter:SemiBold 14px #1f2126
// Cat pill: bg-catBg, rounded-[10px], dot 6px rounded-[3px]
// Pipeline: bg-[#f0edf7] rounded-[3px] px-[8px] py-[3px], progress bar
function AsideCard({ event, onClick, height }: { event: CalendarEvent; onClick: () => void; height: number }) {
  const { color: col, bg } = getEventColor(event);
  const pad = 24;
  const titleH = 22;
  const badgeH = 22;
  const gap = 8;
  const inner = height - pad;
  const showPipeline = inner >= titleH + gap + badgeH;
  const showCategory = inner >= titleH + gap + badgeH + gap + badgeH;

  return (
    <button
      onClick={onClick}
      style={{
        width: '100%',
        height: '100%',
        textAlign: 'left',
        cursor: 'pointer',
        borderRadius: 10,
        backgroundColor: bg,
        border: `1px solid ${col}`,
        padding: '12px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        flexShrink: 0,
        overflow: 'hidden',
        position: 'relative',
        fontFamily: F_INTER,
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLButtonElement).style.filter = 'brightness(0.95)';
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLButtonElement).style.filter = '';
      }}
    >
      <span
        style={{
          fontSize: 12,
          fontWeight: 600,
          color: TXT1,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          display: 'block',
          minWidth: 0,
        }}
      >
        {event.title}
      </span>
      {showCategory && event.category && (
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            fontSize: 9,
            color: col,
          }}
        >
          <span style={{ width: 5, height: 5, borderRadius: '50%', backgroundColor: col, flexShrink: 0 }} />
          {event.category}
        </span>
      )}
      {showPipeline && event.pipelineStage && (
        <span
          style={{
            backgroundColor: 'rgba(255,255,255,0.6)',
            borderRadius: 4,
            padding: '2px 6px',
            fontSize: 7,
            color: col,
            fontWeight: 600,
            whiteSpace: 'nowrap',
            alignSelf: 'flex-start',
          }}
        >
          {event.pipelineStage.stageName}
        </span>
      )}
    </button>
  );
}

// ── Aside 시간뷰 ──────────────────────────────────────────────
const A_H = 80;
const A_S = 8; // 00:00부터 전체 시간

// KST ISO 직렬화 (백엔드 PATCH 용)
function fmtKstIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${dd}T${h}:${min}:00+09:00`;
}

function DayTimeView({
  events,
  selectedDate,
  onEventClick,
  onTimeClick,
  onTimeRangeSelect,
  onResized,
  onNotificationDrop,
}: {
  events: CalendarEvent[];
  selectedDate: Date;
  onEventClick: (e: CalendarEvent) => void;
  onTimeClick?: (d: Date) => void;
  onTimeRangeSelect?: (start: Date, end: Date) => void;
  onResized?: () => void;
  onNotificationDrop?: (date: Date, data: string) => void;
}) {
  // SSR 시점의 시각과 client hydration 시점의 시각이 달라 hydration mismatch 가 발생할 수 있다.
  // 따라서 초기엔 null 로 두고 mount 후에만 현재 시각 라인을 렌더한다.
  const [now, setNow] = useState<Date | null>(null);
  useEffect(() => {
    setNow(new Date());
    const t = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(t);
  }, []);
  const HOURS = Array.from({ length: 15 }, (_, i) => i + A_S); // 08~22시
  const total = HOURS.length * A_H;
  const red = now ? (now.getHours() - A_S + now.getMinutes() / 60) * A_H : -1;

  // ─── 드래그 (WeekGrid 와 동일 패턴, 단일 컬럼) ────────────
  const [drag, setDrag] = useState<{ startY: number; currentY: number; moved: boolean } | null>(null);
  // 카드 상단/하단 핸들 리사이즈. mode='start' 면 startAt 변경, 'end' 면 endAt 변경.
  const [resize, setResize] = useState<{
    event: CalendarEvent;
    mode: 'start' | 'end';
    startY: number;
    endY: number;
    currentY: number;
    grabOffset: number;
  } | null>(null);
  // 카드 본체 이동(move) — 4px 이상 움직이면 시간만 shift.
  const [move, setMove] = useState<{
    event: CalendarEvent;
    startMouseY: number;
    curMouseY: number;
    moved: boolean;
  } | null>(null);
  const justMovedRef = useRef(false);
  const colRef = useRef<HTMLDivElement>(null);

  const yToDate = (y: number): Date => {
    const clamped = Math.max(0, Math.min(y, total));
    const totalMinutes = (clamped / A_H) * 60;
    const snapped = Math.round(totalMinutes / 15) * 15;
    const d = new Date(selectedDate);
    const fromStart = A_S * 60 + snapped;
    d.setHours(Math.floor(fromStart / 60), fromStart % 60, 0, 0);
    return d;
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest('[data-event="true"]')) return;
    if ((e.target as HTMLElement).closest('[data-resize="true"]')) return;
    if (e.button !== 0) return;
    if (!colRef.current) return;
    const rect = colRef.current.getBoundingClientRect();
    const y = e.clientY - rect.top;
    setDrag({ startY: y, currentY: y, moved: false });
  };

  const startResize = (ev: CalendarEvent, mode: 'start' | 'end', e: React.MouseEvent) => {
    e.stopPropagation();
    if (e.button !== 0) return;
    if (!ev.eventId.startsWith('act-')) return;
    const s = new Date(ev.startAt);
    const en = new Date(ev.endAt);
    const startY = (s.getHours() - A_S + s.getMinutes() / 60) * A_H + 4;
    const endY = (en.getHours() - A_S + en.getMinutes() / 60) * A_H + 4;
    let grabOffset = 0;
    if (colRef.current) {
      const rect = colRef.current.getBoundingClientRect();
      const mouseY = e.clientY - rect.top;
      grabOffset = mouseY - (mode === 'end' ? endY : startY);
    }
    setResize({ event: ev, mode, startY, endY, currentY: mode === 'end' ? endY : startY, grabOffset });
    document.body.style.cursor = 'ns-resize';
    document.body.style.userSelect = 'none';
  };

  const startMove = (ev: CalendarEvent, e: React.MouseEvent) => {
    if (e.button !== 0) return;
    if ((e.target as HTMLElement).closest('[data-resize="true"]')) return;
    if (!ev.eventId.startsWith('act-')) return;
    setMove({ event: ev, startMouseY: e.clientY, curMouseY: e.clientY, moved: false });
  };

  useEffect(() => {
    if (!drag) return;
    const onMove = (e: MouseEvent) => {
      if (!colRef.current) return;
      const rect = colRef.current.getBoundingClientRect();
      const y = e.clientY - rect.top;
      const moved = drag.moved || Math.abs(y - drag.startY) > 4;
      setDrag({ ...drag, currentY: y, moved });
    };
    const onUp = () => {
      if (!drag) return;
      const top = Math.min(drag.startY, drag.currentY);
      const bot = Math.max(drag.startY, drag.currentY);
      if (!drag.moved) {
        onTimeClick?.(yToDate(top));
      } else {
        const s = yToDate(top);
        let ed = yToDate(bot);
        if (ed.getTime() - s.getTime() < 15 * 60_000) ed = new Date(s.getTime() + 15 * 60_000);
        if (onTimeRangeSelect) onTimeRangeSelect(s, ed);
        else onTimeClick?.(s);
      }
      setDrag(null);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drag]);

  // ─── 이동 전역 mousemove / mouseup ─────────────────────────
  useEffect(() => {
    if (!move) return;
    const onMv = (e: MouseEvent) => {
      const dy = e.clientY - move.startMouseY;
      const moved = move.moved || Math.abs(dy) > 4;
      setMove({ ...move, curMouseY: e.clientY, moved });
      if (moved) document.body.style.cursor = 'grabbing';
    };
    const onUp = async () => {
      document.body.style.cursor = '';
      const m = move;
      setMove(null);
      if (!m.moved) return;
      justMovedRef.current = true;
      const dy = m.curMouseY - m.startMouseY;
      const minutesDelta = Math.round((dy / A_H) * 60 / 15) * 15;
      const newStart = new Date(m.event.startAt);
      newStart.setMinutes(newStart.getMinutes() + minutesDelta);
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
  }, [move]);

  // ─── 리사이즈 전역 mousemove / mouseup ────────────────────
  // setResize 마다 effect re-mount 되는 race 를 방지하기 위해 ref 로 최신 resize 추적.
  const resizeRef = useRef(resize);
  resizeRef.current = resize;
  useEffect(() => {
    if (!resize) return;
    const onMove = (e: MouseEvent) => {
      const r = resizeRef.current;
      if (!r) return;
      if (!colRef.current) return;
      const rect = colRef.current.getBoundingClientRect();
      const mouseY = e.clientY - rect.top;
      const targetY = mouseY - r.grabOffset;
      let clamped: number;
      if (r.mode === 'end') {
        const minY = r.startY + A_H / 4;
        clamped = Math.max(minY, Math.min(total, targetY));
      } else {
        const maxY = r.endY - A_H / 4;
        clamped = Math.min(maxY, Math.max(0, targetY));
      }
      if (Math.abs(clamped - r.currentY) < 1) return;
      setResize({ ...r, currentY: clamped });
    };
    const onUp = async () => {
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      const r = resizeRef.current;
      if (!r) return;
      setResize(null);
      let newStart: Date;
      let newEnd: Date;
      if (r.mode === 'end') {
        newStart = new Date(r.event.startAt);
        newEnd = yToDate(r.currentY - 4);
      } else {
        newStart = yToDate(r.currentY - 4);
        newEnd = new Date(r.event.endAt);
      }
      const ok = await resizeActivity(
        r.event.eventId,
        fmtKstIso(newStart),
        fmtKstIso(newEnd),
      );
      if (ok) onResized?.();
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [!!resize]);

  const dragTop = drag ? Math.min(drag.startY, drag.currentY) : 0;
  const dragH = drag ? Math.abs(drag.currentY - drag.startY) : 0;
  return (
    <div style={{ flex: 1, overflowY: 'auto', backgroundColor: WHITE }}>
      <div
        ref={colRef}
        onMouseDown={handleMouseDown}
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
            const rect = e.currentTarget.getBoundingClientRect();
            const y = e.clientY - rect.top;
            onNotificationDrop(yToDate(y), raw);
          }
        }}
        style={{ position: 'relative', height: total, cursor: 'crosshair', userSelect: 'none' }}
      >
        {/* 드래그 highlight overlay */}
        {drag && drag.moved && (
          <div
            style={{
              position: 'absolute',
              top: dragTop,
              left: 44,
              right: 6,
              height: Math.max(dragH, 1),
              backgroundColor: 'rgba(6, 182, 212, 0.18)',
              border: '1px solid #06B6D4',
              borderRadius: 6,
              zIndex: 3,
              pointerEvents: 'none',
              padding: '3px 6px',
              fontSize: 10,
              fontWeight: 700,
              color: '#06B6D4',
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {(() => {
              const s = yToDate(Math.min(drag.startY, drag.currentY));
              const e = yToDate(Math.max(drag.startY, drag.currentY));
              const fmt = (d: Date) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
              return `${fmt(s)} – ${fmt(e)}`;
            })()}
          </div>
        )}
        {HOURS.map((h, i) => (
          <div
            key={h}
            style={{
              position: 'absolute',
              top: i * A_H,
              left: 0,
              right: 0,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <div
              style={{
                width: 44,
                textAlign: 'right',
                paddingRight: 6,
                fontSize: 10,
                color: MUTED,
                flexShrink: 0,
                fontFamily: F_SEGOE,
                whiteSpace: 'nowrap',
              }}
            >{`${String(h).padStart(2, '0')}:00`}</div>
            <div style={{ flex: 1, height: 1, backgroundColor: BORDER }} />
          </div>
        ))}
        {now && red >= 0 && red <= total && (
          <div
            style={{
              position: 'absolute',
              top: red,
              left: 0,
              right: 0,
              display: 'flex',
              alignItems: 'center',
              zIndex: 3,
            }}
          >
            <div
              style={{
                width: 44,
                textAlign: 'right',
                paddingRight: 6,
                fontSize: 10,
                color: MUTED,
                flexShrink: 0,
                whiteSpace: 'nowrap',
              }}
            >{`${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`}</div>
            <div
              style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: RED, flexShrink: 0 }}
            />
            <div style={{ flex: 1, height: 2, backgroundColor: RED }} />
          </div>
        )}
        {(() => {
          const sorted = [...events].sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());
          const colAssign = new Map<string, { colIdx: number; totalCols: number }>();
          const groups: CalendarEvent[][] = [];
          let group: CalendarEvent[] = [];
          let groupEnd = 0;
          for (const ev of sorted) {
            const s = new Date(ev.startAt).getTime();
            if (group.length === 0 || s < groupEnd) {
              group.push(ev);
              groupEnd = Math.max(groupEnd, new Date(ev.endAt).getTime());
            } else {
              groups.push(group);
              group = [ev];
              groupEnd = new Date(ev.endAt).getTime();
            }
          }
          if (group.length) groups.push(group);
          for (const g of groups) {
            const colEnds: number[] = [];
            const cols: number[] = [];
            for (const ev of g) {
              const evS = new Date(ev.startAt).getTime();
              let placed = -1;
              for (let c = 0; c < colEnds.length; c++) {
                if (evS >= colEnds[c]) { placed = c; break; }
              }
              if (placed === -1) { placed = colEnds.length; colEnds.push(0); }
              colEnds[placed] = new Date(ev.endAt).getTime();
              cols.push(placed);
            }
            const totalCols = colEnds.length;
            g.forEach((ev, i) => colAssign.set(ev.eventId, { colIdx: cols[i], totalCols }));
          }
          return events.map((ev) => {
          const s = new Date(ev.startAt);
          const en = new Date(ev.endAt);
          const top = (s.getHours() - A_S + s.getMinutes() / 60) * A_H + 4;
          if (top < 0 || top > total) return null;
          const isResizing = resize?.event.eventId === ev.eventId;
          const isMoving = move?.event.eventId === ev.eventId && move.moved;
          const sH = s.getHours() + s.getMinutes() / 60;
          const eH = en.getHours() + en.getMinutes() / 60;
          const naturalHeight = Math.max(50, (eH - sH) * A_H - 2);
          let wrapperTop: number | undefined = top;
          let wrapperHeight: number = naturalHeight;
          if (isResizing) {
            if (resize.mode === 'end') {
              wrapperTop = top;
              wrapperHeight = Math.max(40, resize.currentY - resize.startY);
            } else {
              wrapperTop = resize.currentY;
              wrapperHeight = Math.max(40, resize.endY - resize.currentY);
            }
          }
          const moveTransform = isMoving ? `translateY(${move.curMouseY - move.startMouseY}px)` : undefined;
          const ol = colAssign.get(ev.eventId) ?? { colIdx: 0, totalCols: 1 };
          const colW = ol.totalCols > 1 ? `calc((100% - 56px) / ${ol.totalCols})` : undefined;
          const colL = ol.totalCols > 1 ? `calc(50px + (100% - 56px) * ${ol.colIdx} / ${ol.totalCols})` : 50;
          return (
            <div
              key={ev.eventId}
              data-event="true"
              onMouseDown={(e) => startMove(ev, e)}
              style={{
                position: 'absolute',
                top: wrapperTop,
                left: colL,
                width: colW,
                right: ol.totalCols > 1 ? undefined : 6,
                height: wrapperHeight,
                zIndex: isMoving ? 10 : isResizing ? 4 : 1,
                transform: moveTransform,
                opacity: isMoving ? 0.85 : 1,
                cursor: ev.eventId.startsWith('act-') ? (isMoving ? 'grabbing' : 'grab') : undefined,
              }}
            >
              <AsideCard
                event={ev}
                onClick={() => {
                  if (justMovedRef.current) { justMovedRef.current = false; return; }
                  onEventClick(ev);
                }}
                height={wrapperHeight}
              />
              {/* 리사이즈 핸들 — button 밖 형제 div (이벤트 충돌 방지). FINT 활동만. */}
              {ev.eventId.startsWith('act-') && (
                <>
                  <div
                    data-resize="true"
                    onMouseDown={(e) => startResize(ev, 'start', e)}
                    onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'rgba(6,182,212,0.18)'; }}
                    onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent'; }}
                    style={{ position: 'absolute', left: 0, right: 0, top: 0, height: 12, cursor: 'ns-resize', zIndex: 6, transition: 'background-color .12s', borderRadius: '10px 10px 0 0' }}
                  />
                  <div
                    data-resize="true"
                    onMouseDown={(e) => startResize(ev, 'end', e)}
                    onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'rgba(6,182,212,0.18)'; }}
                    onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent'; }}
                    style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 12, cursor: 'ns-resize', zIndex: 6, transition: 'background-color .12s', borderRadius: '0 0 10px 10px' }}
                  />
                </>
              )}
              {isResizing && (
                <div
                  style={{
                    position: 'absolute',
                    [resize.mode === 'end' ? 'bottom' : 'top']: 2,
                    right: 6,
                    fontSize: 10,
                    fontWeight: 700,
                    color: '#06B6D4',
                    fontVariantNumeric: 'tabular-nums',
                    pointerEvents: 'none',
                    backgroundColor: 'rgba(255,255,255,.9)',
                    padding: '1px 4px',
                    borderRadius: 4,
                    zIndex: 6,
                  }}
                >
                  {(() => {
                    const t = yToDate(resize.currentY - 4);
                    const f = `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`;
                    return resize.mode === 'end' ? `~ ${f}` : `${f} ~`;
                  })()}
                </div>
              )}
            </div>
          );
        });
        })()}
      </div>
    </div>
  );
}

// ── 날짜 빠른 이동 팝업 ────────────────────────────────────────
function DatePicker({
  curr,
  onSel,
  onClose,
}: {
  curr: Date;
  onSel: (d: Date) => void;
  onClose: () => void;
}) {
  const [year, setYear] = useState(curr.getFullYear());
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [onClose]);
  return (
    <div
      ref={ref}
      style={{
        position: 'absolute',
        top: 46,
        left: 0,
        zIndex: 200,
        backgroundColor: WHITE,
        borderRadius: 12,
        border: `1px solid ${BORDER}`,
        boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
        padding: 16,
        width: 220,
        fontFamily: F_PRETENDARD,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 12,
        }}
      >
        <button
          onClick={() => setYear((y) => y - 1)}
          style={{
            width: 28,
            height: 28,
            border: `1px solid ${BORDER}`,
            borderRadius: 6,
            background: 'transparent',
            cursor: 'pointer',
            fontSize: 14,
            color: TXT2,
          }}
        >
          ‹
        </button>
        <span style={{ fontSize: 15, fontWeight: 700, color: TXT1 }}>{year}년</span>
        <button
          onClick={() => setYear((y) => y + 1)}
          style={{
            width: 28,
            height: 28,
            border: `1px solid ${BORDER}`,
            borderRadius: 6,
            background: 'transparent',
            cursor: 'pointer',
            fontSize: 14,
            color: TXT2,
          }}
        >
          ›
        </button>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 4 }}>
        {MONTHS_KO.map((m, i) => {
          const a = i === curr.getMonth() && year === curr.getFullYear();
          return (
            <button
              key={m}
              onClick={() => {
                onSel(new Date(year, i, 1));
                onClose();
              }}
              style={{
                padding: '7px 0',
                borderRadius: 8,
                border: 'none',
                cursor: 'pointer',
                fontSize: 12,
                fontWeight: a ? 700 : 400,
                backgroundColor: a ? TEAL : 'transparent',
                color: a ? WHITE : TXT1,
              }}
              onMouseEnter={(e) => {
                if (!a) (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#F0F2F5';
              }}
              onMouseLeave={(e) => {
                if (!a)
                  (e.currentTarget as HTMLButtonElement).style.backgroundColor = 'transparent';
              }}
            >
              {m}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────
export default function CalendarPage() {
  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';
  const isTablet = bp === 'tablet';
  const isCompact = isMobile || isTablet;
  const [viewMode, setViewModeRaw] = useState<ViewMode>('month');
  const setViewMode = (m: ViewMode) => {
    if (m === 'day' && !isCompact) return;
    setViewModeRaw(m);
  };
  useEffect(() => {
    if (!isCompact && viewMode === 'day') setViewModeRaw('week');
  }, [isCompact, viewMode]);
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [addDate, setAddDate] = useState<Date | undefined>();
  const [addEndDate, setAddEndDate] = useState<Date | undefined>();
  const [asideOpen, setAsideOpen] = useState(true);
  const [showPicker, setShowPicker] = useState(false);

  const [asideWidth, setAsideWidth] = useState(300); // Figma: w-[300px]
  const [editEvent, setEditEvent] = useState<CalendarEvent | null>(null);

  // ── API 연동 ──────────────────────────────────────────────────
  const { events: apiEvents, refetch } = useCalendarEvents({ currentDate, viewMode });

  // ── 파이프라인 필터 ──────────────────────────────────────────
  const [selectedPipeline, setSelectedPipeline] = useState<string | null>(null);

  const pipeline: PipelineItem[] = useMemo(() => {
    const counts: Record<string, number> = {};
    if (viewMode === 'month') {
      const y = currentDate.getFullYear();
      const m = currentDate.getMonth();
      apiEvents.forEach((ev) => {
        const stage = ev.pipelineStage?.stageName;
        if (!stage) return;
        const d = new Date(ev.startAt);
        if (d.getFullYear() === y && d.getMonth() === m) {
          counts[stage] = (counts[stage] ?? 0) + 1;
        }
      });
    } else {
      const weekDays = getWeekDays(currentDate);
      const wStart = weekDays[0].getTime();
      const wEnd = new Date(weekDays[6]).setHours(23, 59, 59, 999);
      apiEvents.forEach((ev) => {
        const stage = ev.pipelineStage?.stageName;
        if (!stage) return;
        const t = new Date(ev.startAt).getTime();
        if (t >= wStart && t <= wEnd) {
          counts[stage] = (counts[stage] ?? 0) + 1;
        }
      });
    }
    return PIPELINE_STYLES.map((s) => ({ ...s, count: counts[s.label] ?? 0 }));
  }, [apiEvents, currentDate, viewMode]);

  const events = useMemo(() => {
    if (!selectedPipeline) return apiEvents;
    return apiEvents.filter((ev) => ev.pipelineStage?.stageName === selectedPipeline);
  }, [apiEvents, selectedPipeline]);

  const dragRef = useRef<{ on: boolean; x0: number; w0: number }>({ on: false, x0: 0, w0: 300 });
  const onDragStart = useCallback(
    (e: React.MouseEvent) => {
      dragRef.current = { on: true, x0: e.clientX, w0: asideWidth };
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    },
    [asideWidth],
  );
  useEffect(() => {
    const mv = (e: MouseEvent) => {
      if (!dragRef.current.on) return;
      setAsideWidth(
        Math.max(220, Math.min(480, dragRef.current.w0 + (e.clientX - dragRef.current.x0))),
      );
    };
    const up = () => {
      dragRef.current.on = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
    window.addEventListener('mousemove', mv);
    window.addEventListener('mouseup', up);
    return () => {
      window.removeEventListener('mousemove', mv);
      window.removeEventListener('mouseup', up);
    };
  }, []);

  const goPrev = () => {
    if (viewMode === 'month') setCurrentDate((d) => addMonths(d, -1));
    else if (viewMode === 'week') setCurrentDate((d) => addWeeks(d, -1));
    else setSelectedDate((d) => { const n = new Date(d); n.setDate(n.getDate() - 1); setCurrentDate(n); return n; });
  };
  const goNext = () => {
    if (viewMode === 'month') setCurrentDate((d) => addMonths(d, 1));
    else if (viewMode === 'week') setCurrentDate((d) => addWeeks(d, 1));
    else setSelectedDate((d) => { const n = new Date(d); n.setDate(n.getDate() + 1); setCurrentDate(n); return n; });
  };

  const dayEvs = getEventsForDay(events, selectedDate);

  // 이벤트 클릭 → 상세 조회 (추가 필드 병합)
  const clickedIdRef = useRef<string | null>(null);
  const handleEventClick = async (ev: CalendarEvent) => {
    clickedIdRef.current = ev.eventId;
    console.log('[handleEventClick] step 1: list event', { eventId: ev.eventId, memo: ev.memo });
    setSelectedEvent(ev); // 즉시 표시
    const detail = await fetchEventDetail(ev.eventId);
    console.log('[handleEventClick] step 2: detail', { eventId: ev.eventId, memo: detail?.memo, detail });
    // 응답 도착 시 여전히 같은 이벤트를 보고 있는지 확인 (race condition 방지)
    if (detail && clickedIdRef.current === ev.eventId) setSelectedEvent(detail);
  };

  // 외부 진입(딜 상세 등) → ?activityId=&date= 로 들어오면 해당 활동 모달 자동 오픈
  // useSearchParams 대신 window.location 을 useEffect 내부에서 읽어 빌드 시
  // Suspense 경계 요구를 회피한다. (next build deopt 방지)
  const autoOpenedRef = useRef(false);
  useEffect(() => {
    if (autoOpenedRef.current) return;
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const aid = params.get('activityId');
    if (!aid) return;
    const dateParam = params.get('date');
    if (dateParam) {
      const d = new Date(dateParam);
      if (!isNaN(d.getTime())) {
        setCurrentDate(d);
        setSelectedDate(d);
      }
    }
    const found = apiEvents.find((e) => e.eventId === `act-${aid}`);
    if (!found) return; // 이벤트 목록 아직 로드 안 됨 → apiEvents 갱신 시 재시도
    autoOpenedRef.current = true;
    handleEventClick(found);
    window.history.replaceState({}, '', '/calendar');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiEvents]);
  const miniWk = getWeekDays(selectedDate);

  // ── 알림 드롭 → 일정 추가 prefill ──
  const [dropPrefill, setDropPrefill] = useState<{
    title?: string; category?: string; accountId?: number; accountName?: string;
  } | null>(null);

  const openAdd = (d: Date, endD?: Date) => {
    setAddDate(new Date(d));
    setAddEndDate(endD ? new Date(endD) : undefined);
    setIsAddOpen(true);
  };

  const handleNotificationDrop = (date: Date, raw: string) => {
    try {
      const data = JSON.parse(raw);
      setDropPrefill({
        title: data.title,
        accountName: data.accountName,
        accountId: data.accountId,
        category: data.category,
      });
      openAdd(date);
    } catch { /* ignore malformed data */ }
  };
  const onDayClick = (d: Date) => {
    const dt = new Date(d);
    dt.setHours(9, 0, 0, 0);
    setSelectedDate(dt);
    if (isCompact) {
      setViewMode('day');
    } else {
      openAdd(dt);
    }
  };
  const onTimeClick = (d: Date) => openAdd(d);
  const onTimeRangeSelect = (start: Date, end: Date) => openAdd(start, end);
  // 월간 드래그 (다일 선택): 첫날 09:00 ~ 끝날 18:00 으로 모달 prefill
  const onDayRangeSelect = (start: Date, end: Date) => {
    const s = new Date(start);
    s.setHours(9, 0, 0, 0);
    const e = new Date(end);
    e.setHours(18, 0, 0, 0);
    openAdd(s, e);
  };

  const mLabel = currentDate.toLocaleDateString('ko-KR', { month: 'long' });
  const yLabel = currentDate.getFullYear();

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: BG,
        overflow: 'hidden',
        fontFamily: F_PRETENDARD,
        WebkitFontSmoothing: 'antialiased',
        MozOsxFontSmoothing: 'grayscale',
      }}
    >
      {/* useBreakpoint 초기값이 'desktop'이라 hydration 직후 aside가 잠깐 보이는 FOUC 방지용 CSS 방어 레이어 */}
      <style>{`
        @media (max-width: ${TABLET_MAX}px) {
          .calendar-aside, .calendar-drag-handle { display: none !important; }
        }
      `}</style>
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* ── Aside (데스크톱 전용) ── */}
        {!isCompact && (<><div
          className="calendar-aside"
          style={{
            maxWidth: asideOpen ? asideWidth : 0,
            minWidth: 0,
            overflow: 'hidden',
            transition: dragRef.current.on ? 'none' : 'max-width 0.25s ease',
            flexShrink: 0,
            display: 'flex',
            backgroundColor: WHITE,
            borderRight: `1px solid ${BORDER}`,
          }}
        >
          <aside
            style={{
              width: asideWidth,
              flexShrink: 0,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
            }}
          >
            {/* Aside 헤더: 날짜 표시 + 미니 주간 */}
            <div
              style={{
                borderBottom: `1px solid ${BORDER}`,
                padding: '14px 14px 11px',
                flexShrink: 0,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0 20px 8px',
                }}
              >
                {/* Figma 719:7403: Pretendard 17px #1f2126 */}
                <span
                  style={{
                    fontSize: 15,
                    fontWeight: 700,
                    color: TXT1,
                    whiteSpace: 'nowrap',
                    fontFamily: F_PRETENDARD,
                  }}
                >
                  {selectedDate.toLocaleDateString('ko-KR', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    weekday: 'short',
                  })}
                </span>
                {/* Figma 719:7404: Pretendard 14px #737880 gap-[12px] */}
                <div style={{ display: 'flex', gap: 12 }}>
                  {['◀', '▶'].map((ch, i) => (
                    <button
                      key={ch}
                      onClick={() =>
                        setSelectedDate((d) => {
                          const n = new Date(d);
                          n.setDate(n.getDate() + (i === 0 ? -7 : 7));
                          return n;
                        })
                      }
                      style={{
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        color: TXT2,
                        fontSize: 14,
                        padding: 0,
                        fontFamily: F_PRETENDARD,
                      }}
                    >
                      {ch}
                    </button>
                  ))}
                </div>
              </div>
              {/* 미니 주간: 월~일 */}
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0 12px' }}>
                {miniWk.map((day, i) => {
                  const selected = isSameDay(day, selectedDate);
                  const today = isToday(day);
                  return (
                    <button
                      key={i}
                      onClick={() => setSelectedDate(day)}
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: 4,
                        flex: 1,
                        border: 'none',
                        backgroundColor: 'transparent',
                        cursor: 'pointer',
                        padding: 0,
                      }}
                    >
                      <span style={{ fontSize: 11, color: i === 0 || i === 6 ? RED : TXT2, fontFamily: F_PRETENDARD }}>
                        {WK[i]}
                      </span>
                      <span
                        style={{
                          fontSize: 13,
                          fontWeight: selected ? 700 : 600,
                          color: selected ? WHITE : today ? TEAL : (i === 0 || i === 6 ? RED : TXT1),
                          backgroundColor: selected ? TEAL : 'transparent',
                          borderRadius: 14,
                          padding: selected ? '5px 8px' : '5px 4px',
                          minWidth: 26,
                          textAlign: 'center',
                        }}
                      >
                        {day.getDate()}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
            <DayTimeView
              events={dayEvs}
              selectedDate={selectedDate}
              onEventClick={(ev) => handleEventClick(ev)}
              onTimeClick={onTimeClick}
              onTimeRangeSelect={onTimeRangeSelect}
              onResized={() => refetch()}
              onNotificationDrop={handleNotificationDrop}
            />
          </aside>
        </div>

        {/* ── 드래그 핸들 (데스크톱 전용) ── */}
        <div
          className="calendar-drag-handle"
          onMouseDown={asideOpen ? onDragStart : undefined}
          style={{
            width: 14,
            flexShrink: 0,
            backgroundColor: WHITE,
            borderRight: `1px solid ${BORDER}`,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: asideOpen ? 'col-resize' : 'pointer',
            zIndex: 10,
            gap: 2,
          }}
        >
          <button
            onClick={() => setAsideOpen((o) => !o)}
            style={{
              border: 'none',
              background: 'transparent',
              cursor: 'pointer',
              color: TXT2,
              fontSize: 11,
              padding: 0,
              lineHeight: 1,
            }}
          >
            {asideOpen ? '‹' : '›'}
          </button>
          {asideOpen &&
            [0, 1, 2].map((i) => (
              <div
                key={i}
                style={{ width: 3, height: 3, borderRadius: '50%', backgroundColor: '#CDD0D8' }}
              />
            ))}
        </div></>)}

        {/* ── 메인 영역 ── */}
        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            minWidth: 0,
          }}
        >
          {/* 달력 헤더 (Figma 719:7523: h-[52px] bg-white border-b #e5e6de) */}
          <div
            style={{
              flexShrink: 0,
              height: 52,
              backgroundColor: WHITE,
              borderBottom: `1px solid ${BORDER}`,
              display: 'flex',
              alignItems: 'center',
              padding: isCompact ? '0 10px' : '0 20px',
              position: 'relative',
            }}
          >
            {/* 월 이름 (Figma: Segoe_UI:Bold 16px #16180f + Segoe_UI:Regular #888) */}
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => setShowPicker((s) => !s)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'baseline',
                  gap: 5,
                  padding: '4px 8px',
                  borderRadius: 6,
                  fontFamily: F_SEGOE,
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#F0F2F5';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLButtonElement).style.backgroundColor = 'transparent';
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 700, color: '#16180F' }}>{mLabel}</span>
                <span style={{ fontSize: 14, fontWeight: 400, color: '#888888' }}>{yLabel}</span>
                <span style={{ fontSize: 9, color: TXT2, marginLeft: 2 }}>▾</span>
              </button>
              {showPicker && (
                <DatePicker
                  curr={currentDate}
                  onSel={(d) => {
                    setCurrentDate(d);
                    setSelectedDate(d);
                  }}
                  onClose={() => setShowPicker(false)}
                />
              )}
            </div>

            <div style={{ flex: 1 }} />

            {/* 뷰 토글 (Figma 719:7529: border-[#e5e6de] rounded-[8px] / active bg-[#06b6d4]) */}
            <div
              style={{
                display: 'flex',
                border: `1px solid ${BORDER}`,
                borderRadius: 8,
                overflow: 'hidden',
                marginRight: 8,
              }}
            >
              {(
                [
                  { t: '월', m: 'month' },
                  { t: '주', m: 'week' },
                  ...(isCompact ? [{ t: '일', m: 'day' as ViewMode }] : []),
                ] as { t: string; m: ViewMode }[]
              ).map(({ t, m }) => (
                <button
                  key={t}
                  onClick={() => setViewMode(m)}
                  style={{
                    // Figma: pt-[5px] pb-[6px] px-[10px] / active bg-[#06b6d4] color-white / inactive Arial 12px #888
                    padding: '5px 10px 6px',
                    border: 'none',
                    cursor: 'pointer',
                    fontSize: 12,
                    fontFamily: F_PRETENDARD,
                    backgroundColor: viewMode === m ? TEAL : 'transparent',
                    color: viewMode === m ? WHITE : '#888888',
                  }}
                >
                  {t}
                </button>
              ))}
            </div>

            {/* 오늘 버튼 (Figma 719:7537: bg-[#f5f7fa] border-[#e5e6de] rounded-[6px] pt-[6px] pb-[7px] px-[11px]) */}
            {/* Figma: ‹ 오늘 › 순서 */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
              <button onClick={goPrev} style={{ ...ARR_ST, fontFamily: F_PRETENDARD }}>
                ‹
              </button>
              <button
                onClick={() => {
                  const n = new Date();
                  setCurrentDate(n);
                  setSelectedDate(n);
                }}
                style={{
                  padding: '5px 10px 6px',
                  borderRadius: 6,
                  border: `1px solid ${BORDER}`,
                  backgroundColor: '#F5F7FA',
                  fontSize: 12,
                  color: TXT1,
                  cursor: 'pointer',
                  fontFamily: F_PRETENDARD,
                  margin: '0 2px',
                }}
              >
                오늘
              </button>
              <button onClick={goNext} style={{ ...ARR_ST, fontFamily: F_PRETENDARD }}>
                ›
              </button>
            </div>
          </div>

          {/* 파이프라인: 월간 뷰 전용 헤더. (주간은 WeekGrid 내부의 sticky 파이프라인이 표시됨)
              월간 그리드는 시간컬럼이 없으므로 7개 동일 분할로 셀과 정확히 정렬. */}
          {viewMode === 'month' && (
            <div
              style={{
                flexShrink: 0,
                backgroundColor: WHITE,
                borderBottom: `1px solid ${BORDER}`,
                display: 'grid',
                gridTemplateColumns: 'repeat(7, 1fr)',
                alignItems: 'stretch',
                overflow: 'hidden',
                height: isCompact ? 'auto' : 44,
                minHeight: isCompact ? 28 : 44,
              }}
            >
              {pipeline.map((s, i) => {
                const active = selectedPipeline === s.label;
                return (
                  <button
                    key={s.label}
                    type="button"
                    onClick={() => setSelectedPipeline(active ? null : s.label)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: isCompact ? 3 : 8,
                      padding: isCompact ? '3px 2px' : '0 16px',
                      borderRight: i < 6 ? '1px solid #D6D6D6' : 'none',
                      borderTop: 'none',
                      borderBottom: 'none',
                      borderLeft: 'none',
                      overflow: 'hidden',
                      minWidth: 0,
                      cursor: 'pointer',
                      backgroundColor: active ? s.cBg : 'transparent',
                      transition: 'background-color 150ms ease',
                      fontFamily: 'inherit',
                    }}
                  >
                    <span
                      style={{
                        width: isCompact ? 6 : 8,
                        height: isCompact ? 6 : 8,
                        borderRadius: '50%',
                        backgroundColor: s.dot,
                        flexShrink: 0,
                      }}
                    />
                    <span
                      style={{
                        fontSize: isCompact ? 10 : 13,
                        fontWeight: active ? 700 : 500,
                        color: active ? s.cTxt : TXT1,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        textAlign: 'center',
                        lineHeight: '1.2',
                        ...(isCompact
                          ? { whiteSpace: 'normal' as const, wordBreak: 'keep-all' as const }
                          : { whiteSpace: 'nowrap' as const, flex: 1, textAlign: 'left' as const }),
                      }}
                    >
                      {s.label}
                    </span>
                    {!isCompact && (
                    <span
                      style={{
                        fontSize: 11,
                        fontWeight: 600,
                        color: s.cTxt,
                        backgroundColor: active ? s.dot : s.cBg,
                        ...(active ? { color: WHITE } : {}),
                        padding: '2px 7px',
                        borderRadius: 10,
                        flexShrink: 0,
                      }}
                    >
                      {s.count}
                    </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {/* ── 달력 그리드 ── */}
          <div style={{ flex: 1, overflow: 'hidden', position: 'relative' }}>
            {viewMode === 'day' ? (
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
                {/* 날짜 헤더 + 미니 주간 (사이드바와 동일 구조) */}
                <div
                  style={{
                    borderBottom: `1px solid ${BORDER}`,
                    padding: '14px 14px 11px',
                    flexShrink: 0,
                    backgroundColor: WHITE,
                  }}
                >
                  {!isCompact && (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '0 20px 8px',
                    }}
                  >
                    <span
                      style={{
                        fontSize: 15,
                        fontWeight: 700,
                        color: TXT1,
                        whiteSpace: 'nowrap',
                        fontFamily: F_PRETENDARD,
                      }}
                    >
                      {selectedDate.toLocaleDateString('ko-KR', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                        weekday: 'short',
                      })}
                    </span>
                    <div style={{ display: 'flex', gap: 12 }}>
                      {['◀', '▶'].map((ch, idx) => (
                        <button
                          key={ch}
                          onClick={() =>
                            setSelectedDate((d) => {
                              const n = new Date(d);
                              n.setDate(n.getDate() + (idx === 0 ? -7 : 7));
                              return n;
                            })
                          }
                          style={{
                            border: 'none',
                            background: 'transparent',
                            cursor: 'pointer',
                            color: TXT2,
                            fontSize: 14,
                            padding: 0,
                            fontFamily: F_PRETENDARD,
                          }}
                        >
                          {ch}
                        </button>
                      ))}
                    </div>
                  </div>
                  )}
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0 12px' }}>
                    {miniWk.map((day, idx) => {
                      const sel = isSameDay(day, selectedDate);
                      const tod = isToday(day);
                      return (
                        <button
                          key={idx}
                          onClick={() => setSelectedDate(day)}
                          style={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            gap: 1,
                            flex: 1,
                            border: 'none',
                            backgroundColor: 'transparent',
                            cursor: 'pointer',
                            padding: '4px 2px',
                          }}
                        >
                          <span style={{ fontSize: 11, fontWeight: 600, color: idx === 0 || idx === 6 ? RED : '#737880', letterSpacing: '0.02em' }}>
                            {WK[idx]}
                          </span>
                          <span
                            style={{
                              width: 22,
                              height: 22,
                              borderRadius: '50%',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: 13,
                              fontWeight: sel ? 700 : 600,
                              color: sel ? WHITE : tod ? TEAL : (idx === 0 || idx === 6 ? RED : TXT1),
                              backgroundColor: sel ? TEAL : tod ? 'transparent' : 'transparent',
                            }}
                          >
                            {day.getDate()}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
                <DayTimeView
                  events={dayEvs}
                  selectedDate={selectedDate}
                  onEventClick={(ev) => {
                    setSelectedDate(new Date(ev.startAt));
                    handleEventClick(ev);
                  }}
                  onTimeClick={onTimeClick}
                  onTimeRangeSelect={onTimeRangeSelect}
                  onResized={() => refetch()}
                  onNotificationDrop={handleNotificationDrop}
                />
              </div>
            ) : viewMode === 'month' ? (
              <MonthGrid
                currentDate={currentDate}
                events={events}
                selectedDate={selectedDate}
                onDayClick={onDayClick}
                onEventClick={(ev) => {
                  setSelectedDate(new Date(ev.startAt));
                  handleEventClick(ev);
                }}
                onMoreClick={(d) => {
                  setCurrentDate(d);
                  setViewMode('week');
                }}
                onDayRangeSelect={onDayRangeSelect}
                onResized={() => refetch()}
                onNotificationDrop={handleNotificationDrop}
              />
            ) : (
              <WeekGrid
                currentDate={currentDate}
                events={events}
                selectedEvent={selectedEvent}
                onEventClick={(ev) => {
                  setSelectedDate(new Date(ev.startAt));
                  handleEventClick(ev);
                }}
                onTimeClick={onTimeClick}
                onTimeRangeSelect={onTimeRangeSelect}
                onResized={() => refetch()}
                pipeline={pipeline}
                selectedPipeline={selectedPipeline}
                onPipelineClick={(label) => setSelectedPipeline(selectedPipeline === label ? null : label)}
                onNotificationDrop={handleNotificationDrop}
              />
            )}
            {/* FAB — 모바일에서는 미노출 */}
            {!isCompact && <button
              onClick={() => openAdd(selectedDate)}
              aria-label="일정 추가"
              style={{
                position: 'absolute',
                right: 24,
                bottom: 28,
                width: 50,
                height: 50,
                borderRadius: '50%',
                border: 'none',
                backgroundColor: BRAND,
                color: WHITE,
                fontSize: 26,
                cursor: 'pointer',
                boxShadow: '0 4px 20px rgba(6,134,212,.4)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 10,
                transition: 'transform .15s',
              }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLButtonElement).style.transform = 'scale(1.08)';
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLButtonElement).style.transform = 'scale(1)';
              }}
            >
              +
            </button>}
          </div>
        </div>
      </div>

      <EventDetailPanel
        event={selectedEvent}
        onClose={() => setSelectedEvent(null)}
        onDeleted={() => {
          refetch();
          setSelectedEvent(null);
        }}
        onEdit={(ev) => {
          setEditEvent(ev);
          setSelectedEvent(null);
        }}
      />
      <AddEventModal
        open={isAddOpen || !!editEvent}
        onClose={() => {
          setIsAddOpen(false);
          setEditEvent(null);
          setDropPrefill(null);
        }}
        onSaved={() => {
          refetch();
          setEditEvent(null);
          setDropPrefill(null);
        }}
        defaultDate={addDate}
        defaultEndDate={addEndDate}
        defaultTitle={dropPrefill?.title}
        defaultCategory={dropPrefill?.category ?? undefined}
        defaultAccountId={dropPrefill?.accountId}
        defaultAccountName={dropPrefill?.accountName}
        editEvent={editEvent}
      />
    </div>
  );
}

// Figma ‹ ›: Pretendard 18px #6d7164
const ARR_ST: React.CSSProperties = {
  width: 28,
  height: 28,
  border: 'none',
  backgroundColor: 'transparent',
  cursor: 'pointer',
  fontSize: 18,
  color: '#6D7164',
};
