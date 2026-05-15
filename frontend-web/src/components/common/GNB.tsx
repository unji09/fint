'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { UserIcon } from '@/components/common/Icon';
import LoginModal from '@/components/common/LoginModal';
import NotificationPanel, { type NotificationItem } from '@/components/common/NotificationPanel';
import { fetchWithAuth } from '@/hooks/useAuth';
import { useNotificationSocket } from '@/hooks/useNotificationSocket';

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

  // 페이지 진입 시 한 번 미리 로드 (배지 개수용)
  useEffect(() => { loadNotis(); }, []);

  const handleNotiClick = () => {
    if (!notiOpen) loadNotis();
    setNotiOpen((v) => !v);
  };

  // ── 실시간 긴급 알림 (WebSocket STOMP) ──
  // /user/queue/notifications 구독. 새 알림 수신 시 목록 push + 토스트 표시.
  const [toast, setToast] = useState<NotificationItem | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onWsNotification = useCallback((noti: NotificationItem) => {
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

  // ── 시연용: 백엔드 WebSocket push 가 연결되기 전까지 매 새로고침 시 더미 알림 ──
  // 패널 시각 검증용. 백엔드 정상 push 후 제거 예정.
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const now = Date.now();
    const iso = (msAgo: number) => new Date(now - msAgo).toISOString();
    const MIN = 60_000, HOUR = 60 * MIN, DAY = 24 * HOUR;
    const demoBase: NotificationItem[] = [
      {
        notificationId: -1,
        title: '긴급: 삼성SDS CFO 교체 — 즉시 제안서 발송 권장',
        category: '긴급 대응',
        pipelineStage: '발굴', accountName: '삼성SDS',
        isRead: false, createdAt: iso(2 * MIN),
        sources: {
          news: [
            { title: '삼성SDS, 박성준 CFO 내정 발표', summary: '디지털 전환 가속 명시. 클라우드/AI 투자 확대 시사.', url: 'https://www.example.com/news/samsung-sds-cfo-1' },
            { title: '재무총괄 교체 — 의사결정 가속 시그널', summary: '내부 의사결정 라인 정비.', url: 'https://www.example.com/news/samsung-sds-cfo-2' },
          ],
          dart: [
            { title: '주요 경영사항 — 임원 변경', summary: '재무 총괄 임원(CFO) 신규 선임 공시.', url: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260515000851' },
          ],
        },
      },
      {
        notificationId: -2,
        title: 'LG CNS — 1분기 사업보고서 공시',
        category: '실적 기반 제안',
        pipelineStage: '제안 제출', accountName: 'LG CNS',
        isRead: false, createdAt: iso(45 * MIN),
        sources: {
          dart: [{ title: '2026년 1분기 사업보고서', summary: '매출 12% 증가. 클라우드 부문 성장 견인.', url: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260515000900' }],
        },
      },
      {
        notificationId: -3,
        title: '카카오 — 신규 임원 선임 공시 다수',
        category: '관계 강화',
        pipelineStage: '협상', accountName: '카카오',
        isRead: false, createdAt: iso(3 * HOUR),
        sources: {
          dart: [
            { title: '사외이사 신규 선임', summary: '플랫폼 사업 자문역 영입.', url: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260515001100' },
            { title: '부사장 선임', summary: '플랫폼 사업 총괄 부사장 신규 선임.', url: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260515001200' },
          ],
        },
      },
      {
        notificationId: -4,
        title: '네이버 — AI 데이터 분석 플랫폼 출시 예고',
        category: '경쟁 대응',
        pipelineStage: '가치 제안', accountName: '네이버',
        isRead: false, createdAt: iso(DAY),
        sources: {
          news: [{ title: '네이버, 엔터프라이즈 AI 분석 플랫폼 출시 임박', summary: 'B2B 시장 본격 진입. 파트너사 모집 시작.', url: 'https://www.example.com/news/naver-ai' }],
        },
      },
      {
        notificationId: -5,
        title: '현대오토에버 — 분기 실적 발표',
        category: 'ROI 기반 전략',
        pipelineStage: '계약 대기', accountName: '현대오토에버',
        isRead: true, createdAt: iso(3 * DAY),
        sources: {
          dart: [{ title: '2026년 1분기 사업보고서', summary: '영업이익 8% 개선. SaaS 구독 매출 분기 최초 500억원 돌파.', url: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260512000123' }],
        },
      },
      {
        notificationId: -6,
        title: 'SK텔레콤 — 5G IoT 플랫폼 파트너십 보도',
        category: '신규 기회 발굴',
        pipelineStage: '솔루션 설계', accountName: 'SK텔레콤',
        isRead: true, createdAt: iso(8 * DAY),
        sources: {
          news: [{ title: 'SK텔레콤, 5G IoT 파트너 모집', summary: '제조업 IoT 파트너 모집 — F!NT 솔루션 적합도 검토 필요.', url: 'https://www.example.com/news/skt-iot' }],
        },
      },
    ];
    setNotis((prev) => {
      const ids = new Set(prev.map((n) => n.notificationId));
      const merged = [...prev, ...demoBase.filter((d) => !ids.has(d.notificationId))];
      merged.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      return merged;
    });
    setNotiCount((c) => c + demoBase.filter((d) => !d.isRead).length);
    // 토스트 — 가장 최신 안 읽음 알림으로 즉시 띄움 (8초 표시)
    const t = setTimeout(() => {
      setToast(demoBase[0]);
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
      toastTimerRef.current = setTimeout(() => setToast(null), 8_000);
    }, 500);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <header style={{ height: 64, flexShrink: 0, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', padding: '0 20px 0 0', zIndex: 50, position: 'sticky', top: 0, fontFamily: F }}>
        {/* 로고 — 캘린더 사이드바 너비(300px)에 맞춤 */}
        <div onClick={() => router.push('/calendar')} style={{ width: 300, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', height: '100%' }}>
          <img src="/logo.png" alt="F!NT" style={{ maxHeight: 44, maxWidth: 200, objectFit: 'contain' }} />
        </div>

        {/* 네비게이션 — 로고 오른쪽, 캘린더 그리드와 정렬 */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: 4, height: '100%' }}>
          {NAV.map(({ label, href }) => {
            const active = pathname === href || pathname.startsWith(href + '/');
            return (
              <button key={href} onClick={() => router.push(href)}
                onMouseEnter={() => router.prefetch(href)}
                style={{ border: 'none', backgroundColor: 'transparent', cursor: 'pointer', fontSize: 14, fontWeight: active ? 600 : 400, fontFamily: F, color: active ? '#0f172a' : '#64748b', padding: '0 16px', height: '100%', borderBottom: active ? '2px solid #0f172a' : '2px solid transparent', display: 'flex', alignItems: 'center', transition: 'color 0.12s, background-color 0.12s' }}
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
          {/* 검색 */}
          <div ref={searchRef} style={{ position: 'relative' }}>
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
          </div>

          {/* 알림 — 우측 슬라이드 오버 패널 (NotificationPanel) */}
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

      {/* 긴급 알림 토스트 — WebSocket 으로 실시간 수신 시 우측 상단에 5초 노출 */}
      {toast && (
        <div
          onClick={() => { setToast(null); setNotiOpen(true); }}
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
            cursor: 'pointer',
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
            fontFamily: F,
            animation: 'toastSlideIn 0.2s ease-out',
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
      )}
    </>
  );
}
