'use client';

import { useCallback, useEffect, useState } from 'react';
import type { CalendarEvent, EventCategory, EventSource, ViewMode } from '@/components/calendar/types';
import { fetchWithAuth } from '@/hooks/useAuth';

function getMonthRange(date: Date) {
  const start = new Date(date.getFullYear(), date.getMonth(), 1);
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0);
  return { start, end };
}

function getWeekRange(date: Date) {
  const d = new Date(date);
  const sun = new Date(d);
  sun.setDate(d.getDate() - d.getDay());
  const sat = new Date(sun);
  sat.setDate(sun.getDate() + 6);
  return { start: sun, end: sat };
}

function fmt(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${dd}`;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function toCalendarEvent(raw: any): CalendarEvent {
  return {
    eventId: raw.eventId,
    source: raw.source as EventSource,
    title: raw.title,
    startAt: raw.startAt,
    endAt: raw.endAt,
    category: raw.category as EventCategory | undefined,
    accountName: raw.accountName ?? undefined,
    accountId: raw.accountId ?? undefined,
    pipelineStage: raw.pipelineStage ?? undefined,
    linkedActivityId: raw.linkedActivityId ?? undefined,
    dealId: raw.dealId ?? undefined,
    dealTitle: raw.dealTitle ?? undefined,
    attendees: raw.attendees ?? undefined,
    memo: raw.memo ?? undefined,
  };
}

interface UseCalendarEventsOptions {
  currentDate: Date;
  viewMode: ViewMode;
}

export interface UseCalendarEventsReturn {
  events: CalendarEvent[];
  loading: boolean;
  refetch: () => void;
}

export function useCalendarEvents({
  currentDate,
  viewMode,
}: UseCalendarEventsOptions): UseCalendarEventsReturn {
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchEvents = useCallback(async () => {
    setLoading(true);

    const range = viewMode === 'month' ? getMonthRange(currentDate) : getWeekRange(currentDate);

    const params = new URLSearchParams({
      startDate: fmt(range.start),
      endDate: fmt(range.end),
      size: '100',
    });

    try {
      const res = await fetchWithAuth(`/calendar/events?${params}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setEvents((json.data?.content ?? []).map((r: any) => toCalendarEvent(r)));
    } catch (err) {
      console.error('[useCalendarEvents]', err);
      setEvents([]); // 실패 시 빈 배열 — 사용자가 명시적으로 빈 캘린더를 보게 둔다.
    } finally {
      setLoading(false);
    }
  }, [currentDate, viewMode]);

  useEffect(() => {
    fetchEvents();
  }, [fetchEvents]);

  return { events, loading, refetch: fetchEvents };
}

/** 일정 상세 단건 조회.
 *  FINT 활동 (eventId = "act-{activityId}") 이면 활동 상세 API 도 함께 호출해
 *  memo / attendees 같은 활동 고유 필드를 보강한다.
 *  (백엔드 /calendar/events 응답이 memo 를 포함하지 않을 수 있어, 누락되면 패널을
 *   닫고 다시 열었을 때 memo 가 빈 값으로 덮어쓰이는 문제를 방어)
 */
export async function fetchEventDetail(eventId: string): Promise<CalendarEvent | null> {
  try {
    const res = await fetchWithAuth(`/calendar/events/${encodeURIComponent(eventId)}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    const base = toCalendarEvent(json.data);
    console.log('[fetchEventDetail] calendar', { eventId, calendarMemo: json?.data?.memo, base });

    if (base.source !== 'FINT' || !eventId.startsWith('act-')) {
      // FINT 활동이 아니면 localStorage 캐시 fallback (캘린더 응답에 memo 없는 경우)
      const cached = readMemoCache(eventId);
      return cached !== null ? { ...base, memo: base.memo || cached } : base;
    }
    const activityId = eventId.replace(/^act-/, '');
    try {
      const r = await fetchWithAuth(`/activities/${activityId}`);
      if (!r.ok) return base;
      const aj = await r.json();
      const d = aj?.data ?? {};
      console.log('[fetchEventDetail] activity', { activityId, activityMemo: d?.memo, raw: d });
      const activityMemo: unknown = d.memo;
      const activityAttendees: unknown = d.attendees;
      const merged: CalendarEvent = {
        ...base,
        memo: typeof activityMemo === 'string' && activityMemo.length > 0
          ? activityMemo
          : (base.memo || undefined),
        attendees: activityAttendees && typeof activityAttendees === 'object'
          ? (activityAttendees as { internal: string[]; external: string[] })
          : base.attendees,
      };
      // 백엔드가 어떤 이유로 memo 를 누락해도 사용자가 직전에 저장한 값은 보존되어야 한다.
      // localStorage 캐시 fallback.
      if (!merged.memo) {
        const cached = readMemoCache(eventId);
        if (cached) {
          console.log('[fetchEventDetail] using cached memo', { eventId, cached });
          merged.memo = cached;
        }
      }
      return merged;
    } catch {
      return base;
    }
  } catch (err) {
    console.error('[fetchEventDetail]', err);
    return null;
  }
}

// ─── 메모 localStorage 캐시 ───────────────────────────────────────────────
// 백엔드가 어떤 이유로 memo 를 정상 반환하지 못하는 환경에서도 사용자가 방금
// 저장한 메모가 사라지지 않도록 짧은 임시 캐시를 유지한다.
const MEMO_CACHE_PREFIX = 'fint:memo:';
const MEMO_CACHE_TTL_MS = 24 * 60 * 60 * 1000;

export function writeMemoCache(eventId: string, memo: string): void {
  if (typeof window === 'undefined') return;
  try {
    if (memo) {
      localStorage.setItem(
        MEMO_CACHE_PREFIX + eventId,
        JSON.stringify({ memo, ts: Date.now() }),
      );
    } else {
      localStorage.removeItem(MEMO_CACHE_PREFIX + eventId);
    }
  } catch { /* ignore */ }
}

export function readMemoCacheSync(eventId: string): string | null {
  return readMemoCache(eventId);
}

function readMemoCache(eventId: string): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem(MEMO_CACHE_PREFIX + eventId);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { memo?: string; ts?: number };
    if (!parsed?.memo) return null;
    if (parsed.ts && Date.now() - parsed.ts > MEMO_CACHE_TTL_MS) {
      localStorage.removeItem(MEMO_CACHE_PREFIX + eventId);
      return null;
    }
    return parsed.memo;
  } catch {
    return null;
  }
}

/** 일정 카드 리사이즈 (드래그 종료 시점) — startAt/endAt 만 PATCH.
 *  FINT 활동(eventId = "act-{id}") 만 처리. Google 이벤트는 false 반환.
 *  ISO 문자열은 호출자가 KST(+09:00) 로 직렬화해 전달.
 */
export async function resizeActivity(eventId: string, startAt: string, endAt: string): Promise<boolean> {
  if (!eventId.startsWith('act-')) return false;
  const activityId = eventId.replace(/^act-/, '');
  try {
    const res = await fetchWithAuth(`/activities/${activityId}`, {
      method: 'PATCH',
      body: JSON.stringify({ startAt, endAt }),
    });
    return res.ok;
  } catch (err) {
    console.error('[resizeActivity]', err);
    return false;
  }
}

/** 구글 캘린더 동기화 — 동기화 버튼에 직접 연결해서 사용 */
export async function syncCalendar(): Promise<boolean> {
  try {
    const res = await fetchWithAuth(`/calendar/sync`, { method: 'POST' });
    return res.ok;
  } catch {
    return false;
  }
}
