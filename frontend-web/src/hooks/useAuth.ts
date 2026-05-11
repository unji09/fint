'use client';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

let refreshPromise: Promise<boolean> | null = null;

/**
 * accessToken이 만료(401)되었을 때 refreshToken으로 재발급 시도.
 * 동시 호출 시 하나의 요청만 보내고 나머지는 대기.
 */
export async function refreshAccessToken(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return false;

    try {
      const res = await fetch(`${API_BASE}/auth/reissue`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!res.ok) {
        // refresh 실패 → 로그인 페이지로
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        window.location.href = '/login';
        return false;
      }

      const json = await res.json();
      const newAccessToken = json.data?.accessToken;
      if (newAccessToken) {
        localStorage.setItem('accessToken', newAccessToken);
        return true;
      }
      return false;
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

/**
 * fetch wrapper — 401 시 토큰 갱신 후 자동 재시도.
 */
export async function fetchWithAuth(url: string, options?: RequestInit): Promise<Response> {
  const doFetch = () => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) h['Authorization'] = `Bearer ${token}`;
    if (options?.headers) {
      const oh = options.headers;
      if (oh instanceof Headers) {
        oh.forEach((v, k) => { h[k] = v; });
      } else if (Array.isArray(oh)) {
        oh.forEach(([k, v]) => { h[k] = v; });
      } else {
        Object.assign(h, oh);
      }
    }
    const fullUrl = `${API_BASE}${url}`;
    return fetch(fullUrl, { ...options, headers: h });
  };

  let res = await doFetch();

  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      res = await doFetch();
    }
  }

  return res;
}
