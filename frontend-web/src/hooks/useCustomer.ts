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
  Signal,
  TemperatureLevel,
} from '@/types/customer';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

function authHeader(): Record<string, string> {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : {};
}

// ─── 매핑 헬퍼 ───────────────────────────────────────────────────────────────

const CONTACT_COLORS = ['#7c3aed', '#0891b2', '#059669', '#dc2626', '#d97706'];

function toTemperatureLevel(temp: number | null): TemperatureLevel {
  if (temp === null || temp === undefined) return 'COOL';
  if (temp > 75) return 'HOT';
  if (temp > 50) return 'WARM';
  if (temp > 25) return 'COOL';
  if (temp > 10) return 'COLD';
  return 'STORM';
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

function mapApiDeal(d: ApiDeal): Deal {
  return {
    dealId: d.dealId,
    title: d.title,
    assignee: d.stage ?? '-',
    expectedAmount: d.amount ?? 0,
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
      const res = await fetch(`${API_BASE}/accounts`, { headers: authHeader() });
      const json = await res.json();
      const items: ApiAccountItem[] = json.data?.content ?? json.data ?? [];
      setAccounts(
        items.map((a) => ({
          accountId: a.accountId,
          name: a.name,
          industry: a.industry,
          temperature: toTemperatureLevel(a.temperature),
          pipelineStage: '',
        })),
      );
    } catch {
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

// ─── 선택된 고객사 상세 (signals / contacts / deals) ─────────────────────────

export function useAccountDetail(accountId: string | number | null) {
  const [signals, setSignals] = useState<Signal[]>([]);
  const [contacts, setContacts] = useState<ContactInfo[]>([]);
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!accountId) return;

    setSignals([]);
    setContacts([]);
    setDeals([]);
    setLoading(true);

    const headers = authHeader();

    Promise.allSettled([
      fetch(`${API_BASE}/accounts/${accountId}/signals`, { headers })
        .then((r) => r.json())
        .then((j) => setSignals((j.data ?? []).map(mapApiSignal))),

      fetch(`${API_BASE}/accounts/${accountId}/contacts`, { headers })
        .then((r) => r.json())
        .then((j) => setContacts((j.data ?? []).map(mapApiContact))),

      fetch(`${API_BASE}/accounts/${accountId}`, { headers })
        .then((r) => r.json())
        .then((j) => setDeals((j.data?.deals ?? []).map(mapApiDeal))),
    ]).finally(() => setLoading(false));
  }, [accountId]);

  return { signals, contacts, deals, loading };
}
