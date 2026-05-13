'use client';
// src/components/common/NotificationPanel.tsx
//
// 우측 슬라이드 오버 알림 패널.
// 기존 GNB 의 320px 드롭다운을 대체.
//
// 데이터 매핑 정책:
//   - 백엔드 GET /notifications 응답은 현재 { notificationId, message, type, createdAt } 로 단순.
//   - 카드의 풍부한 필드 (accountName / signal / recommendation / successRate 등) 는 옵셔널.
//     백엔드가 확장하면 자동으로 그대로 반영됨.
//
// 시간 그룹: "방금 전" (5분 이내) / "오늘" / "이번 주" / "이전".

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { fetchWithAuth } from '@/hooks/useAuth';

const F = "'Pretendard', -apple-system, sans-serif";

/**
 * 백엔드 NotificationItemResponse 와 1:1 매핑.
 *   record NotificationItemResponse(
 *     Long notificationId, String title, String category,
 *     String signalSummary, String signalTypeBadge,
 *     String pipelineStage, String accountName,
 *     boolean isRead, OffsetDateTime createdAt
 *   )
 *
 * 명세서 그림의 successRate / recommendation / accountId 등은 백엔드 미구현이라
 * 옵셔널로만 두고 응답에 들어오면 자동 표시.
 */
export interface NotificationItem {
  notificationId: number;
  title: string;
  category: string | null;          // activityType (MEETING / CALL / EMAIL / MEMO)
  signalSummary: string | null;     // 신호 요약 본문
  signalTypeBadge: string | null;   // News / DART / AI 분석
  pipelineStage: string | null;     // 파이프라인 단계명
  accountName: string | null;
  isRead: boolean;
  createdAt: string;

  // ── 미래 확장용 (백엔드 미구현, 응답에 없으면 표시 안 됨) ──
  accountId?: number;
  successRate?: number;
  recommendation?: { title?: string; sub?: string; predictedDelta?: number };
}

interface Props {
  open: boolean;
  onClose: () => void;
  notifications: NotificationItem[];
  onChanged?: () => void;
}

// ── 시간 그룹 분류 ──────────────────────────────────────────
type Bucket = '방금 전' | '오늘' | '이번 주' | '이전';

function classifyBucket(iso: string): Bucket {
  const now = Date.now();
  const t = new Date(iso).getTime();
  const diff = now - t;
  if (diff < 5 * 60_000) return '방금 전';
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  if (t >= today.getTime()) return '오늘';
  if (diff < 7 * 24 * 60 * 60_000) return '이번 주';
  return '이전';
}

const BUCKET_ORDER: Bucket[] = ['방금 전', '오늘', '이번 주', '이전'];
const BUCKET_COLOR: Record<Bucket, string> = {
  '방금 전': '#EF4444',
  오늘: '#737880',
  '이번 주': '#737880',
  이전: '#9CA193',
};

// ── 신호 타입 뱃지 색상 ─────────────────────────────────────
function signalBadge(type?: string): { label: string; bg: string; color: string } | null {
  if (!type) return null;
  const t = type.toLowerCase();
  if (t.includes('news') || t.includes('뉴스')) return { label: 'News', bg: '#FFE4E1', color: '#D85A30' };
  if (t.includes('dart') || t.includes('공시')) return { label: 'DART', bg: '#FEF3C7', color: '#B45309' };
  if (t.includes('ai') || t.includes('분석')) return { label: 'AI 분석', bg: '#E0E7FF', color: '#5B5BD6' };
  return { label: type, bg: '#F0F0EE', color: '#737880' };
}

