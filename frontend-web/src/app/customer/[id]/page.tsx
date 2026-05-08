'use client';

import { useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import CustomerSidebar from '@/components/customer/CustomerSidebar';
import StrategyCardComponent from '@/components/customer/StrategyCard';
import SignalItem from '@/components/customer/SignalItem';
import DealCard from '@/components/customer/DealCard';
import DealDetailPanel from '@/components/customer/DealDetailPanel';
import { useAccountList, useAccountDetail } from '@/hooks/useCustomer';
import type { ContactInfo, Deal, StrategyCard } from '@/types/customer';

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

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const scrollRef = useRef<HTMLDivElement>(null);

  // ─── 훅 ──────────────────────────────────────────────────────────────────
  const { accounts, loading: accountsLoading } = useAccountList();
  const { signals, contacts, deals } = useAccountDetail(id ?? null);

  // ─── 로컬 UI 상태 ─────────────────────────────────────────────────────────
  const [selectedContact, setSelectedContact] = useState<ContactInfo | null>(null);
  const [showDealList, setShowDealList] = useState(false);
  const [selectedDeal, setSelectedDeal] = useState<Deal | null>(null);

  const account = accounts.find((a) => String(a.accountId) === String(id)) ?? accounts[0];

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
          loading={accountsLoading}
          contacts={contacts}
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
          {/* ── 브레드크럼 ── */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
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
              {account?.name ?? '고객사'}
            </h1>
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
                  {selectedContact.name}
                </h1>
              </>
            )}
            {showDealList && (
              <>
                <span style={{ color: '#cbd5e1', fontSize: 20 }}>→</span>
                <h1
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontWeight: 900,
                    fontSize: 26,
                    margin: 0,
                    lineHeight: 1,
                    color: '#0f172a',
                  }}
                >
                  딜 목록
                </h1>
              </>
            )}
          </div>

          {/* ── 딜 목록 뷰 ── */}
          {showDealList ? (
            <div
              ref={scrollRef}
              style={{
                display: 'flex',
                gap: 20,
                overflowX: 'auto',
                paddingBottom: 8,
                flexWrap: 'wrap',
              }}
            >
              {deals.map((d) => (
                <DealCard
                  key={d.dealId}
                  deal={d}
                  selected={selectedDeal?.dealId === d.dealId}
                  onClick={() => handleDealClick(d)}
                />
              ))}
            </div>
          ) : (
            <>
              {/* ── 정보 + 딜 카드 행 ── */}
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
                          style={{ fontFamily: 'Inter,sans-serif', fontSize: 14, color: '#475569' }}
                        >
                          📞 {selectedContact.phone ?? '-'}
                        </div>
                        <div
                          style={{ fontFamily: 'Inter,sans-serif', fontSize: 14, color: '#475569' }}
                        >
                          ✉️ {selectedContact.email ?? '-'}
                        </div>
                      </div>
                      {selectedContact.memo && (
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
                          {selectedContact.memo}
                        </div>
                      )}
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
                      {signals.length > 0 ? (
                        signals
                          .slice(0, 3)
                          .map((sig, i) => (
                            <SignalItem
                              key={i}
                              signal={sig}
                              accent={SIGNAL_ACCENTS[i] ?? '#cbd5e1'}
                            />
                          ))
                      ) : (
                        <p
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontSize: 13,
                            color: '#94a3b8',
                          }}
                        >
                          시그널 데이터가 없습니다.
                        </p>
                      )}
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
                    {deals.length > 0 ? (
                      deals
                        .slice(0, 2)
                        .map((d) => (
                          <DealCard
                            key={d.dealId}
                            deal={d}
                            selected={selectedDeal?.dealId === d.dealId}
                            onClick={() => handleDealClick(d)}
                          />
                        ))
                    ) : (
                      <p
                        style={{
                          fontFamily: 'Pretendard,sans-serif',
                          fontSize: 13,
                          color: '#94a3b8',
                        }}
                      >
                        진행 중인 딜이 없습니다.
                      </p>
                    )}
                  </div>
                </div>
              </div>

              {selectedDeal && <DealDetailPanel />}

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
