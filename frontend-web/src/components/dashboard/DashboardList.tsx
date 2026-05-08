'use client';

import { useRouter } from 'next/navigation';
import type { Dashboard } from '@/types/dashboard';

interface DashboardListProps {
  dashboards: Dashboard[];
  loading: boolean;
  onCreateNew: () => void;
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

/** 공통 카드 스타일 — 고정 width, height는 부모 높이에 맞춤 */
const CARD_BASE: React.CSSProperties = {
  background: '#fbfbfb',
  border: '1px solid #e0e0e0',
  borderRadius: 10,
  width: 320,
  flexShrink: 0,
  alignSelf: 'stretch' /* 부모 flex row의 높이를 꽉 채움 */,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
  padding: 0,
  transition: 'border-color 0.2s, background 0.2s',
};

function NewCard({ onClick }: { onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{ ...CARD_BASE, gap: 6 }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = '#06b6d4';
        e.currentTarget.style.background = 'rgba(6,182,212,0.03)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = '#e0e0e0';
        e.currentTarget.style.background = '#fbfbfb';
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

function DashboardCard({ dashboard }: { dashboard: Dashboard }) {
  const router = useRouter();
  return (
    <button
      onClick={() => router.push(`/dashboard/${dashboard.dashboardId}`)}
      style={{ ...CARD_BASE, gap: 4 }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = '#06b6d4';
        e.currentTarget.style.background = 'rgba(6,182,212,0.03)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = '#e0e0e0';
        e.currentTarget.style.background = '#fbfbfb';
      }}
    >
      <span
        style={{
          fontFamily: 'Pretendard, sans-serif',
          fontWeight: 600,
          fontSize: 16,
          color: '#1d1a24',
          letterSpacing: '0.13px',
          whiteSpace: 'nowrap',
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
        {formatRelativeTime(dashboard.updatedAt)}
      </span>
    </button>
  );
}

export default function DashboardList({ dashboards, loading, onCreateNew }: DashboardListProps) {
  return (
    /* height:100% → 부모 flex:1 영역을 꽉 채움 */
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, height: '100%' }}>
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

      {/* 점선 컨테이너 — flex:1 로 남은 높이 전부 차지 */}
      <div
        style={{
          flex: 1,
          border: '2px dashed #ccc3d8',
          borderRadius: 12,
          background: 'white',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '16px 24px',
          gap: 32,
          minHeight: 0,
        }}
      >
        {loading ? (
          <>
            <div
              style={{ ...CARD_BASE, background: '#f3f4f6', cursor: 'default', height: '100%' }}
            />
            <div
              style={{ ...CARD_BASE, background: '#f3f4f6', cursor: 'default', height: '100%' }}
            />
          </>
        ) : (
          <>
            <NewCard onClick={onCreateNew} />
            {dashboards.map((db) => (
              <DashboardCard key={db.dashboardId} dashboard={db} />
            ))}
          </>
        )}
      </div>
    </div>
  );
}