export default function NotificationPanel({ open, onClose, notifications, onChanged }: Props) {
  const router = useRouter();
  const panelRef = useRef<HTMLDivElement>(null);

  // ESC 닫기
  useEffect(() => {
    if (!open) return;
    const h = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, onClose]);

  // 패널 열림 시 body scroll lock
  useEffect(() => {
    if (!open) return;
    const original = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = original;
    };
  }, [open]);

  if (!open) return null;

  // 시간 그룹화
  const grouped: Record<Bucket, NotificationItem[]> = {
    '방금 전': [],
    오늘: [],
    '이번 주': [],
    이전: [],
  };
  notifications.forEach((n) => {
    grouped[classifyBucket(n.createdAt)].push(n);
  });

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  const handleMarkAllRead = async () => {
    try {
      const res = await fetchWithAuth('/notifications/read-all', { method: 'PATCH' });
      if (res.ok || res.status === 204) onChanged?.();
    } catch {
      /* ignore */
    }
  };

  const handleClick = async (n: NotificationItem) => {
    // 읽음 처리
    if (!n.isRead) {
      try {
        await fetchWithAuth(`/notifications/${n.notificationId}/read`, { method: 'PATCH' });
      } catch {
        /* ignore */
      }
    }
    // 관련 리소스로 점프 — accountId 가 응답에 들어오면 사용, 없으면 /customer 목록으로
    if (n.accountId) router.push(`/customer?accountId=${n.accountId}`);
    else if (n.accountName) router.push('/customer');
    onChanged?.();
    onClose();
  };

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.32)',
          zIndex: 999,
          animation: 'noti-backdrop-in 0.18s ease-out',
        }}
      />

      {/* Panel */}
      <aside
        ref={panelRef}
        style={{
          position: 'fixed',
          top: 0,
          right: 0,
          bottom: 0,
          width: 420,
          maxWidth: '100vw',
          backgroundColor: '#fff',
          boxShadow: '-12px 0 32px rgba(0, 0, 0, 0.08)',
          zIndex: 1000,
          display: 'flex',
          flexDirection: 'column',
          fontFamily: F,
          animation: 'noti-panel-in 0.22s cubic-bezier(0.16, 1, 0.3, 1)',
        }}
      >
        <style>{`
          @keyframes noti-backdrop-in { from { opacity: 0; } to { opacity: 1; } }
          @keyframes noti-panel-in {
            from { transform: translateX(24px); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
          }
        `}</style>

        {/* 헤더 */}
        <div
          style={{
            padding: '20px 24px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #F1F2EC',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#1F2126' }}>알림</h2>
            {unreadCount > 0 && (
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  color: '#fff',
                  backgroundColor: '#EF4444',
                  padding: '2px 8px',
                  borderRadius: 999,
                  minWidth: 22,
                  textAlign: 'center',
                  lineHeight: '14px',
                }}
              >
                {unreadCount}
              </span>
            )}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              onClick={handleMarkAllRead}
              disabled={unreadCount === 0}
              style={{
                border: 'none',
                background: 'none',
                color: unreadCount === 0 ? '#CBD5E1' : '#475569',
                fontSize: 12,
                fontWeight: 500,
                cursor: unreadCount === 0 ? 'default' : 'pointer',
                padding: 0,
                fontFamily: F,
              }}
            >
              모두 읽음
            </button>
            <button
              onClick={onClose}
              aria-label="닫기"
              style={{
                border: 'none',
                background: 'none',
                cursor: 'pointer',
                fontSize: 18,
                color: '#9CA193',
                padding: 0,
                width: 24,
                height: 24,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              ✕
            </button>
          </div>
        </div>

        {/* 본문 */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '8px 0 32px' }}>
          {notifications.length === 0 ? (
            <div style={{ padding: '48px 24px', textAlign: 'center', color: '#9CA193', fontSize: 13 }}>
              새로운 알림이 없어요.
            </div>
          ) : (
            BUCKET_ORDER.map((bucket) => {
              const items = grouped[bucket];
              if (items.length === 0) return null;
              return (
                <section key={bucket} style={{ marginTop: 16 }}>
                  <div
                    style={{
                      padding: '0 24px 8px',
                      fontSize: 11,
                      fontWeight: 600,
                      color: BUCKET_COLOR[bucket],
                      letterSpacing: '0.02em',
                    }}
                  >
                    {bucket}
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '0 16px' }}>
                    {items.map((n) => (
                      <NotificationCard key={n.notificationId} n={n} onClick={() => handleClick(n)} />
                    ))}
                  </div>
                </section>
              );
            })
          )}
        </div>
      </aside>
    </>
  );
}

