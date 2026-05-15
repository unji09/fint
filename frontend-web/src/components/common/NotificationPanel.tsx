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

import { useEffect, useRef, useState } from 'react';
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
export interface SourceItem {
  title?: string;
  summary?: string;
  url?: string;
}

export interface NotificationItem {
  notificationId: number;
  title: string;
  /** 전략 카테고리 — NextAction.data.category (예: "ROI 기반 전략") */
  category: string | null;
  sources: Record<string, SourceItem[]> | null;
  pipelineStage: string | null;
  accountName: string | null;
  isRead: boolean;
  createdAt: string;

  // ── 미래 확장용 (백엔드 미구현, 응답에 없으면 표시 안 됨) ──
  accountId?: number;
  successRate?: number;
  recommendation?: { title?: string; sub?: string; predictedDelta?: number };
}

// 7일 이내 상대(방금 전/N분 전/오늘/어제/N일 전), 그 이상은 MM/DD
function formatRelativeOrDate(iso: string): string {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const now = Date.now();
  const diffMs = now - t;
  if (diffMs < 5 * 60_000) return '방금 전';
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 60) return `${diffMin}분 전`;
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const target = new Date(t); target.setHours(0, 0, 0, 0);
  const dayDelta = Math.round((today.getTime() - target.getTime()) / (24 * 60 * 60_000));
  if (dayDelta <= 0) return '오늘';
  if (dayDelta === 1) return '어제';
  if (dayDelta <= 6) return `${dayDelta}일 전`;
  const d = new Date(t);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

interface Props {
  open: boolean;
  onClose: () => void;
  notifications: NotificationItem[];
  onChanged?: () => void;
  /** 단건 읽음 처리 직후 부모에 통보 — 부모는 메모리상의 isRead 만 토글 (백엔드 GET 은 unread 만 반환하므로 클라이언트 보관) */
  onItemRead?: (notificationId: number) => void;
  /** 전체 읽음 처리 후 부모에 통보 — 모든 알림 isRead=true 로 토글 */
  onAllRead?: () => void;
}

// ── 소스 타입별 뱃지 스타일 ─────────────────────────────────
const SOURCE_BADGE_STYLE: Record<string, { label: string; bg: string; color: string }> = {
  news: { label: 'News', bg: '#FFE4E1', color: '#D85A30' },
  dart: { label: 'DART', bg: '#FEF3C7', color: '#B45309' },
  crm: { label: 'CRM', bg: '#E0E7FF', color: '#5B5BD6' },
};

function getActiveSourceTypes(sources: Record<string, SourceItem[]> | null | undefined) {
  if (!sources) return [];
  return Object.entries(sources)
    .filter(([, items]) => Array.isArray(items) && items.length > 0)
    .map(([type]) => type);
}

function getFirstSummary(sources: Record<string, SourceItem[]> | null | undefined): string | null {
  if (!sources) return null;
  for (const items of Object.values(sources)) {
    if (!Array.isArray(items)) continue;
    for (const item of items) {
      if (item.summary) return item.summary;
      if (item.title) return item.title;
    }
  }
  return null;
}

