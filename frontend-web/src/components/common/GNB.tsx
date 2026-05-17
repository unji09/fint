'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { UserIcon } from '@/components/common/Icon';
import LoginModal from '@/components/common/LoginModal';
import NotificationPanel, { type NotificationItem } from '@/components/common/NotificationPanel';
import { fetchWithAuth } from '@/hooks/useAuth';
import { useNotificationSocket } from '@/hooks/useNotificationSocket';
import useBreakpoint from '@/hooks/useBreakpoint';

const F = "'Pretendard', -apple-system, sans-serif";
const NAV = [
  { label: '캘린더', href: '/calendar' },
  { label: '대시보드', href: '/dashboard' },
  { label: '고객 정보', href: '/customer' },
] as const;

interface SearchResult { type: 'account' | 'contact' | 'deal'; id: number; label: string; sub: string; href: string }

export default function GNB() {
  const pathname = usePathname();
  const router = useRouter();
  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';
  const [showLogin, setShowLogin] = useState(false);

  // 검색
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  // 알림
  const [notiOpen, setNotiOpen] = useState(false);
  const [notis, setNotis] = useState<NotificationItem[]>([]);
  const [notiCount, setNotiCount] = useState(0);
  // 토스트 state — 아래 WebSocket 효과보다 먼저 선언 (로그아웃 cleanup 에서 참조)
  const [toast, setToast] = useState<NotificationItem | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── 로그인 상태 — accessToken 유무. 로그아웃되면 알림 모두 차단 ──
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const check = () => setIsLoggedIn(!!localStorage.getItem('accessToken'));
    check();
    const onStorage = (e: StorageEvent) => {
      if (e.key === 'accessToken' || e.key === null) check();
    };
    window.addEventListener('storage', onStorage);
    // 같은 탭의 토큰 변경(로그인/로그아웃) 감지 위한 폴링 (1초)
    const t = setInterval(check, 1_000);
    return () => {
      window.removeEventListener('storage', onStorage);
      clearInterval(t);
    };
  }, []);

  // 로그아웃 시 알림 상태 전부 초기화
  useEffect(() => {
    if (isLoggedIn) return;
    setNotis([]);
    setNotiCount(0);
    setNotiOpen(false);
    setToast(null);
    if (toastTimerRef.current) { clearTimeout(toastTimerRef.current); toastTimerRef.current = null; }
  }, [isLoggedIn]);

  useEffect(() => {
    if (!query.trim()) { setResults([]); return; }
    const t = setTimeout(async () => {
      const items: SearchResult[] = [];
      try {
        const res = await fetchWithAuth(`/accounts/searchable?keyword=${encodeURIComponent(query)}&size=5`);
        if (res.ok) {
          const j = await res.json();
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          (j.data ?? []).forEach((a: any) => items.push({ type: 'account', id: a.accountId, label: a.name, sub: a.industry ?? '', href: `/customer/${a.accountId}` }));
        }
      } catch { /* */ }
      setResults(items);
      setSearchOpen(items.length > 0);
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  // 외부 클릭 (검색만 — 알림은 NotificationPanel 자체 backdrop 으로 처리)
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) setSearchOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  // 알림 로드 — GET /notifications
  // 백엔드 응답: { data: { content: NotificationItemResponse[] } } (unread 최대 10개)
  // 클라이언트는 한 번 받은 알림을 메모리에 보관 (읽음 처리되어 다음 GET 에서 빠져도 목록 유지).
  // merge 규칙:
  //  - 응답에 있는 알림 → 백엔드 데이터 우선 (isRead=false)
  //  - 응답에 없고 기존 state 에만 있는 알림 → isRead=true 로 토글 후 유지
  //  - createdAt desc 정렬
  const loadNotis = async () => {
    if (!isLoggedIn) return; // 로그인 안 됐으면 호출 안 함
    try {
      const res = await fetchWithAuth('/notifications');
      if (!res.ok) return;
      const j = await res.json();
      const fresh: NotificationItem[] = Array.isArray(j?.data?.content) ? j.data.content : [];
      const freshIds = new Set(fresh.map((n) => n.notificationId));
      setNotis((prev) => {
        const carried = prev
          .filter((n) => !freshIds.has(n.notificationId))
          .map((n) => ({ ...n, isRead: true }));
        const merged = [...fresh, ...carried];
        merged.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        return merged;
      });
      setNotiCount(fresh.length);
    } catch {
      /* ignore */
    }
  };

  // 로그인 상태일 때만 페이지 진입 시 미리 로드
  useEffect(() => { if (isLoggedIn) loadNotis(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [isLoggedIn]);

  const handleNotiClick = () => {
    if (!isLoggedIn) return;
    if (!notiOpen) loadNotis();
    setNotiOpen((v) => !v);
  };

  // ── 실시간 긴급 알림 (WebSocket STOMP) ──
  // /user/queue/notifications 구독. 새 알림 수신 시 목록 push + 토스트 표시.
  // 토스트 swipe-dismiss — 우측/위로 드래그하면 사라짐
  const [toastDrag, setToastDrag] = useState<{ startX: number; startY: number; dx: number; dy: number; dragging: boolean }>(
    { startX: 0, startY: 0, dx: 0, dy: 0, dragging: false },
  );
  const dismissToast = useCallback(() => {
    if (toastTimerRef.current) { clearTimeout(toastTimerRef.current); toastTimerRef.current = null; }
    setToast(null);
    setToastDrag({ startX: 0, startY: 0, dx: 0, dy: 0, dragging: false });
  }, []);
  useEffect(() => {
    if (!toastDrag.dragging) return;
    const onMv = (e: MouseEvent) => {
      setToastDrag((d) => ({ ...d, dx: e.clientX - d.startX, dy: e.clientY - d.startY }));
    };
    const onUp = () => {
      setToastDrag((d) => {
        // 80px 이상 우측 또는 위로 던지면 dismiss
        if (d.dx > 80 || d.dy < -80) {
          dismissToast();
          return d;
        }
        return { ...d, dragging: false, dx: 0, dy: 0 };
      });
    };
    window.addEventListener('mousemove', onMv);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMv);
      window.removeEventListener('mouseup', onUp);
    };
  }, [toastDrag.dragging, dismissToast]);
  const onWsNotification = useCallback((noti: NotificationItem) => {
    // 로그아웃 상태 보호 — 토큰 만료 직후 늦게 도착한 메시지 등 차단
    if (typeof window !== 'undefined' && !localStorage.getItem('accessToken')) return;
    setNotis((prev) => {
      // 중복(같은 notificationId) 방지
      if (prev.some((n) => n.notificationId === noti.notificationId)) return prev;
      return [noti, ...prev];
    });
    setNotiCount((c) => c + 1);
    // 토스트 — 5초 자동 닫힘
    setToast(noti);
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    toastTimerRef.current = setTimeout(() => setToast(null), 5_000);
  }, []);
  useNotificationSocket(onWsNotification);


  return (
    <>
      <header style={{ height: 64, flexShrink: 0, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', padding: isMobile ? '0 12px' : '0 20px 0 0', zIndex: 50, position: 'sticky', top: 0, fontFamily: F }}>
        {/* 로고 */}
        <div onClick={() => router.push('/calendar')} style={{ width: isMobile ? 'auto' : 300, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', height: '100%', padding: isMobile ? '0 8px' : 0 }}>
          <img src="/logo.png" alt="F!NT" style={{ maxHeight: isMobile ? 32 : 44, maxWidth: isMobile ? 80 : 200, objectFit: 'contain' }} />
        </div>

        {/* 네비게이션 */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: isMobile ? 0 : 4, height: '100%' }}>
          {NAV.map(({ label, href }) => {
            const active = pathname === href || pathname.startsWith(href + '/');
            return (
              <button key={href} onClick={() => router.push(href)}
                onMouseEnter={() => router.prefetch(href)}
                style={{ border: 'none', backgroundColor: 'transparent', cursor: 'pointer', fontSize: isMobile ? 12 : 14, fontWeight: active ? 600 : 400, fontFamily: F, color: active ? '#0f172a' : '#64748b', padding: isMobile ? '0 8px' : '0 16px', height: '100%', borderBottom: active ? '2px solid #0f172a' : '2px solid transparent', display: 'flex', alignItems: 'center', transition: 'color 0.12s, background-color 0.12s', whiteSpace: 'nowrap' }}
                onMouseOver={(e) => { if (!active) e.currentTarget.style.color = '#0f172a'; }}
                onMouseOut={(e) => { if (!active) e.currentTarget.style.color = '#64748b'; }}>
                {label}
              </button>
            );
          })}
        </nav>

        <div style={{ flex: 1 }} />

        {/* 우측: 검색 + 알림 + 프로필 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* 검색 — 모바일에서 숨김 */}
          {!isMobile && <div ref={searchRef} style={{ position: 'relative' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, border: '1px solid #e2e8f0', borderRadius: 6, padding: '5px 10px', backgroundColor: '#f8fafc', width: 200 }}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
                <circle cx="6" cy="6" r="4" stroke="#94a3b8" strokeWidth="1.2" />
                <path d="M9.5 9.5L12 12" stroke="#94a3b8" strokeWidth="1.2" strokeLinecap="round" />
              </svg>
              <input placeholder="검색..." value={query}
                onChange={(e) => { setQuery(e.target.value); setSearchOpen(true); }}
                onFocus={() => results.length > 0 && setSearchOpen(true)}
                style={{ border: 'none', outline: 'none', backgroundColor: 'transparent', fontSize: 13, color: '#1f2126', width: '100%', fontFamily: F }} />
            </div>
            {searchOpen && results.length > 0 && (
              <div style={{ position: 'absolute', top: '100%', right: 0, width: 280, zIndex: 200, backgroundColor: '#fff', border: '1px solid #e2e8f0', borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.08)', marginTop: 4, maxHeight: 240, overflowY: 'auto' }}>
                {results.map((r) => (
                  <button key={`${r.type}-${r.id}`} onClick={() => { router.push(r.href); setQuery(''); setSearchOpen(false); }}
                    style={{ width: '100%', textAlign: 'left', padding: '8px 12px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, color: '#1f2126', borderBottom: '1px solid #f3f4f6', display: 'flex', alignItems: 'center', gap: 6, fontFamily: F }}>
                    <span style={{ fontSize: 10, color: '#94a3b8', backgroundColor: '#f1f5f9', padding: '1px 4px', borderRadius: 2, flexShrink: 0 }}>
                      {{ account: '고객사', contact: '담당자', deal: '딜' }[r.type]}
                    </span>
                    <span style={{ flex: 1 }}>{r.label}</span>
                    <span style={{ fontSize: 11, color: '#94a3b8' }}>{r.sub}</span>
                  </button>
                ))}
              </div>
            )}
          </div>}

          {/* 알림 — 우측 슬라이드 오버 패널 (NotificationPanel). 로그인 상태에서만 노출. */}
          {isLoggedIn && (
            <button onClick={handleNotiClick}
              aria-label="알림"
              style={{ width: 32, height: 32, borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0, position: 'relative', transition: 'background-color 0.12s, border-color 0.12s' }}
              onMouseOver={(e) => { e.currentTarget.style.backgroundColor = '#f8fafc'; e.currentTarget.style.borderColor = '#cbd5e1'; }}
              onMouseOut={(e) => { e.currentTarget.style.backgroundColor = '#fff'; e.currentTarget.style.borderColor = '#e2e8f0'; }}>
              <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
                <path d="M10 2a5 5 0 00-5 5v3l-1.3 2.6a.5.5 0 00.45.7h11.7a.5.5 0 00.45-.7L15 10V7a5 5 0 00-5-5z" stroke="#64748b" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M8 15a2 2 0 004 0" stroke="#64748b" strokeWidth="1.4" strokeLinecap="round" />
              </svg>
              {notiCount > 0 && (
                <span style={{ position: 'absolute', top: -2, right: -2, width: 14, height: 14, borderRadius: '50%', backgroundColor: '#ef4444', color: '#fff', fontSize: 9, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {notiCount > 9 ? '9+' : notiCount}
                </span>
              )}
            </button>
          )}

          {/* 프로필 */}
          <div onClick={() => setShowLogin((prev) => !prev)}
            style={{ width: 32, height: 32, borderRadius: 6, backgroundColor: '#e2eaf0', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0, transition: 'background-color 0.12s' }}
            onMouseOver={(e) => { e.currentTarget.style.backgroundColor = '#cbd5e1'; }}
            onMouseOut={(e) => { e.currentTarget.style.backgroundColor = '#e2eaf0'; }}>
            <UserIcon size={16} />
          </div>
        </div>
      </header>

      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}

      <NotificationPanel
        open={notiOpen}
        onClose={() => setNotiOpen(false)}
        notifications={notis}
        onItemRead={(id) => {
          // 백엔드 PATCH 와 별도로 클라이언트 메모리 isRead 즉시 토글 (목록 유지)
          setNotis((prev) => prev.map((n) => (n.notificationId === id ? { ...n, isRead: true } : n)));
          setNotiCount((c) => Math.max(0, c - 1));
        }}
        onAllRead={() => {
          setNotis((prev) => prev.map((n) => ({ ...n, isRead: true })));
          setNotiCount(0);
        }}
      />

      {/* 긴급 알림 토스트 — 로그인 상태에서만, WebSocket 실시간. 우측/위로 swipe 시 dismiss. */}
      {isLoggedIn && toast && (() => {
        const dx = toastDrag.dx;
        const dy = toastDrag.dy;
        // 우측(>0) 또는 위(<0) 방향만 따라감
        const tx = Math.max(0, dx);
        const ty = Math.min(0, dy);
        // dismiss 임계 80px — 점점 투명
        const dist = Math.max(tx, -ty);
        const opacity = toastDrag.dragging ? Math.max(0.3, 1 - dist / 120) : 1;
        return (
        <div
          onMouseDown={(e) => {
            // 닫기 버튼 mousedown 은 drag 시작 안 함
            if ((e.target as HTMLElement).closest('[data-toast-close]')) return;
            setToastDrag({ startX: e.clientX, startY: e.clientY, dx: 0, dy: 0, dragging: true });
          }}
          onClick={() => {
            // 드래그 했으면 (4px 이상 이동) 패널 열지 않음 (의도치 않은 클릭 방지)
            if (Math.abs(dx) > 4 || Math.abs(dy) > 4) return;
            dismissToast();
            setNotiOpen(true);
          }}
          style={{
            position: 'fixed',
            top: 80,
            right: 20,
            zIndex: 1100,
            maxWidth: 360,
            backgroundColor: '#fff',
            border: '1px solid #FECACA',
            borderLeft: '3px solid #EF4444',
            borderRadius: 10,
            boxShadow: '0 8px 24px rgba(15, 23, 42, 0.12)',
            padding: '12px 14px',
            cursor: toastDrag.dragging ? 'grabbing' : 'grab',
            userSelect: 'none',
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
            fontFamily: F,
            animation: 'toastSlideIn 0.2s ease-out',
            transform: `translate(${tx}px, ${ty}px)`,
            opacity,
            transition: toastDrag.dragging ? 'none' : 'transform 0.18s ease-out, opacity 0.18s ease-out',
          }}
        >
          <style>{`@keyframes toastSlideIn { from { transform: translateX(20px); opacity: 0; } to { transform: translateX(0); opacity: 1; } }`}</style>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 10, fontWeight: 700, color: '#DC2626', backgroundColor: '#FEE2E2', padding: '2px 6px', borderRadius: 3 }}>긴급</span>
            <span style={{ fontSize: 11, color: '#94A3B8' }}>{toast.accountName}</span>
          </div>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#1F2126', lineHeight: 1.4, overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
            {toast.title}
          </div>
          {(() => {
            if (!toast.sources) return null;
            const items = Object.values(toast.sources).flat();
            const text = items[0]?.summary || items[0]?.title;
            if (!text) return null;
            return (
              <div style={{ fontSize: 11, color: '#64748B', lineHeight: 1.4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {text}
              </div>
            );
          })()}
        </div>
        );
      })()}
    </>
  );
}
