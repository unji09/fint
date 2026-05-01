/**
 * 구매위원회 페르소나 — 블로커 / 챔피언 / 회의주의자 / 동반자.
 * 컬러: --persona-*-bg / --persona-*-fg (tokens.css)
 */

export type PersonaCode = 'BLOCKER' | 'CHAMPION' | 'SKEPTIC' | 'COMPANION';

export interface PersonaMeta {
  code: PersonaCode;
  label: string;
  bgVar: string;
  fgVar: string;
}

export const PERSONAS: readonly PersonaMeta[] = [
  {
    code: 'BLOCKER',
    label: '블로커 — 공략',
    bgVar: '--persona-blocker-bg',
    fgVar: '--persona-blocker-fg',
  },
  {
    code: 'CHAMPION',
    label: '챔피언 — 활용',
    bgVar: '--persona-champion-bg',
    fgVar: '--persona-champion-fg',
  },
  {
    code: 'SKEPTIC',
    label: '회의주의자',
    bgVar: '--persona-skeptic-bg',
    fgVar: '--persona-skeptic-fg',
  },
  {
    code: 'COMPANION',
    label: '동반자 — 전파',
    bgVar: '--persona-companion-bg',
    fgVar: '--persona-companion-fg',
  },
] as const;

const BY_CODE = new Map<PersonaCode, PersonaMeta>(PERSONAS.map((p) => [p.code, p]));

export function getPersona(code: PersonaCode): PersonaMeta {
  const p = BY_CODE.get(code);
  if (!p) throw new Error(`Unknown persona: ${code}`);
  return p;
}
