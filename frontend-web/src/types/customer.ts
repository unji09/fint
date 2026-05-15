// 백엔드 Mood enum: RAINBOW | SUNNY | CLOUDY | RAINY | THUNDER
export type MoodLevel = 'RAINBOW' | 'SUNNY' | 'CLOUDY' | 'RAINY' | 'THUNDER';
export type TemperatureLevel = MoodLevel;

export interface Account {
  accountId: number;
  name: string;
  industry: string;
  temperature: MoodLevel;
  pipelineStage: string;
  updatedAt?: string;
  /** 현재 분위기(날씨) 의 이유. 백엔드 응답에 들어오면 자동 표시. */
  moodReason?: string | null;
  /** 분위기 갱신 시각 (ISO) */
  moodUpdatedAt?: string | null;
}

/** GET /accounts 또는 /accounts/searchable 응답 아이템 */
export interface ApiAccountItem {
  accountId: number;
  name: string;
  industry: string;
  latestMood?: MoodLevel | null;
  temperature?: number | null;
}

/** GET /accounts/{id}/contacts 응답 아이템 */
export interface ApiContact {
  contactId: number;
  name: string;
  title: string | null;
  phone: string | null;
  email: string | null;
  personality: string | null;
}

/** GET /accounts/{id}/signals 응답 아이템 */
export interface ApiSignal {
  source: 'NEWS' | 'DART';
  title: string;
  content: string;
  url: string | null;
  occurredAt: string;
}

/** GET /accounts/{id} 딜 아이템 */
export interface ApiDeal {
  dealId: number;
  title: string;
  stage: string | null;
  probability: number | null;
  amount: number | null;
  expectedClose: string | null;
}

/** CustomerSidebar에서 사용하는 담당자 UI 타입 */
export interface ContactInfo {
  contactId?: number;
  name: string;
  role: string;
  color: string;
  phone?: string;
  email?: string;
  memo?: string;
}

export interface Signal {
  type: 'DART' | 'NEWS';
  time: string;
  /** 한 줄 제목 (평소 표시) */
  title: string;
  /** 본문/요약 — 호버 시 펼쳐서 미리보기, 클릭 시 전체 표시 */
  content: string;
  /** 외부 원문 링크 — 있을 때만 "자세히 보기" 노출 */
  url?: string;
  isNew?: boolean;
}

export interface Deal {
  dealId: number;
  title: string;
  assignee: string;
  expectedAmount: number;
  expectedCloseDate?: string;
}

// API 명세 data.sources.{news|dart|crm}[].{ title, summary, url } 평탄화. content 는 호환 폴백.
export interface StrategyBasis {
  type: 'NEWS' | 'DART' | 'CRM';
  title?: string;
  summary?: string;
  url?: string;
  /** 호환: 구버전 표시 텍스트 — title/summary 가 없을 때만 사용 */
  content?: string;
}

export interface StrategyCard {
  id: number;
  title: string;
  category: string;
  successRate: number;
  isExpanded?: boolean;
  basisData?: StrategyBasis[];
  aiComment?: string;
  warning?: string;
}
