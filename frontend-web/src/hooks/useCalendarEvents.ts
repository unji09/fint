import { useCallback, useEffect, useState } from 'react';
import type { CalendarEvent, EventCategory, EventSource } from '@/components/calendar/types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

function authHeader(): HeadersInit {
  const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return token ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } : {};
}

function getMonthRange(date: Date) {
  const start = new Date(date.getFullYear(), date.getMonth(), 1);
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0);
  return { start, end };
}

function getWeekRange(date: Date) {
  const d = new Date(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  const mon = new Date(d);
  mon.setDate(d.getDate() + diff);
  const sun = new Date(mon);
  sun.setDate(mon.getDate() + 6);
  return { start: mon, end: sun };
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
  viewMode: 'month' | 'week';
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
      const res = await fetch(`${API_BASE}/calendar/events?${params}`, {
        headers: authHeader(),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setEvents((json.data?.content ?? []).map((r: any) => toCalendarEvent(r)));
    } catch (err) {
      console.error('[useCalendarEvents]', err);
      setEvents([]); // 실패 시 빈 배열 → page에서 MOCK fallback 처리
    } finally {
      setLoading(false);
    }
  }, [currentDate, viewMode]);

  useEffect(() => {
    fetchEvents();
  }, [fetchEvents]);

  return { events, loading, refetch: fetchEvents };
}

/** 일정 상세 단건 조회 */
export async function fetchEventDetail(eventId: string): Promise<CalendarEvent | null> {
  try {
    const res = await fetch(`${API_BASE}/calendar/events/${encodeURIComponent(eventId)}`, {
      headers: authHeader(),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    return toCalendarEvent(json.data);
  } catch (err) {
    console.error('[fetchEventDetail]', err);
    return null;
  }
}

/** 구글 캘린더 동기화 — 동기화 버튼에 직접 연결해서 사용 */
export async function syncCalendar(): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE}/calendar/sync`, {
      method: 'POST',
      headers: authHeader(),
    });
    return res.ok;
  } catch {
    return false;
  }
}
