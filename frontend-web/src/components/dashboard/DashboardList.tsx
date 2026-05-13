'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Dashboard } from '@/types/dashboard';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

function useThumbnailUrl(thumbnailKey: string | null) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!thumbnailKey) return;
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
    fetch(`${API_BASE}/files/presigned-download`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ fileKey: thumbnailKey, expiresIn: 300 }),
    })
      .then((r) => r.json())
      .then((j) => setUrl(j.data?.downloadUrl ?? null))
      .catch(() => setUrl(null));
  }, [thumbnailKey]);

  return url;
}

interface DashboardListProps {
  dashboards: Dashboard[];
  loading: boolean;
  onCreateNew: () => void;
  onDelete?: (dashboardId: number, title: string) => void;
  deleting?: boolean;
}

function formatRelativeTime(isoString?: string): string {
  if (!isoString) return '';
  const diff = Date.now() - new Date(isoString).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 60) return `${minutes}분 전 편집함`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전 편집함`;
  return `${Math.floor(hours / 24)}일 전 편집함`;
}

/** 공통 카드 스타일 — 떠 있는 paper 느낌 (다층 그림자) */
const CARD_BASE: React.CSSProperties = {
  background: '#ffffff',
  border: '1px solid rgba(226,232,240,0.8)',
  borderRadius: 12,
  width: '100%',
  height: '100%',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
  padding: 0,
  boxShadow:
    '0 1px 0 rgba(255,255,255,0.9) inset, ' +
    '0 1px 2px rgba(15,23,42,0.04), ' +
    '0 4px 12px rgba(15,23,42,0.06)',
  transition: 'border-color 0.15s, background 0.15s, transform 0.15s, box-shadow 0.15s',
};

function NewCard({ onClick }: { onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{ ...CARD_BASE, gap: 6 }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = '#06b6d4';
        e.currentTarget.style.background = 'rgba(6,182,212,0.04)';
        e.currentTarget.style.transform = 'translateY(-2px)';
        e.currentTarget.style.boxShadow =
          '0 1px 0 rgba(255,255,255,0.9) inset, 0 2px 4px rgba(15,23,42,0.06), 0 12px 28px rgba(6,182,212,0.16)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = '#e0e0e0';
        e.currentTarget.style.background = '#fbfbfb';
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow =
          '0 1px 0 rgba(255,255,255,0.9) inset, 0 1px 2px rgba(15,23,42,0.04), 0 4px 12px rgba(15,23,42,0.06)';
      }}
    >
      <svg width="44" height="44" viewBox="0 0 50 50" fill="none">
        <path
          d="M25 10.417V39.583M10.417 25H39.583"
          stroke="#d7d7d7"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      </svg>
      <span
        style={{
          fontFamily: 'Pretendard, sans-serif',
          fontWeight: 600,
          fontSize: 13,
          color: '#d7d7d7',
          letterSpacing: '0.13px',
        }}
      >
        새 대시보드 만들기
      </span>
    </button>
  );
}

function DashboardCard({
  dashboard,
  onDelete,
  deleting,
}: {
  dashboard: Dashboard;
  onDelete?: (dashboardId: number, title: string) => void;
  deleting?: boolean;
}) {
  const router = useRouter();
  const thumbnailUrl = useThumbnailUrl(dashboard.thumbnailKey);

  return (
    <div
      onMouseEnter={(e) => {
        router.prefetch(`/dashboard/${dashboard.dashboardId}`);
        const card = e.currentTarget.firstElementChild as HTMLElement | null;
        if (card) {
          card.style.borderColor = '#06b6d4';
          card.style.background = 'rgba(6,182,212,0.04)';
          card.style.transform = 'translateY(-2px)';
          card.style.boxShadow = '0 6px 16px rgba(6,182,212,0.10)';
        }
        const del = e.currentTarget.querySelector<HTMLButtonElement>('[data-delete-btn]');
        if (del) del.style.opacity = '1';
      }}
      onMouseLeave={(e) => {
        const card = e.currentTarget.firstElementChild as HTMLElement | null;
        if (card) {
          card.style.borderColor = '#e0e0e0';
          card.style.background = '#ffffff';
          card.style.transform = 'translateY(0)';
          card.style.boxShadow =
            '0 1px 0 rgba(255,255,255,0.9) inset, 0 1px 2px rgba(15,23,42,0.04), 0 4px 12px rgba(15,23,42,0.06)';
        }
        const del = e.currentTarget.querySelector<HTMLButtonElement>('[data-delete-btn]');
        if (del) del.style.opacity = '0';
      }}
      style={{ position: 'relative' }}
    >
      <button
        onClick={() => {
          const target = `/dashboard/${dashboard.dashboardId}`;
          console.log('[FINT] DashboardCard click → push:', target);
          router.push(target);
        }}
        style={{ ...CARD_BASE, gap: 0, justifyContent: 'flex-start', overflow: 'hidden' }}
      >
        <div
          style={{
            width: '100%',
            flex: 1,
            minHeight: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: '#f3f4f6',
            borderRadius: '11px 11px 0 0',
            overflow: 'hidden',
          }}
        >
          {thumbnailUrl ? (
            <img
              src={thumbnailUrl}
              alt={dashboard.title}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="3" width="18" height="18" rx="2" stroke="#d0d5dd" strokeWidth="1.5" />
              <path d="M3 16l5-5 4 4 3-3 6 6" stroke="#d0d5dd" strokeWidth="1.5" strokeLinejoin="round" />
              <circle cx="15.5" cy="8.5" r="1.5" fill="#d0d5dd" />
            </svg>
          )}
        </div>
        <div
          style={{
            width: '100%',
            padding: '10px 16px',
            flexShrink: 0,
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          <span
            style={{
              fontFamily: 'Pretendard, sans-serif',
              fontWeight: 600,
              fontSize: 15,
              color: '#1d1a24',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              textAlign: 'left',
            }}
          >
            {dashboard.title}
          </span>
          <span
            style={{
              fontFamily: 'Pretendard, sans-serif',
              fontWeight: 500,
              fontSize: 11,
              color: '#6e7590',
              whiteSpace: 'nowrap',
              textAlign: 'left',
            }}
          >
            {formatRelativeTime(dashboard.lastAccessedAt ?? undefined)}
          </span>
        </div>
      </button>

      {onDelete && (
        <button
          data-delete-btn
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onDelete(dashboard.dashboardId, dashboard.title);
          }}
          disabled={deleting}
          aria-label={`${dashboard.title} 삭제`}
          title="이 대시보드 삭제"
          style={{
            position: 'absolute',
            top: 10,
            right: 10,
            width: 26,
            height: 26,
            borderRadius: 8,
            border: '1px solid #e5e7eb',
            background: '#fff',
            color: '#94a3b8',
            cursor: deleting ? 'wait' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 0,
            opacity: 0,
            transition: 'opacity 0.12s, color 0.12s, border-color 0.12s, background-color 0.12s',
          }}
          onMouseOver={(e) => {
            e.currentTarget.style.color = '#ef4444';
            e.currentTarget.style.borderColor = '#fecaca';
            e.currentTarget.style.background = '#fff5f5';
          }}
          onMouseOut={(e) => {
            e.currentTarget.style.color = '#94a3b8';
            e.currentTarget.style.borderColor = '#e5e7eb';
            e.currentTarget.style.background = '#fff';
          }}
        >
          <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
            <path d="M5 3V2a1 1 0 011-1h2a1 1 0 011 1v1m-7 0h10M4 3v8a2 2 0 002 2h2a2 2 0 002-2V3"
              stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      )}
    </div>
  );
}

export default function DashboardList({ dashboards, loading, onCreateNew, onDelete, deleting }: DashboardListProps) {
  return (
    /* height 자동 → 카드 행수에 맞게 컨텐츠 기반 높이 */
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <h2
        style={{
          fontFamily: 'Pretendard, sans-serif',
          fontWeight: 600,
          fontSize: 18,
          lineHeight: '26px',
          letterSpacing: '-0.2px',
          color: '#1d1a24',
          margin: 0,
          flexShrink: 0,
        }}
      >
        대시보드 목록
      </h2>

      {/* 점선 컨테이너 — 카드 행수에 맞게 자라되 maxHeight 안에서 세로 스크롤 */}
      <div
        style={{
          border: '2px dashed #ccc3d8',
          borderRadius: 12,
          background: 'white',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gridAutoRows: '200px',
          alignContent: 'flex-start',
          padding: '20px 24px',
          gap: 16,
          maxHeight: 440,
          overflowY: 'auto',
        }}
      >
        {loading ? (
          <>
            <div style={{ ...CARD_BASE, background: '#f3f4f6', cursor: 'default' }} />
            <div style={{ ...CARD_BASE, background: '#f3f4f6', cursor: 'default' }} />
          </>
        ) : (
          <>
            <NewCard onClick={onCreateNew} />
            {dashboards.map((db) => (
              <DashboardCard key={db.dashboardId} dashboard={db} onDelete={onDelete} deleting={deleting} />
            ))}
          </>
        )}
      </div>
    </div>
  );
}
