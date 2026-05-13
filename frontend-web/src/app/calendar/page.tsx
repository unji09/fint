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

import { useState, useEffect, useRef, useCallback } from 'react';
import MonthGrid from '@/components/calendar/MonthGrid';
import WeekGrid, { PipelineItem } from '@/components/calendar/WeekGrid';
import EventDetailPanel from '@/components/calendar/EventDetailPanel';
import AddEventModal from '@/components/calendar/AddEventModal';
import type { CalendarEvent, ViewMode } from '@/components/calendar/types';
import { CATEGORY_COLOR, CATEGORY_BG } from '@/components/calendar/types';
import { useCalendarEvents, fetchEventDetail } from '@/hooks/useCalendarEvents';
import {
  addMonths,
  addWeeks,
  getEventsForDay,
  isToday,
  isSameDay,
} from '@/components/calendar/utils';

// ── 피그마 수치 상수 ─────────────────────────────────────────
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';
function authHeader(): HeadersInit {
  const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return token ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } : {};
}

const BG = '#F8F8F5'; // 페이지 배경 (linear-gradient 근사)
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

// ── 미니 주간 (월~일) ─────────────────────────────────────────
const WK = ['월', '화', '수', '목', '금', '토', '일'];
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

function getMondayWeek(d: Date): Date[] {
  const b = new Date(d);
  b.setDate(b.getDate() + (b.getDay() === 0 ? -6 : 1 - b.getDay()));
  return Array.from({ length: 7 }, (_, i) => {
    const n = new Date(b);
    n.setDate(n.getDate() + i);
    return n;
  });
}

// ── Aside 이벤트 카드 (Figma 719:7431)
// bg-[#f6f7f9] border-[catBg] rounded-[10px] px-[14px] py-[12px] gap-[8px]
// 제목: Inter:SemiBold 14px #1f2126
// Cat pill: bg-catBg, rounded-[10px], dot 6px rounded-[3px]
// Pipeline: bg-[#f0edf7] rounded-[3px] px-[8px] py-[3px], progress bar
function AsideCard({ event, onClick }: { event: CalendarEvent; onClick: () => void }) {
  const col = event.category ? CATEGORY_COLOR[event.category] : '#7F77DD';
  const bg = event.category ? CATEGORY_BG[event.category] : '#ECEBFA';

  return (
    <button
      onClick={onClick}
      style={{
        width: '100%',
        textAlign: 'left',
        cursor: 'pointer',
        borderRadius: 10,
        backgroundColor: '#F6F7F9',
        border: `1px solid ${bg}`,
        padding: '12px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        flexShrink: 0,
        fontFamily: F_INTER,
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#F0F2F7';
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#F6F7F9';
      }}
    >
      <span
        style={{
          fontSize: 14,
          fontWeight: 600,
          color: TXT1,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          display: 'block',
        }}
      >
        {event.title}
      </span>
      <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
        {event.category && (
          <span
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              backgroundColor: bg,
              borderRadius: 10,
              padding: '3px 8px',
              fontSize: 11,
              color: col,
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: col }} />
            {event.category}
          </span>
        )}
        {event.pipelineStage && (
          <span
            style={{
              backgroundColor: '#F0EDF7',
              borderRadius: 3,
              padding: '3px 8px',
              fontSize: 10,
              color: '#534AB7',
              fontWeight: 600,
              whiteSpace: 'nowrap',
            }}
          >
            {event.pipelineStage.stageName}
          </span>
        )}
      </div>
    </button>
  );
}

// ── Aside 시간뷰 ──────────────────────────────────────────────
const A_H = 80;
const A_S = 8; // 00:00부터 전체 시간

