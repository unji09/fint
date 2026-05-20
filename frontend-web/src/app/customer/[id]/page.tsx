'use client';

import { useRef, useState, useEffect, useCallback } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import StrategyCardComponent from '@/components/customer/StrategyCard';
import SignalItem from '@/components/customer/SignalItem';
import AllSignalsModal from '@/components/customer/AllSignalsModal';
import EventDetailPanel from '@/components/calendar/EventDetailPanel';
import type { CalendarEvent, EventCategory } from '@/components/calendar/types';
import DealCard from '@/components/customer/DealCard';
import DealDetailPanel from '@/components/customer/DealDetailPanel';
import WeatherPanel from '@/components/customer/WeatherPanel';
import AddEventModal from '@/components/calendar/AddEventModal';
import { useAccountList, useAccountDetail, useAccountDeals, invalidateDetailCache, mapApiDeal } from '@/hooks/useCustomer';
import { useCustomer } from '../CustomerContext';
import { useDeleteContact, useUpdateContact } from '@/hooks/useContact';
import { useCreateDeal } from '@/hooks/useDeal';
import { useConfirm } from '@/components/common/ConfirmDialog';
import { useNextActions, fetchNextActionDetail } from '@/hooks/useNextActions';
import { fetchWithAuth } from '@/hooks/useAuth';
import useBreakpoint from '@/hooks/useBreakpoint';
import type { ApiDeal, Deal, StrategyCard } from '@/types/customer';

const SA = ['#06b6d4', '#cbd5e1', '#fb923c'];
const F = 'Pretendard,sans-serif';

