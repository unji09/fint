'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import CustomerSidebar from '@/components/customer/CustomerSidebar';
import StrategyCardComponent from '@/components/customer/StrategyCard';
import SignalItem from '@/components/customer/SignalItem';
import DealCard from '@/components/customer/DealCard';
import DealDetailPanel from '@/components/customer/DealDetailPanel';
import type { Account, Signal, Deal, StrategyCard } from '@/types/customer';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';
function authHeader() {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}` } : {};
}

type ContactInfo = {
  name: string;
  role: string;
  color: string;
  phone?: string;
  email?: string;
  memo?: string;
};

/* ── Mock 데이터 (API 연동 전) ── */
const MOCK_ACCOUNTS: Account[] = [
  {
    accountId: 1,
    name: 'Samsung SDS',
    industry: 'IT서비스',
    temperature: 'HOT',
    pipelineStage: '파이프 단계',
  },
  {
    accountId: 2,
    name: 'Hanul CNS',
    industry: 'SI',
    temperature: 'WARM',
    pipelineStage: '파이프 단계',
  },
  {
    accountId: 3,
    name: 'Kakao Mobility',
    industry: '플랫폼',
    temperature: 'COOL',
    pipelineStage: '파이프 단계',
  },
  {
    accountId: 4,
    name: 'Hyundai Card',
    industry: '금융',
    temperature: 'COLD',
    pipelineStage: '파이프 단계',
  },
  {
    accountId: 5,
    name: 'CJ Logistics',
    industry: '물류',
    temperature: 'STORM',
    pipelineStage: '파이프 단계',
  },
  {
    accountId: 6,
    name: 'NCSOFT',
    industry: '게임',
    temperature: 'WARM',
    pipelineStage: '파이프 단계',
  },
];
const MOCK_SIGNALS: Signal[] = [
  { type: 'DART', time: '오늘 09:30', content: '신규 시설투자 3,000억 공시 - IT 인프라 확장 예상' },
  {
    type: 'NEWS',
    time: '어제 14:15',
    content: '데이터센터 신축으로 클라우드 전환 가속화 기사 보도',
  },
  { type: 'NEWS', time: '3일 전', content: '신임 CFO 박성준 부사장 선임 (비용 효율화 강조 성향)' },
];
const MOCK_DEALS: Deal[] = [
  { dealId: 1, title: '제목', assignee: '담당자', expectedAmount: 0 },
  {
    dealId: 2,
    title: '한번 팔기',
    assignee: '홍길동',
    expectedAmount: 120000000,
    expectedCloseDate: '2024-12-12',
  },
  {
    dealId: 3,
    title: 'AI 연장',
    assignee: '이영희',
    expectedAmount: 850000000,
    expectedCloseDate: '2024-06-30',
  },
  { dealId: 4, title: '인프라 계약', assignee: '김민준', expectedAmount: 250000000 },
  { dealId: 5, title: '보안 솔루션', assignee: '박지수', expectedAmount: 180000000 },
];
const MOCK_STRATEGIES: StrategyCard[] = [
  {
    id: 1,
    title: '시장 점유율 확대를 위한 채널 다각화',
    category: '시장 확장 전략',
    successRate: 76,
  },
  {
    id: 2,
    title: 'ROI 기반 아키텍처 재설계안 리뷰',
    category: 'ROI 기반 전략',
    successRate: 89,
    basisData: [
      { type: 'NEWS', content: '클라우드 전환 비용 최적화 트렌드 (2024.Q1)' },
      { type: 'DART', content: '분기 실적 보고서 내 IT 인프라 유지비용 분석' },
      { type: 'CRM', content: '고객 데이터 유실 위험 방지 및 가용성 개선 요청' },
    ],
    aiComment:
      '"이지은 팀장님, 지난 미팅에서 말씀하신 ROI 최적화 관점을 반영하여 아키텍처를 재설계했습니다. 특히 레거시 호환 모듈을 추가하여 초기 전환 비용을 40% 절감하는 방향으로 제안 드리고자 합니다."',
    warning: `상대방의 침묵이 5초 이상 지속될 시, '기술 지원 기간 보장' 카드를 제시하세요.`,
  },
  { id: 3, title: '신규 플랫폼 통합 로열티 프로그램', category: '고객 유지 전략', successRate: 62 },
  {
    id: 4,
    title: '데이터 보안 강화 및 컴플라이언스 대응',
    category: '리스크 관리 전략',
    successRate: 94,
  },
];
const SIGNAL_ACCENTS = ['#06b6d4', '#cbd5e1', '#fb923c'];

/* ════════ 메인 페이지 (상태 관리 + 레이아웃만) ════════ */
export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const scrollRef = useRef<HTMLDivElement>(null);

  const [accounts, setAccounts] = useState<Account[]>(MOCK_ACCOUNTS);
  const [loading, setLoading] = useState(false);
  const [selectedContact, setSelectedContact] = useState<ContactInfo | null>(null);
  const [showDealList, setShowDealList] = useState(false); // 더보기 → 딜 목록
  const [selectedDeal, setSelectedDeal] = useState<Deal | null>(null);

  const account = accounts.find((a) => String(a.accountId) === String(id)) ?? accounts[0];

  /* 고객사 이동 시 뷰 초기화 */
  useEffect(() => {
    setSelectedContact(null);
    setShowDealList(false);
    setSelectedDeal(null);
  }, [id]);

  /* API 연동 */
  useEffect(() => {
    fetch(`${API_BASE}/accounts`, { headers: authHeader() as HeadersInit })
      .then((r) => r.json())
      .then((j) => setAccounts(j.data ?? []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const handleMoreDeals = () => {
    setShowDealList(true);
    setSelectedDeal(null);
  };
  const handleDealClick = (deal: Deal) =>
    setSelectedDeal((prev) => (prev?.dealId === deal.dealId ? null : deal));
  const handleContactSelect = (c: ContactInfo | null) => {
    setSelectedContact(c);
    setShowDealList(false);
    setSelectedDeal(null);
  };

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, backgroundColor: '#f8fafc', zIndex: -1 }} />
      <div
        style={{
          position: 'fixed',
          top: 90,
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          overflow: 'hidden',
        }}
      >
        <CustomerSidebar
          accounts={accounts}
          selectedId={account?.accountId ?? null}
          loading={loading}
          onContactSelect={handleContactSelect}
        />

        <main
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '28px 60px 48px',
            display: 'flex',
            flexDirection: 'column',
            gap: 24,
          }}
        >
          {/* ── 브레드크럼 헤더 ── */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {/* 회사명 */}
            <h1
              onClick={() => {
                setShowDealList(false);
                setSelectedContact(null);
                setSelectedDeal(null);
              }}
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontWeight: 900,
                fontSize: 26,
                margin: 0,
                lineHeight: 1,
                color: showDealList || selectedContact ? '#64748b' : '#0f172a',
                cursor: showDealList || selectedContact ? 'pointer' : 'default',
              }}
            >
              {account?.name ?? 'Samsung SDS'}
            </h1>

            {/* → 담당자 */}
            {selectedContact && (
              <>
                <span style={{ color: '#cbd5e1', fontSize: 20 }}>→</span>
                <h1
                  onClick={() => {
                    setShowDealList(false);
                    setSelectedDeal(null);
                  }}
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontWeight: 900,
                    fontSize: 26,
                    margin: 0,
                    lineHeight: 1,
                    color: showDealList ? '#64748b' : '#0f172a',
                    cursor: showDealList ? 'pointer' : 'default',
                  }}
                >
                  담당자
                </h1>
              </>
            )}

            {/* → 상세 딜 */}
            {showDealList && (
              <>
                <span style={{ color: '#cbd5e1', fontSize: 20 }}>→</span>
                <h1
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontWeight: 900,
                    fontSize: 26,
                    color: '#0f172a',
                    margin: 0,
                    lineHeight: 1,
                  }}
                >
                  상세 딜
                </h1>
              </>
            )}

            {/* 기본 뷰 subtitle */}
            {!showDealList && !selectedContact && (
              <span
                style={{
                  fontFamily: 'Pretendard,sans-serif',
                  fontWeight: 300,
                  fontSize: 14,
                  color: '#64748b',
                  alignSelf: 'flex-end',
                  marginBottom: 2,
                }}
              >
                {account?.industry} · 최신 온도
              </span>
            )}
          </div>

          {/* ── 딜 목록 뷰 (더보기 클릭) ── */}
          {showDealList && (
            <>
              <div
                style={{
                  background: 'white',
                  border: '1px solid #e2eaf0',
                  borderRadius: 8,
                  boxShadow: '0 1px 1px rgba(0,0,0,0.05)',
                  padding: '22px 24px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
                  <span
                    style={{
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 600,
                      fontSize: 18,
                      color: '#1e293b',
                    }}
                  >
                    딜 목록
                  </span>
                  <button
                    style={{
                      background: '#f2fcff',
                      border: '1px solid #dbeafe',
                      borderRadius: 9,
                      padding: '4px 12px',
                      fontFamily: 'Pretendard,sans-serif',
                      fontSize: 13,
                      color: '#06b6d4',
                      cursor: 'pointer',
                    }}
                  >
                    + 딜 추가
                  </button>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <button
                    onClick={() => scrollRef.current?.scrollBy({ left: -216, behavior: 'smooth' })}
                    style={{
                      width: 32,
                      height: 32,
                      borderRadius: '50%',
                      border: '1px solid #e2e8f0',
                      background: 'white',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
                    }}
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                      <path
                        d="M15 18l-6-6 6-6"
                        stroke="#64748b"
                        strokeWidth="2"
                        strokeLinecap="round"
                      />
                    </svg>
                  </button>
                  <div style={{ overflow: 'hidden', flex: 1 }}>
                    <div
                      ref={scrollRef}
                      style={{
                        display: 'flex',
                        gap: 16,
                        overflowX: 'auto',
                        scrollbarWidth: 'none',
                        alignItems: 'stretch',
                      }}
                    >
                      {MOCK_DEALS.map((d) => (
                        <DealCard
                          key={d.dealId}
                          deal={d}
                          selected={selectedDeal?.dealId === d.dealId}
                          onClick={() => handleDealClick(d)}
                        />
                      ))}
                    </div>
                  </div>
                  <button
                    onClick={() => scrollRef.current?.scrollBy({ left: 216, behavior: 'smooth' })}
                    style={{
                      width: 32,
                      height: 32,
                      borderRadius: '50%',
                      border: '1px solid #e2e8f0',
                      background: 'white',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
                    }}
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                      <path
                        d="M9 18l6-6-6-6"
                        stroke="#64748b"
                        strokeWidth="2"
                        strokeLinecap="round"
                      />
                    </svg>
                  </button>
                </div>
              </div>
              {selectedDeal && <DealDetailPanel />}
            </>
          )}

          {/* ── 기본 / 담당자 뷰 ── */}
          {!showDealList && (
            <>
              {/* 정보 카드 */}
              <div
                style={{
                  background: 'white',
                  border: '1px solid #e2eaf0',
                  borderRadius: 8,
                  boxShadow: '0 1px 1px rgba(0,0,0,0.05)',
                  display: 'flex',
                  flexShrink: 0,
                }}
              >
                {/* 왼쪽: 회사 정보 or 담당자 정보 */}
                <div
                  style={{
                    flex: '1 1 0',
                    minWidth: 0,
                    borderRight: '2px solid rgba(6,182,212,0.3)',
                    padding: '22px 40px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 12,
                  }}
                >
                  {selectedContact ? (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontWeight: 600,
                            fontSize: 16,
                            color: '#1e293b',
                          }}
                        >
                          담당자
                        </span>
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontSize: 12,
                            color: '#94a3b8',
                            cursor: 'pointer',
                          }}
                        >
                          담당자 삭제
                        </span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div
                          style={{
                            width: 32,
                            height: 32,
                            borderRadius: '50%',
                            background: selectedContact.color,
                            flexShrink: 0,
                          }}
                        />
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontWeight: 700,
                            fontSize: 20,
                            color: '#1e293b',
                          }}
                        >
                          {selectedContact.name}
                        </span>
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontSize: 14,
                            color: '#6d797d',
                          }}
                        >
                          {selectedContact.role}
                        </span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            fontFamily: 'Inter,sans-serif',
                            fontSize: 14,
                            color: '#475569',
                          }}
                        >
                          📞 {selectedContact.phone ?? '02-1234-5678'}
                        </div>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            fontFamily: 'Inter,sans-serif',
                            fontSize: 14,
                            color: '#475569',
                          }}
                        >
                          ✉️ {selectedContact.email ?? 'contact@samsung.com'}
                        </div>
                      </div>
                      <div
                        style={{
                          background: '#f8fafc',
                          borderLeft: '2px solid #06b6d4',
                          borderRadius: '0 6px 6px 0',
                          padding: '12px 14px',
                          fontFamily: 'Pretendard,sans-serif',
                          fontSize: 13,
                          color: '#334155',
                          lineHeight: 1.6,
                        }}
                      >
                        {selectedContact.memo ??
                          '꼼꼼한 성격, 가격에 민감하며 데이터 기반 의사결정 선호. ROI 수치 제시 필수.'}
                      </div>
                    </>
                  ) : (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontWeight: 600,
                            fontSize: 16,
                            color: '#1e293b',
                          }}
                        >
                          회사 정보
                        </span>
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontSize: 12,
                            fontWeight: 300,
                            color: '#94a3b8',
                            cursor: 'pointer',
                          }}
                        >
                          더보기
                        </span>
                      </div>
                      {MOCK_SIGNALS.map((sig, i) => (
                        <SignalItem key={i} signal={sig} accent={SIGNAL_ACCENTS[i] ?? '#cbd5e1'} />
                      ))}
                    </>
                  )}
                </div>

                {/* 오른쪽: 최근 딜 */}
                <div
                  style={{
                    flex: '1 1 0',
                    minWidth: 0,
                    padding: '22px 40px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 12,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span
                      style={{
                        fontFamily: 'Pretendard,sans-serif',
                        fontWeight: 600,
                        fontSize: 16,
                        color: '#1e293b',
                      }}
                    >
                      {selectedContact ? '담당자 최근 딜' : '최근 딜'}
                    </span>
                    <button
                      onClick={handleMoreDeals}
                      style={{
                        fontFamily: 'Pretendard,sans-serif',
                        fontSize: 12,
                        fontWeight: 300,
                        color: '#94a3b8',
                        cursor: 'pointer',
                        background: 'none',
                        border: 'none',
                        padding: 0,
                      }}
                    >
                      더보기
                    </button>
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      gap: 20,
                      alignItems: 'center',
                      justifyContent: 'center',
                      flex: 1,
                    }}
                  >
                    {MOCK_DEALS.slice(0, 2).map((d) => (
                      <DealCard
                        key={d.dealId}
                        deal={d}
                        selected={selectedDeal?.dealId === d.dealId}
                        onClick={() => handleDealClick(d)}
                      />
                    ))}
                  </div>
                </div>
              </div>

              {/* 딜 상세 패널 (딜 카드 클릭 시) */}
              {selectedDeal && <DealDetailPanel />}

              {/* AI 추천 전략 (딜 미선택 시) */}
              {!selectedDeal && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingTop: 4 }}>
                  <h2
                    style={{
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 600,
                      fontSize: 18,
                      color: '#0d1c2e',
                      margin: 0,
                    }}
                  >
                    AI 추천 전략
                  </h2>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    {MOCK_STRATEGIES.map((card, idx) => (
                      <StrategyCardComponent key={card.id} card={card} index={idx} />
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </>
  );
}
