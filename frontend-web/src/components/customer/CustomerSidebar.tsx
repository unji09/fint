'use client';

import { useState, useEffect, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import type { Account, ContactInfo, MoodLevel } from '@/types/customer';
import WeatherIcon from '@/components/customer/WeatherIcon';

// ─── 정렬 옵션 ─────────────────────────────────────────────
type SortKey = 'recent' | 'name' | 'mood' | 'industry';
const SORT_LABELS: Record<SortKey, string> = {
  recent: '최신순',
  name: '이름순',
  mood: '분위기순',
  industry: '업종순',
};
// 분위기 좋음(무지개) → 나쁨(천둥) 순서
const MOOD_ORDER: Record<MoodLevel, number> = {
  RAINBOW: 0, SUNNY: 1, CLOUDY: 2, RAINY: 3, THUNDER: 4,
};
function sortAccounts(list: Account[], key: SortKey): Account[] {
  const arr = [...list];
  switch (key) {
    case 'name':
      return arr.sort((a, b) => a.name.localeCompare(b.name, 'ko'));
    case 'mood':
      return arr.sort((a, b) => (MOOD_ORDER[a.temperature] ?? 99) - (MOOD_ORDER[b.temperature] ?? 99));
    case 'industry':
      return arr.sort((a, b) => (a.industry ?? '').localeCompare(b.industry ?? '', 'ko'));
    case 'recent':
    default:
      return arr.sort((a, b) => {
        const ta = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const tb = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        if (ta !== tb) return tb - ta;
        return b.accountId - a.accountId;
      });
  }
}

interface CustomerSidebarProps {
  accounts: Account[];
  selectedId: number | null;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  contacts?: ContactInfo[];
  onContactSelect?: (contact: ContactInfo | null) => void;
  onAddAccount?: () => void;
  onDeleteAccount?: (accountId: number, name: string) => void;
}

export default function CustomerSidebar({
  accounts,
  selectedId,
  loading,
  error,
  onRetry,
  contacts = [],
  onContactSelect,
  onAddAccount,
  onDeleteAccount,
}: CustomerSidebarProps) {
  const router = useRouter();
  const [expandedId, setExpandedId] = useState<number | null>(selectedId);
  const [activeContact, setActiveContact] = useState<string | null>(null);

  // ── 정렬 ──
  const [sortKey, setSortKey] = useState<SortKey>('recent');
  const [sortOpen, setSortOpen] = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);
  const sortedAccounts = useMemo(() => sortAccounts(accounts, sortKey), [accounts, sortKey]);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (sortRef.current && !sortRef.current.contains(e.target as Node)) setSortOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  // selectedId 변경 시 expandedId 동기화
  useEffect(() => {
    if (selectedId !== null) setExpandedId(selectedId);
  }, [selectedId]);

  const handleAccountClick = (accountId: number) => {
    router.push(`/customer/${accountId}`);
    setExpandedId((prev) => (prev === accountId ? null : accountId));
    setActiveContact(null);
    onContactSelect?.(null);
  };

  const handleContactClick = (e: React.MouseEvent, contact: ContactInfo) => {
    e.stopPropagation();
    const key = contact.name;
    if (activeContact === key) {
      setActiveContact(null);
      onContactSelect?.(null);
    } else {
      setActiveContact(key);
      onContactSelect?.(contact);
    }
  };

  return (
    <aside
      style={{
        width: 300,
        flexShrink: 0,
        background: 'white',
        borderRight: '1px solid #e2eaf0',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div
        style={{
          height: 56,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          borderBottom: '1px solid rgba(6,182,212,0.8)',
          padding: '0 14px',
          flexShrink: 0,
        }}
      >
        <div ref={sortRef} style={{ position: 'relative' }}>
          <button
            onClick={() => setSortOpen((v) => !v)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: '5px 8px',
              border: 'none',
              background: 'transparent',
              cursor: 'pointer',
              borderRadius: 6,
              fontFamily: 'Pretendard,sans-serif',
              fontSize: 14,
              color: '#475569',
              fontWeight: 500,
            }}
            onMouseOver={(e) => { e.currentTarget.style.backgroundColor = '#F1F5F9'; }}
            onMouseOut={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; }}
          >
            {SORT_LABELS[sortKey]}
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" style={{ transform: sortOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}>
              <path d="M6 9l6 6 6-6" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>
          {sortOpen && (
            <div
              style={{
                position: 'absolute',
                top: 'calc(100% + 4px)',
                left: 0,
                zIndex: 100,
                backgroundColor: '#fff',
                border: '1px solid #e2e8f0',
                borderRadius: 8,
                boxShadow: '0 6px 18px rgba(0, 0, 0, 0.08)',
                minWidth: 130,
                overflow: 'hidden',
              }}
            >
              {(Object.keys(SORT_LABELS) as SortKey[]).map((k) => {
                const active = k === sortKey;
                return (
                  <button
                    key={k}
                    onClick={() => { setSortKey(k); setSortOpen(false); }}
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      padding: '8px 12px',
                      border: 'none',
                      backgroundColor: active ? '#F2FCFF' : 'transparent',
                      color: active ? '#06B6D4' : '#1E293B',
                      fontSize: 13,
                      fontWeight: active ? 600 : 400,
                      cursor: 'pointer',
                      fontFamily: 'Pretendard,sans-serif',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                    onMouseOver={(e) => { if (!active) e.currentTarget.style.backgroundColor = '#F8FAFC'; }}
                    onMouseOut={(e) => { if (!active) e.currentTarget.style.backgroundColor = 'transparent'; }}
                  >
                    {SORT_LABELS[k]}
                    {active && (
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                        <path d="M5 13l4 4L19 7" stroke="#06B6D4" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>
        <button
          onClick={onAddAccount}
          style={{
            background: '#f2fcff',
            border: '1px solid #dbeafe',
            borderRadius: 9,
            padding: '3px 10px',
            fontFamily: 'Pretendard,sans-serif',
            fontSize: 12,
            color: '#06b6d4',
            cursor: 'pointer',
          }}
        >
          + 고객사 추가
        </button>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '8px 8px' }}>
        {loading
          ? Array.from({ length: 5 }).map((_, i) => (
              <div
                key={i}
                style={{ height: 60, marginBottom: 7, background: '#f1f5f9', borderRadius: 6 }}
              />
            ))
          : error ? (
              <div style={{ padding: '20px 12px', textAlign: 'center' }}>
                <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#ef4444', margin: 0 }}>{error}</p>
                {onRetry && (
                  <button onClick={onRetry} style={{ marginTop: 8, fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#06b6d4', background: 'none', border: '1px solid #06b6d4', borderRadius: 6, padding: '4px 12px', cursor: 'pointer' }}>
                    다시 시도
                  </button>
                )}
              </div>
            )
          : sortedAccounts.length === 0 ? (
              <div style={{ padding: '20px 12px', textAlign: 'center' }}>
                <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#94a3b8', margin: 0 }}>
                  등록된 고객사가 없습니다.
                </p>
              </div>
            )
          : sortedAccounts.map((acc) => {
              const isSelected = acc.accountId === selectedId;
              const isExpanded = acc.accountId === expandedId;
              // 선택된 고객사만 API 담당자 데이터 사용, 나머지는 빈 배열
              const accountContacts = isSelected ? contacts : [];

              return (
                <div key={acc.accountId} style={{ marginBottom: 7 }}>
                  <button
                    onClick={() => handleAccountClick(acc.accountId)}
                    onMouseEnter={(e) => {
                      if (!isSelected) e.currentTarget.style.background = '#f8fafc';
                      // hover 시 다음 페이지 prefetch — 클릭 시 즉시 전환
                      router.prefetch(`/customer/${acc.accountId}`);
                    }}
                    onMouseOut={(e) => { if (!isSelected) e.currentTarget.style.background = 'transparent'; }}
                    style={{
                      width: '100%',
                      background: isSelected ? '#f2fcff' : 'transparent',
                      border: 'none',
                      borderLeft: `3px solid ${isSelected ? '#00bfff' : 'transparent'}`,
                      borderRadius: isExpanded ? '0 6px 0 0' : '0 6px 6px 0',
                      padding: '10px 12px 10px 13px',
                      display: 'flex',
                      alignItems: 'center',
                      cursor: 'pointer',
                      textAlign: 'left',
                      transition: 'background-color 0.18s ease, border-color 0.18s ease',
                    }}
                  >
                    <WeatherIcon mood={acc.temperature} size="sm" />
                    <div style={{ flex: 1, paddingLeft: 10, minWidth: 0 }}>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          marginBottom: 3,
                        }}
                      >
                        <span
                          style={{
                            fontFamily: 'Pretendard,sans-serif',
                            fontSize: 15,
                            color: isSelected ? '#06b6d4' : '#1e293b',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {acc.name}
                        </span>
                        {acc.pipelineStage && (
                          <span
                            style={{
                              background: '#fee2e2',
                              borderRadius: 4,
                              padding: '1px 5px',
                              fontFamily: 'Pretendard,sans-serif',
                              fontSize: 10,
                              color: '#dc2626',
                              flexShrink: 0,
                              marginLeft: 4,
                            }}
                          >
                            {acc.pipelineStage}
                          </span>
                        )}
                      </div>
                      <span
                        style={{
                          fontFamily: 'Pretendard,sans-serif',
                          fontSize: 12,
                          fontWeight: 300,
                          color: '#64748b',
                        }}
                      >
                        {acc.industry}
                      </span>
                    </div>
                    <svg
                      width="12"
                      height="7"
                      viewBox="0 0 12 7"
                      fill="none"
                      style={{
                        flexShrink: 0,
                        marginLeft: 6,
                        transform: isExpanded ? 'rotate(180deg)' : 'none',
                        transition: 'transform 0.2s',
                      }}
                    >
                      <path
                        d="M1 1l5 5 5-5"
                        stroke="#94a3b8"
                        strokeWidth="1.5"
                        strokeLinecap="round"
                      />
                    </svg>
                  </button>

                  {/* 담당자 목록 + 액션 — 선택된 고객사만 표시 */}
                  {isExpanded && isSelected && (
                    <div
                      style={{
                        background: '#f2fcff',
                        borderLeft: '3px solid #00bfff',
                        borderRadius: '0 0 6px 6px',
                        padding: '4px 12px 8px 13px',
                      }}
                    >
                      {accountContacts.map((c, i) => {
                        const isActive = activeContact === c.name;
                        return (
                          <button
                            key={c.contactId ?? i}
                            onClick={(e) => handleContactClick(e, c)}
                            style={{
                              width: '100%',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 10,
                              padding: '7px 0',
                              background: 'none',
                              border: 'none',
                              cursor: 'pointer',
                              borderBottom:
                                i < accountContacts.length - 1
                                  ? '1px solid rgba(226,232,240,0.5)'
                                  : 'none',
                              borderLeft: isActive ? '2px solid #06b6d4' : '2px solid transparent',
                              paddingLeft: isActive ? 6 : 0,
                              transition: 'border-color 0.15s',
                            }}
                          >
                            <div
                              style={{
                                width: 28,
                                height: 28,
                                borderRadius: '50%',
                                background: c.color,
                                flexShrink: 0,
                                opacity: isActive ? 1 : 0.8,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontSize: 12,
                                fontWeight: 700,
                                color: '#fff',
                                fontFamily: 'Pretendard,sans-serif',
                              }}
                            >
                              {c.name.charAt(0)}
                            </div>
                            <div style={{ textAlign: 'left' }}>
                              <div
                                style={{
                                  fontFamily: 'Pretendard,sans-serif',
                                  fontSize: 13,
                                  color: isActive ? '#06b6d4' : '#1e293b',
                                  fontWeight: isActive ? 600 : 400,
                                }}
                              >
                                {c.name}
                              </div>
                              <div
                                style={{
                                  fontFamily: 'Pretendard,sans-serif',
                                  fontSize: 11,
                                  fontWeight: 300,
                                  color: '#64748b',
                                }}
                              >
                                {c.role}
                              </div>
                            </div>
                          </button>
                        );
                      })}
                      {/* 고객사 관리 */}
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 6, paddingTop: 6, borderTop: '1px solid rgba(0,0,0,0.05)' }}>
                        {onDeleteAccount && (
                          <button onClick={(e) => { e.stopPropagation(); onDeleteAccount(acc.accountId, acc.name); }}
                            style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 10, color: '#cbd5e1', cursor: 'pointer', background: 'none', border: 'none', padding: 0 }}>
                            고객사 삭제
                          </button>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
      </div>
    </aside>
  );
}