// ─── 알림 카드 ───────────────────────────────────────────────
function NotificationCard({ n, onClick }: { n: NotificationItem; onClick: () => void }) {
  const badge = signalBadge(n.signalTypeBadge ?? undefined);
  const hasAccount = !!n.accountName;
  const hasSignal = !!n.signalSummary || !!badge;
  const hasRecommendation = !!n.recommendation?.title;

  return (
    <button
      onClick={onClick}
      style={{
        width: '100%',
        textAlign: 'left',
        backgroundColor: n.isRead ? '#FAFAF7' : '#fff',
        border: '1px solid #ECEDE5',
        borderRadius: 10,
        padding: '14px 16px',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        transition: 'background-color 0.12s, border-color 0.12s',
        fontFamily: F,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = '#D6D9CB';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = '#ECEDE5';
      }}
    >
      {/* 제목 */}
      <div style={{ fontSize: 14, fontWeight: 600, color: '#1F2126', lineHeight: 1.45, letterSpacing: '-0.005em' }}>
        {n.title}
      </div>

      {/* 회사명 · (성공 가능성) · 파이프라인 단계 */}
      {(hasAccount || n.pipelineStage) && (
        <div style={{ fontSize: 12, color: '#737880', display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          {hasAccount && <span>{n.accountName}</span>}
          {typeof n.successRate === 'number' && (
            <>
              <span aria-hidden style={{ color: '#CBD5E1' }}>·</span>
              <span style={{ fontWeight: 500 }}>{n.successRate}% 성공 가능성</span>
            </>
          )}
          {n.pipelineStage && (
            <>
              {hasAccount && <span aria-hidden style={{ color: '#CBD5E1' }}>·</span>}
              <span style={{ fontSize: 11, color: '#534AB7', backgroundColor: '#F0EDF7', padding: '2px 7px', borderRadius: 3, fontWeight: 500 }}>
                {n.pipelineStage}
              </span>
            </>
          )}
        </div>
      )}

      {/* 감지된 신호 */}
      {hasSignal && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 11, fontWeight: 600, color: '#737880' }}>감지된 신호</span>
            {badge && (
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  color: badge.color,
                  backgroundColor: badge.bg,
                  padding: '2px 8px',
                  borderRadius: 4,
                  letterSpacing: '0.02em',
                }}
              >
                {badge.label}
              </span>
            )}
          </div>
          {n.signalSummary && (
            <p style={{ margin: 0, fontSize: 12, lineHeight: 1.6, color: '#475569' }}>{n.signalSummary}</p>
          )}
        </div>
      )}

      {/* 추천 전략 — 백엔드 미구현, 응답에 들어오면 자동 표시 */}
      {hasRecommendation && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ fontSize: 11, fontWeight: 600, color: '#737880' }}>추천 전략</span>
          <div
            style={{
              backgroundColor: '#F8F8F5',
              borderRadius: 8,
              padding: '10px 12px',
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
            }}
          >
            <div style={{ fontSize: 12, color: '#1F2126', lineHeight: 1.5, fontWeight: 500 }}>
              &ldquo;{n.recommendation?.title}&rdquo;
            </div>
            {n.recommendation?.sub && (
              <div style={{ fontSize: 11, color: '#9CA193', lineHeight: 1.45 }}>{n.recommendation.sub}</div>
            )}
            {typeof n.recommendation?.predictedDelta === 'number' && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  fontSize: 11,
                  fontWeight: 600,
                  color: '#16A34A',
                  marginTop: 2,
                }}
              >
                <span aria-hidden>↗</span>
                <span>성사율 예측 {n.recommendation.predictedDelta > 0 ? '+' : ''}{n.recommendation.predictedDelta}%</span>
              </div>
            )}
          </div>
        </div>
      )}
    </button>
  );
}