// 전화번호 입력 자동 포맷팅. 숫자만 남기고 11자리(휴대폰) / 10자리(일반) 기준 하이픈 삽입.
function formatPhoneNumber(input: string): string {
  const digits = input.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  if (digits.length <= 10) return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

// 이메일 형식 검증. 빈 문자열은 valid 로 간주(선택 입력).
function isValidEmail(value: string): boolean {
  if (!value.trim()) return true;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

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
  const searchParams = useSearchParams();
  const dealParam = searchParams.get('deal');
  const bp = useBreakpoint();
  const isCompact = bp !== 'desktop';
  const scrollRef = useRef<HTMLDivElement>(null);
  const { accounts } = useAccountList();
  const { signals, contacts, deals, latestMood, latestMoodReason, refetch: refDetail, prependDeal: prependDetailDeal } = useAccountDetail(id ?? null);
  const { deals: pagedDeals, hasNext: hasMoreDeals, loading: dealsLoading, loadMore: loadMoreDeals, refetch: refDeals, prependDeal: prependPagedDeal } = useAccountDeals(id ?? null, 10);
  const allDealsScrollRef = useRef<HTMLDivElement>(null);
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
  // NextAction 의 CRM 근거 항목 클릭으로 띄우는 미팅 상세 — 캘린더의 EventDetailPanel 그대로 재사용.
  const [selectedMeetingEvent, setSelectedMeetingEvent] = useState<CalendarEvent | null>(null);
  // EventDetailPanel 에서 "수정" 클릭 시 AddEventModal 을 편집 모드로 띄우기 위한 state.
  const [editMeetingEvent, setEditMeetingEvent] = useState<CalendarEvent | null>(null);
  // 삭제 등 mutation 후 사용자 피드백 메시지 (2.5초 뒤 자동 사라짐)
  const [toast, setToast] = useState<string | null>(null);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2500);
    return () => clearTimeout(t);
  }, [toast]);
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

  // ?deal={dealId} URL 파라미터로 진입 시 해당 딜 자동 선택
  useEffect(() => {
    if (!dealParam || deals.length === 0) return;
    const found = deals.find(d => String(d.dealId) === dealParam);
    if (found) {
      setSelDeal(found);
      setAllDeals(true);
      router.replace(`/customer/${id}`);
    }
  }, [deals, dealParam, id, router]);

  // 전체 딜: 스크롤이 없으면 (화면이 넓어서 카드가 다 보이면) 자동으로 다음 페이지 로딩
  useEffect(() => {
    if (!allDeals || selContact || dealsLoading || !hasMoreDeals) return;
    const el = allDealsScrollRef.current;
    if (!el) return;
    if (el.scrollWidth <= el.clientWidth) loadMoreDeals();
  }, [allDeals, selContact, dealsLoading, hasMoreDeals, pagedDeals.length, loadMoreDeals]);

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
      setEcPhone(formatPhoneNumber(selContact.phone ?? ''));
      setEcEmail(selContact.email ?? '');
    }
  }, [selContact]);

  // contacts 가 갱신되면(편집/등록/삭제 후 refDetail) selContact 도 최신 객체로 동기화.
  // 새로고침 없이 편집 결과가 즉시 화면에 반영된다.
  useEffect(() => {
    if (!selContact) return;
    const updated = contacts.find((c) => c.contactId === selContact.contactId);
    if (updated && updated !== selContact) {
      setSelContact(updated);
    }
    // selContact 자체를 의존성에 두면 setSelContact 후 무한 루프 → contactId 만 추적
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contacts, selContact?.contactId]);

  // deals 가 갱신되면(추가/수정/삭제 후 refDetail) selDeal 도 동기화.
  // 삭제된 경우(deals 에서 못 찾음) selDeal=null + 토스트로 사용자 피드백.
  useEffect(() => {
    if (!selDeal) return;
    const updated = deals.find((d) => d.dealId === selDeal.dealId);
    if (!updated) {
      setToast(`‘${selDeal.title}’ 딜이 삭제되었습니다.`);
      setSelDeal(null);
      setAllDeals(false);
      return;
    }
    if (updated !== selDeal) setSelDeal(updated);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deals, selDeal?.dealId]);

  // contacts 에서 selContact 가 사라진 경우(삭제됨) 피드백.
  useEffect(() => {
    if (!selContact) return;
    if (contacts.length === 0) return; // 초기 로딩 중에는 무시
    const exists = contacts.some((c) => c.contactId === selContact.contactId);
    if (!exists) {
      setToast(`‘${selContact.name}’ 담당자가 삭제되었습니다.`);
      setSelContact(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contacts, selContact?.contactId]);

  const crumbs: { label: string; onClick?: () => void }[] = [{ label: acc?.name ?? '고객사', onClick: () => { setSelContact(null); setSelDeal(null); setAllDeals(false); } }];
  if (selContact) crumbs.push({ label: selContact.name, onClick: () => { setSelDeal(null); setAllDeals(false); } });
  if (selDeal) crumbs.push({ label: selDeal.title });

  return (
    <>
      <main ref={scrollRef} style={{ flex: 1, height: '100%', overflowX: 'hidden', overflowY: 'auto', padding: isCompact ? '16px 16px 32px' : '24px 40px 48px', display: 'flex', flexDirection: 'column', gap: isCompact ? 12 : 16 }}>
          {/* 모바일 뒤로가기 */}
          {isCompact && (
            <button
              type="button"
              onClick={() => { setSelContact(null); router.push('/customer'); }}
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
                {i > 0 && <span style={{ color: '#0f172a', fontSize: 28, fontWeight: 700, lineHeight: 1 }}>›</span>}
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
                  <span style={{ fontFamily: F, fontWeight: 600, fontSize: 15, color: '#1e293b' }}>{selContact ? `${selContact.name} 관련 딜` : '전체 딜'}</span>
                  <button type="button" onClick={() => setAllDeals(false)} style={{ fontFamily: F, fontSize: 12, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>접기</button>
                </div>
                {(selContact ? visDeal : pagedDeals).length > 0 ? (
                  <div
                    ref={selContact ? undefined : allDealsScrollRef}
                    style={{ display: 'flex', flexWrap: 'nowrap', overflowX: 'auto', gap: 16, paddingBottom: 4 }}
                    onScroll={selContact ? undefined : (e) => {
                      const el = e.currentTarget;
                      if (el.scrollLeft + el.clientWidth >= el.scrollWidth - 50) loadMoreDeals();
                    }}
                  >
                    {(selContact ? visDeal : pagedDeals).map(d => (
                      <DealCard key={d.dealId} deal={d} selected={selDeal?.dealId === d.dealId} onClick={() => setSelDeal(selDeal?.dealId === d.dealId ? null : d)} fixedWidth />
                    ))}
                    {!selContact && dealsLoading && (
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minWidth: 80, flexShrink: 0 }}>
                        <span style={{ fontFamily: F, fontSize: 12, color: '#94a3b8' }}>로딩 중...</span>
                      </div>
                    )}
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
                <div style={{ flex: isCompact ? '0 0 auto' : '1 1 0', minWidth: 0, minHeight: isCompact ? undefined : 0, borderRight: isCompact ? 'none' : '1px solid #e2eaf0', borderBottom: isCompact ? '1px solid #e2eaf0' : 'none', padding: isCompact ? '16px' : '20px 28px', display: 'flex', flexDirection: 'column', gap: 10, overflowY: 'auto' }}>
                  {selContact ? (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontFamily: F, fontWeight: 600, fontSize: 14, color: '#1e293b' }}>담당자</span>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button type="button" onClick={() => setEditContact(v => !v)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>{editContact ? '취소' : '편집'}</button>
                          <button type="button" onClick={async () => {
                            if (!selContact.contactId || !await confirm(`${selContact.name} 삭제?`)) return;
                            if (await delContact(selContact.contactId)) {
                              setSelContact(null);
                              invalidateDetailCache(id!);
                              refDetail();
                            }
                          }} style={{ fontFamily: F, fontSize: 11, color: '#94a3b8', cursor: 'pointer', background: 'none', border: 'none' }}>삭제</button>
                        </div>
                      </div>
                      {editContact ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                            <input value={ecName} onChange={e => setEcName(e.target.value)} placeholder="이름" style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                            <input value={ecTitle} onChange={e => setEcTitle(e.target.value)} placeholder="직책" style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, alignItems: 'start' }}>
                            <input
                              value={ecPhone}
                              onChange={e => setEcPhone(formatPhoneNumber(e.target.value))}
                              placeholder="전화번호 (숫자만)"
                              inputMode="numeric"
                              autoComplete="tel"
                              style={{ padding: '7px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }}
                            />
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                              <input
                                type="email"
                                value={ecEmail}
                                onChange={e => setEcEmail(e.target.value)}
                                placeholder="이메일"
                                autoComplete="email"
                                style={{ padding: '7px 10px', borderRadius: 6, border: `1px solid ${isValidEmail(ecEmail) ? '#e2eaf0' : '#ef4444'}`, fontSize: 13, outline: 'none', fontFamily: F }}
                              />
                              {!isValidEmail(ecEmail) && (
                                <span style={{ fontSize: 11, color: '#ef4444', fontFamily: F }}>
                                  이메일 형식이 올바르지 않습니다.
                                </span>
                              )}
                            </div>
                          </div>
                          <button
                            onClick={async () => {
                              if (!selContact.contactId) return;
                              await updContact(selContact.contactId, { name: ecName.trim() || undefined, title: ecTitle.trim() || undefined, phone: ecPhone.trim() || undefined, email: ecEmail.trim() || undefined });
                              setEditContact(false); refDetail();
                            }}
                            disabled={!isValidEmail(ecEmail)}
                            style={{ alignSelf: 'flex-end', padding: '5px 14px', borderRadius: 6, border: 'none', backgroundColor: isValidEmail(ecEmail) ? '#06b6d4' : '#cbd5e1', color: '#fff', fontSize: 12, fontWeight: 600, cursor: isValidEmail(ecEmail) ? 'pointer' : 'default', fontFamily: F }}
                          >저장</button>
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
                <div style={{ flex: isCompact ? '0 0 auto' : '1 1 0', minWidth: 0, minHeight: isCompact ? undefined : 0, padding: isCompact ? '16px' : '20px 28px', display: 'flex', flexDirection: 'column', gap: 10, overflowY: 'auto' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontFamily: F, fontWeight: 600, fontSize: 14, color: '#1e293b' }}>{selContact ? `${selContact.name} 딜` : '최근 딜'}</span>
                    <div style={{ display: 'flex', gap: 8 }}>
                      {visDeal.length > 0 && <button type="button" onClick={() => setAllDeals(true)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>전체보기</button>}
                      <button type="button" onClick={() => setShowAddDeal(v => !v)} style={{ fontFamily: F, fontSize: 11, color: '#06b6d4', cursor: 'pointer', background: 'none', border: 'none' }}>{showAddDeal ? '취소' : '+ 딜 추가'}</button>
                    </div>
                  </div>
                  {showAddDeal ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <input placeholder="딜 제목 *" value={ndTitle} onChange={e => setNdTitle(e.target.value)} style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                        <input placeholder="예상 금액" type="number" value={ndAmount} onChange={e => setNdAmount(e.target.value)} onKeyDown={e => { if (['e', 'E', '+', '-'].includes(e.key)) e.preventDefault(); }} style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                        <input type="date" value={ndDate} onChange={e => setNdDate(e.target.value)} style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #e2eaf0', fontSize: 13, outline: 'none', fontFamily: F }} />
                      </div>
                      <button type="button" onClick={async () => {
                        if (!ndTitle.trim() || !acc || addingD) return;
                        if (ndAmount && Number(ndAmount) < 0) { alert('금액은 0 이상이어야 합니다.'); return; }
                        const newDealData = await addDeal({ accountId: acc.accountId, title: ndTitle.trim(), amount: ndAmount ? Number(ndAmount) : undefined, expectedClose: ndDate || undefined });
                        if (newDealData) {
                          const mapped = mapApiDeal(newDealData as ApiDeal & Record<string, unknown>);
                          prependDetailDeal(mapped);
                          prependPagedDeal(mapped);
                        }
                        setShowAddDeal(false); setNdTitle(''); setNdAmount(''); setNdDate('');
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
          {selDeal && (
            <DealDetailPanel
              deal={selDeal}
              onDealChanged={() => { invalidateDetailCache(id!); refDetail(); refDeals(); }}
              onMeetingDetail={(a) => {
                // 미팅 활동 객체를 CalendarEvent 로 변환해 EventDetailPanel 모달 마운트.
                // 캘린더 페이지로 navigation 하지 않는다.
                const typeMap: Record<string, EventCategory> = { MEETING: '미팅', CALL: '전화', EMAIL: '이메일', MEMO: '업무' };
                setSelectedMeetingEvent({
                  eventId: `act-${a.activityId}`,
                  source: 'FINT',
                  title: a.title ?? '',
                  startAt: a.startAt ?? '',
                  endAt: a.startAt ?? '', // list 응답엔 endAt 없음 — EventDetailPanel 내부 fetch 가 실제 endAt 으로 덮어씀
                  category: typeMap[a.type] ?? '미팅',
                  accountName: acc?.name,
                  accountId: id ? Number(id) : undefined,
                  memo: a.memo ?? undefined,
                });
              }}
            />
          )}

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
                  onCrmClick={async (summary) => {
                    // BE 응답에 activity_id 가 없어서 summary 텍스트로 그 고객사 미팅을 매칭.
                    // 같은 제목 미팅이 여럿이면 첫 매칭만 잡으므로 100% 정확하진 않다 (사용자 합의).
                    if (!id) return;
                    try {
                      const res = await fetchWithAuth(`/activities?accountId=${id}&type=MEETING&size=50`);
                      if (!res.ok) { setToast('미팅 정보를 불러오지 못했습니다.'); return; }
                      const json = await res.json();
                      const data = json?.data;
                      const list: unknown[] = Array.isArray(data) ? data
                        : (data && typeof data === 'object')
                          ? (['items', 'content', 'activities', 'results']
                              .map((k) => (data as Record<string, unknown>)[k])
                              .find((v) => Array.isArray(v)) as unknown[] | undefined) ?? []
                          : [];
                      const trimmed = summary.trim();
                      const match = list.find((a) => {
                        if (!a || typeof a !== 'object') return false;
                        const obj = a as Record<string, unknown>;
                        const t = typeof obj.title === 'string' ? obj.title.trim() : '';
                        const m = typeof obj.memo === 'string' ? obj.memo.trim() : '';
                        return t === trimmed || m === trimmed;
                      }) as Record<string, unknown> | undefined;
                      if (!match) { setToast('연결된 미팅을 찾지 못했습니다.'); return; }
                      const rawId = match.activityId ?? match.activity_id;
                      const aid = typeof rawId === 'number' ? rawId
                        : (typeof rawId === 'string' && /^\d+$/.test(rawId)) ? Number(rawId)
                        : null;
                      if (!aid) { setToast('미팅을 식별할 수 없습니다.'); return; }
                      const typeMap: Record<string, EventCategory> = { MEETING: '미팅', CALL: '전화', EMAIL: '이메일', MEMO: '업무' };
                      const rawType = typeof match.type === 'string' ? match.type : '';
                      // 캘린더 EventDetailPanel 이 사용하는 CalendarEvent 형태로 변환.
                      // 자체적으로 GET /activities/{id} 를 호출해 메모/요약/녹음까지 풍부하게 표시한다.
                      setSelectedMeetingEvent({
                        eventId: `act-${aid}`,
                        source: 'FINT',
                        title: typeof match.title === 'string' ? match.title : '',
                        startAt: typeof match.startAt === 'string' ? match.startAt : '',
                        endAt: typeof match.endAt === 'string' ? match.endAt : '',
                        category: typeMap[rawType] ?? '미팅',
                        accountName: acc?.name,
                        accountId: id ? Number(id) : undefined,
                        memo: typeof match.memo === 'string' ? match.memo : undefined,
                      });
                    } catch {
                      setToast('미팅 정보를 불러오지 못했습니다.');
                    }
                  }}
                />
              ))}
            </div>
          )}
          {!selDeal && strats.length === 0 && !aiL && null}
      </main>

      {/* AI 추천 전략 → 일정 추가 모달 */}
      <AddEventModal
        open={addEventOpen || !!editMeetingEvent}
        onClose={() => { setAddEventOpen(false); setAddEventDefaults({}); setEditMeetingEvent(null); }}
        defaultTitle={addEventDefaults.title}
        defaultCategory={addEventDefaults.category}
        defaultAccountId={id ? Number(id) : undefined}
        defaultAccountName={accounts.find((a) => String(a.accountId) === id)?.name}
        editEvent={editMeetingEvent}
        onSaved={() => { setAddEventOpen(false); setAddEventDefaults({}); setEditMeetingEvent(null); refDetail(); }}
      />

      {/* 회사 정보 시그널 전체 보기 모달 */}
      <AllSignalsModal
        open={showAllSignals}
        onClose={() => setShowAllSignals(false)}
        signals={signals}
        accountName={acc?.name}
      />

      {/* NextAction CRM 근거 항목 클릭 시 그 미팅 상세 (캘린더 일정 화면 그대로 재사용) */}
      <EventDetailPanel
        event={selectedMeetingEvent}
        onClose={() => setSelectedMeetingEvent(null)}
        onDeleted={() => setSelectedMeetingEvent(null)}
        onEdit={async (ev) => {
          // ev 는 DealDetailPanel 미팅 list 에서 만든 가벼운 객체라 dealId/attendees 등이 비어있다.
          // BE GET /activities/{id} 로 풍부한 데이터를 가져와 AddEventModal 에 넘긴다.
          const activityId = Number(ev.eventId.replace(/^act-/, ''));
          let enriched: CalendarEvent = ev;
          try {
            const res = await fetchWithAuth(`/activities/${activityId}`);
            if (res.ok) {
              const j = await res.json();
              const d = (j?.data ?? j) as Record<string, unknown>;
              enriched = {
                ...ev,
                title: typeof d.title === 'string' ? d.title : ev.title,
                startAt: typeof d.startAt === 'string' ? d.startAt : ev.startAt,
                endAt: typeof d.endAt === 'string' ? d.endAt : ev.endAt,
                memo: typeof d.memo === 'string' ? d.memo : ev.memo,
                dealId: typeof d.dealId === 'number' ? d.dealId : ev.dealId,
                attendees: (d.attendees && typeof d.attendees === 'object')
                  ? (d.attendees as { internal: string[]; external: string[] })
                  : ev.attendees,
                pipelineStage: (d.pipelineStage && typeof d.pipelineStage === 'object')
                  ? (d.pipelineStage as { stageId: number; stageName: string; stageCode: string })
                  : ev.pipelineStage,
              };
            }
          } catch { /* 실패해도 ev 그대로 사용 */ }
          setEditMeetingEvent(enriched);
          setSelectedMeetingEvent(null);
        }}
      />

      {/* 삭제/수정 후 사용자 피드백 토스트 (자동 2.5초 후 사라짐) */}
      {toast && (
        <div
          style={{
            position: 'fixed',
            top: 80,
            right: 24,
            zIndex: 1300,
            padding: '10px 16px',
            borderRadius: 8,
            backgroundColor: '#1e293b',
            color: '#fff',
            fontFamily: F,
            fontSize: 13,
            fontWeight: 500,
            boxShadow: '0 6px 16px rgba(0,0,0,0.18)',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            animation: 'fintToastIn 180ms ease-out',
          }}
        >
          <span style={{ color: '#22c55e' }}>✓</span>
          <span>{toast}</span>
        </div>
      )}
      <style jsx global>{`
        @keyframes fintToastIn {
          from { opacity: 0; transform: translateY(-6px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </>
  );
}
