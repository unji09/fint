export type TemperatureLevel = 'HOT' | 'WARM' | 'COOL' | 'COLD' | 'STORM';

export interface Account {
  accountId: number;
  name: string;
  industry: string;
  temperature: TemperatureLevel;
  pipelineStage: string;
  updatedAt?: string;
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
