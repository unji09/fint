// StrategyCard 가 basisData 를 받았을 때 NEWS/DART/CRM 라벨이 정확히 몇 개씩 렌더되는지 검증.
// useNextActions 의 limitBasisPerType 결과가 실제 화면 DOM 에 그대로 반영되는지 본다.

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import StrategyCardComponent from './StrategyCard';
import type { StrategyCard } from '@/types/customer';

// useBreakpoint 가 window matchMedia 에 접근하므로 mock
vi.mock('@/hooks/useBreakpoint', () => ({
  default: () => 'desktop',
}));

const baseCard: StrategyCard = {
  id: 1,
  title: 'Decision Process / Paper Process 매핑',
  category: 'QUALIFICATION',
  successRate: 40,
  isExpanded: true,
  basisData: [
    { type: 'NEWS', title: '삼성SDS 주가 급등', summary: '9.62% 상승', url: 'https://news/1' },
    { type: 'NEWS', title: '기업용 챗GPT 시장 판 커진다', summary: 'SK AX 합류', url: 'https://news/2' },
    { type: 'NEWS', title: 'SI 업계 실행형 AI 경쟁', summary: '경쟁 본격화', url: 'https://news/3' },
    { type: 'DART', title: '유상증자 결정', summary: '500억 규모', url: 'https://dart/1' },
    { type: 'DART', title: '사업목적 추가', summary: 'AI 솔루션', url: 'https://dart/2' },
    { type: 'DART', title: '주요사항보고서', summary: '임원 교체', url: 'https://dart/3' },
    { type: 'CRM', title: '직전 미팅 요약', summary: 'CIO 관심 표현' },
    { type: 'CRM', title: '구매팀 합의 필요', summary: 'paper process 매핑' },
  ],
};

describe('StrategyCard 가데이터 렌더링', () => {
  it('basisData 8개를 받으면 화면에 NEWS 3 / DART 3 / CRM 2 라벨이 보인다', () => {
    render(<StrategyCardComponent card={baseCard} index={0} />);

    const newsTags = screen.getAllByText('NEWS');
    const dartTags = screen.getAllByText('DART');
    const crmTags = screen.getAllByText('CRM');

    console.log('[화면 시뮬레이션]');
    console.log('  - 카드 제목:', baseCard.title);
    console.log('  - NEWS 라벨 등장 횟수:', newsTags.length);
    console.log('  - DART 라벨 등장 횟수:', dartTags.length);
    console.log('  - CRM 라벨 등장 횟수:', crmTags.length);
    console.log('[근거 데이터 목록 표시 순서]');
    baseCard.basisData!.forEach((d, i) => {
      console.log(`  ${i + 1}. [${d.type}] ${d.title}${d.summary ? ' — ' + d.summary : ''}`);
    });

    expect(newsTags.length).toBe(3);
    expect(dartTags.length).toBe(3);
    expect(crmTags.length).toBe(2);
  });

  it('isExpanded=false 이면 근거 데이터 영역은 그려지지 않는다 (불도 안 들어옴)', () => {
    render(<StrategyCardComponent card={{ ...baseCard, isExpanded: false }} index={0} />);

    expect(screen.queryByText('근거 데이터')).toBeNull();
    expect(screen.queryAllByText('NEWS').length).toBe(0);
  });

  it('isExpanded 가 다른 카드 두 개를 같이 렌더해도 한 쪽만 펼쳐진다 (불 두 개 버그 회귀 방지)', () => {
    const cardA: StrategyCard = { ...baseCard, id: 1, isExpanded: true };
    const cardB: StrategyCard = { ...baseCard, id: 2, isExpanded: false, title: 'Economic Buyer 식별' };

    render(
      <div>
        <StrategyCardComponent card={cardA} index={0} />
        <StrategyCardComponent card={cardB} index={1} />
      </div>,
    );

    // A 만 근거 데이터 영역이 있어야 한다
    expect(screen.getAllByText('근거 데이터').length).toBe(1);
  });
});
