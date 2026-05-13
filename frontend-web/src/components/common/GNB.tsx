'use client';

import { useState, useRef, useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { UserIcon } from '@/components/common/Icon';
import LoginModal from '@/components/common/LoginModal';
import NotificationPanel, { type NotificationItem } from '@/components/common/NotificationPanel';
import { fetchWithAuth } from '@/hooks/useAuth';

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
  // 백엔드 응답: { data: { content: NotificationItemResponse[] } }
  // findUnreadNotifications 이므로 응답은 항상 "읽지 않은" 항목만 (최대 10개).
  // 따라서 unreadCount = content.length.
  const loadNotis = async () => {
    try {
      const res = await fetchWithAuth('/notifications');
      if (!res.ok) return;
      const j = await res.json();
      const list: NotificationItem[] = Array.isArray(j?.data?.content) ? j.data.content : [];
      setNotis(list);
      setNotiCount(list.length);
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
        onChanged={loadNotis}
      />
    </>
  );
}
