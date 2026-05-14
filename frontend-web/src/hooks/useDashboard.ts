'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Dashboard, TemplateGroup, CreateDashboardRequest } from '@/types/dashboard';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

async function fetchWithAuth<T>(url: string, options?: RequestInit): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  const res = await fetch(`${API_BASE}${url}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  return json.data as T;
}

export function useDashboardList() {
  const [dashboards, setDashboards] = useState<Dashboard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const applyTitleOverrides = (list: Dashboard[]): Dashboard[] => {
    let map: Record<string, string> = {};
    try { map = JSON.parse(localStorage.getItem('fint:dashboardTitles') ?? '{}'); } catch { /* ignore */ }
    return list.map((d) => (map[String(d.dashboardId)] ? { ...d, title: map[String(d.dashboardId)] } : d));
  };

  const fetch = useCallback(async () => {
    try {
      setLoading(true);
      const data = await fetchWithAuth<Dashboard[]>('/dashboards');
      setDashboards(applyTitleOverrides(data ?? []));
    } catch (e) {
      setError('대시보드 목록을 불러오지 못했습니다.');
      // 개발 중 mock 데이터 (사용자가 바꾼 이름이 있으면 그것도 머지)
      setDashboards(applyTitleOverrides([
        {
          dashboardId: 1,
          title: '기본',
          thumbnailKey: null,
          lastAccessedAt: new Date(Date.now() - 86400000).toISOString(),
        },
        {
          dashboardId: 2,
          title: '제목없음',
          thumbnailKey: null,
          lastAccessedAt: new Date(Date.now() - 3600000).toISOString(),
        },
      ]));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { dashboards, loading, error, refetch: fetch };
}

export function useDashboardTemplates() {
  const [groups, setGroups] = useState<TemplateGroup[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchWithAuth<TemplateGroup[]>('/dashboards/templates')
      .then((data) => setGroups(data ?? []))
      .catch(() => setGroups([]))
      .finally(() => setLoading(false));
  }, []);

  return { groups, loading };
}

// ─── 위젯 업데이트 ──────────────────────────────────────────────────────────

export function useUpdateWidget() {
  const [loading, setLoading] = useState(false);

  const update = useCallback(
    async (dashboardId: number, widgetId: number, req: Record<string, unknown>) => {
      setLoading(true);
      try {
        // 공통 fetchWithAuth 는 res.json() 강제 호출하므로 204 No Content 응답에서 throw 됨.
        // 위젯 PATCH 는 본문 없을 수 있어 직접 fetch 로 처리해 res.ok 만 본다.
        const token =
          typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
        const res = await fetch(
          `${API_BASE}/dashboards/${dashboardId}/widgets/${widgetId}`,
          {
            method: 'PATCH',
            headers: {
              'Content-Type': 'application/json',
              ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify(req),
          },
        );
        return res.ok;
      } catch {
        return false;
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  return { update, loading };
}

// ─── 위젯 삭제 ─────────────────────────────────────────────────────────────

export function useDeleteWidget() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (dashboardId: number, widgetId: number) => {
    setLoading(true);
    try {
      const token =
        typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
      const res = await fetch(
        `${API_BASE}/dashboards/${dashboardId}/widgets/${widgetId}`,
        {
          method: 'DELETE',
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        },
      );
      return res.ok;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { remove, loading };
}

// ─── 대시보드 쿼리 (SSE 스트림) ──────────────────────────────────────────────

export interface QueryStartResult {
  traceId: string;
  queryId: number;
}

export function useDashboardQuery() {
  const [loading, setLoading] = useState(false);
  const [traceId, setTraceId] = useState<string | null>(null);

  const startQuery = useCallback(async (dashboardId: number, inputText: string) => {
    setLoading(true);
    try {
      const data = await fetchWithAuth<QueryStartResult>(
        `/dashboards/${dashboardId}/queries`,
        { method: 'POST', body: JSON.stringify({ inputText }) },
      );
      setTraceId(data.traceId);
      return data;
    } catch (err) {
      console.error('[useDashboardQuery] 쿼리 시작 실패', err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const streamResults = useCallback(
    (tid: string, onMessage: (data: unknown) => void, onDone: () => void) => {
      const token =
        typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
      const url = `${API_BASE}/dashboards/queries/${tid}/stream`;
      const eventSource = new EventSource(
        `${url}${token ? `?token=${encodeURIComponent(token)}` : ''}`,
      );

      eventSource.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data);
          onMessage(parsed);
        } catch {
          onMessage(event.data);
        }
      };

      eventSource.addEventListener('done', () => {
        eventSource.close();
        onDone();
      });

      eventSource.onerror = () => {
        eventSource.close();
        onDone();
      };

      return () => eventSource.close();
    },
    [],
  );

  return { startQuery, streamResults, traceId, loading };
}

// ─── 썸네일 업로드 ──────────────────────────────────────────────────────────

const MAX_THUMBNAIL_WIDTH = 640;
const THUMBNAIL_QUALITY = 0.7;

function compressImage(blob: Blob): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(blob);
    img.onload = () => {
      URL.revokeObjectURL(url);
      const ratio = Math.min(1, MAX_THUMBNAIL_WIDTH / img.width);
      const w = Math.round(img.width * ratio);
      const h = Math.round(img.height * ratio);
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d');
      if (!ctx) return reject(new Error('Canvas context failed'));
      ctx.drawImage(img, 0, 0, w, h);
      canvas.toBlob(
        (result) => (result ? resolve(result) : reject(new Error('toBlob failed'))),
        'image/png',
        THUMBNAIL_QUALITY,
      );
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Image load failed'));
    };
    img.src = url;
  });
}

export function useThumbnailUpload() {
  const [uploading, setUploading] = useState(false);

  const upload = useCallback(async (dashboardId: number, imageBlob: Blob) => {
    setUploading(true);
    try {
      const compressed = await compressImage(imageBlob);
      const fileName = `thumb_${dashboardId}_${Date.now()}.png`;

      const { uploadUrl, fileKey } = await fetchWithAuth<{
        uploadUrl: string;
        fileKey: string;
      }>('/files/presigned-url', {
        method: 'POST',
        body: JSON.stringify({
          fileName,
          contentType: 'image/png',
          fileType: 'IMAGE',
          purpose: 'THUMBNAIL',
          fileSize: compressed.size,
          dashboardId,
        }),
      });

      const s3Res = await fetch(uploadUrl, {
        method: 'PUT',
        body: compressed,
        headers: {
          'Content-Type': 'image/png',
          'x-amz-server-side-encryption': 'aws:kms',
          'x-amz-server-side-encryption-aws-kms-key-id': 'alias/crm-fint-s3-key',
        },
      });
      if (!s3Res.ok) throw new Error('S3 업로드 실패');

      const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
      const patchRes = await fetch(`${API_BASE}/dashboards/${dashboardId}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ thumbnailKey: fileKey }),
      });
      if (!patchRes.ok) throw new Error('thumbnailKey 저장 실패');

      return fileKey;
    } finally {
      setUploading(false);
    }
  }, []);

  return { upload, uploading };
}

