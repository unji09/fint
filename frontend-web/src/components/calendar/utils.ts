// src/components/calendar/utils.ts

import type { CalendarEvent } from './types';

/** 월간 달력용 42칸 배열 (null = 이전/다음달) */
export function getCalendarDays(year: number, month: number): (Date | null)[] {
  const firstDay = new Date(year, month, 1).getDay(); // 0=일
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const result: (Date | null)[] = [];

  for (let i = 0; i < firstDay; i++) result.push(null);
  for (let d = 1; d <= daysInMonth; d++) result.push(new Date(year, month, d));
  while (result.length < 42) result.push(null);

  return result;
}

/** 주간 달력용 해당 주 7일 배열 (일~토) */
export function getWeekDays(date: Date): Date[] {
  const d = new Date(date);
  d.setDate(d.getDate() - d.getDay());
  return Array.from({ length: 7 }, (_, i) => {
    const dd = new Date(d);
    dd.setDate(dd.getDate() + i);
    return dd;
  });
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

export function isToday(date: Date): boolean {
  return isSameDay(date, new Date());
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

export function formatMonthYear(date: Date): string {
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long' });
}

export function formatFullDate(date: Date): string {
  return date.toLocaleDateString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  });
}

export function getEventsForDay(events: CalendarEvent[], date: Date): CalendarEvent[] {
  return events
    .filter((e) => isSameDay(new Date(e.startAt), date))
    .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());
}

/** 주간 뷰: 이벤트의 세로 위치 (기준 시각 8시, 1시간=64px) */
export function getEventTopPx(startAt: string, baseHour = 8): number {
  const d = new Date(startAt);
  return (d.getHours() - baseHour + d.getMinutes() / 60) * 64;
}

/** 주간 뷰: 이벤트 높이 (최소 28px) */
export function getEventHeightPx(startAt: string, endAt: string): number {
  const diff = (new Date(endAt).getTime() - new Date(startAt).getTime()) / (1000 * 60 * 60);
  return Math.max(diff * 64, 28);
}

export function addMonths(date: Date, n: number): Date {
  const d = new Date(date);
  d.setMonth(d.getMonth() + n);
  return d;
}

export function addWeeks(date: Date, n: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + n * 7);
  return d;
}
