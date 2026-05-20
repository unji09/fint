// NextAction 근거 데이터(source) type별 3개 제한 동작 검증.
// 알림 패널(NotificationPanel)의 slice(0,3) 와 동일 정책이 적용되는지 확인.

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchNextActionDetail } from './useNextActions';

vi.mock('./useAuth', () => ({
  fetchWithAuth: vi.fn(),
}));

import { fetchWithAuth } from './useAuth';

const mockFetch = fetchWithAuth as unknown as ReturnType<typeof vi.fn>;

function makeResponse(data: unknown) {
  return {
    ok: true,
    json: async () => ({ data }),
  } as Response;
}

describe('fetchNextActionDetail — basisData type별 3개 제한', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('news 10개 + dart 4개 + crm 2개 → news 3 + dart 3 + crm 2 로 잘린다', async () => {
    mockFetch.mockResolvedValueOnce(
      makeResponse({
        suggestionId: 1,
        title: 'Decision Process / Paper Process 매핑',
        category: 'QUALIFICATION',
        successProbability: 40,
        importanceScore: 4,
        recommendedScript: '결재 라인부터 정리하자',
        caution: '구매팀 합의 필요',
        sources: {
          news: [
            { title: '삼성SDS 주가, 급등세… 이유는?', summary: '주가 9.62% 상승', url: 'https://news/1' },
            { title: '국내 기업용 챗GPT 시장 판 커진다', summary: 'SK AX 합류', url: 'https://news/2' },
            { title: '삼성·LG 이어 SK까지 SI 업계, 오픈AI와 실행형 AI 경쟁', summary: '경쟁 본격화', url: 'https://news/3' },
            { title: '알음알음 구독 대신 기업용 챗GPT로', summary: 'SK도 파트너', url: 'https://news/4' },
            { title: '반도체 랠리 온기, AI 데이터센터로 확산?', summary: 'GPU 부족', url: 'https://news/5' },
            { title: '삼성SDS·LG CNS 금융 AX 타고 실적 상승', summary: '차세대 수요 확대', url: 'https://news/6' },
            { title: 'SI 기업들 오픈AI와 손잡고 AI시장 경쟁', summary: '엔터프라이즈 AI', url: 'https://news/7' },
            { title: '클로드·챗GPT·제미나이 사무실 PC 점령전', summary: '도입 관건', url: 'https://news/8' },
            { title: '지역정보개발원 AI기반 보안 시스템', summary: '지방선거 활용', url: 'https://news/9' },
            { title: '삼성 노사갈등 공통 동심원 찾아야', summary: '노사발전재단', url: 'https://news/10' },
          ],
          dart: [
            { title: '유상증자 결정', summary: '500억 규모', url: 'https://dart/1' },
            { title: '사업목적 추가', summary: 'AI 솔루션', url: 'https://dart/2' },
            { title: '주요사항보고서', summary: '임원 교체', url: 'https://dart/3' },
            { title: '분기보고서', summary: '실적 호조', url: 'https://dart/4' },
          ],
          crm: [
            { title: '직전 미팅 요약', summary: 'CIO 관심 표현' },
            { title: '구매팀 합의 필요', summary: 'paper process 매핑' },
          ],
        },
      }),
    );

    const result = await fetchNextActionDetail(6, 1);

    expect(result).not.toBeNull();
    const basis = result!.basisData ?? [];

    const newsCount = basis.filter((b) => b.type === 'NEWS').length;
    const dartCount = basis.filter((b) => b.type === 'DART').length;
    const crmCount = basis.filter((b) => b.type === 'CRM').length;

    // 결과를 콘솔에 그대로 찍어 화면에서 보일 모습을 확인할 수 있게 한다.
    console.log('[가데이터 결과] basisData =', basis);
    console.log('[가데이터 결과] count =', { NEWS: newsCount, DART: dartCount, CRM: crmCount });

    expect(newsCount).toBe(3);
    expect(dartCount).toBe(3);
    expect(crmCount).toBe(2);
    expect(basis.length).toBe(8);
  });

  it('한 source만 무더기로 와도 (news 15개) 3개로 잘린다', async () => {
    const manyNews = Array.from({ length: 15 }, (_, i) => ({
      title: `뉴스 ${i + 1}`,
      summary: `요약 ${i + 1}`,
      url: `https://news/${i + 1}`,
    }));

    mockFetch.mockResolvedValueOnce(
      makeResponse({
        suggestionId: 2,
        title: '뉴스 폭주 케이스',
        category: 'DISCOVERY',
        successProbability: 30,
        importanceScore: 3,
        sources: { news: manyNews, dart: [], crm: [] },
      }),
    );

    const result = await fetchNextActionDetail(6, 2);
    const basis = result!.basisData ?? [];

    console.log('[가데이터 결과 2] basisData =', basis);

    expect(basis.length).toBe(3);
    expect(basis.every((b) => b.type === 'NEWS')).toBe(true);
  });

  it('dedup 이후에도 type별 3개 한도가 유지된다 (중복 url 5개 + 고유 5개 = 8개 → 3개)', async () => {
    mockFetch.mockResolvedValueOnce(
      makeResponse({
        suggestionId: 3,
        title: 'dedup 후 제한',
        category: 'CLOSING',
        successProbability: 90,
        importanceScore: 5,
        sources: {
          news: [
            { title: '동일1', url: 'https://news/dup' },
            { title: '동일1', url: 'https://news/dup' }, // dup
            { title: '동일1', url: 'https://news/dup' }, // dup
            { title: '고유1', url: 'https://news/a' },
            { title: '고유2', url: 'https://news/b' },
            { title: '고유3', url: 'https://news/c' },
            { title: '고유4', url: 'https://news/d' },
          ],
        },
      }),
    );

    const result = await fetchNextActionDetail(6, 3);
    const basis = result!.basisData ?? [];

    console.log('[가데이터 결과 3] basisData =', basis);

    // dedup → 5개 (dup/a/b/c/d), 그 중 NEWS 첫 3개만 남는다
    expect(basis.length).toBe(3);
    expect(basis.every((b) => b.type === 'NEWS')).toBe(true);
  });
});
