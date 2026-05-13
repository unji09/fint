'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

export default function LoginPage() {
  const router = useRouter();
  const [form, setForm] = useState({ companyCode: '', empNo: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

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

      // 토큰 저장
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
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f8fafc',
        fontFamily: 'Pretendard, -apple-system, sans-serif',
      }}
    >
      <div
        style={{
          background: 'white',
          borderRadius: 16,
          padding: '48px 40px',
          boxShadow: '0 4px 24px rgba(0,0,0,0.08)',
          width: 360,
        }}
      >
        {/* 로고 */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ fontSize: 28, fontWeight: 900, color: '#06b6d4', letterSpacing: '-1px' }}>
            F!NT
          </div>
          <div style={{ fontSize: 13, color: '#94a3b8', marginTop: 4 }}>B2B 영업 CRM</div>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 회사 코드 */}
          <div>
            <label
              style={{
                fontSize: 13,
                fontWeight: 500,
                color: '#374151',
                display: 'block',
                marginBottom: 6,
              }}
            >
              회사 코드
            </label>
            <input
              name="companyCode"
              value={form.companyCode}
              onChange={handleChange}
              placeholder="예) FINT2024"
              autoComplete="organization"
              style={inputStyle}
            />
          </div>

          {/* 사번 */}
          <div>
            <label
              style={{
                fontSize: 13,
                fontWeight: 500,
                color: '#374151',
                display: 'block',
                marginBottom: 6,
              }}
            >
              사번
            </label>
            <input
              name="empNo"
              value={form.empNo}
              onChange={handleChange}
              placeholder="예) EMP001"
              autoComplete="username"
              style={inputStyle}
            />
          </div>

          {/* 비밀번호 */}
          <div>
            <label
              style={{
                fontSize: 13,
                fontWeight: 500,
                color: '#374151',
                display: 'block',
                marginBottom: 6,
              }}
            >
              비밀번호
            </label>
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder="비밀번호 입력"
              autoComplete="current-password"
              style={inputStyle}
            />
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div
              style={{
                fontSize: 13,
                color: '#ef4444',
                background: '#fef2f2',
                borderRadius: 8,
                padding: '10px 14px',
              }}
            >
              {error}
            </div>
          )}

          {/* 로그인 버튼 */}
          <button
            type="submit"
            disabled={loading}
            style={{
              marginTop: 4,
              padding: '13px',
              borderRadius: 10,
              border: 'none',
              background: loading ? '#94a3b8' : '#06b6d4',
              color: 'white',
              fontSize: 15,
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              transition: 'background 0.15s',
              fontFamily: 'Pretendard, sans-serif',
            }}
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '11px 14px',
  borderRadius: 8,
  border: '1px solid #e2e8f0',
  fontSize: 14,
  color: '#1e293b',
  outline: 'none',
  boxSizing: 'border-box',
  fontFamily: 'Pretendard, sans-serif',
  transition: 'border-color 0.15s',
};
