'use client';

import { useRef, useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import CustomerSidebar from '@/components/customer/CustomerSidebar';
import StrategyCardComponent from '@/components/customer/StrategyCard';
import SignalItem from '@/components/customer/SignalItem';
import DealCard from '@/components/customer/DealCard';
import DealDetailPanel from '@/components/customer/DealDetailPanel';
import { useAccountList, useAccountDetail, useRegisterAccount, useDeleteAccount } from '@/hooks/useCustomer';
import { useDeleteContact, useUpdateContact } from '@/hooks/useContact';
import { useCreateDeal } from '@/hooks/useDeal';
import { useNextActions, fetchNextActionDetail } from '@/hooks/useNextActions';
import { fetchWithAuth } from '@/hooks/useAuth';
import type { ContactInfo, Deal, StrategyCard } from '@/types/customer';

const SA = ['#06b6d4', '#cbd5e1', '#fb923c'];
const F = 'Pretendard,sans-serif';
const ML: Record<string, string> = { RAINBOW: '🌈', SUNNY: '☀️', CLOUDY: '☁️', RAINY: '🌧️', THUNDER: '⛈️' };

function Av({ name, color, size = 30 }: { name: string; color: string; size?: number }) {
  return <div style={{ width: size, height: size, borderRadius: '50%', background: color, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size * 0.42, fontWeight: 700, color: '#fff', fontFamily: F }}>{name.charAt(0)}</div>;
}

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const scrollRef = useRef<HTMLDivElement>(null);
  const { accounts, loading: aL, error: aE, refetch: refA } = useAccountList();
  const { signals, contacts, deals, refetch: refDetail } = useAccountDetail(id ?? null);
  const { actions: nextActions, loading: aiL } = useNextActions(id ?? null);
  const { remove: delContact } = useDeleteContact();
  const { update: updContact } = useUpdateContact();
  const { register: regAccount, loading: regL } = useRegisterAccount();
  const { remove: delAccount } = useDeleteAccount();
  const { create: addDeal, loading: addingD } = useCreateDeal();

  const strats: StrategyCard[] = nextActions.map(a => ({ id: a.suggestionId, title: a.title, category: a.category, successRate: a.successRate }));

  const [selContact, setSelContact] = useState<ContactInfo | null>(null);
  const [selDeal, setSelDeal] = useState<Deal | null>(null);
  const [allDeals, setAllDeals] = useState(false);
  const [expStrat, setExpStrat] = useState<StrategyCard | null>(null);
  const [showAddAcc, setShowAddAcc] = useState(false);
  const [nAName, setNAName] = useState('');
  const [nAInd, setNAInd] = useState('');
  // 담당자 편집
  const [editContact, setEditContact] = useState(false);
  const [ecName, setEcName] = useState('');
  const [ecTitle, setEcTitle] = useState('');
  const [ecPhone, setEcPhone] = useState('');
  const [ecEmail, setEcEmail] = useState('');
  // 딜 추가
  const [showAddDeal, setShowAddDeal] = useState(false);
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
      if ((j.data?.contacts ?? []).some((c: any) => c.contactId === cid)) ids.add(d.dealId);
    }));
    setCDealIds(ids);
  }, [deals]);
  useEffect(() => { if (selContact?.contactId && deals.length > 0) loadCD(selContact.contactId); else setCDealIds(null); }, [selContact, deals, loadCD]);

  const visDeal = cDealIds ? deals.filter(d => cDealIds.has(d.dealId)) : deals;
  const acc = accounts.find(a => String(a.accountId) === String(id)) ?? accounts[0];
  const moodIcon = ML[acc?.temperature] ?? '☁️';

  const onContactSel = (c: ContactInfo | null) => { setSelContact(c); setSelDeal(null); setAllDeals(false); setEditContact(false); if (c) { setEcName(c.name); setEcTitle(c.role); setEcPhone(c.phone ?? ''); setEcEmail(c.email ?? ''); } };

  const crumbs: { label: string; onClick?: () => void }[] = [{ label: acc?.name ?? '고객사', onClick: () => { setSelContact(null); setSelDeal(null); setAllDeals(false); } }];
  if (selContact) crumbs.push({ label: selContact.name, onClick: () => { setSelDeal(null); setAllDeals(false); } });
  if (selDeal) crumbs.push({ label: selDeal.title });

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, backgroundColor: '#f8fafc', zIndex: -1 }} />
      <div style={{ position: 'fixed', top: 64, left: 0, right: 0, bottom: 0, display: 'flex', overflow: 'hidden' }}>
        <CustomerSidebar accounts={accounts} selectedId={acc?.accountId ?? null} loading={aL}
          error={aE} onRetry={refA} contacts={contacts} onContactSelect={onContactSel} onAddAccount={() => setShowAddAcc(true)}
          onDeleteAccount={async (accountId, name) => {
            const msg = `"${name}" 고객사를 삭제하시겠습니까?\n\n연결된 모든 담당자, 딜, 활동 데이터가 함께 삭제됩니다.\n이 작업은 되돌릴 수 없습니다.`;
            if (!window.confirm(msg)) return;
            if (!window.confirm(`정말로 "${name}"을(를) 삭제합니까?`)) return;
            if (await delAccount(accountId)) { refA(); router.push('/customer/1'); }
          }} />

        <main ref={scrollRef} style={{ flex: 1, overflowY: 'auto', padding: '24px 40px 48px', display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 브레드크럼 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            {crumbs.map((c, i) => {
              const last = i === crumbs.length - 1;
              return <span key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {i > 0 && <span style={{ color: '#cbd5e1', fontSize: 14 }}>›</span>}
                <span onClick={!last ? c.onClick : undefined} style={{ fontFamily: F, fontWeight: 700, fontSize: 22, color: last ? '#0f172a' : '#94a3b8', cursor: !last ? 'pointer' : 'default' }}>{c.label}</span>
              </span>;
            })}
            {acc && <span style={{ fontSize: 16 }}>{moodIcon}</span>}
            {acc && <span style={{ fontFamily: F, fontSize: 12, color: '#94a3b8' }}>{acc.industry}</span>}
          </div>

          {/* 메인 카드 */}
          <div style={{ background: '#fff', border: '1px solid #e2eaf0', borderRadius: 10, display: 'flex', flexDirection: allDeals ? 'column' : 'row', flexShrink: 0, minHeight: 180 }}>
            {allDeals ? (
              <div style={{ padding: '20px 28px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontFamily: F, fontWeight: 600, fontSize: 15, color: '#1e293b' }}>{selContact ? `${selContact.name} 관련 딜` : '전체 딜'} ({visDeal.length})</span>
                  <button onClick={() => setAllDeals(false)} style={{ fontFamily: F, fontSize: 12, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>접기</button>
                </div>
                <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
                  {visDeal.map(d => <DealCard key={d.dealId} deal={d} selected={selDeal?.dealId === d.dealId} onClick={() => { setSelDeal(selDeal?.dealId === d.dealId ? null : d); setAllDeals(false); }} />)}
                  {visDeal.length === 0 && <p style={{ fontFamily: F, fontSize: 13, color: '#94a3b8' }}>딜이 없습니다.</p>}
                </div>
              </div>
            ) : (
              <>
                {/* 왼쪽 */}
                <div style={{ flex: '1 1 0', minWidth: 0, borderRight: '1px solid #e2eaf0', padding: '20px 28px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {selContact ? (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontFamily: F, fontWeight: 600, fontSize: 14, color: '#1e293b' }}>담당자</span>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button onClick={() => setEditContact(v => !v)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>{editContact ? '취소' : '편집'}</button>
                          <button onClick={async () => {
                            if (!selContact.contactId || !window.confirm(`${selContact.name} 삭제?`)) return;
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
                        <button style={{ fontFamily: F, fontSize: 11, color: '#94a3b8', cursor: 'pointer', background: 'none', border: 'none' }}>더보기</button>
                      </div>
                      {signals.length > 0 ? signals.slice(0, 3).map((s, i) => <SignalItem key={i} signal={s} accent={SA[i] ?? '#cbd5e1'} />) : <p style={{ fontFamily: F, fontSize: 13, color: '#94a3b8' }}>시그널 없음</p>}
                    </>
                  )}
                </div>

                {/* 오른쪽: 최근 딜 */}
                <div style={{ flex: '1 1 0', minWidth: 0, padding: '20px 28px', display: 'flex', flexDirection: 'column', gap: 10 }}>
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
                  ) : (
                    <div style={{ display: 'flex', gap: 14, alignItems: 'center', flexWrap: 'wrap', flex: 1 }}>
                      {visDeal.length > 0 ? visDeal.slice(0, 2).map(d => (
                        <DealCard key={d.dealId} deal={d} selected={selDeal?.dealId === d.dealId} onClick={() => setSelDeal(selDeal?.dealId === d.dealId ? null : d)} />
                      )) : <p style={{ fontFamily: F, fontSize: 13, color: '#94a3b8' }}>{selContact ? '연결된 딜 없음' : '진행 중인 딜 없음'}</p>}
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
                  }} />
              ))}
            </div>
          )}
          {!selDeal && strats.length === 0 && !aiL && null}
        </main>
      </div>

      {/* 고객사 추가 모달 */}
      {showAddAcc && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div onClick={() => setShowAddAcc(false)} style={{ position: 'absolute', inset: 0, backgroundColor: 'rgba(0,0,0,0.35)' }} />
          <div style={{ position: 'relative', backgroundColor: '#fff', borderRadius: 12, width: 380, padding: 22, boxShadow: '0 12px 40px rgba(0,0,0,0.12)', display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontFamily: F, fontSize: 16, fontWeight: 700, color: '#16180F' }}>고객사 추가</span>
              <button onClick={() => setShowAddAcc(false)} style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: 18, color: '#9CA3AF' }}>×</button>
            </div>
            <input value={nAName} onChange={e => setNAName(e.target.value)} placeholder="고객사명 *"
              style={{ padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5', fontSize: 13, outline: 'none', fontFamily: F }} />
            <input value={nAInd} onChange={e => setNAInd(e.target.value)} placeholder="업종"
              style={{ padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5', fontSize: 13, outline: 'none', fontFamily: F }} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowAddAcc(false)} style={{ padding: '7px 14px', borderRadius: 6, border: '1px solid #E5E6DE', backgroundColor: '#fff', fontSize: 13, color: '#737880', cursor: 'pointer', fontFamily: F }}>취소</button>
              <button onClick={async () => {
                if (!nAName.trim() || regL) return;
                await regAccount({ name: nAName.trim(), industry: nAInd.trim() || undefined });
                setShowAddAcc(false); setNAName(''); setNAInd(''); refA();
              }} disabled={!nAName.trim() || regL}
                style={{ padding: '7px 16px', borderRadius: 6, border: 'none', backgroundColor: nAName.trim() ? '#06B6D4' : '#cbd5e1', fontSize: 13, fontWeight: 600, color: '#fff', cursor: nAName.trim() ? 'pointer' : 'default', fontFamily: F }}>
                {regL ? '등록 중...' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
