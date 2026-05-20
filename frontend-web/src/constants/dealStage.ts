/**
 * Deal 파이프라인 스테이지 — 7단계 (F!nt CRM Color System v3.0 기준).
 *
 * 컬러 토큰: src/styles/tokens.css 의 --stage-* 변수.
 * StageBadge 컴포넌트는 이 매핑만 참조한다.
 *
 * NOTE: docs/requirements.md REQ-DEAL이 6단계(LEAD/QUALIFIED/...)로 적혀있다면
 *       시안 기준 7단계로 갱신 제안 필요.
 */

export type DealStageCode =
  | 'FIRST_MEETING'
  | 'NEEDS'
  | 'PROPOSAL_WRITE'
  | 'PROPOSAL_PRESENT'
  | 'NEGOTIATION'
  | 'CONTRACT'
  | 'CLOSED';

export interface DealStageMeta {
  code: DealStageCode;
  label: string;
  /** CSS 변수 이름 (예: '--stage-first-meeting'). var() 래핑은 호출부에서. */
  colorVar: string;
}

// 라벨은 BE pipeline_stages 테이블 / DealDetailPanel 의 PIPELINE_LABELS 와 동일한 7단계 명칭을 사용.
// (코드는 영문 식별자 그대로 유지)
export const DEAL_STAGES: readonly DealStageMeta[] = [
  { code: 'FIRST_MEETING', label: '발굴', colorVar: '--stage-first-meeting' },
  { code: 'NEEDS', label: '가치 제안', colorVar: '--stage-needs' },
  { code: 'PROPOSAL_WRITE', label: '솔루션 설계', colorVar: '--stage-proposal-write' },
  { code: 'PROPOSAL_PRESENT', label: '제안 제출', colorVar: '--stage-proposal-present' },
  { code: 'NEGOTIATION', label: '협상', colorVar: '--stage-negotiation' },
  { code: 'CONTRACT', label: '계약 대기', colorVar: '--stage-contract' },
  { code: 'CLOSED', label: '수주', colorVar: '--stage-closed' },
] as const;

const STAGE_BY_CODE = new Map<DealStageCode, DealStageMeta>(
  DEAL_STAGES.map((s) => [s.code, s]),
);

export function getDealStage(code: DealStageCode): DealStageMeta {
  const stage = STAGE_BY_CODE.get(code);
  if (!stage) throw new Error(`Unknown deal stage: ${code}`);
  return stage;
}