// ─── 대시보드 이름 변경 ─────────────────────────────────────────────────────

export function useRenameDashboard() {
  const [loading, setLoading] = useState(false);

  const rename = useCallback(async (dashboardId: number, title: string) => {
    setLoading(true);
    try {
      const token =
        typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
      const res = await fetch(`${API_BASE}/dashboards/${dashboardId}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ title }),
      });
      return res.ok;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { rename, loading };
}

// ─── 대시보드 삭제 ───────────────────────────────────────────────────────────

export function useDeleteDashboard() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (dashboardId: number) => {
    setLoading(true);
    try {
      // DELETE 응답은 204 No Content가 일반적이라 res.json() 호출하지 않는다.
      // 공통 fetchWithAuth 헬퍼는 항상 json 파싱을 시도해 빈 본문에서 실패함.
      const token =
        typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
      const res = await fetch(`${API_BASE}/dashboards/${dashboardId}`, {
        method: 'DELETE',
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      return res.ok;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { remove, loading };
}

// ─── 대시보드 생성 ───────────────────────────────────────────────────────────

export function useCreateDashboard() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  const create = useCallback(
    async (req: CreateDashboardRequest) => {
      setLoading(true);
      try {
        const token =
          typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
        const res = await fetch(`${API_BASE}/dashboards`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify(req),
        });
        console.log('[FINT] POST /dashboards', { status: res.status, req });
        if (res.status === 401) throw new Error('UNAUTHORIZED');
        if (!res.ok) throw new Error(`HTTP_${res.status}`);
        // 응답 본문이 비었거나 JSON이 아닐 수 있으니 안전 파싱
        let data: { dashboardId?: number; traceId?: string | null } = {};
        try {
          const text = await res.text();
          if (text) {
            const json = JSON.parse(text);
            data = json.data ?? json ?? {};
          }
        } catch { /* ignore parse error */ }
        if (data.dashboardId) {
          router.push(`/dashboard/${data.dashboardId}`);
        }
        return data as { dashboardId: number; traceId: string | null };
      } finally {
        setLoading(false);
      }
    },
    [router],
  );

  return { create, loading };
}
