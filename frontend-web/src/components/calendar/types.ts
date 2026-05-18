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

export type ViewMode = 'month' | 'week' | 'day';

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

export const STAGE_COLOR: Record<string, string> = {
  FIRST_MEETING: '#7c3aed', NEEDS: '#2563eb', NEEDS_ANALYSIS: '#2563eb',
  PROPOSAL_WRITE: '#0d9488', PROPOSAL_WRITING: '#0d9488', PROPOSAL_PRESENT: '#06b6d4',
  NEGOTIATION: '#ea580c', CONTRACT: '#475569', CONTRACT_REVIEW: '#475569', CLOSED: '#ef4444',
  '발굴': '#8CB2E5', '가치 제안': '#738CD9', '솔루션 설계': '#806BC7',
  '제안 제출': '#6B5CBF', '협상': '#997333', '계약 대기': '#339E80', '수주': '#268C66',
  '첫 미팅 준비': '#7c3aed', '니즈 파악': '#2563eb', '제안서 작성': '#0d9488',
  '제안 발표': '#06b6d4', '협상 중': '#ea580c', '계약 검토': '#475569',
};

export const STAGE_BG: Record<string, string> = {
  FIRST_MEETING: '#f5f3ff', NEEDS: '#eff6ff', NEEDS_ANALYSIS: '#eff6ff',
  PROPOSAL_WRITE: '#f0fdfa', PROPOSAL_WRITING: '#f0fdfa', PROPOSAL_PRESENT: '#ecfeff',
  NEGOTIATION: '#fff7ed', CONTRACT: '#f8fafc', CONTRACT_REVIEW: '#f8fafc', CLOSED: '#fef2f2',
  '발굴': '#EEF4FB', '가치 제안': '#EAEEF9', '솔루션 설계': '#ECE9F7',
  '제안 제출': '#E9E7F5', '협상': '#F0EAE0', '계약 대기': '#E0F0EC', '수주': '#DEEEE8',
  '첫 미팅 준비': '#f5f3ff', '니즈 파악': '#eff6ff', '제안서 작성': '#f0fdfa',
  '제안 발표': '#ecfeff', '협상 중': '#fff7ed', '계약 검토': '#f8fafc',
};

const GRAY = { color: '#9CA3AF', bg: '#F3F4F6' };

export function getEventColor(event: CalendarEvent): { color: string; bg: string } {
  const ps = event.pipelineStage;
  if (ps) {
    const key = ps.stageCode || ps.stageName;
    if (key) {
      const c = STAGE_COLOR[key]; const b = STAGE_BG[key];
      if (c && b) return { color: c, bg: b };
    }
    if (ps.stageName) {
      const c = STAGE_COLOR[ps.stageName]; const b = STAGE_BG[ps.stageName];
      if (c && b) return { color: c, bg: b };
    }
  }
  return GRAY;
}

export const SOURCE_STYLE: Record<EventSource, { dot: string; bg: string; label: string }> = {
  GOOGLE: { dot: '#EA4335', bg: '#FEF2F2', label: 'Google' },
  FINT: { dot: '#0686D4', bg: '#EBF5FF', label: 'F!NT' },
};