function DayTimeView({
  events,
  onEventClick,
}: {
  events: CalendarEvent[];
  onEventClick: (e: CalendarEvent) => void;
}) {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(t);
  }, []);
  const HOURS = Array.from({ length: 13 }, (_, i) => i + A_S); // 08~20시
  const total = HOURS.length * A_H;
  const red = (now.getHours() - A_S + now.getMinutes() / 60) * A_H;
  return (
    <div style={{ flex: 1, overflowY: 'auto' }}>
      <div style={{ position: 'relative', height: total }}>
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
        {red >= 0 && red <= total && (
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
        {events.map((ev) => {
          const s = new Date(ev.startAt);
          const top = (s.getHours() - A_S + s.getMinutes() / 60) * A_H + 4;
          if (top < 0 || top > total) return null;
          return (
            <div
              key={ev.eventId}
              style={{ position: 'absolute', top, left: 42, right: 6, zIndex: 1 }}
            >
              <AsideCard event={ev} onClick={() => onEventClick(ev)} />
            </div>
          );
        })}
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
  const [viewMode, setViewMode] = useState<ViewMode>('month');
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [addDate, setAddDate] = useState<Date | undefined>();
  const [asideOpen, setAsideOpen] = useState(true);
  const [showPicker, setShowPicker] = useState(false);

  const [asideWidth, setAsideWidth] = useState(300); // Figma: w-[300px]
  const [editEvent, setEditEvent] = useState<CalendarEvent | null>(null);

  // ── 파이프라인 state (API에서 집계) ──────────────────────────
  const [pipeline, setPipeline] = useState<PipelineItem[]>(
    PIPELINE_STYLES.map((s) => ({ ...s, count: 0 })),
  );

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_BASE}/deals?size=200`, { headers: authHeader() });
        if (!res.ok) return;
        const json = await res.json();
        // 백엔드 응답: { status, message, data: { data: [...], totalElements } }
        const deals: any[] = Array.isArray(json?.data?.data) ? json.data.data : [];
        const counts: Record<string, number> = {};
        deals.forEach((d) => {
          // 백엔드 DealListResponse: currentPipelineStage (DealDetailResponse 와 동일 명명 가정)
          // 또는 currentPipeline. 둘 다 한글 stage 이름.
          const code = d?.currentPipelineStage ?? d?.currentPipeline ?? '';
          if (code) counts[code] = (counts[code] ?? 0) + 1;
        });
        setPipeline(PIPELINE_STYLES.map((s) => ({ ...s, count: counts[s.code] ?? 0 })));
      } catch { /* count 0 유지 */ }
    })();
  }, []);

  // ── API 연동 ──────────────────────────────────────────────────
  const { events: apiEvents, refetch } = useCalendarEvents({ currentDate, viewMode });

  const events = apiEvents;

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

  const goPrev = () =>
    viewMode === 'month'
      ? setCurrentDate((d) => addMonths(d, -1))
      : setCurrentDate((d) => addWeeks(d, -1));
  const goNext = () =>
    viewMode === 'month'
      ? setCurrentDate((d) => addMonths(d, 1))
      : setCurrentDate((d) => addWeeks(d, 1));

  const dayEvs = getEventsForDay(events, selectedDate);

  // 이벤트 클릭 → 상세 조회 (추가 필드 병합)
  const clickedIdRef = useRef<string | null>(null);
  const handleEventClick = async (ev: CalendarEvent) => {
    clickedIdRef.current = ev.eventId;
    setSelectedEvent(ev); // 즉시 표시
    const detail = await fetchEventDetail(ev.eventId);
    // 응답 도착 시 여전히 같은 이벤트를 보고 있는지 확인 (race condition 방지)
    if (detail && clickedIdRef.current === ev.eventId) setSelectedEvent(detail);
  };
  const miniWk = getMondayWeek(selectedDate);

  const openAdd = (d: Date) => {
    setAddDate(new Date(d));
    setIsAddOpen(true);
  };
  const onDayClick = (d: Date) => {
    const dt = new Date(d);
    dt.setHours(9, 0, 0, 0);
    setSelectedDate(dt);
    openAdd(dt);
  };
  const onTimeClick = (d: Date) => openAdd(d);

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
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* ── Aside (Figma: w-[300px] overflow-auto border-r #e5e6de) ── */}
        <div
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
                      <span style={{ fontSize: 11, color: TXT2, fontFamily: F_PRETENDARD }}>
                        {WK[i]}
                      </span>
                      {/* 선택일: teal원 / 오늘: teal텍스트 / 나머지: 기본 */}
                      <span
                        style={{
                          fontSize: 13,
                          fontWeight: selected ? 700 : 400,
                          color: selected ? WHITE : today ? TEAL : TXT1,
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
            <DayTimeView events={dayEvs} onEventClick={(ev) => handleEventClick(ev)} />
          </aside>
        </div>

        {/* ── 드래그 핸들 ── */}
        <div
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
        </div>

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
              padding: '0 20px',
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

          {/* 파이프라인: 월간 뷰에서만 표시 (주간은 WeekGrid 내부에서 렌더링 → 완벽 정렬) */}
          {viewMode === 'month' && (
            <div
              style={{
                flexShrink: 0,
                backgroundColor: WHITE,
                borderBottom: `1px solid ${BORDER}`,
                display: 'grid',
                // 주간 뷰와 동일한 컬럼 구성: 좌측 시간컬럼(52px) + 7개 단계(1fr)
                gridTemplateColumns: `52px repeat(7, 1fr)`,
                alignItems: 'stretch',
                overflow: 'hidden',
                height: 44,
              }}
            >
              {/* 시간 컬럼 자리 (주간 뷰와 정렬 맞춤) */}
              <div style={{ borderRight: `1px solid ${BORDER}` }} />
              {pipeline.map((s, i) => (
                <div
                  key={s.label}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '0 16px',
                    boxShadow: i > 0 ? `-1px 0 0 0 #EBEDF0` : 'none',
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
                      color: TXT1,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      flex: 1,
                      fontFamily: F_INTER,
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
                      fontFamily: F_INTER,
                    }}
                  >
                    {s.count}
                  </span>
                </div>
              ))}
            </div>
          )}

          {/* ── 달력 그리드 ── */}
          <div style={{ flex: 1, overflow: 'hidden', position: 'relative' }}>
            {viewMode === 'month' ? (
              <MonthGrid
                currentDate={currentDate}
                events={events}
                selectedDate={selectedDate}
                onDayClick={onDayClick}
                onEventClick={(ev) => {
                  setSelectedDate(new Date(ev.startAt));
                  setSelectedEvent(ev);
                }}
                onMoreClick={(d) => {
                  setCurrentDate(d);
                  setViewMode('week');
                }}
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
                pipeline={pipeline}
              />
            )}
            {/* FAB */}
            <button
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
            </button>
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
        }}
        onSaved={() => {
          refetch();
          setEditEvent(null);
        }}
        defaultDate={addDate}
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
