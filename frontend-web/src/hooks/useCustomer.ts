'use client';

import { useCallback, useEffect, useState } from 'react';
import type {
  Account,
  ApiAccountItem,
  ApiContact,
  ApiDeal,
  ApiSignal,
  ContactInfo,
  Deal,
  MoodLevel,
  Signal,
} from '@/types/customer';
import { fetchWithAuth } from '@/hooks/useAuth';

// ─── 매핑 헬퍼 ───────────────────────────────────────────────────────────────

const CONTACT_COLORS = ['#7c3aed', '#0891b2', '#059669', '#dc2626', '#d97706'];

function toMoodLevel(item: ApiAccountItem): MoodLevel {
  // latestMood가 있으면 직접 사용 (백엔드 Mood enum)
  if (item.latestMood) return item.latestMood;
  // 숫자 temperature fallback
  const temp = item.temperature ?? null;
  if (temp === null || temp === undefined) return 'CLOUDY';
  if (temp > 75) return 'RAINBOW';
  if (temp > 50) return 'SUNNY';
  if (temp > 25) return 'CLOUDY';
  if (temp > 10) return 'RAINY';
  return 'THUNDER';
}

function mapApiContact(c: ApiContact, idx: number): ContactInfo {
  return {
    contactId: c.contactId,
    name: c.name,
    role: c.title ?? '',
    color: CONTACT_COLORS[idx % CONTACT_COLORS.length],
    phone: c.phone ?? undefined,
    email: c.email ?? undefined,
    memo: c.personality ?? undefined,
  };
}

// DART 공시 URL 합성.
//   백엔드 url 필드는 dart_disclosures.rcept_no(14자 접수번호) 또는 쿼리스트링 형태로 옴.
//   DART 공식 뷰어: https://dart.fss.or.kr/dsaf001/main.do?rcpNo={접수번호}
//   허용 입력:
//     1) "https://..."         → 이미 full URL  → 그대로
//     2) "?rcpNo=20250515..." → base + raw
//     3) "rcpNo=20250515..."  → base + "?" + raw
//     4) "20250515000123"     → base + "?rcpNo=" + raw (값만)
const DART_BASE = 'https://dart.fss.or.kr/dsaf001/main.do';
function composeDartUrl(raw: string): string {
  const v = raw.trim();
  if (!v) return DART_BASE;
  if (/^https?:\/\//i.test(v)) return v;
  if (v.startsWith('?')) return `${DART_BASE}${v}`;
  if (v.includes('=')) return `${DART_BASE}?${v}`;
  // rcept_no 만 온 경우 — 숫자/문자 검증 없이 그대로 쿼리 키에 붙임 (안전)
  return `${DART_BASE}?rcpNo=${encodeURIComponent(v)}`;
}

function mapApiSignal(s: ApiSignal): Signal {
  const diffMs = Date.now() - new Date(s.occurredAt).getTime();
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);
  const time =
    diffDays > 0 ? `${diffDays}일 전` : diffHours > 0 ? `${diffHours}시간 전` : '방금 전';

  // DART: 백엔드 title = report_nm (공시명 raw), content = content_summary (AI 요약).
  //   사용자에게 의미 있는 건 요약 → title 위치에 content, 호버 위치에 원문 공시명.
  // NEWS: 백엔드 title = 뉴스 제목, content = content_summary (또는 article).
  //   매핑 그대로. url 은 original_link 또는 link 가 full URL 로 옴.
  const isDart = s.source === 'DART';
  const summary = (s.content ?? '').trim();
  const rawUrl = (s.url ?? '').trim();
  return {
    type: s.source,
    time,
    title: isDart && summary ? summary : s.title,
    content: isDart ? (s.title ?? '') : (s.content ?? ''),
    url: rawUrl ? (isDart ? composeDartUrl(rawUrl) : rawUrl) : undefined,
  };
}

