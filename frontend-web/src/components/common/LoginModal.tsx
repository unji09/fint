'use client';

import { useEffect, useRef, useState } from 'react';
import { clearAuthAndCache } from '@/hooks/useAuth';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

// ── 스타일 상수 (컴포넌트보다 위에 선언) ──────────────
const iStyle: React.CSSProperties = {
  padding: '10px 12px',
  borderRadius: 8,
  border: '1px solid #e2e8f0',
  fontSize: 13,
  color: '#1e293b',
  outline: 'none',
  width: '100%',
  boxSizing: 'border-box',
  fontFamily: 'Pretendard, sans-serif',
};

interface User {
  name: string;
  empNo: string;
  role: string;
}

export interface LoginModalProps {
  onClose: () => void;
}

// ── 컴포넌트 ──────────────────────────────────────────
export default function LoginModal({ onClose }: LoginModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const [user, setUser] = useState<User | null>(null);
  const [form, setForm] = useState({ companyCode: '', empNo: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 기존 로그인 상태 복원
  useEffect(() => {
    const saved = localStorage.getItem('user');
    if (saved) setUser(JSON.parse(saved));
  }, []);

  // 모달 바깥 클릭 → 닫기
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (modalRef.current && !modalRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
    setError('');
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!form.companyCode || !form.empNo || !form.password) {
      setError('모든 항목을 입력해주세요.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      const json = await res.json();
      if (!res.ok) {
        // 백엔드 raw 메시지 그대로 노출 금지 — 상태 코드별 정형 메시지로 변환.
        // (내부 클래스명 / 스택 / DB 제약 위반 메시지가 사용자에게 노출되는 것 방지)
        if (res.status === 401 || res.status === 403) setError('아이디 또는 비밀번호가 올바르지 않습니다.');
        else if (res.status === 400) setError('입력 정보를 다시 확인해주세요.');
        else if (res.status >= 500) setError('일시적인 서버 오류입니다. 잠시 후 다시 시도해주세요.');
        else setError('로그인에 실패했습니다.');
        console.error('[Login] failed', { status: res.status, message: json?.message });
        return;
      }

      localStorage.setItem('accessToken', json.data.accessToken);
      localStorage.setItem('refreshToken', json.data.refreshToken);
      localStorage.setItem('user', JSON.stringify(json.data.user));
      setUser(json.data.user);
      onClose();
      window.location.reload();
    } catch {
      setError('서버에 연결할 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      await fetch(`${API_BASE}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => {});
    }
    // 토큰 + fint:* 캐시 일괄 정리. (localStorage.clear() 는 도메인의 다른 라이브러리
    //  키까지 지워버려 부작용 위험이 있어 fint 프리픽스만 선별 삭제)
    clearAuthAndCache();
    onClose();
    window.location.reload();
  };

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000 }}>
      <div
        ref={modalRef}
        style={{
          position: 'absolute',
          top: 56,
          right: 24,
          background: 'white',
          borderRadius: 14,
          boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
          border: '1px solid #e2e8f0',
          width: 300,
          overflow: 'hidden',
          fontFamily: 'Pretendard, -apple-system, sans-serif',
        }}
      >
        {user ? (
          /* 로그인 상태 */
          <>
            <div style={{ padding: '20px 20px 16px', borderBottom: '1px solid #f1f5f9' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: '50%',
                    background: '#06b6d4',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'white',
                    fontWeight: 700,
                    fontSize: 16,
                    flexShrink: 0,
                  }}
                >
                  {user.name?.[0] ?? 'U'}
                </div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 15, color: '#1e293b' }}>{user.name}</div>
                  <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>
                    {user.empNo} · {user.role}
                  </div>
                </div>
              </div>
            </div>
            <div style={{ padding: 8 }}>
              <button
                onClick={handleLogout}
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  borderRadius: 8,
                  border: 'none',
                  background: 'none',
                  cursor: 'pointer',
                  textAlign: 'left',
                  fontSize: 14,
                  color: '#ef4444',
                  fontFamily: 'Pretendard, sans-serif',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
                로그아웃
              </button>
            </div>
          </>
        ) : (
          /* 비로그인 상태 */
          <div style={{ padding: 20 }}>
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontWeight: 700, fontSize: 16, color: '#1e293b' }}>로그인</div>
              <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>
                F!NT CRM에 접속하세요
              </div>
            </div>
            <form
              onSubmit={handleLogin}
              style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              <input
                name="companyCode"
                value={form.companyCode}
                onChange={handleChange}
                placeholder="회사 코드"
                autoComplete="organization"
                style={iStyle}
              />
              <input
                name="empNo"
                value={form.empNo}
                onChange={handleChange}
                placeholder="사번"
                autoComplete="username"
                style={iStyle}
              />
              <input
                name="password"
                type="password"
                value={form.password}
                onChange={handleChange}
                placeholder="비밀번호"
                autoComplete="current-password"
                style={iStyle}
              />
              {error && <div style={{ fontSize: 12, color: '#ef4444' }}>{error}</div>}
              <button
                type="submit"
                disabled={loading}
                style={{
                  padding: 11,
                  borderRadius: 8,
                  border: 'none',
                  background: loading ? '#94a3b8' : '#06b6d4',
                  color: 'white',
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: loading ? 'not-allowed' : 'pointer',
                  fontFamily: 'Pretendard, sans-serif',
                  marginTop: 2,
                }}
              >
                {loading ? '로그인 중...' : '로그인'}
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}