export default function NotificationPanel({ open, onClose, notifications, onItemRead, onAllRead }: Props) {
  const panelRef = useRef<HTMLDivElement>(null);
  // 클릭한 알림의 인라인 확장 — 한 번에 하나만 펼침
  const [expandedId, setExpandedId] = useState<number | null>(null);

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

  // 안 읽음 위, 읽음 아래로 정렬 (각 그룹 내 최신순)
  const sortedNotis = [...notifications].sort((a, b) => {
    if (a.isRead !== b.isRead) return a.isRead ? 1 : -1;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
  const unreadList = sortedNotis.filter((n) => !n.isRead);
  const readList = sortedNotis.filter((n) => n.isRead);
  const unreadCount = unreadList.length;

  const handleMarkAllRead = async () => {
    try {
      const res = await fetchWithAuth('/notifications/read-all', { method: 'PATCH' });
      if (res.ok || res.status === 204) onAllRead?.();
    } catch {
      /* ignore */
    }
  };

  const handleClick = async (n: NotificationItem) => {
    // 인라인 토글 — 이미 펼쳐진 알림이면 접고, 아니면 펼침
    const willExpand = expandedId !== n.notificationId;
    setExpandedId(willExpand ? n.notificationId : null);
    // 펼칠 때 미읽음이면 백엔드 PATCH + 부모 메모리 isRead 토글 (목록에서 사라지지 않고 읽음 표시만)
    if (willExpand && !n.isRead) {
      onItemRead?.(n.notificationId);
      try {
        await fetchWithAuth(`/notifications/${n.notificationId}/read`, { method: 'PATCH' });
      } catch {
        /* ignore */
      }
    }
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

        {/* 본문 — 안 읽음 위, 읽음 아래 */}
        <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', padding: '16px 0 32px', minWidth: 0 }}>
          {notifications.length === 0 ? (
            <div style={{ padding: '48px 24px', textAlign: 'center', color: '#9CA193', fontSize: 13 }}>
              새로운 알림이 없어요.
            </div>
          ) : (
            <>
              {unreadList.length > 0 && (
                <section>
                  <div style={{ padding: '0 24px 8px', fontSize: 11, fontWeight: 600, color: '#EF4444', letterSpacing: '0.02em' }}>
                    새 알림 {unreadList.length}
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '0 16px' }}>
                    {unreadList.map((n) => (
                      <NotificationCard key={n.notificationId} n={n} expanded={expandedId === n.notificationId} onClick={() => handleClick(n)} />
                    ))}
                  </div>
                </section>
              )}
              {readList.length > 0 && (
                <section style={{ marginTop: unreadList.length > 0 ? 20 : 0 }}>
                  <div style={{ padding: '0 24px 8px', fontSize: 11, fontWeight: 600, color: '#9CA193', letterSpacing: '0.02em' }}>
                    읽은 알림
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '0 16px' }}>
                    {readList.map((n) => (
                      <NotificationCard key={n.notificationId} n={n} expanded={expandedId === n.notificationId} onClick={() => handleClick(n)} />
                    ))}
                  </div>
                </section>
              )}
            </>
          )}
        </div>
      </aside>
    </>
  );
}

