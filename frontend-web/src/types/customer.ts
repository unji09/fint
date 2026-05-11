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
  content: string;
  isNew?: boolean;
}

export interface Deal {
  dealId: number;
  title: string;
  assignee: string;
  expectedAmount: number;
  expectedCloseDate?: string;
}

export interface StrategyCard {
  id: number;
  title: string;
  category: string;
  successRate: number;
  isExpanded?: boolean;
  basisData?: { type: 'NEWS' | 'DART' | 'CRM'; content: string }[];
  aiComment?: string;
  warning?: string;
}
