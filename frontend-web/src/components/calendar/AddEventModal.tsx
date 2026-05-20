'use client';
// src/components/calendar/AddEventModal.tsx

import { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import type { DealSearchItem } from '@/hooks/useDeal';
import type { CalendarEvent } from './types';
import useBreakpoint from '@/hooks/useBreakpoint';

interface Props {
  open: boolean;
  onClose: () => void;
  defaultDate?: Date;
  /** 드래그로 시간 범위를 선택했을 때 끝 시각. 없으면 시작 +1시간 기본값. */
  defaultEndDate?: Date;
  /** 일정 제목 prefill (예: Next Action 에서 일정 추가 시) */
  defaultTitle?: string;
  /** 카테고리 prefill ('미팅' | '전화' | '업무' | '이메일'). 없으면 '미팅' */
  defaultCategory?: string;
  /** 고객사 prefill (Next Action 에서 호출 시 해당 고객사로 자동 매핑) */
  defaultAccountId?: number;
  defaultAccountName?: string;
  onSaved?: () => void;
  editEvent?: CalendarEvent | null;
}

const TEAL = '#06B6D4';
const pad = (n: number) => String(n).padStart(2, '0');
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

const CATS = [
  { id: '전화', color: '#378ADD', bg: '#EBF2FF' },
  { id: '미팅', color: '#7F77DD', bg: '#ECEBFA' },
  { id: '업무', color: '#1D9E75', bg: '#DDF0EA' },
  { id: '이메일', color: '#D85A30', bg: '#FEF0EC' },
];

// BE pipeline_stages 테이블의 실제 stageName 과 stageId 에 맞춰 정의.
// 이전엔 "협상 중", "첫 미팅 준비" 같은 옛 라벨이 박혀있어 BE 응답("협상", "발굴" 등)과 매칭 실패 →
// 수정 모달에 파이프라인 선택이 빈 상태로 보이고 새로고침해도 반영 안 되던 문제 해소.
const PIPELINE_STAGES = [
  { id: 1, label: '발굴', code: 'FIRST_MEETING' },
  { id: 2, label: '가치 제안', code: 'NEEDS_ANALYSIS' },
  { id: 3, label: '솔루션 설계', code: 'PROPOSAL_WRITING' },
  { id: 4, label: '제안 제출', code: 'PROPOSAL_PRESENT' },
  { id: 5, label: '협상', code: 'NEGOTIATION' },
  { id: 6, label: '계약 대기', code: 'CONTRACT_REVIEW' },
  { id: 7, label: '수주', code: 'CLOSED' },
];
// 백엔드 ActivityType: MEETING("미팅") | CALL("통화") | TASK("업무") | EMAIL("이메일")
const CAT_TO_TYPE: Record<string, string> = {
  전화: 'CALL',
  미팅: 'MEETING',
  업무: 'TASK',
  이메일: 'EMAIL',
};
const TYPE_TO_CAT: Record<string, string> = {
  CALL: '전화',
  MEETING: '미팅',
  TASK: '업무',
  EMAIL: '이메일',
  // displayName fallback
  통화: '전화',
  미팅: '미팅',
  업무: '업무',
  이메일: '이메일',
};

function authHeader(): Record<string, string> {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : {};
}

function toDateVal(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function toTimeVal(d: Date) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const IconCal = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
    <rect x="1.5" y="2.5" width="11" height="10" rx="1.5" stroke="#9CA193" strokeWidth="1.2" />
    <path d="M4.5 1v3M9.5 1v3M1.5 5.5h11" stroke="#9CA193" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
);
const IconClock = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
    <circle cx="7" cy="7" r="5.5" stroke="#9CA193" strokeWidth="1.2" />
    <path d="M7 4v3.2l2 1.2" stroke="#9CA193" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
const IconSearch = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
    <circle cx="6" cy="6" r="4" stroke="#C0C0BB" strokeWidth="1.2" />
    <path d="M9.5 9.5L12 12" stroke="#C0C0BB" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
);

const inputStyle: React.CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '9px 12px',
  borderRadius: 8,
  border: '1px solid #E5E6DE',
  backgroundColor: '#F8F8F5',
  fontSize: 13,
  color: '#16180F',
  outline: 'none',
  fontFamily: 'inherit',
};

function DateTimeBlock({ date, time, onDate, onTime }: { date: string; time: string; onDate: (v: string) => void; onTime: (v: string) => void }) {
  const boxStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5' };
  const inpStyle: React.CSSProperties = { border: 'none', background: 'transparent', fontSize: 13, fontWeight: 500, color: '#16180F', outline: 'none', fontFamily: 'inherit', cursor: 'pointer', padding: 0 };
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <div style={{ ...boxStyle, flex: 1 }}>
        <IconCal />
        <input type="date" value={date} onChange={(e) => onDate(e.target.value)} style={{ ...inpStyle, flex: 1 }} />
      </div>
      <div style={{ ...boxStyle, width: 130 }}>
        <IconClock />
        <input type="time" value={time} onChange={(e) => onTime(e.target.value)} style={{ ...inpStyle, flex: 1 }} />
      </div>
    </div>
  );
}

function Label({ children }: { children: string }) {
  return <div style={{ fontSize: 13, fontWeight: 600, color: '#16180F', marginBottom: 7 }}>{children}</div>;
}

