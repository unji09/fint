'use client';

import { useRouter } from 'next/navigation';
import type { Dashboard } from '@/types/dashboard';

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
          card.style.background = '#fbfbfb';
          card.style.transform = 'translateY(0)';
          card.style.boxShadow = 'none';
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
        style={{ ...CARD_BASE, gap: 6 }}
      >
        <span
          style={{
            fontFamily: 'Pretendard, sans-serif',
            fontWeight: 600,
            fontSize: 17,
            color: '#1d1a24',
            letterSpacing: '0.13px',
            whiteSpace: 'nowrap',
            maxWidth: 220,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {dashboard.title}
        </span>
        <span
          style={{
            fontFamily: 'Pretendard, sans-serif',
            fontWeight: 500,
            fontSize: 12,
            color: '#6e7590',
            letterSpacing: '0.13px',
            whiteSpace: 'nowrap',
          }}
        >
          {formatRelativeTime(dashboard.lastAccessedAt ?? undefined)}
        </span>
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
          gridAutoRows: '168px',
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
