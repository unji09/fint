'use client';
// src/components/common/GNB.tsx

import { useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { SearchIcon, UserIcon } from '@/components/common/Icon';
import LoginModal from '@/components/common/LoginModal';

const FONT = "'Pretendard', -apple-system, sans-serif";

const NAV = [
  { label: '캘린더', href: '/calendar' },
  { label: '대시보드', href: '/dashboard' },
  { label: '고객 정보', href: '/customer' },
] as const;

export default function GNB() {
  const pathname = usePathname();
  const router = useRouter();
  const [showLogin, setShowLogin] = useState(false);

  return (
    <>
      <header
        style={{
          height: 80,
          flexShrink: 0,
          backgroundColor: '#FFFFFF',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 32px',
          zIndex: 50,
          position: 'sticky',
          top: 0,
          fontFamily: FONT,
          WebkitFontSmoothing: 'antialiased',
        }}
      >
        {/* 로고 */}
        <img
          src="/logo.jpg"
          alt="F!NT"
          style={{ height: 64, objectFit: 'contain', flexShrink: 0, cursor: 'pointer' }}
          onClick={() => router.push('/calendar')}
        />

        {/* 내비게이션 */}
        <nav style={{ display: 'flex', alignItems: 'center' }}>
          {NAV.map(({ label, href }) => {
            const active = pathname === href || pathname.startsWith(href + '/');
            return (
              <div
                key={href}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 140,
                  height: 70,
                }}
              >
                <button
                  onClick={() => router.push(href)}
                  style={{
                    border: 'none',
                    backgroundColor: 'transparent',
                    cursor: 'pointer',
                    fontSize: 18,
                    fontWeight: active ? 700 : 400,
                    fontFamily: FONT,
                    color: active ? '#1A1A1A' : '#64748B',
                    borderBottom: active ? '2px solid #1A1A1A' : '2px solid transparent',
                    padding: '0 0 4px',
                    transition: 'color 0.15s, border-color 0.15s',
                  }}
                >
                  {label}
                </button>
              </div>
            );
          })}
        </nav>

        {/* 검색 + 아바타 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              border: '1px solid #E5E6DE',
              borderRadius: 8,
              padding: '7px 14px',
              backgroundColor: '#F8FAFC',
            }}
          >
            <SearchIcon size={14} />
            <input
              placeholder="Search..."
              style={{
                border: 'none',
                outline: 'none',
                backgroundColor: 'transparent',
                fontSize: 13,
                color: '#1F2126',
                width: 160,
                fontFamily: FONT,
              }}
            />
          </div>

          {/* 사람 아이콘 → 클릭 시 로그인 모달 */}
          <div
            onClick={() => setShowLogin((prev) => !prev)}
            style={{
              width: 38,
              height: 38,
              borderRadius: '50%',
              backgroundColor: '#E2EAF0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              cursor: 'pointer',
              outline: showLogin ? '2px solid #06b6d4' : 'none',
              transition: 'outline 0.15s',
            }}
          >
            <UserIcon size={18} />
          </div>
        </div>
      </header>

      {/* 로그인 모달 */}
      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
    </>
  );
}
