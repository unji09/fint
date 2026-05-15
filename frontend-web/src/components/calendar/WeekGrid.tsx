'use client';
// src/components/calendar/WeekGrid.tsx
// 08:00~20:00 표시, borderLeft→boxShadow 통일로 파이프라인/요일 완벽 정렬

import { useState, useEffect, useRef } from 'react';
import type { CalendarEvent } from './types';
import { CATEGORY_COLOR, CATEGORY_BG } from './types';
import { getWeekDays, isToday } from './utils';
import { resizeActivity } from '@/hooks/useCalendarEvents';

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
  /** 드래그로 시간 범위 선택 시 호출 (start < end). 짧은 드래그면 호출 안 되고 onTimeClick 만 호출. */
  onTimeRangeSelect?: (start: Date, end: Date) => void;
  /** 이벤트 카드 리사이즈로 endAt 이 변경되어 PATCH 성공한 직후 호출 (refetch 트리거용). */
  onResized?: () => void;
  pipeline?: readonly PipelineItem[] | PipelineItem[];
  /** 알림 카드 드롭 시 해당 날짜+시간과 드롭 데이터 전달. */
  onNotificationDrop?: (date: Date, data: string) => void;
}

// PATCH body 직렬화 (KST). useCalendarEvents.resizeActivity 와 한 쌍.
function fmtKstIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${dd}T${h}:${min}:00+09:00`;
}

export const WEEK_TIME_COL = 52;

const START = 8; // 08:00부터
const END = 22; // 22:00까지 (저녁 이벤트도 리사이즈 가능)
const HOURS = Array.from({ length: END - START + 1 }, (_, i) => i + START);
const HOUR_H = 110; // 1시간 높이, 30분=55px → 이벤트 제목+태그 다 보임
const TOTAL = (END - START) * HOUR_H;

const BORDER = '#E5E6DE';
const RED = '#EE5555';
const TIME_C = '#9CA193';
const DAY_FULL = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
const DAY_COLOR = (i: number) => (i === 0 || i === 6 ? RED : '#888888');

const NEAR_MS = 15 * 60_000;
const OVERLAP_OFFSET = 10;

interface OverlapLayout {
  mode: 'side' | 'cascade';
  colIdx: number;
  totalCols: number;
  cascadeIdx: number;
}

function computeOverlapLayout(evs: CalendarEvent[]): Map<string, OverlapLayout> {
  if (evs.length === 0) return new Map();

  const sorted = [...evs].sort((a, b) => {
    const diff = new Date(a.startAt).getTime() - new Date(b.startAt).getTime();
    if (diff !== 0) return diff;
    return new Date(b.endAt).getTime() - new Date(a.endAt).getTime();
  });

  const groups: CalendarEvent[][] = [];
  let group: CalendarEvent[] = [sorted[0]];
  for (let i = 1; i < sorted.length; i++) {
    const prevStart = new Date(sorted[i - 1].startAt).getTime();
    const curStart = new Date(sorted[i].startAt).getTime();
    if (curStart - prevStart <= NEAR_MS) {
      group.push(sorted[i]);
    } else {
      groups.push(group);
      group = [sorted[i]];
    }
  }
  groups.push(group);

  const result = new Map<string, OverlapLayout>();
  let maxEndSoFar = 0;
  let cascadeIdx = 0;

  for (const g of groups) {
    const gStart = new Date(g[0].startAt).getTime();
    if (gStart < maxEndSoFar) {
      cascadeIdx++;
    } else {
      cascadeIdx = 0;
    }

    if (g.length >= 2) {
      const colEnds: number[] = [];
      const assignments: number[] = [];
      for (const ev of g) {
        const evStart = new Date(ev.startAt).getTime();
        let placed = -1;
        for (let c = 0; c < colEnds.length; c++) {
          if (evStart >= colEnds[c]) { placed = c; break; }
        }
        if (placed === -1) {
          placed = colEnds.length;
          colEnds.push(0);
        }
        colEnds[placed] = new Date(ev.endAt).getTime();
        assignments.push(placed);
      }
      const totalCols = colEnds.length;
      for (let i = 0; i < g.length; i++) {
        result.set(g[i].eventId, { mode: 'side', colIdx: assignments[i], totalCols, cascadeIdx });
      }
    } else {
      result.set(g[0].eventId, { mode: 'cascade', colIdx: 0, totalCols: 1, cascadeIdx });
    }

    for (const ev of g) {
      maxEndSoFar = Math.max(maxEndSoFar, new Date(ev.endAt).getTime());
    }
  }

  return result;
}

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
  onResizeStartTop,
  onResizeStartBottom,
}: {
  event: CalendarEvent;
  onClick: () => void;
  selected: boolean;
  onResizeStartTop?: (e: React.MouseEvent) => void;
  onResizeStartBottom?: (e: React.MouseEvent) => void;
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
        backgroundColor: bg,
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
        (e.currentTarget as HTMLButtonElement).style.filter = 'brightness(0.95)';
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLButtonElement).style.filter = '';
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
      {/* 리사이즈 핸들 — 카드 상단(startAt) / 하단(endAt). 평소 투명, 호버 시만 옅게. */}
      {onResizeStartTop && (
        <div
          data-resize="true"
          onMouseDown={onResizeStartTop}
          onClick={(e) => e.stopPropagation()}
          onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'rgba(6,182,212,0.18)'; }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent'; }}
          style={{ position: 'absolute', left: 0, right: 0, top: 0, height: 12, cursor: 'ns-resize', zIndex: 6, transition: 'background-color .12s' }}
        />
      )}
      {onResizeStartBottom && (
        <div
          data-resize="true"
          onMouseDown={onResizeStartBottom}
          onClick={(e) => e.stopPropagation()}
          onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'rgba(6,182,212,0.18)'; }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent'; }}
          style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 12, cursor: 'ns-resize', zIndex: 6, transition: 'background-color .12s' }}
        />
      )}
    </button>
  );
}

export default function WeekGrid({
  currentDate,
  events,
  selectedEvent,
  onEventClick,
  onTimeClick,
  onTimeRangeSelect,
  onResized,
  pipeline,
  onNotificationDrop,
}: Props) {
  const [now, setNow] = useState(new Date());
  const [scrollTop, setST] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  // ─── 드래그 상태 ─────────────────────────────────────────
  // colIdx: 어느 요일 컬럼, startY / currentY: 컬럼 내부 픽셀 좌표 (스크롤 포함)
  const [drag, setDrag] = useState<{
    colIdx: number;
    day: Date;
    startY: number;
    currentY: number;
    moved: boolean;
  } | null>(null);

  // ─── 리사이즈 상태 ─────────────────────────────────────────
  // 카드 상단/하단 핸들 둘 다 지원.
  //  mode='start' → currentY 가 startAt 픽셀(드래그), endY 고정
  //  mode='end'   → startY 고정, currentY 가 endAt 픽셀(드래그)
  // grabOffset: 핸들의 정확한 기준점과 마우스 클릭점 사이의 거리. 점프 방지.
  const [resize, setResize] = useState<{
    event: CalendarEvent;
    mode: 'start' | 'end';
    colIdx: number;
    day: Date;
    startY: number;
    endY: number;
    currentY: number;
    grabOffset: number;
  } | null>(null);

  // ─── 이동(move) 상태 ──────────────────────────────────────
  // 카드 본체 mousedown → 4px 임계치 넘으면 이동 모드. mouseup 시 startAt/endAt 둘 다 shift.
  // 임계치 미만이면 moved=false 로 끝나 button onClick 이 정상 발화(상세 열기).
  const [move, setMove] = useState<{
    event: CalendarEvent;
    origColIdx: number;
    startMouseY: number;
    startMouseX: number;
    curMouseY: number;
    curMouseX: number;
    curColIdx: number;
    moved: boolean;
  } | null>(null);

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

  // ─── 픽셀 → 시간 변환 (15분 스냅) ────────────────────────
  // 컬럼 내부 y(px) → Date 객체 (해당 요일의 시각).
  const yToDate = (y: number, day: Date): Date => {
    const clampedY = Math.max(0, Math.min(y, TOTAL));
    const totalMinutes = (clampedY / HOUR_H) * 60;
    const snapped = Math.round(totalMinutes / 15) * 15; // 15분 단위 스냅
    const d = new Date(day);
    const totalFromStart = START * 60 + snapped;
    d.setHours(Math.floor(totalFromStart / 60), totalFromStart % 60, 0, 0);
    return d;
  };

  // ─── 마우스 다운: 드래그 시작 ─────────────────────────────
  const handleColMouseDown = (e: React.MouseEvent<HTMLDivElement>, colIdx: number, day: Date) => {
    if ((e.target as HTMLElement).closest('[data-event="true"]')) return;
    if ((e.target as HTMLElement).closest('[data-resize="true"]')) return;
    if (e.button !== 0) return; // 좌클릭만
    const rect = e.currentTarget.getBoundingClientRect();
    const y = e.clientY - rect.top + scrollTop;
    setDrag({ colIdx, day, startY: y, currentY: y, moved: false });
  };

  // ─── 리사이즈 핸들 mousedown ─────────────────────────────
  const handleResizeStart = (ev: CalendarEvent, colIdx: number, day: Date, mode: 'start' | 'end', e: React.MouseEvent) => {
    e.stopPropagation();
    if (e.button !== 0) return;
    if (!ev.eventId.startsWith('act-')) return; // FINT 활동만
    const s = new Date(ev.startAt);
    const en = new Date(ev.endAt);
    const startY = (s.getHours() + s.getMinutes() / 60 - START) * HOUR_H;
    const endY = (en.getHours() + en.getMinutes() / 60 - START) * HOUR_H;
    // 마우스 클릭 위치와 기준점(start 또는 end 픽셀) 사이의 offset — 점프 방지
    const colEl = scrollRef.current?.querySelector<HTMLDivElement>(`[data-week-col="${colIdx}"]`);
    let grabOffset = 0;
    if (colEl) {
      const rect = colEl.getBoundingClientRect();
      const mouseY = e.clientY - rect.top + scrollTop;
      grabOffset = mouseY - (mode === 'end' ? endY : startY);
    }
    setResize({ event: ev, mode, colIdx, day, startY, endY, currentY: mode === 'end' ? endY : startY, grabOffset });
    document.body.style.cursor = 'ns-resize';
    document.body.style.userSelect = 'none';
  };

  // ─── 카드 본체 mousedown → 이동 후보 모드. 4px 넘기면 실제 이동. ───
  const handleCardMouseDown = (ev: CalendarEvent, colIdx: number, e: React.MouseEvent) => {
    if (e.button !== 0) return;
    if ((e.target as HTMLElement).closest('[data-resize="true"]')) return; // 리사이즈 핸들이면 skip
    if (!ev.eventId.startsWith('act-')) return; // 외부 이벤트는 이동 불가
    setMove({
      event: ev,
      origColIdx: colIdx,
      startMouseY: e.clientY,
      startMouseX: e.clientX,
      curMouseY: e.clientY,
      curMouseX: e.clientX,
      curColIdx: colIdx,
      moved: false,
    });
  };

  // ─── 전역 mousemove / mouseup ────────────────────────────
  useEffect(() => {
    if (!drag) return;

    const onMove = (e: MouseEvent) => {
      const colEl = scrollRef.current?.querySelector<HTMLDivElement>(`[data-week-col="${drag.colIdx}"]`);
      if (!colEl) return;
      const rect = colEl.getBoundingClientRect();
      const y = e.clientY - rect.top + scrollTop;
      const moved = drag.moved || Math.abs(y - drag.startY) > 4;
      setDrag({ ...drag, currentY: y, moved });
    };

    const onUp = () => {
      if (!drag) return;
      const { startY, currentY, day, moved } = drag;
      const top = Math.min(startY, currentY);
      const bot = Math.max(startY, currentY);

      if (!moved) {
        // 단순 클릭 — onTimeClick
        onTimeClick?.(yToDate(top, day));
      } else {
        // 드래그 — onTimeRangeSelect. 최소 15분 보장.
        const startD = yToDate(top, day);
        let endD = yToDate(bot, day);
        if (endD.getTime() - startD.getTime() < 15 * 60_000) {
          endD = new Date(startD.getTime() + 15 * 60_000);
        }
        if (onTimeRangeSelect) onTimeRangeSelect(startD, endD);
        else onTimeClick?.(startD);
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
  }, [drag, scrollTop]);

  // ─── 이동 전역 mousemove / mouseup ───────────────────────
  useEffect(() => {
    if (!move) return;
    const onMv = (e: MouseEvent) => {
      const dx = e.clientX - move.startMouseX;
      const dy = e.clientY - move.startMouseY;
      const moved = move.moved || Math.abs(dx) > 4 || Math.abs(dy) > 4;
      // 마우스 X 좌표 기준으로 현재 가리키는 컬럼 추적
      let curColIdx = move.origColIdx;
      for (let i = 0; i < 7; i++) {
        const el = scrollRef.current?.querySelector<HTMLDivElement>(`[data-week-col="${i}"]`);
        if (!el) continue;
        const rect = el.getBoundingClientRect();
        if (e.clientX >= rect.left && e.clientX < rect.right) { curColIdx = i; break; }
      }
      setMove({ ...move, curMouseY: e.clientY, curMouseX: e.clientX, curColIdx, moved });
      if (moved) document.body.style.cursor = 'grabbing';
    };
    const onUp = async () => {
      document.body.style.cursor = '';
      const m = move;
      setMove(null);
      if (!m.moved) return; // 임계치 미만 → button onClick 으로 상세 열림
      const dy = m.curMouseY - m.startMouseY;
      const minutesDelta = Math.round((dy / HOUR_H) * 60 / 15) * 15; // 15분 스냅
      const dayDelta = m.curColIdx - m.origColIdx;
      const newStart = new Date(m.event.startAt);
      newStart.setDate(newStart.getDate() + dayDelta);
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

  // ─── 리사이즈 전역 mousemove / mouseup ───────────────────────
  // resize 상태를 ref 로 추적해 setResize 마다 effect re-mount 되는 race 방지.
  // effect 는 resize 시작/종료에만 mount/cleanup.
  const resizeRef = useRef(resize);
  resizeRef.current = resize;
  const scrollTopRef = useRef(scrollTop);
  scrollTopRef.current = scrollTop;
  useEffect(() => {
    if (!resize) return;
    const onMove = (e: MouseEvent) => {
      const r = resizeRef.current;
      if (!r) return;
      const colEl = scrollRef.current?.querySelector<HTMLDivElement>(`[data-week-col="${r.colIdx}"]`);
      if (!colEl) return;
      const rect = colEl.getBoundingClientRect();
      const mouseY = e.clientY - rect.top + scrollTopRef.current;
      const targetY = mouseY - r.grabOffset;
      let clamped: number;
      if (r.mode === 'end') {
        const minY = r.startY + HOUR_H / 4;
        clamped = Math.max(minY, Math.min(TOTAL, targetY));
      } else {
        const maxY = r.endY - HOUR_H / 4;
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
        newEnd = yToDate(r.currentY, r.day);
      } else {
        newStart = yToDate(r.currentY, r.day);
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
        {/* 파이프라인: 시간컬럼 + 7컬럼. 월간 뷰와 동일한 셀 스타일(높이·폰트·dot·배지) */}
        {pipeline && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: `${WEEK_TIME_COL}px repeat(7, 1fr)`,
              borderBottom: `1px solid ${BORDER}`,
              height: 44, // 월간 뷰 파이프라인과 동일
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
                  padding: '0 16px',
                  boxShadow: dividerShadow(i),
                  overflow: 'hidden',
                  minWidth: 0,
                }}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    backgroundColor: s.dot,
                    flexShrink: 0,
                  }}
                />
                <span
                  style={{
                    fontSize: 13,
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
                    fontWeight: 600,
                    color: s.cTxt,
                    backgroundColor: s.cBg,
                    padding: '2px 7px',
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

        {/* 7 요일 컬럼: boxShadow(-1px) → 파이프라인·요일헤더와 완벽 정렬
            드래그로 시간 범위 선택 가능 (구글 캘린더 패턴). */}
        {weekDays.map((day, colIdx) => {
          const dayEvs = events.filter((ev) => sameDay(ev, day));
          const overlapLayout = computeOverlapLayout(dayEvs);
          const isDragCol = drag?.colIdx === colIdx;
          const dragTop = isDragCol ? Math.min(drag.startY, drag.currentY) : 0;
          const dragH = isDragCol ? Math.abs(drag.currentY - drag.startY) : 0;
          return (
            <div
              key={colIdx}
              data-week-col={colIdx}
              onMouseDown={(e) => handleColMouseDown(e, colIdx, day)}
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
                  const y = e.clientY - rect.top + scrollTop;
                  const dropDate = yToDate(y, day);
                  onNotificationDrop(dropDate, raw);
                }
              }}
              style={{
                flex: 1,
                boxShadow: dividerShadow(colIdx),
                position: 'relative',
                minWidth: 0,
                cursor: 'crosshair',
                userSelect: 'none',
              }}
            >
              {HOURS.map((h) => (
                <div
                  key={h}
                  style={{ height: HOUR_H, borderTop: `1px solid ${BORDER}`, position: 'relative' }}
                />
              ))}

              {/* 드래그 highlight overlay */}
              {isDragCol && drag.moved && (
                <div
                  style={{
                    position: 'absolute',
                    top: dragTop,
                    left: 1,
                    right: 1,
                    height: Math.max(dragH, 1),
                    backgroundColor: 'rgba(6, 182, 212, 0.18)',
                    border: '1px solid #06B6D4',
                    borderRadius: 6,
                    zIndex: 3,
                    pointerEvents: 'none',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: dragH < 36 ? 'center' : 'flex-start',
                    padding: '4px 6px',
                    gap: 2,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  <span style={{ fontSize: 10, fontWeight: 700, color: '#06B6D4' }}>
                    {(() => {
                      const s = yToDate(Math.min(drag.startY, drag.currentY), day);
                      const e = yToDate(Math.max(drag.startY, drag.currentY), day);
                      const fmt = (d: Date) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
                      return `${fmt(s)} – ${fmt(e)}`;
                    })()}
                  </span>
                </div>
              )}

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
                const isResizing = resize?.event.eventId === ev.eventId;
                const isMoving = move?.event.eventId === ev.eventId && move.moved;
                // 미리보기: end 모드면 top 고정 height 변경, start 모드면 top 이동 height 변경
                let renderTop = topPx + 1;
                let hPx: number;
                if (isResizing) {
                  if (resize.mode === 'end') {
                    hPx = Math.max(50, resize.currentY - topPx - 2);
                  } else {
                    renderTop = resize.currentY + 1;
                    hPx = Math.max(50, resize.endY - resize.currentY - 2);
                  }
                } else {
                  hPx = Math.max(50, (Math.min(hEnd, END) - Math.max(hStart, START)) * HOUR_H - 2);
                }
                const moveTransform = isMoving
                  ? `translate(${(move.curColIdx - move.origColIdx) * 100}%, ${move.curMouseY - move.startMouseY}px)`
                  : undefined;
                const layout = overlapLayout.get(ev.eventId) ?? { mode: 'cascade' as const, colIdx: 0, totalCols: 1, cascadeIdx: 0 };
                const isSide = layout.mode === 'side';
                const leftVal = isSide
                  ? `calc(${(layout.colIdx / layout.totalCols) * 100}% + 1px)`
                  : layout.cascadeIdx * OVERLAP_OFFSET + 1;
                const sizeStyle = isSide
                  ? { width: `calc(${(1 / layout.totalCols) * 100}% - 2px)` }
                  : { right: 1 as const };
                return (
                  <div
                    key={ev.eventId}
                    data-event="true"
                    onMouseDown={(e) => handleCardMouseDown(ev, colIdx, e)}
                    style={{
                      position: 'absolute',
                      top: renderTop,
                      left: leftVal,
                      ...sizeStyle,
                      height: hPx,
                      zIndex: isMoving ? 10 : isResizing ? 4 : (2 + layout.cascadeIdx),
                      transform: moveTransform,
                      transition: isMoving ? 'none' : undefined,
                      opacity: isMoving ? 0.85 : 1,
                      cursor: ev.eventId.startsWith('act-') ? (isMoving ? 'grabbing' : 'grab') : undefined,
                    }}
                  >
                    <WeekCard
                      event={ev}
                      onClick={() => onEventClick(ev)}
                      selected={selectedEvent?.eventId === ev.eventId}
                      onResizeStartTop={
                        ev.eventId.startsWith('act-')
                          ? (e) => handleResizeStart(ev, colIdx, day, 'start', e)
                          : undefined
                      }
                      onResizeStartBottom={
                        ev.eventId.startsWith('act-')
                          ? (e) => handleResizeStart(ev, colIdx, day, 'end', e)
                          : undefined
                      }
                    />
                    {/* 리사이즈 중 시각 라벨 */}
                    {isResizing && (
                      <div
                        style={{
                          position: 'absolute',
                          [resize.mode === 'end' ? 'bottom' : 'top']: 2,
                          right: 4,
                          fontSize: 10,
                          fontWeight: 700,
                          color: '#06B6D4',
                          fontVariantNumeric: 'tabular-nums',
                          pointerEvents: 'none',
                          backgroundColor: 'rgba(255,255,255,.85)',
                          padding: '1px 4px',
                          borderRadius: 4,
                          zIndex: 6,
                        }}
                      >
                        {(() => {
                          const t = yToDate(resize.currentY, resize.day);
                          const fmt = `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`;
                          return resize.mode === 'end' ? `~ ${fmt}` : `${fmt} ~`;
                        })()}
                      </div>
                    )}
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
