'use client';

import { useRef, useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import StrategyCardComponent from '@/components/customer/StrategyCard';
import SignalItem from '@/components/customer/SignalItem';
import AllSignalsModal from '@/components/customer/AllSignalsModal';
import DealCard from '@/components/customer/DealCard';
import DealDetailPanel from '@/components/customer/DealDetailPanel';
import WeatherPanel from '@/components/customer/WeatherPanel';
import AddEventModal from '@/components/calendar/AddEventModal';
import { useAccountList, useAccountDetail } from '@/hooks/useCustomer';
import { useCustomer } from '../CustomerContext';
import { useDeleteContact, useUpdateContact } from '@/hooks/useContact';
import { useCreateDeal } from '@/hooks/useDeal';
import { useConfirm } from '@/components/common/ConfirmDialog';
import { useNextActions, fetchNextActionDetail } from '@/hooks/useNextActions';
import { fetchWithAuth } from '@/hooks/useAuth';
import useBreakpoint from '@/hooks/useBreakpoint';
import type { Deal, StrategyCard } from '@/types/customer';

const SA = ['#06b6d4', '#cbd5e1', '#fb923c'];
const F = 'Pretendard,sans-serif';

// Next Action category → AddEventModal 카테고리 매핑.
// NEWS/DART/CRM 같은 시그널 출처는 '미팅'(가장 일반적) 으로, ActivityType 코드면 직접 매핑.
function mapNextActionCategory(c: string): string {
  switch (c) {
    case 'CALL': return '전화';
    case 'EMAIL': return '이메일';
    case 'TASK': return '업무';
    case 'MEETING':
    case 'NEWS':
    case 'DART':
    case 'CRM':
    default: return '미팅';
  }
}

function Av({ name, color, size = 30 }: { name: string; color: string; size?: number }) {
  return <div style={{ width: size, height: size, borderRadius: '50%', background: color, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size * 0.42, fontWeight: 700, color: '#fff', fontFamily: F }}>{name.charAt(0)}</div>;
}

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const bp = useBreakpoint();
  const isCompact = bp !== 'desktop';
  const scrollRef = useRef<HTMLDivElement>(null);
  const { accounts } = useAccountList();
  const { signals, deals, latestMood, latestMoodReason, refetch: refDetail } = useAccountDetail(id ?? null);
  const { actions: nextActions, loading: aiL } = useNextActions(id ?? null);
  const { remove: delContact } = useDeleteContact();
  const { update: updContact } = useUpdateContact();
  const { create: addDeal, loading: addingD } = useCreateDeal();

  // 사이드바와 공유하는 선택 상태 (layout 의 CustomerProvider)
  const { selContact, setSelContact } = useCustomer();
  const confirm = useConfirm();

  // 추천 전략은 최근 4개만 노출 (suggestionId BIGSERIAL desc = 최신순).
  const strats: StrategyCard[] = nextActions
    .slice()
    .sort((a, b) => b.suggestionId - a.suggestionId)
    .slice(0, 4)
    .map(a => ({ id: a.suggestionId, title: a.title, category: a.category, successRate: a.successRate }));

  // ── AI 추천 → 일정 추가 모달 ────────────────────────────────
  const [addEventOpen, setAddEventOpen] = useState(false);
  const [addEventDefaults, setAddEventDefaults] = useState<{
    title?: string;
    category?: string;
  }>({});

  const [selDeal, setSelDeal] = useState<Deal | null>(null);
  const [allDeals, setAllDeals] = useState(false);
  const [expStrat, setExpStrat] = useState<StrategyCard | null>(null);
  // 담당자 편집
  const [editContact, setEditContact] = useState(false);
  const [ecName, setEcName] = useState('');
  const [ecTitle, setEcTitle] = useState('');
  const [ecPhone, setEcPhone] = useState('');
  const [ecEmail, setEcEmail] = useState('');
  // 딜 추가
  const [showAddDeal, setShowAddDeal] = useState(false);
  const [showAllSignals, setShowAllSignals] = useState(false);
  const [ndTitle, setNdTitle] = useState('');
  const [ndAmount, setNdAmount] = useState('');
  const [ndDate, setNdDate] = useState('');

  // 담당자별 딜 필터
  const [cDealIds, setCDealIds] = useState<Set<number> | null>(null);
  const loadCD = useCallback(async (cid: number) => {
    const ids = new Set<number>();
    await Promise.allSettled(deals.map(async d => {
      const r = await fetchWithAuth(`/deals/${d.dealId}`);
      if (!r.ok) return;
      const j = await r.json();
      // 백엔드 DealDetailResponse: { contacts: [{ contactId, name, title, email, phone, personality }] }
      const contacts = (j.data?.contacts ?? []) as { contactId: number }[];
      if (contacts.some((c) => c.contactId === cid)) ids.add(d.dealId);
    }));
    setCDealIds(ids);
  }, [deals]);
  useEffect(() => { if (selContact?.contactId && deals.length > 0) loadCD(selContact.contactId); else setCDealIds(null); }, [selContact, deals, loadCD]);

  const visDeal = cDealIds ? deals.filter(d => cDealIds.has(d.dealId)) : deals;
  const acc = accounts.find(a => String(a.accountId) === String(id)) ?? accounts[0];

  // 회사 정보 박스 대표 3건: 최신 NEWS 1 + 최신 DART 1 + 사용 안 한 것 중 최신순으로 3건 채움.
  // 시간순으로만 자르면 한 종류에 몰려서 다양성이 사라지므로 종류별 최신을 우선 보장한다.
  // DART 가 없으면 NEWS 로, NEWS 가 없으면 DART 로 채워 항상 가능한 만큼 3건까지 노출.
  const repSignals = (() => {
    const result: typeof signals = [];
    const used = new Set<number>();
    const pick = (predicate: (s: typeof signals[number]) => boolean) => {
      const idx = signals.findIndex((s, i) => !used.has(i) && predicate(s));
      if (idx >= 0) { result.push(signals[idx]); used.add(idx); }
    };
    pick((s) => s.type === 'NEWS');
    pick((s) => s.type === 'DART');
    while (result.length < 3) {
      const before = result.length;
      pick(() => true);
      if (result.length === before) break;
    }
    return result;
  })();

  // 사이드바에서 setSelContact 호출 시 — 관련 page state reset
  useEffect(() => {
    setSelDeal(null);
    setAllDeals(false);
    setEditContact(false);
    if (selContact) {
      setEcName(selContact.name);
      setEcTitle(selContact.role);
      setEcPhone(selContact.phone ?? '');
      setEcEmail(selContact.email ?? '');
    }
  }, [selContact]);

  const crumbs: { label: string; onClick?: () => void }[] = [{ label: acc?.name ?? '고객사', onClick: () => { setSelContact(null); setSelDeal(null); setAllDeals(false); } }];
  if (selContact) crumbs.push({ label: selContact.name, onClick: () => { setSelDeal(null); setAllDeals(false); } });
  if (selDeal) crumbs.push({ label: selDeal.title });

  return (
    <>
      <main ref={scrollRef} style={{ flex: 1, height: '100%', overflowX: 'hidden', overflowY: 'auto', padding: isCompact ? '16px 16px 32px' : '24px 40px 48px', display: 'flex', flexDirection: 'column', gap: isCompact ? 12 : 16 }}>
          {/* 모바일 뒤로가기 */}
          {isCompact && (
            <button
              onClick={() => router.push('/customer')}
              style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', padding: 0, fontFamily: F, fontSize: 14, color: '#06b6d4', fontWeight: 500 }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
              목록
            </button>
          )}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            {crumbs.map((c, i) => {
              const last = i === crumbs.length - 1;
              return <span key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {i > 0 && <span style={{ color: '#cbd5e1', fontSize: 14 }}>›</span>}
                <span onClick={!last ? c.onClick : undefined} style={{ fontFamily: F, fontWeight: 700, fontSize: 22, color: last ? '#0f172a' : '#94a3b8', cursor: !last ? 'pointer' : 'default' }}>{c.label}</span>
              </span>;
            })}
            {acc && <span style={{ fontFamily: F, fontSize: 12, color: '#94a3b8' }}>{acc.industry}</span>}
          </div>

          {/* 분위기(날씨) — 미팅 분석 결과 기반. useAccountDetail 에서 mood history 의 최신 항목 사용. */}
          {acc && !selDeal && !selContact && (
            <WeatherPanel
              mood={latestMood ?? acc.temperature}
              reason={latestMoodReason ?? acc.moodReason ?? null}
            />
          )}

          {/* 메인 카드 — height 자동. 카드 수에 따라 박스 세로가 자라/줄어 빈 공간을 없앤다.
              일반 모드(row): 양 패널 중 큰 쪽에 맞춰 stretch. 전체 보기(column): 콘텐츠 그대로. */}
          <div style={{ background: '#fff', border: '1px solid #e2eaf0', borderRadius: 10, display: 'flex', flexDirection: (allDeals || isCompact) ? 'column' : 'row', flexShrink: 0, overflow: 'hidden' }}>
            {allDeals ? (
              <div style={{ padding: '20px 28px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontFamily: F, fontWeight: 600, fontSize: 15, color: '#1e293b' }}>{selContact ? `${selContact.name} 관련 딜` : '전체 딜'} ({visDeal.length})</span>
                  <button onClick={() => setAllDeals(false)} style={{ fontFamily: F, fontSize: 12, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>접기</button>
                </div>
                {visDeal.length > 0 ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'flex-start', gap: 16 }}>
                    {visDeal.map(d => (
                      <DealCard key={d.dealId} deal={d} selected={selDeal?.dealId === d.dealId} onClick={() => setSelDeal(selDeal?.dealId === d.dealId ? null : d)} isCompact={isCompact} />
                    ))}
                  </div>
                ) : (
                  <div style={{ minHeight: 120, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <p style={{ fontFamily: F, fontSize: 13, color: '#94a3b8', margin: 0, textAlign: 'center' }}>딜이 없습니다.</p>
                  </div>
                )}
              </div>
            ) : (
              <>
                {/* 왼쪽 */}
                <div style={{ flex: isCompact ? '0 0 auto' : '1 1 0', minWidth: 0, minHeight: isCompact ? undefined : 0, borderRight: isCompact ? 'none' : '1px solid #e2eaf0', borderBottom: isCompact ? '1px solid #e2eaf0' : 'none', padding: isCompact ? '16px' : '20px 28px', display: 'flex', flexDirection: 'column', gap: 10, overflowY: isCompact ? undefined : 'auto' }}>
                  {selContact ? (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontFamily: F, fontWeight: 600, fontSize: 14, color: '#1e293b' }}>담당자</span>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button onClick={() => setEditContact(v => !v)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>{editContact ? '취소' : '편집'}</button>
                          <button onClick={async () => {
                            if (!selContact.contactId || !await confirm(`${selContact.name} 삭제?`)) return;
                            if (await delContact(selContact.contactId)) { setSelContact(null); refDetail(); }
                          }} style={{ fontFamily: F, fontSize: 11, color: '#94a3b8', cursor: 'pointer', background: 'none', border: 'none' }}>삭제</button>
                        </div>
                      </div>
                      {editContact ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                            <input value={ecName} onChange={e => setEcName(e.target.value)} placeholder="이름" style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                            <input value={ecTitle} onChange={e => setEcTitle(e.target.value)} placeholder="직책" style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                            <input value={ecPhone} onChange={e => setEcPhone(e.target.value)} placeholder="전화번호" style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                            <input value={ecEmail} onChange={e => setEcEmail(e.target.value)} placeholder="이메일" style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                          </div>
                          <button onClick={async () => {
                            if (!selContact.contactId) return;
                            await updContact(selContact.contactId, { name: ecName.trim() || undefined, title: ecTitle.trim() || undefined, phone: ecPhone.trim() || undefined, email: ecEmail.trim() || undefined });
                            setEditContact(false); refDetail();
                          }} style={{ alignSelf: 'flex-end', padding: '5px 14px', borderRadius: 6, border: 'none', backgroundColor: '#06b6d4', color: '#fff', fontSize: 12, fontWeight: 600, cursor: 'pointer', fontFamily: F }}>저장</button>
                        </div>
                      ) : (
                        <>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <Av name={selContact.name} color={selContact.color} size={34} />
                            <div>
                              <span style={{ fontFamily: F, fontWeight: 700, fontSize: 16, color: '#1e293b' }}>{selContact.name}</span>
                              {selContact.role && <span style={{ fontFamily: F, fontSize: 12, color: '#6d797d', marginLeft: 6 }}>{selContact.role}</span>}
                            </div>
                          </div>
                          <div style={{ fontSize: 13, color: '#475569', display: 'flex', flexDirection: 'column', gap: 2 }}>
                            <span>📞 {selContact.phone ?? '-'}</span>
                            <span>✉️ {selContact.email ?? '-'}</span>
                          </div>
                          {selContact.memo && <div style={{ background: '#f8fafc', borderLeft: '2px solid #06b6d4', borderRadius: '0 4px 4px 0', padding: '8px 10px', fontSize: 12, color: '#334155', lineHeight: 1.6 }}>{selContact.memo}</div>}
                        </>
                      )}
                    </>
                  ) : (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontFamily: F, fontWeight: 600, fontSize: 14, color: '#1e293b' }}>회사 정보</span>
                        {signals.length > 0 && (
                          <button
                            onClick={() => setShowAllSignals(true)}
                            style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}
                          >
                            더보기
                          </button>
                        )}
                      </div>
                      {repSignals.length > 0 ? (
                        repSignals.map((s, i) => <SignalItem key={i} signal={s} accent={SA[i] ?? '#cbd5e1'} />)
                      ) : (
                        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <p style={{ fontFamily: F, fontSize: 13, color: '#94a3b8', margin: 0, textAlign: 'center' }}>시그널 없음</p>
                        </div>
                      )}
                    </>
                  )}
                </div>

                {/* 오른쪽: 최근 딜 */}
                <div style={{ flex: isCompact ? '0 0 auto' : '1 1 0', minWidth: 0, minHeight: isCompact ? undefined : 0, padding: isCompact ? '16px' : '20px 28px', display: 'flex', flexDirection: 'column', gap: 10, overflowY: isCompact ? undefined : 'auto' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontFamily: F, fontWeight: 600, fontSize: 14, color: '#1e293b' }}>{selContact ? `${selContact.name} 딜` : '최근 딜'}</span>
                    <div style={{ display: 'flex', gap: 8 }}>
                      {visDeal.length > 0 && <button onClick={() => setAllDeals(true)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>전체보기</button>}
                      <button onClick={() => setShowAddDeal(v => !v)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>{showAddDeal ? '취소' : '+ 딜 추가'}</button>
                    </div>
                  </div>
                  {showAddDeal ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <input placeholder="딜 제목 *" value={ndTitle} onChange={e => setNdTitle(e.target.value)} style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                        <input placeholder="예상 금액" type="number" value={ndAmount} onChange={e => setNdAmount(e.target.value)} style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                        <input type="date" value={ndDate} onChange={e => setNdDate(e.target.value)} style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                      </div>
                      <button onClick={async () => {
                        if (!ndTitle.trim() || !acc || addingD) return;
                        await addDeal({ accountId: acc.accountId, title: ndTitle.trim(), amount: ndAmount ? Number(ndAmount) : undefined, expectedClose: ndDate || undefined });
                        setShowAddDeal(false); setNdTitle(''); setNdAmount(''); setNdDate(''); refDetail();
                      }} disabled={!ndTitle.trim() || addingD}
                        style={{ alignSelf: 'flex-end', padding: '5px 14px', borderRadius: 6, border: 'none', backgroundColor: ndTitle.trim() ? '#06b6d4' : '#cbd5e1', color: '#fff', fontSize: 12, fontWeight: 600, cursor: ndTitle.trim() ? 'pointer' : 'default', fontFamily: F }}>
                        {addingD ? '등록 중...' : '등록'}
                      </button>
                    </div>
                  ) : visDeal.length > 0 ? (
                    // 전체 보기와 동일 패턴: flex + space-evenly + 세로 가운데
                    <div style={{ flex: 1, minHeight: 0, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-evenly', alignItems: 'center', alignContent: 'center', gap: 16 }}>
                      {visDeal.slice(0, 2).map(d => (
                        <DealCard key={d.dealId} deal={d} selected={selDeal?.dealId === d.dealId} onClick={() => setSelDeal(selDeal?.dealId === d.dealId ? null : d)} isCompact={isCompact} />
                      ))}
                    </div>
                  ) : (
                    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <p style={{ fontFamily: F, fontSize: 13, color: '#94a3b8', margin: 0, textAlign: 'center' }}>
                        {selContact ? '연결된 딜 없음' : '진행 중인 딜 없음'}
                      </p>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>

          {/* 딜 상세 */}
          {selDeal && <DealDetailPanel deal={selDeal} onDealChanged={() => refDetail()} />}

          {/* AI 추천 전략 — 데이터 있을 때만 */}
          {!selDeal && strats.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h2 style={{ fontFamily: F, fontWeight: 600, fontSize: 15, color: '#0d1c2e', margin: 0 }}>AI 추천 전략</h2>
              {strats.map((card, idx) => (
                <StrategyCardComponent key={card.id}
                  card={expStrat?.id === card.id ? expStrat : card} index={idx}
                  onExpand={async () => {
                    if (expStrat?.id === card.id) { setExpStrat(null); return; }
                    const d = await fetchNextActionDetail(id!, card.id);
                    if (d) setExpStrat({ ...card, isExpanded: true, basisData: d.basisData, aiComment: d.aiComment, warning: d.warning });
                  }}
                  onAddToCalendar={(c) => {
                    setAddEventDefaults({ title: c.title, category: mapNextActionCategory(c.category) });
                    setAddEventOpen(true);
                  }}
                />
              ))}
            </div>
          )}
          {!selDeal && strats.length === 0 && !aiL && null}
      </main>

      {/* AI 추천 전략 → 일정 추가 모달 */}
      <AddEventModal
        open={addEventOpen}
        onClose={() => { setAddEventOpen(false); setAddEventDefaults({}); }}
        defaultTitle={addEventDefaults.title}
        defaultCategory={addEventDefaults.category}
        defaultAccountId={id ? Number(id) : undefined}
        defaultAccountName={accounts.find((a) => String(a.accountId) === id)?.name}
        onSaved={() => { setAddEventOpen(false); setAddEventDefaults({}); }}
      />

      {/* 회사 정보 시그널 전체 보기 모달 */}
      <AllSignalsModal
        open={showAllSignals}
        onClose={() => setShowAllSignals(false)}
        signals={signals}
        accountName={acc?.name}
      />
    </>
  );
}
