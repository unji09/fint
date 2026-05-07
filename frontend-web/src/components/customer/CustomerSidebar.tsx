'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Account, ContactInfo, TemperatureLevel } from '@/types/customer';

function TempIcon({ level }: { level: TemperatureLevel }) {
  const icons: Record<TemperatureLevel, string> = {
    HOT: '🌈',
    WARM: '☀️',
    COOL: '☁️',
    COLD: '🌧️',
    STORM: '⛈️',
  };
  return (
    <div
      style={{
        width: 34,
        height: 34,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 20,
        flexShrink: 0,
      }}
    >
      {icons[level]}
    </div>
  );
}

interface CustomerSidebarProps {
  accounts: Account[];
  selectedId: number | null;
  loading?: boolean;
  /** 현재 선택된 고객사의 담당자 목록 (API에서 fetch) */
  contacts?: ContactInfo[];
  onContactSelect?: (contact: ContactInfo | null) => void;
}

export default function CustomerSidebar({
  accounts,
  selectedId,
  loading,
  contacts = [],
  onContactSelect,
}: CustomerSidebarProps) {
  const router = useRouter();
  const [expandedId, setExpandedId] = useState<number | null>(selectedId);
  const [activeContact, setActiveContact] = useState<string | null>(null);

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
          height: 62,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderBottom: '2px solid rgba(6,182,212,0.8)',
          padding: '0 20px',
          gap: 32,
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 15, color: '#64748b' }}>
            최신순
          </span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M6 9l6 6 6-6" stroke="#64748b" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </div>
        <button
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
          : accounts.map((acc) => {
              const isSelected = acc.accountId === selectedId;
              const isExpanded = acc.accountId === expandedId;
              // 선택된 고객사만 API 담당자 데이터 사용, 나머지는 빈 배열
              const accountContacts = isSelected ? contacts : [];

              return (
                <div key={acc.accountId} style={{ marginBottom: 7 }}>
                  <button
                    onClick={() => handleAccountClick(acc.accountId)}
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
                    }}
                  >
                    <TempIcon level={acc.temperature} />
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

                  {/* 담당자 목록 — 선택된 고객사만 표시 */}
                  {isExpanded && accountContacts.length > 0 && (
                    <div
                      style={{
                        background: isSelected ? '#f2fcff' : '#f8fafc',
                        borderLeft: `3px solid ${isSelected ? '#00bfff' : 'transparent'}`,
                        borderRadius: '0 0 6px 6px',
                        padding: '4px 12px 10px 13px',
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
                              transition: 'all 0.15s',
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
                              }}
                            />
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
                    </div>
                  )}
                </div>
              );
            })}
      </div>
    </aside>
  );
}