// ─── 타입 ────────────────────────────────────────────────────────────────────
interface AccountItem { accountId: number; name: string; industry: string }
interface ContactItem { contactId: number; name: string; title: string | null }

// ─── 초기값 ──────────────────────────────────────────────────────────────────
const EMPTY_NEW_DEAL = { title: '', amount: '', date: '' };
const EMPTY_NEW_CONTACT = { name: '', title: '', phone: '', email: '' };

export default function AddEventModal({
  open,
  onClose,
  defaultDate,
  defaultEndDate,
  defaultTitle,
  defaultCategory,
  defaultAccountId,
  defaultAccountName,
  onSaved,
  editEvent,
}: Props) {
  const isEdit = !!editEvent;
  const bp = useBreakpoint();
  const isCompact = bp !== 'desktop';

  // ── 기본 필드 ──
  const [title, setTitle] = useState('');
  const [startD, setStartD] = useState('');
  const [startT, setStartT] = useState('');
  const [endD, setEndD] = useState('');
  const [endT, setEndT] = useState('');
  const [cat, setCat] = useState('미팅');
  const [pipe, setPipe] = useState('');
  const [memo, setMemo] = useState('');
  const [visible, setVisible] = useState(false);
  const [saving, setSaving] = useState(false);

  // ── 고객사 (공통 — 딜·담당자 모두 여기서 파생) ──
  const [accountQuery, setAccountQuery] = useState('');
  const [accountResults, setAccountResults] = useState<AccountItem[]>([]);
  const [accountDropOpen, setAccountDropOpen] = useState(false);
  const [accountPage, setAccountPage] = useState(0);
  const [accountHasNext, setAccountHasNext] = useState(false);
  const [accountLoadingMore, setAccountLoadingMore] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState<AccountItem | null>(null);

  // ── 고객사 선택 시 자동 로드되는 목록 ──
  const [accountContacts, setAccountContacts] = useState<ContactItem[]>([]);

  // ── 딜 검색 드롭다운 ──
  const [dealQuery, setDealQuery] = useState('');
  const [dealResults, setDealResults] = useState<DealSearchItem[]>([]);
  const [dealDropOpen, setDealDropOpen] = useState(false);
  const [dealSearchLoading, setDealSearchLoading] = useState(false);
  const [dealPage, setDealPage] = useState(0);
  const [dealHasNext, setDealHasNext] = useState(false);
  const [dealLoadingMore, setDealLoadingMore] = useState(false);
  const dealDropdownRef = useRef<HTMLDivElement>(null);
  // 무한 스크롤 effect에서 최신 쿼리·계정을 stale closure 없이 읽기 위한 refs
  const dealQueryRef = useRef(dealQuery);
  const dealAccountRef = useRef<AccountItem | null>(null);

  // ── 딜 선택 / 신규 생성 ──
  const [selectedDeal, setSelectedDeal] = useState<DealSearchItem | null>(null);
  const [creatingDeal, setCreatingDeal] = useState(false);
  const [newDeal, setNewDeal] = useState(EMPTY_NEW_DEAL);

  // ── 담당자 선택 / 신규 생성 ──
  const [selectedContactId, setSelectedContactId] = useState<number | null>(null);
  const [creatingContact, setCreatingContact] = useState(false);
  const [newContact, setNewContact] = useState(EMPTY_NEW_CONTACT);

  // ref 동기화 (매 렌더, effect stale closure 방지)
  dealQueryRef.current = dealQuery;
  dealAccountRef.current = selectedAccount;

  // ── 전체 초기화 함수 ──
  const resetForm = useCallback((date?: Date, ev?: CalendarEvent | null, endDate?: Date, prefill?: {
    title?: string; category?: string; accountId?: number; accountName?: string;
  }) => {
    const base = ev ? new Date(ev.startAt) : (date ?? new Date());
    // 우선순위: editEvent.endAt > 드래그로 받은 endDate > base + 1시간
    const endBase = ev
      ? new Date(ev.endAt)
      : endDate
      ? new Date(endDate)
      : new Date(base.getTime() + 3600_000);

    setTitle(ev?.title ?? prefill?.title ?? '');
    setStartD(toDateVal(base));
    setStartT(toTimeVal(base));
    setEndD(toDateVal(endBase));
    setEndT(toTimeVal(endBase));
    setCat(ev?.category ? (TYPE_TO_CAT[ev.category] ?? '미팅') : (prefill?.category ?? '미팅'));
    setMemo(ev?.memo ?? '');
    setPipe(ev?.pipelineStage?.stageName ?? '');

    setAccountQuery('');
    setAccountResults([]);
    setAccountDropOpen(false);
    setAccountPage(0);
    setAccountHasNext(false);
    setAccountLoadingMore(false);
    setDealQuery('');
    setDealResults([]);
    setDealDropOpen(false);
    setDealPage(0);
    setDealHasNext(false);
    setSelectedDeal(null);
    setCreatingDeal(false);
    setNewDeal(EMPTY_NEW_DEAL);
    setCreatingContact(false);
    setNewContact(EMPTY_NEW_CONTACT);
    setSaving(false);

    const headers = authHeader();

    // 수정 모드: 고객사·딜 정보 복원
    if (ev?.accountId && ev?.accountName) {
      const acct: AccountItem = { accountId: ev.accountId, name: ev.accountName, industry: '' };
      setSelectedAccount(acct);
      setSelectedContactId(null);

      // 딜 복원: dealTitle이 있으면 바로 설정, 없으면 상세 API로 조회
      console.log('[resetForm] ev.dealId:', ev.dealId, 'ev.dealTitle:', ev.dealTitle);
      if (ev.dealId) {
        if (ev.dealTitle) {
          console.log('[resetForm] deal from ev (no fetch):', ev.dealId, ev.dealTitle);
          setSelectedDeal({ dealId: ev.dealId, title: ev.dealTitle, amount: null, expectedClose: null });
        } else {
          console.log('[resetForm] fetching /deals/', ev.dealId);
          fetch(`${API_BASE}/deals/${ev.dealId}`, { headers })
            .then(r => { console.log('[resetForm] deal fetch status:', r.status); return r.ok ? r.json() : null; })
            .then(json => {
              console.log('[resetForm] deal response:', json);
              const detail = json?.data ?? json;
              if (detail?.dealId) {
                console.log('[resetForm] setSelectedDeal with:', detail);
                setSelectedDeal({
                  dealId: detail.dealId,
                  title: detail.title,
                  amount: detail.amount ?? null,
                  expectedClose: detail.expectedClose ?? null,
                });
              } else {
                console.log('[resetForm] deal detail invalid, fallback to dealId only');
                // fallback: dealId 만으로 표시 (제목 누락 시 placeholder)
                setSelectedDeal({ dealId: ev.dealId!, title: `딜 #${ev.dealId}`, amount: null, expectedClose: null });
              }
            })
            .catch((err) => {
              console.log('[resetForm] deal fetch error:', err);
            });
        }
      } else {
        console.log('[resetForm] ev.dealId 없음 → 영업 목표 비어둠');
      }

      // 담당자 로드
      fetch(`${API_BASE}/accounts/${ev.accountId}/contacts`, { headers })
        .then(r => r.ok ? r.json() : null)
        .then(json => {
          if (!json) return;
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const contacts = (json.data ?? []).map((c: any) => ({
            contactId: c.contactId, name: c.name, title: c.title ?? null,
          }));
          setAccountContacts(contacts);
          console.log('[resetForm] ev.attendees:', ev.attendees);
          console.log('[resetForm] contacts:', contacts);
          // attendees 응답이 BE 별 DTO 에 따라 형태가 다를 수 있어 robust 하게 매칭한다.
          // 1) Attendees object 형태: { external: ['박지수'] }
          // 2) List<Map> 형태: [{ name: '박지수', type: 'external' }]
          let attendeeName: string | undefined;
          const attRaw = ev.attendees as unknown;
          if (attRaw && typeof attRaw === 'object') {
            const obj = attRaw as { external?: string[] };
            if (Array.isArray(obj.external) && obj.external[0]) {
              attendeeName = String(obj.external[0]);
            } else if (Array.isArray(attRaw)) {
              // List<Map> 형태인 경우 첫 external 항목 추출
              const list = attRaw as Array<Record<string, unknown>>;
              const ext = list.find((x) => x?.type === 'external');
              if (ext && typeof ext.name === 'string') attendeeName = ext.name;
            }
          }
          console.log('[resetForm] resolved attendeeName:', attendeeName);
          if (attendeeName) {
            const target = attendeeName.trim();
            const match = contacts.find((c: ContactItem) => {
              const n = c.name.trim();
              // 정확 일치 우선, 그 다음 부분 일치(어느 한쪽이 다른 쪽 포함)
              return n === target || n.includes(target) || target.includes(n);
            });
            console.log('[resetForm] matched contact:', match);
            if (match) setSelectedContactId(match.contactId);
          }
        })
        .catch(() => {});

    } else if (prefill?.accountId && prefill.accountName) {
      // Next Action 등에서 고객사 prefill 받은 신규 모드
      const acct: AccountItem = { accountId: prefill.accountId, name: prefill.accountName, industry: '' };
      setSelectedAccount(acct);
      setSelectedDeal(null);
      setSelectedContactId(null);

      fetch(`${API_BASE}/accounts/${prefill.accountId}/contacts`, { headers })
        .then(r => r.ok ? r.json() : null)
        .then(json => {
          if (!json) return;
          setAccountContacts((json.data ?? []).map((c: { contactId: number; name: string; title?: string }) => ({
            contactId: c.contactId, name: c.name, title: c.title ?? null,
          })));
        })
        .catch(() => {});

    } else {
      setSelectedAccount(null);
      setAccountContacts([]);
      setSelectedDeal(null);
      setSelectedContactId(null);
    }
  }, []);

  // ── open/close 시 상태 관리 ──
  useEffect(() => {
    if (open) {
      resetForm(defaultDate, editEvent, defaultEndDate, {
        title: defaultTitle,
        category: defaultCategory,
        accountId: defaultAccountId,
        accountName: defaultAccountName,
      });
      document.body.style.overflow = 'hidden';
      requestAnimationFrame(() => setVisible(true));
    } else {
      setVisible(false);
      setTimeout(() => { document.body.style.overflow = ''; }, 200);
    }
    return () => { document.body.style.overflow = ''; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // ── 고객사 검색: 쿼리 변경 또는 드롭다운 열림 시 page 0 부터 재검색 ──
  useEffect(() => {
    if (!accountDropOpen) return;
    setAccountPage(0);
    setAccountHasNext(false);
    const t = setTimeout(async () => {
      try {
        const qs = new URLSearchParams();
        if (accountQuery.trim()) qs.set('keyword', accountQuery.trim());
        qs.set('page', '0');
        qs.set('size', '15');
        const res = await fetch(`${API_BASE}/accounts/searchable?${qs.toString()}`, { headers: authHeader() });
        const json = await res.json();
        const data = json.data ?? {};
        setAccountResults((data.content ?? []) as AccountItem[]);
        setAccountHasNext(data.hasNext ?? false);
      } catch { setAccountResults([]); }
    }, 250);
    return () => clearTimeout(t);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountQuery, accountDropOpen]);

  // ── 고객사 무한 스크롤: page > 0 일 때 결과 append ──
  useEffect(() => {
    if (accountPage === 0 || !accountHasNext || accountLoadingMore) return;
    setAccountLoadingMore(true);
    const qs = new URLSearchParams();
    if (accountQuery.trim()) qs.set('keyword', accountQuery.trim());
    qs.set('page', String(accountPage));
    qs.set('size', '15');
    fetch(`${API_BASE}/accounts/searchable?${qs.toString()}`, { headers: authHeader() })
      .then(r => r.ok ? r.json() : null)
      .then(json => {
        if (!json) return;
        const data = json.data ?? {};
        setAccountResults(prev => [...prev, ...(data.content ?? []) as AccountItem[]]);
        setAccountHasNext(data.hasNext ?? false);
      })
      .catch(() => {})
      .finally(() => setAccountLoadingMore(false));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountPage, accountHasNext, accountLoadingMore]);

  // ── 딜 검색: 드롭다운 열림 / 쿼리 / 고객사 변경 → page 0부터 새로 로드 ──
  useEffect(() => {
    if (!dealDropOpen) return;
    setDealPage(0);
    setDealHasNext(false);
    setDealSearchLoading(true);
    const t = setTimeout(async () => {
      try {
        const qs = new URLSearchParams();
        if (dealQuery.trim()) qs.set('keyword', dealQuery.trim());
        if (selectedAccount) qs.set('accountId', String(selectedAccount.accountId));
        qs.set('size', '15');
        qs.set('page', '0');
        const res = await fetch(`${API_BASE}/deals?${qs.toString()}`, { headers: authHeader() });
        if (!res.ok) throw new Error();
        const json = await res.json();
        const listResponse = json.data ?? {};
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setDealResults((listResponse.data ?? []).map((d: any) => ({
          dealId: d.dealId, title: d.title,
          amount: d.amount != null ? Number(d.amount) : null,
          expectedClose: d.expectedClose ?? null,
        })));
        setDealHasNext(listResponse.hasNext ?? false);
      } catch {
        setDealResults([]);
      } finally {
        setDealSearchLoading(false);
      }
    }, 250);
    return () => clearTimeout(t);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dealQuery, selectedAccount?.accountId, dealDropOpen]);

  // ── 딜 무한 스크롤: page > 0 일 때 결과 append ──
  useEffect(() => {
    if (dealPage === 0) return;
    setDealLoadingMore(true);
    const qs = new URLSearchParams();
    if (dealQueryRef.current.trim()) qs.set('keyword', dealQueryRef.current.trim());
    if (dealAccountRef.current) qs.set('accountId', String(dealAccountRef.current.accountId));
    qs.set('size', '15');
    qs.set('page', String(dealPage));
    fetch(`${API_BASE}/deals?${qs.toString()}`, { headers: authHeader() })
      .then(r => r.ok ? r.json() : null)
      .then(json => {
        if (!json) return;
        const listResponse = json.data ?? {};
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setDealResults(prev => [...prev, ...(listResponse.data ?? []).map((d: any) => ({
          dealId: d.dealId, title: d.title,
          amount: d.amount != null ? Number(d.amount) : null,
          expectedClose: d.expectedClose ?? null,
        }))]);
        setDealHasNext(listResponse.hasNext ?? false);
      })
      .catch(() => {})
      .finally(() => setDealLoadingMore(false));
  }, [dealPage]); // dealPage만 의존 — query/account는 ref로 읽음

  // ── 딜 드롭다운 스크롤 → 다음 페이지 로드 ──
  const handleDealDropdownScroll = useCallback(() => {
    const el = dealDropdownRef.current;
    if (!el || !dealHasNext || dealLoadingMore || dealSearchLoading) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 60) {
      setDealPage(prev => prev + 1);
    }
  }, [dealHasNext, dealLoadingMore, dealSearchLoading]);

  // ── 고객사 선택 시 담당자 로드 ──
  const loadAccountContacts = useCallback(async (accountId: number) => {
    try {
      const res = await fetch(`${API_BASE}/accounts/${accountId}/contacts`, { headers: authHeader() });
      if (!res.ok) return;
      const json = await res.json();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setAccountContacts((json.data ?? []).map((c: any) => ({
        contactId: c.contactId, name: c.name, title: c.title ?? null,
      })));
    } catch {
      setAccountContacts([]);
    }
  }, []);

  const handleSelectAccount = useCallback((a: AccountItem) => {
    setSelectedAccount(a);
    setAccountQuery('');
    setAccountDropOpen(false);
    setSelectedDeal(null);
    setDealQuery('');
    setDealResults([]);
    setSelectedContactId(null);
    setCreatingDeal(false);
    setCreatingContact(false);
    loadAccountContacts(a.accountId);
  }, [loadAccountContacts]);

  const handleClearAccount = useCallback(() => {
    setSelectedAccount(null);
    setAccountContacts([]);
    setSelectedDeal(null);
    setDealQuery('');
    setDealResults([]);
    setSelectedContactId(null);
    setCreatingDeal(false);
    setCreatingContact(false);
  }, []);

  // ── 딜 선택 ──
  const handleSelectDeal = useCallback((deal: DealSearchItem) => {
    setSelectedDeal(deal);
    setDealDropOpen(false);
    setDealQuery('');
  }, []);

  if (!open && !visible) return null;
  if (typeof document === 'undefined') return null;

  const close = () => {
    setVisible(false);
    setTimeout(onClose, 200);
  };

  // ── 저장 ──
  const handleSave = async () => {
    if (!title.trim() || saving) return;
    const startDt = new Date(`${startD}T${startT}:00+09:00`);
    const endDt   = new Date(`${endD}T${endT}:00+09:00`);
    if (isNaN(startDt.getTime()) || isNaN(endDt.getTime())) {
      alert('날짜/시간을 올바르게 입력해주세요.'); return;
    }
    if (startDt >= endDt) {
      alert('종료 시간은 시작 시간보다 늦어야 합니다.'); return;
    }
    setSaving(true);
    try {
      let dealId: number | null = selectedDeal?.dealId ?? null;

      // 신규 담당자 생성
      let newContactId: number | null = null;
      if (creatingContact && selectedAccount && newContact.name.trim()) {
        const body: Record<string, unknown> = { accountId: selectedAccount.accountId, name: newContact.name.trim() };
        if (newContact.title) body.title = newContact.title;
        if (newContact.phone) body.phone = newContact.phone;
        if (newContact.email) body.email = newContact.email;
        const r = await fetch(`${API_BASE}/contacts`, { method: 'POST', headers: authHeader(), body: JSON.stringify(body) });
        if (r.ok) { newContactId = (await r.json()).data.contactId; }
      }

      // 신규 딜 생성
      if (creatingDeal && selectedAccount && newDeal.title.trim()) {
        const body: Record<string, unknown> = { accountId: selectedAccount.accountId, title: newDeal.title.trim() };
        if (newDeal.amount) body.amount = Number(newDeal.amount);
        if (newDeal.date) body.expectedClose = newDeal.date;
        const contactId = newContactId ?? selectedContactId;
        if (contactId) body.contacts = [{ contactId }];
        const r = await fetch(`${API_BASE}/deals`, { method: 'POST', headers: authHeader(), body: JSON.stringify(body) });
        if (r.ok) { dealId = (await r.json()).data.dealId; }
      }

      // 활동 본문
      const actBody: Record<string, unknown> = {
        type: CAT_TO_TYPE[cat] ?? 'MEETING',
        title: title.trim(),
        startAt: `${startD}T${startT}:00+09:00`,
        endAt: `${endD}T${endT}:00+09:00`,
        memo: memo.trim() || null,
      };
      if (isEdit) {
        actBody.dealId = (dealId && dealId > 0) ? dealId : null;
      } else {
        if (dealId && dealId > 0) actBody.dealId = dealId;
      }
      if (selectedAccount) actBody.accountId = selectedAccount.accountId;
      if (pipe) {
        const stage = PIPELINE_STAGES.find((s) => s.label === pipe);
        if (stage) actBody.pipelineStageId = stage.id;
      }
      // attendees
      const contactId = newContactId ?? selectedContactId;
      if (contactId) {
        const c = accountContacts.find(cc => cc.contactId === contactId);
        if (c) actBody.attendees = [{ name: c.name, type: 'external' }];
      }

      console.log('[handleSave] selectedContactId:', selectedContactId, 'newContactId:', newContactId);
      console.log('[handleSave] accountContacts:', accountContacts);
      console.log('[handleSave] actBody full:', JSON.stringify(actBody, null, 2));
      console.log('[handleSave] actBody.attendees:', actBody.attendees);
      let res: Response;
      if (isEdit && editEvent) {
        res = await fetch(`${API_BASE}/activities/${editEvent.eventId.replace(/^act-/, '')}`, { method: 'PATCH', headers: authHeader(), body: JSON.stringify(actBody) });
      } else {
        res = await fetch(`${API_BASE}/activities`, { method: 'POST', headers: authHeader(), body: JSON.stringify(actBody) });
      }
      console.log('[handleSave] response status:', res.status, 'ok:', res.ok);
      const respJson = await res.clone().json().catch(() => null);
      console.log('[handleSave] response body:', respJson);
      console.log('[handleSave] response attendees:', respJson?.data?.attendees);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      onSaved?.();
      close();
    } catch (e) {
      console.error('[AddEventModal] 저장 실패', e);
      alert('일정 저장에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  };

  // ── 렌더링 ──
  return createPortal(
    <div style={{ position: 'fixed', inset: 0, zIndex: 1200, fontFamily: "'Pretendard',-apple-system,sans-serif", WebkitFontSmoothing: 'antialiased', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div onClick={close} style={{ position: 'absolute', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', opacity: visible ? 1 : 0, transition: 'opacity 0.2s' }} />
      <div style={{ position: 'relative', backgroundColor: '#fff', borderRadius: isCompact ? 0 : 16, width: isCompact ? '100%' : '90%', maxWidth: isCompact ? '100%' : 520, height: isCompact ? '100%' : 'auto', maxHeight: isCompact ? '100%' : '90vh', display: 'flex', flexDirection: 'column', boxShadow: isCompact ? 'none' : '0 20px 60px rgba(0,0,0,0.15)', transform: visible ? 'scale(1)' : 'scale(0.96)', opacity: visible ? 1 : 0, transition: 'transform 0.2s, opacity 0.2s' }}>
        {/* 헤더 */}
        <div style={{ padding: '20px 22px 16px', borderBottom: '1px solid #F0F0EE', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
          <span style={{ fontSize: 17, fontWeight: 700, color: '#16180F' }}>{isEdit ? '일정 수정' : '일정 추가'}</span>
          <button onClick={close} style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: 20, color: '#9CA3AF', lineHeight: 1 }}>×</button>
        </div>

        {/* 본문 */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 18 }}>
          {/* 제목 */}
          <div>
            <Label>일정 제목</Label>
            <input placeholder="일정 제목을 입력하세요" value={title} onChange={(e) => setTitle(e.target.value)} style={inputStyle}
              onFocus={(e) => { e.target.style.borderColor = TEAL; e.target.style.backgroundColor = '#fff'; }}
              onBlur={(e) => { e.target.style.borderColor = '#E5E6DE'; e.target.style.backgroundColor = '#F8F8F5'; }} />
          </div>

          {/* 날짜 */}
          <div>
            <Label>시작</Label>
            <DateTimeBlock date={startD} time={startT} onDate={setStartD} onTime={setStartT} />
          </div>
          <div>
            <Label>종료</Label>
            <DateTimeBlock date={endD} time={endT} onDate={setEndD} onTime={setEndT} />
          </div>

          {/* ── 고객사 (공통) ── */}
          <div>
            <Label>고객사</Label>
            {selectedAccount ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', borderRadius: 8, border: `1px solid ${TEAL}`, backgroundColor: '#ECFEFF' }}>
                <span style={{ flex: 1, fontSize: 13, fontWeight: 500, color: '#16180F' }}>{selectedAccount.name}</span>
                <span style={{ fontSize: 11, color: '#6B7280' }}>{selectedAccount.industry}</span>
                <button onClick={handleClearAccount} style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#9CA3AF', fontSize: 16, lineHeight: 1 }}>×</button>
              </div>
            ) : (
              <div style={{ position: 'relative' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5' }}>
                  <IconSearch />
                  <input placeholder="고객사 이름으로 검색..." value={accountQuery}
                    onChange={(e) => { setAccountQuery(e.target.value); setAccountDropOpen(true); }}
                    onFocus={() => setAccountDropOpen(true)}
                    onBlur={() => setTimeout(() => setAccountDropOpen(false), 150)}
                    style={{ flex: 1, border: 'none', background: 'transparent', fontSize: 13, color: '#16180F', outline: 'none', fontFamily: 'inherit' }} />
                </div>
                {accountDropOpen && (
                  <div
                    style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 200, backgroundColor: '#fff', border: '1px solid #E5E6DE', borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)', marginTop: 4, maxHeight: 160, overflowY: 'auto' }}
                    onScroll={(e) => {
                      const el = e.currentTarget;
                      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 30 && accountHasNext && !accountLoadingMore) {
                        setAccountPage(prev => prev + 1);
                      }
                    }}
                  >
                    {accountResults.length === 0 && !accountLoadingMore && <div style={{ padding: '10px 12px', fontSize: 13, color: '#9CA3AF' }}>결과 없음</div>}
                    {accountResults.map((a) => (
                      <button key={a.accountId} onClick={() => handleSelectAccount(a)}
                        style={{ width: '100%', textAlign: 'left', padding: '10px 12px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, color: '#16180F', borderBottom: '1px solid #F3F4F6', display: 'flex', alignItems: 'center', gap: 8 }}
                        onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#F8F8F5'; }}
                        onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent'; }}>
                        <span style={{ flex: 1 }}>{a.name}</span>
                        <span style={{ fontSize: 11, color: '#6B7280' }}>{a.industry}</span>
                      </button>
                    ))}
                    {accountLoadingMore && <div style={{ padding: '10px 12px', fontSize: 12, color: '#9CA193', textAlign: 'center' }}>로딩 중...</div>}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* ── 고객사 선택 시: 딜 + 담당자 ── */}
          {selectedAccount && (
          <>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Label>영업 목표</Label>
              <span style={{ fontSize: 11, color: '#9CA3AF' }}>선택사항</span>
            </div>

            {selectedDeal ? (
              /* 선택된 딜 chip */
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', borderRadius: 8, border: `1px solid ${TEAL}`, backgroundColor: '#ECFEFF' }}>
                <span style={{ flex: 1, fontSize: 13, fontWeight: 500, color: '#16180F' }}>{selectedDeal.title}</span>
                {selectedDeal.expectedClose && (
                  <span style={{ fontSize: 11, color: '#6B7280' }}>{selectedDeal.expectedClose}</span>
                )}
                <button
                  onClick={() => { setSelectedDeal(null); setDealQuery(''); }}
                  style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#9CA3AF', fontSize: 16, lineHeight: 1 }}>×</button>
              </div>
            ) : creatingDeal ? (
              /* 신규 딜 입력 폼 — 고객사 선택된 경우에만 활성화 */
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <input placeholder="딜 제목 *" value={newDeal.title} onChange={(e) => setNewDeal(p => ({ ...p, title: e.target.value }))} style={inputStyle} />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <input placeholder="예상 금액 (₩)" type="number" value={newDeal.amount} onChange={(e) => setNewDeal(p => ({ ...p, amount: e.target.value }))} style={inputStyle} />
                  <input type="date" value={newDeal.date} onChange={(e) => setNewDeal(p => ({ ...p, date: e.target.value }))} style={inputStyle} />
                </div>
                <button onClick={() => { setCreatingDeal(false); setNewDeal(EMPTY_NEW_DEAL); }}
                  style={{ alignSelf: 'flex-start', padding: '4px 10px', borderRadius: 6, border: 'none', backgroundColor: 'transparent', cursor: 'pointer', fontSize: 12, color: '#9CA3AF' }}>
                  ← 딜 검색으로 돌아가기
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {/* 딜 키워드 검색 드롭다운 */}
                <div style={{ position: 'relative' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5' }}>
                    <IconSearch />
                    <input
                      placeholder={`${selectedAccount.name}의 딜 검색...`}
                      value={dealQuery}
                      onChange={(e) => setDealQuery(e.target.value)}
                      onFocus={() => setDealDropOpen(true)}
                      onBlur={() => setTimeout(() => setDealDropOpen(false), 150)}
                      style={{ flex: 1, border: 'none', background: 'transparent', fontSize: 13, color: '#16180F', outline: 'none', fontFamily: 'inherit' }}
                    />
                    {dealSearchLoading && <span style={{ fontSize: 11, color: '#9CA193' }}>...</span>}
                  </div>
                  {dealDropOpen && (
                    <div
                      ref={dealDropdownRef}
                      onScroll={handleDealDropdownScroll}
                      style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 200, backgroundColor: '#fff', border: '1px solid #E5E6DE', borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)', marginTop: 4, maxHeight: 200, overflowY: 'auto' }}>
                      {dealSearchLoading && dealResults.length === 0
                        ? <div style={{ padding: '10px 12px', fontSize: 13, color: '#9CA3AF' }}>불러오는 중...</div>
                        : dealResults.length === 0
                          ? <div style={{ padding: '10px 12px', fontSize: 13, color: '#9CA3AF' }}>결과 없음</div>
                          : dealResults.map((d) => (
                              <button key={d.dealId} onClick={() => handleSelectDeal(d)}
                                style={{ width: '100%', textAlign: 'left', padding: '10px 12px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, color: '#16180F', borderBottom: '1px solid #F3F4F6', display: 'flex', alignItems: 'center', gap: 8 }}
                                onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#F8F8F5'; }}
                                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent'; }}>
                                <span style={{ flex: 1 }}>{d.title}</span>
                                {d.amount != null && (
                                  <span style={{ fontSize: 11, color: '#6B7280' }}>{Number(d.amount).toLocaleString()}원</span>
                                )}
                              </button>
                            ))
                      }
                      {dealLoadingMore && (
                        <div style={{ padding: '8px 12px', fontSize: 12, color: '#9CA3AF', textAlign: 'center' }}>더 불러오는 중...</div>
                      )}
                    </div>
                  )}
                </div>

                <button onClick={() => setCreatingDeal(true)}
                  style={{ padding: '8px 12px', borderRadius: 8, border: '1px dashed #D1D5DB', backgroundColor: 'transparent', cursor: 'pointer', fontSize: 12, color: TEAL, fontWeight: 500, textAlign: 'center' }}>
                  + 새 딜 만들기
                </button>
              </div>
            )}
          </div>

          <div>
              <Label>담당자</Label>
              {accountContacts.length > 0 && !creatingContact ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {accountContacts.map((c) => {
                    const sel = selectedContactId === c.contactId;
                    return (
                      <button key={c.contactId} onClick={() => setSelectedContactId(sel ? null : c.contactId)}
                        style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', borderRadius: 8, border: sel ? `1.5px solid ${TEAL}` : '1px solid #E5E6DE', backgroundColor: sel ? '#ECFEFF' : '#fff', cursor: 'pointer', textAlign: 'left', transition: 'border-color 0.15s' }}>
                        <span style={{ width: 16, height: 16, borderRadius: '50%', border: sel ? `5px solid ${TEAL}` : '2px solid #D1D5DB', flexShrink: 0, boxSizing: 'border-box' }} />
                        <span style={{ flex: 1, fontSize: 13, fontWeight: sel ? 600 : 400, color: '#16180F' }}>{c.name}{c.title ? ` · ${c.title}` : ''}</span>
                      </button>
                    );
                  })}
                  <button onClick={() => { setCreatingContact(true); setSelectedContactId(null); }}
                    style={{ padding: '8px 12px', borderRadius: 8, border: '1px dashed #D1D5DB', backgroundColor: 'transparent', cursor: 'pointer', fontSize: 12, color: TEAL, fontWeight: 500, textAlign: 'center' }}>
                    + 새 담당자 추가
                  </button>
                </div>
              ) : creatingContact ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                    <input placeholder="이름 *" value={newContact.name} onChange={(e) => setNewContact(p => ({ ...p, name: e.target.value }))} style={inputStyle} />
                    <input placeholder="직책" value={newContact.title} onChange={(e) => setNewContact(p => ({ ...p, title: e.target.value }))} style={inputStyle} />
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                    <input placeholder="전화번호" value={newContact.phone} onChange={(e) => setNewContact(p => ({ ...p, phone: e.target.value }))} style={inputStyle} />
                    <input placeholder="이메일" value={newContact.email} onChange={(e) => setNewContact(p => ({ ...p, email: e.target.value }))} style={inputStyle} />
                  </div>
                  {accountContacts.length > 0 && (
                    <button onClick={() => { setCreatingContact(false); setNewContact(EMPTY_NEW_CONTACT); }}
                      style={{ alignSelf: 'flex-start', padding: '4px 10px', borderRadius: 6, border: 'none', backgroundColor: 'transparent', cursor: 'pointer', fontSize: 12, color: '#9CA3AF' }}>
                      ← 기존 담당자에서 선택
                    </button>
                  )}
                </div>
              ) : (
                <button onClick={() => setCreatingContact(true)}
                  style={{ padding: '10px 12px', borderRadius: 8, border: '1px dashed #D1D5DB', backgroundColor: 'transparent', cursor: 'pointer', fontSize: 13, color: TEAL, fontWeight: 500, textAlign: 'center', width: '100%' }}>
                  + 새 담당자 추가
                </button>
              )}
            </div>
          </>
          )}

          {/* 카테고리 */}
          <div>
            <Label>카테고리</Label>
            <div style={{ display: 'flex', gap: 6 }}>
              {CATS.map((c) => {
                const sel = cat === c.id;
                return (
                  <button key={c.id} onClick={() => setCat(c.id)}
                    style={{ padding: '6px 14px', borderRadius: 20, cursor: 'pointer', border: sel ? `1.5px solid ${c.color}` : '1.5px solid #E5E6DE', backgroundColor: sel ? c.bg : '#fff', fontSize: 13, fontWeight: sel ? 600 : 400, color: sel ? c.color : '#737880', display: 'flex', alignItems: 'center', gap: 5, transition: 'border-color 0.15s' }}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: c.color, flexShrink: 0 }} />
                    {c.id}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 파이프라인 */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 7 }}>
              <Label>파이프라인 단계</Label>
              <span style={{ fontSize: 11, color: '#9CA193', marginTop: -1 }}>선택사항</span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {PIPELINE_STAGES.map(({ label: p }) => {
                const sel = pipe === p;
                return (
                  <button key={p} onClick={() => setPipe(sel ? '' : p)}
                    style={{ padding: '5px 12px', borderRadius: 8, cursor: 'pointer', border: sel ? `1.5px solid ${TEAL}` : '1.5px solid #E5E6DE', backgroundColor: sel ? '#ECFEFF' : '#fff', fontSize: 12, fontWeight: sel ? 600 : 400, color: sel ? TEAL : '#737880', transition: 'border-color 0.15s' }}>
                    {p}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 메모 */}
          <div>
            <Label>메모</Label>
            <textarea placeholder="메모를 입력하세요..." value={memo} onChange={(e) => setMemo(e.target.value)} rows={3}
              style={{ ...inputStyle, resize: 'vertical', lineHeight: 1.6 }}
              onFocus={(e) => { e.target.style.borderColor = TEAL; e.target.style.backgroundColor = '#fff'; }}
              onBlur={(e) => { e.target.style.borderColor = '#E5E6DE'; e.target.style.backgroundColor = '#F8F8F5'; }} />
          </div>
        </div>

        {/* 푸터 */}
        <div style={{ padding: '12px 22px 16px', borderTop: '1px solid #F0F0EE', display: 'flex', gap: 8, justifyContent: 'flex-end', flexShrink: 0 }}>
          <button onClick={close} style={{ padding: '8px 18px', borderRadius: 8, cursor: 'pointer', border: '1px solid #E5E6DE', backgroundColor: '#fff', fontSize: 13, fontWeight: 500, color: '#737880', fontFamily: 'inherit' }}>취소</button>
          <button onClick={handleSave} disabled={!title.trim() || saving}
            style={{ padding: '8px 22px', borderRadius: 8, border: 'none', backgroundColor: title.trim() && !saving ? TEAL : '#B8DEE8', fontSize: 13, fontWeight: 600, color: '#fff', cursor: title.trim() && !saving ? 'pointer' : 'default', fontFamily: 'inherit', transition: 'background-color 0.15s' }}>
            {saving ? '저장 중...' : isEdit ? '수정 완료' : '저장'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