// ─── 알림 카드 ───────────────────────────────────────────────
function NotificationCard({ n, expanded, onClick }: { n: NotificationItem; expanded: boolean; onClick: () => void }) {
  const activeTypes = getActiveSourceTypes(n.sources);
  const firstSummary = getFirstSummary(n.sources);
  const hasAccount = !!n.accountName;
  const hasSignal = activeTypes.length > 0 || !!firstSummary;
  const hasRecommendation = !!n.recommendation?.title;
  // 좌상단 — NextAction 전략 카테고리 (data.category)
  const categoryLabel = n.category ?? null;

  return (
    <button
      onClick={onClick}
      style={{
        position: 'relative',
        width: '100%',
        maxWidth: '100%',
        minWidth: 0,
        textAlign: 'left',
        backgroundColor: expanded ? '#fff' : n.isRead ? '#FAFAF7' : '#fff',
        border: `1px solid ${expanded ? '#06B6D4' : n.isRead ? '#ECEDE5' : '#cffafe'}`,
        borderRadius: 10,
        // 우상단 dot(8) / 우하단 시간 영역 확보
        padding: '14px 18px 26px 16px',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        transition: 'background-color 0.12s, border-color 0.12s',
        fontFamily: F,
        opacity: n.isRead && !expanded ? 0.75 : 1,
      }}
      onMouseEnter={(e) => {
        if (!expanded) e.currentTarget.style.borderColor = '#06B6D4';
      }}
      onMouseLeave={(e) => {
        if (!expanded) e.currentTarget.style.borderColor = n.isRead ? '#ECEDE5' : '#cffafe';
      }}
    >
      {/* 좌상단 — 전략 카테고리 (NextAction.category) */}
      {categoryLabel && (
        <div>
          <span
            style={{
              display: 'inline-block',
              fontSize: 10,
              fontWeight: 600,
              color: '#0686D4',
              backgroundColor: '#EEF6FF',
              padding: '3px 9px',
              borderRadius: 4,
              letterSpacing: '0.02em',
            }}
          >
            {categoryLabel}
          </span>
        </div>
      )}
      {/* 우상단 — 읽음/안 읽음 표시 (텍스트 + dot) */}
      <div
        aria-label={n.isRead ? '읽은 알림' : '안 읽은 알림'}
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          display: 'flex',
          alignItems: 'center',
          gap: 5,
          pointerEvents: 'none',
        }}
      >
        {!n.isRead ? (
          <>
            <span
              style={{
                fontSize: 10,
                fontWeight: 700,
                color: '#EF4444',
                letterSpacing: '0.04em',
              }}
            >
              NEW
            </span>
            <span style={{ width: 7, height: 7, borderRadius: '50%', backgroundColor: '#EF4444' }} />
          </>
        ) : (
          <span
            style={{
              fontSize: 10,
              fontWeight: 500,
              color: '#94A3B8',
              letterSpacing: '0.04em',
            }}
          >
            읽음
          </span>
        )}
      </div>
      {/* 우하단 — 시간 */}
      <span
        style={{
          position: 'absolute',
          bottom: 10,
          right: 12,
          fontSize: 11,
          color: '#94A3B8',
          fontWeight: 400,
          fontVariantNumeric: 'tabular-nums',
          pointerEvents: 'none',
          whiteSpace: 'nowrap',
        }}
      >
        {formatRelativeOrDate(n.createdAt)}
      </span>
      {/* 제목 — 우상단 NEW/읽음 라벨 공간 확보 */}
      <div style={{ fontSize: 14, fontWeight: 600, color: '#1F2126', lineHeight: 1.45, letterSpacing: '-0.005em', paddingRight: !n.isRead ? 44 : 30, wordBreak: 'break-word', overflowWrap: 'break-word' }}>
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
            {activeTypes.map((type) => {
              const style = SOURCE_BADGE_STYLE[type] ?? { label: type, bg: '#F0F0EE', color: '#737880' };
              return (
                <span
                  key={type}
                  style={{
                    fontSize: 10,
                    fontWeight: 600,
                    color: style.color,
                    backgroundColor: style.bg,
                    padding: '2px 8px',
                    borderRadius: 4,
                    letterSpacing: '0.02em',
                  }}
                >
                  {style.label}
                </span>
              );
            })}
          </div>
          {firstSummary && (
            <p style={{ margin: 0, fontSize: 12, lineHeight: 1.6, color: '#475569' }}>{firstSummary}</p>
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

      {/* ── 확장 영역 — sources 의 각 source 카드 클릭 시 원문 새 탭 ── */}
      {expanded && n.sources && activeTypes.length > 0 && (
        <div
          style={{
            marginTop: 4,
            paddingTop: 12,
            borderTop: '1px solid #ECEDE5',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <span style={{ fontSize: 11, fontWeight: 600, color: '#737880' }}>근거 데이터</span>
          {activeTypes.flatMap((type) => {
            const items = n.sources![type];
            const style = SOURCE_BADGE_STYLE[type] ?? { label: type, bg: '#F0F0EE', color: '#737880' };
            return items.map((item, i) => (
              <a
                key={`${type}-${i}`}
                href={item.url || undefined}
                target={item.url ? '_blank' : undefined}
                rel="noopener noreferrer"
                onClick={(e) => e.stopPropagation()}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                  textDecoration: 'none',
                  backgroundColor: '#F8FAFC',
                  border: '1px solid #ECEDE5',
                  borderRadius: 8,
                  padding: '10px 12px',
                  cursor: item.url ? 'pointer' : 'default',
                  transition: 'background-color 0.12s, border-color 0.12s',
                  maxWidth: '100%',
                  minWidth: 0,
                }}
                onMouseEnter={(e) => {
                  if (item.url) {
                    (e.currentTarget as HTMLAnchorElement).style.backgroundColor = '#fff';
                    (e.currentTarget as HTMLAnchorElement).style.borderColor = '#06B6D4';
                  }
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLAnchorElement).style.backgroundColor = '#F8FAFC';
                  (e.currentTarget as HTMLAnchorElement).style.borderColor = '#ECEDE5';
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                  <span
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      color: style.color,
                      backgroundColor: style.bg,
                      padding: '2px 7px',
                      borderRadius: 3,
                      letterSpacing: '0.02em',
                      flexShrink: 0,
                    }}
                  >
                    {style.label}
                  </span>
                  {item.title && (
                    <span style={{ flex: 1, minWidth: 0, fontSize: 12, fontWeight: 600, color: '#1F2126', lineHeight: 1.4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {item.title}
                    </span>
                  )}
                  {item.url && (
                    <span style={{ fontSize: 10, color: '#0686d4', fontWeight: 500, flexShrink: 0 }}>원문 →</span>
                  )}
                </div>
                {item.summary && (
                  <p style={{ margin: 0, fontSize: 11, lineHeight: 1.5, color: '#475569', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', wordBreak: 'break-word' }}>
                    {item.summary}
                  </p>
                )}
              </a>
            ));
          })}
        </div>
      )}
    </button>
  );
}

