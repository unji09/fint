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

function mapApiSignal(s: ApiSignal): Signal {
  const diffMs = Date.now() - new Date(s.occurredAt).getTime();
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);
  const time =
    diffDays > 0 ? `${diffDays}일 전` : diffHours > 0 ? `${diffHours}시간 전` : '방금 전';

  return {
    type: s.source,
    time,
    content: s.content ? `${s.title} - ${s.content}` : s.title,
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

export function useAccountList() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetch_ = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // GET /accounts 시도
      let res = await fetchWithAuth('/accounts');
      let items: ApiAccountItem[] = [];

      if (res.ok) {
        const json = await res.json();
        items = json.data?.content ?? json.data ?? [];
      } else {
        // fallback: /accounts/searchable (GET /accounts가 500일 경우)
        res = await fetchWithAuth('/accounts/searchable?keyword=%25&size=100');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();
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

      setAccounts(
        items.map((a) => ({
          accountId: a.accountId,
          name: a.name,
          industry: a.industry,
          temperature: toMoodLevel(a),
          pipelineStage: '',
        })),
      );
    } catch (e) {
      console.error('[useAccountList] 고객사 목록 로드 실패:', e);
      setError('고객사 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

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

// ─── 선택된 고객사 상세 (signals / contacts / deals) ─────────────────────────

export function useAccountDetail(accountId: string | number | null) {
  const [signals, setSignals] = useState<Signal[]>([]);
  const [contacts, setContacts] = useState<ContactInfo[]>([]);
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!accountId) return;
    setSignals([]);
    setContacts([]);
    setDeals([]);
    setLoading(true);

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
      // 1) signals + contacts 병렬
      const [sigRes, conRes] = await Promise.allSettled([
        fetchWithAuth(`/accounts/${accountId}/signals`).then((r) => r.json()),
        fetchWithAuth(`/accounts/${accountId}/contacts`).then((r) => r.json()),
      ]);

      if (sigRes.status === 'fulfilled') {
        const sigs = extractList<ApiSignal>(sigRes.value);
        console.log('[FINT] /signals', { count: sigs.length, raw: sigRes.value });
        setSignals(sigs.map(mapApiSignal));
      }

      let accountContacts: ContactInfo[] = [];
      if (conRes.status === 'fulfilled') {
        const cons = extractList<ApiContact>(conRes.value);
        console.log('[FINT] /contacts', { count: cons.length, raw: conRes.value });
        accountContacts = cons.map(mapApiContact);
        setContacts(accountContacts);
      }

      // 2) deals 매칭
      //    DealListResponse 에 accountId 가 없으므로 deal_contacts 를 통해 매핑한다.
      //    한 account 의 contactIds 와 각 deal 의 contacts 의 교집합으로 그 account 의 딜 판별.
      const contactIds = new Set(
        accountContacts.map((c) => c.contactId).filter((v): v is number => typeof v === 'number'),
      );
      if (contactIds.size === 0) {
        setDeals([]);
        return;
      }

      const listRes = await fetchWithAuth('/deals?size=200');
      if (!listRes.ok) return;
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

      setDeals((filtered as unknown as (ApiDeal & Record<string, unknown>)[]).map(mapApiDeal));
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => { load(); }, [load]);

  return { signals, contacts, deals, loading, refetch: load };
}