function mapApiDeal(d: ApiDeal & Record<string, unknown>): Deal {
  // 백엔드 DealDetailResponse: currentPipelineStage (한글 stage 이름)
  // DealListResponse: currentPipeline 또는 동일 키
  const stage =
    (d.currentPipelineStage as string | undefined) ??
    (d.currentPipeline as string | undefined) ??
    null;
  return {
    dealId: d.dealId,
    title: d.title,
    assignee: stage ?? '-',
    expectedAmount: (d.amount as number | null) ?? 0,
    expectedCloseDate: (d.expectedClose as string | undefined) ?? undefined,
  };
}

// ─── 고객사 목록 ──────────────────────────────────────────────────────────────

export interface AccountListFilter {
  /** GET /accounts?keyword= — 회사명/업종 키워드 검색 (백엔드 명세) */
  keyword?: string;
  /** GET /accounts?industry= — 업종 필터 (백엔드 명세) */
  industry?: string;
}

// ── 모듈 레벨 캐시 (페이지 재마운트 시 즉시 표시 후 백그라운드 refetch) ──
// customer/ 폴더에 layout.tsx 가 없어 [id] 변경 시 페이지가 unmount/mount 되는
// Next.js 동작 때문에 매번 빈 상태에서 시작하던 전환 끊김을 방지.
let accountListCache: { key: string; data: Account[] } | null = null;
const cacheKey = (f?: AccountListFilter) => `${f?.keyword ?? ''}|${f?.industry ?? ''}`;

