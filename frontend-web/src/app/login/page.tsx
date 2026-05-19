'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

// 디자인 시스템 컬러 토큰. 페이지 내부 한정 상수로 보유.
// (CLAUDE.md 컬러표는 가이드 이미지와 일부 값이 어긋나 있으니, 시스템 가이드 기준)
const BRAND_MAIN = '#06B6D4';   // --color-main
const BRAND_SUB = '#0E7490';    // --color-sub
const PAGE_BG = '#F5F7FA';      // --color-bg-page
const BORDER = '#E2EAF0';       // --color-border
const TEXT_PRIMARY = '#1E293B'; // --color-text-primary
const TEXT_SECONDARY = '#475569'; // --color-text-secondary
const TEXT_MUTED = '#64748B';   // --color-text-muted

export default function LoginPage() {
  const router = useRouter();
  const [form, setForm] = useState({ companyCode: '', empNo: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 이미 인증된 사용자가 /login 으로 진입하면 캘린더로 보낸다.
  // (로그아웃 직후 재진입·북마크 진입 시 빈 폼이 보이는 UX 결함 방지)
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (localStorage.getItem('accessToken')) {
      router.replace('/calendar');
    }
  }, [router]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
    setError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.companyCode || !form.empNo || !form.password) {
      setError('모든 항목을 입력해주세요.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });

      const json = await res.json();

      if (!res.ok) {
        // 백엔드 raw 메시지 그대로 노출 금지. 상태 코드별 정형 메시지로.
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

      router.replace('/calendar');
    } catch {
      setError('서버에 연결할 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        width: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        // 베이스는 Page BG(#F5F7FA), 좌상단 Brand Cyan + 우하단 Deep Cyan 글로우.
        background: `
          radial-gradient(circle at 15% 0%, rgba(6, 182, 212, 0.20) 0%, transparent 50%),
          radial-gradient(circle at 90% 100%, rgba(14, 116, 144, 0.22) 0%, transparent 55%),
          ${PAGE_BG}
        `,
        fontFamily: "'Pretendard', -apple-system, sans-serif",
        padding: 24,
        position: 'relative',
      }}
    >
      <style>{`
        .fint-login-input:focus {
          border-color: ${BRAND_MAIN};
          box-shadow: 0 0 0 3px rgba(6, 182, 212, 0.18);
          background: #FFFFFF;
        }
        .fint-login-btn:not(:disabled):hover {
          background: ${BRAND_SUB};
        }
        .fint-login-btn:not(:disabled):active {
          transform: translateY(1px);
        }
      `}</style>

      <div
        style={{
          background: '#FFFFFF',
          borderRadius: 20,
          padding: '40px 36px 32px',
          boxShadow: '0 20px 60px rgba(14,116,144,0.18), 0 4px 12px rgba(15,23,42,0.06)',
          width: 380,
          maxWidth: '100%',
          boxSizing: 'border-box',
        }}
      >
        {/* 로고 & 브랜드 */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <img
            src="/logo.png"
            alt="F!NT"
            style={{
              width: 72,
              height: 72,
              objectFit: 'contain',
              display: 'inline-block',
              marginBottom: 12,
            }}
          />
          <div
            style={{
              fontSize: 22,
              fontWeight: 800,
              color: TEXT_PRIMARY,
              letterSpacing: '-0.5px',
              lineHeight: 1.2,
            }}
          >
            F!NT
          </div>
          <div
            style={{
              fontSize: 12,
              color: TEXT_MUTED,
              marginTop: 6,
              lineHeight: 1.5,
            }}
          >
            기록하는 CRM이 아니라,
            <br />
            행동을 만들어내는 CRM
          </div>
        </div>

        {/* 구분선 */}
        <div
          style={{
            height: 1,
            background: `linear-gradient(to right, transparent, ${BORDER}, transparent)`,
            marginBottom: 24,
          }}
        />

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="회사 코드">
            <input
              name="companyCode"
              value={form.companyCode}
              onChange={handleChange}
              placeholder="예) FINT2024"
              autoComplete="organization"
              className="fint-login-input"
              style={inputStyle}
            />
          </Field>

          <Field label="사번">
            <input
              name="empNo"
              value={form.empNo}
              onChange={handleChange}
              placeholder="예) EMP001"
              autoComplete="username"
              className="fint-login-input"
              style={inputStyle}
            />
          </Field>

          <Field label="비밀번호">
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder="비밀번호 입력"
              autoComplete="current-password"
              className="fint-login-input"
              style={inputStyle}
            />
          </Field>

          {error && (
            <div
              role="alert"
              style={{
                fontSize: 13,
                color: '#B91C1C',
                background: '#FEF2F2',
                border: '1px solid #FECACA',
                borderRadius: 8,
                padding: '10px 12px',
                lineHeight: 1.4,
              }}
            >
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="fint-login-btn"
            style={{
              marginTop: 6,
              padding: '13px',
              borderRadius: 10,
              border: 'none',
              background: loading ? '#94A3B8' : BRAND_MAIN,
              color: '#FFFFFF',
              fontSize: 15,
              fontWeight: 700,
              letterSpacing: '-0.2px',
              cursor: loading ? 'not-allowed' : 'pointer',
              transition: 'background 0.15s, transform 0.05s',
              fontFamily: "'Pretendard', sans-serif",
              boxShadow: loading ? 'none' : '0 4px 12px rgba(6, 182, 212, 0.28)',
            }}
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </div>

      {/* 푸터 */}
      <div
        style={{
          position: 'absolute',
          bottom: 20,
          left: 0,
          right: 0,
          textAlign: 'center',
          fontSize: 12,
          color: TEXT_SECONDARY,
        }}
      >
        F!NT · B2B 영업 CRM · © {new Date().getFullYear()}
      </div>
    </div>
  );
}

// 라벨 + children 묶음. 동일 마크업이 3번 반복되어 추출.
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label
        style={{
          fontSize: 12,
          fontWeight: 600,
          color: TEXT_SECONDARY,
          display: 'block',
          marginBottom: 6,
          letterSpacing: '-0.1px',
        }}
      >
        {label}
      </label>
      {children}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '12px 14px',
  borderRadius: 10,
  border: `1px solid ${BORDER}`,
  fontSize: 14,
  color: TEXT_PRIMARY,
  outline: 'none',
  boxSizing: 'border-box',
  fontFamily: "'Pretendard', sans-serif",
  background: '#FAFBFC',
  transition: 'border-color 0.15s, box-shadow 0.15s, background 0.15s',
};
