// src/components/calendar/types.ts

export type EventSource = 'GOOGLE' | 'FINT';
// ✅ 피그마 기준: 통화→전화, 메모→업무
export type EventCategory = '미팅' | '전화' | '이메일' | '업무';

export interface CalendarEvent {
  eventId: string;
  source: EventSource;
  title: string;
  startAt: string;
  endAt: string;
  category?: EventCategory;
  accountName?: string;
  accountId?: number;
  dealId?: number;
  dealTitle?: string;
  pipelineStage?: {
    stageId: number;
    stageName: string;
    stageCode: string;
  };
  linkedActivityId?: number;
  attendees?: {
    internal: string[];
    external: string[];
  };
  memo?: string;
}

export type ViewMode = 'month' | 'week';

// ✅ 피그마 색상 규칙 기준 (색상_규칙.png 확인)
export const CATEGORY_COLOR: Record<string, string> = {
  미팅: '#7F77DD', // 보라 (Figma: #ecebfa bg)
  전화: '#378ADD', // 파랑 (Figma: #e1edfa bg)
  이메일: '#D85A30', // 코랄 (Figma: #f9e6e0 bg)
  업무: '#1D9E75', // 틸/초록 (Figma: #ddf0ea bg)
};

export const CATEGORY_BG: Record<string, string> = {
  미팅: '#ECEBFA',
  전화: '#E1EDFA',
  이메일: '#F9E6E0',
  업무: '#DDF0EA',
};

export const SOURCE_STYLE: Record<EventSource, { dot: string; bg: string; label: string }> = {
  GOOGLE: { dot: '#EA4335', bg: '#FEF2F2', label: 'Google' },
  FINT: { dot: '#0686D4', bg: '#EBF5FF', label: 'F!NT' },
};