export function useAccountList(filter?: AccountListFilter) {
  const initialKey = cacheKey(filter);
  const initial = accountListCache && accountListCache.key === initialKey ? accountListCache.data : [];
  const [accounts, setAccounts] = useState<Account[]>(initial);
  const [loading, setLoading] = useState(initial.length === 0);
  const [error, setError] = useState<string | null>(null);

  const keyword = filter?.keyword ?? '';
  const industry = filter?.industry ?? '';

  const fetch_ = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // GET /accounts?keyword=&industry= (백엔드 명세 그대로)
      const params = new URLSearchParams();
      if (keyword.trim()) params.set('keyword', keyword.trim());
      if (industry.trim()) params.set('industry', industry.trim());
      const qs = params.toString();
      let res = await fetchWithAuth(`/accounts${qs ? `?${qs}` : ''}`);
      let items: ApiAccountItem[] = [];

      if (res.ok) {
        const json = await res.json();
        items = json.data?.content ?? json.data ?? [];
      } else {
        // fallback: /accounts/searchable (GET /accounts가 500일 경우)
        const fbKw = keyword.trim() || '%';
        res = await fetchWithAuth(`/accounts/searchable?keyword=${encodeURIComponent(fbKw)}&size=100`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        items = (json.data ?? []).map((a: any) => ({
          accountId: a.accountId,
          name: a.name,
          industry: a.industry,
        }));
      }

      // mood가 없는 항목은 상세 API에서 조회
      const needsMood = items.filter((a) => !a.latestMood);
      if (needsMood.length > 0) {
        const moodResults = await Promise.allSettled(
          needsMood.map((a) =>
            fetchWithAuth(`/accounts/${a.accountId}`).then((r) => r.json()),
          ),
        );
        const moodMap = new Map<number, MoodLevel>();
        moodResults.forEach((r, i) => {
          if (r.status === 'fulfilled' && r.value.data?.latestMood) {
            moodMap.set(needsMood[i].accountId, r.value.data.latestMood);
          }
        });
        items = items.map((a) => ({
          ...a,
          latestMood: a.latestMood ?? moodMap.get(a.accountId) ?? null,
        }));
      }

      const mapped: Account[] = items.map((a) => ({
        accountId: a.accountId,
        name: a.name,
        industry: a.industry,
        temperature: toMoodLevel(a),
        pipelineStage: '',
      }));
      setAccounts(mapped);
      accountListCache = { key: cacheKey({ keyword, industry }), data: mapped };
    } catch (e) {
      console.error('[useAccountList] 고객사 목록 로드 실패:', e);
      setError('고객사 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [keyword, industry]);

  useEffect(() => {
    fetch_();
  }, [fetch_]);

  return { accounts, loading, error, refetch: fetch_ };
}

// ─── 고객사 등록 ─────────────────────────────────────────────────────────────

export interface AccountRegisterRequest {
  existingAccountId?: number;
  name?: string;
  industry?: string;
  bizNo?: string;
}

export function useRegisterAccount() {
  const [loading, setLoading] = useState(false);

  const register = useCallback(async (req: AccountRegisterRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth('/accounts', {
        method: 'POST',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      return json.data;
    } finally {
      setLoading(false);
    }
  }, []);

  return { register, loading };
}

// ─── 고객사 수정 ─────────────────────────────────────────────────────────────

export function useUpdateAccount() {
  const [loading, setLoading] = useState(false);

  const update = useCallback(async (accountId: number, req: Partial<AccountRegisterRequest>) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/accounts/${accountId}`, {
        method: 'PATCH',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return true;
    } finally {
      setLoading(false);
    }
  }, []);

  return { update, loading };
}

// ─── 고객사 삭제 ─────────────────────────────────────────────────────────────

export function useDeleteAccount() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (accountId: number) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/accounts/${accountId}`, {
        method: 'DELETE',
      });
      if (!res.ok && res.status !== 204) throw new Error(`HTTP ${res.status}`);
      return true;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { remove, loading };
}

// ─── 선택된 고객사 상세 (signals / contacts / deals / mood) ──────────────────

export interface MoodEntry {
  recordedAt: string;
  mood: MoodLevel;
  reason: string | null;
}

// accountId 별 모듈 레벨 캐시 (페이지 재마운트 시 즉시 표시용)
const detailCache: Map<string, {
  signals: Signal[];
  contacts: ContactInfo[];
  deals: Deal[];
  mood: MoodEntry[];
}> = new Map();

export function useAccountDetail(accountId: string | number | null) {
  const key = accountId !== null ? String(accountId) : '';
  const cached = key ? detailCache.get(key) : undefined;
  const [signals, setSignals] = useState<Signal[]>(cached?.signals ?? []);
  const [contacts, setContacts] = useState<ContactInfo[]>(cached?.contacts ?? []);
  const [deals, setDeals] = useState<Deal[]>(cached?.deals ?? []);
  const [mood, setMood] = useState<MoodEntry[]>(cached?.mood ?? []);
  const [loading, setLoading] = useState(!cached);

  const load = useCallback(async () => {
    if (!accountId) return;
    // 같은 instance 에서 accountId 가 변경되면 캐시된 데이터로 즉시 교체 (깜빡임 방지),
    // 캐시 없으면 이전 데이터를 유지한 채 백그라운드 로딩.
    const k = String(accountId);
    const c = detailCache.get(k);
    if (c) {
      setSignals(c.signals);
      setContacts(c.contacts);
      setDeals(c.deals);
      setMood(c.mood);
    }
    setLoading(true);

    // load 끝에서 캐시 갱신을 위한 local snapshot
    let snapSignals: Signal[] = c?.signals ?? [];
    let snapContacts: ContactInfo[] = c?.contacts ?? [];
    let snapDeals: Deal[] = c?.deals ?? [];
    let snapMood: MoodEntry[] = c?.mood ?? [];

    // 백엔드 응답 wrapper 종류가 다양 — 가능한 배열 위치를 모두 시도해서 추출.
    const extractList = <T,>(j: unknown): T[] => {
      if (!j || typeof j !== 'object') return [];
      const obj = j as Record<string, unknown>;
      // 후보 1: j.data 직접 배열
      if (Array.isArray(obj.data)) return obj.data as T[];
      // 후보 2~7: j.data.data / signals / contacts / deals / items / content
      const inner = obj.data as Record<string, unknown> | undefined;
      if (inner && typeof inner === 'object') {
        for (const key of ['data', 'signals', 'contacts', 'deals', 'items', 'content', 'results']) {
          if (Array.isArray(inner[key])) return inner[key] as T[];
        }
      }
      // 후보 8: 응답 최상위가 배열
      if (Array.isArray(j)) return j as unknown as T[];
      return [];
    };

    try {
      // 1) signals + contacts + mood history 병렬
      const [sigRes, conRes, moodRes] = await Promise.allSettled([
        fetchWithAuth(`/accounts/${accountId}/signals`).then((r) => r.json()),
        fetchWithAuth(`/accounts/${accountId}/contacts`).then((r) => r.json()),
        fetchWithAuth(`/accounts/${accountId}/mood`).then((r) => r.json()),
      ]);

      if (sigRes.status === 'fulfilled') {
        const sigs = extractList<ApiSignal>(sigRes.value);
        snapSignals = sigs.map(mapApiSignal);
        setSignals(snapSignals);
      }

      if (moodRes.status === 'fulfilled') {
        // 응답: List<AccountMoodResponse> = [{ recordedAt, mood, reason }, ...] (createdAt desc)
        const rawList = extractList<{ recordedAt: string; mood: MoodLevel; reason: string | null }>(moodRes.value);
        snapMood = rawList.map((m) => ({
          recordedAt: m.recordedAt,
          mood: m.mood,
          reason: m.reason,
        }));
        setMood(snapMood);
      }

      let accountContacts: ContactInfo[] = [];
      if (conRes.status === 'fulfilled') {
        const cons = extractList<ApiContact>(conRes.value);
        accountContacts = cons.map(mapApiContact);
        snapContacts = accountContacts;
        setContacts(accountContacts);
      }

      // 2) deals 매칭
      //    DealListResponse 에 accountId 가 없으므로 deal_contacts 를 통해 매핑한다.
      //    한 account 의 contactIds 와 각 deal 의 contacts 의 교집합으로 그 account 의 딜 판별.
      const contactIds = new Set(
        accountContacts.map((c) => c.contactId).filter((v): v is number => typeof v === 'number'),
      );
      if (contactIds.size === 0) {
        snapDeals = [];
        setDeals([]);
        return;
      }

      const listRes = await fetchWithAuth('/deals?size=200');
      if (!listRes.ok) {
        snapDeals = [];
        setDeals([]);
        return;
      }
      const listJson = await listRes.json();
      const list: { dealId: number }[] = Array.isArray(listJson?.data?.data)
        ? listJson.data.data
        : [];
      console.log('[FINT] /deals returned', {
        accountId,
        accountContactIds: Array.from(contactIds),
        totalDeals: list.length,
        dealIds: list.map((d) => d.dealId),
      });

      // 각 deal 상세 받아서 contacts 매칭
      const detailResults = await Promise.allSettled(
        list.map(async (d) => {
          const r = await fetchWithAuth(`/deals/${d.dealId}`);
          if (!r.ok) return null;
          const dj = await r.json();
          const detail = dj.data ?? dj;
          const dealContacts: { contactId: number }[] = detail?.contacts ?? [];
          const isAccountDeal = dealContacts.some((c) => contactIds.has(c.contactId));
          if (!isAccountDeal) return null;
          // 상세 응답으로 stage 정보까지 보강
          return { ...d, ...detail };
        }),
      );

      const filtered = detailResults
        .filter(
          (r): r is PromiseFulfilledResult<Record<string, unknown>> =>
            r.status === 'fulfilled' && r.value !== null,
        )
        .map((r) => r.value);

      snapDeals = (filtered as unknown as (ApiDeal & Record<string, unknown>)[]).map(mapApiDeal);
      setDeals(snapDeals);
    } finally {
      setLoading(false);
      // 새로 받은 데이터로 캐시 갱신
      detailCache.set(k, { signals: snapSignals, contacts: snapContacts, deals: snapDeals, mood: snapMood });
    }
  }, [accountId]);

  useEffect(() => { load(); }, [load]);

  // 최신 mood 와 사유 — WeatherPanel 에 사용
  const latestMood: MoodLevel | null = mood[0]?.mood ?? null;
  const latestMoodReason: string | null = mood[0]?.reason ?? null;

  return { signals, contacts, deals, mood, latestMood, latestMoodReason, loading, refetch: load };
}
