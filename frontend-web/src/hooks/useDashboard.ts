import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Dashboard, DashboardTemplate, CreateDashboardRequest } from '@/types/dashboard';

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

  const fetch = useCallback(async () => {
    try {
      setLoading(true);
      const data = await fetchWithAuth<Dashboard[]>('/dashboards');
      setDashboards(data);
    } catch (e) {
      setError('대시보드 목록을 불러오지 못했습니다.');
      // 개발 중 mock 데이터
      setDashboards([
        { dashboardId: 1, title: '기본', updatedAt: new Date(Date.now() - 86400000).toISOString() },
        {
          dashboardId: 2,
          title: '제목없음',
          updatedAt: new Date(Date.now() - 3600000).toISOString(),
        },
      ]);
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
  const [templates, setTemplates] = useState<DashboardTemplate[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchWithAuth<DashboardTemplate[]>('/dashboards/templates')
      .then(setTemplates)
      .catch(() => {
        // mock templates during dev
        setTemplates([
          {
            templateId: 1,
            title: '기본 대시보드 1',
            widgetType: 'BAR_CHART',
            thumbnailUrl: null,
            config: { labels: ['리드', '제안', '계약'] },
            position: { x: 0, y: 0, w: 6, h: 4 },
          },
          {
            templateId: 2,
            title: '기본 대시보드 2',
            widgetType: 'TABLE',
            thumbnailUrl: null,
            config: {},
            position: { x: 0, y: 0, w: 6, h: 4 },
          },
          {
            templateId: 3,
            title: '기본 대시보드 3',
            widgetType: 'PIE',
            thumbnailUrl: null,
            config: {},
            position: { x: 0, y: 0, w: 6, h: 4 },
          },
        ]);
      })
      .finally(() => setLoading(false));
  }, []);

  return { templates, loading };
}

export function useCreateDashboard() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  const create = useCallback(
    async (req: CreateDashboardRequest) => {
      setLoading(true);
      try {
        const data = await fetchWithAuth<{ dashboardId: number; traceId: string | null }>(
          '/dashboards',
          { method: 'POST', body: JSON.stringify(req) },
        );
        router.push(`/dashboard/${data.dashboardId}`);
        return data;
      } finally {
        setLoading(false);
      }
    },
    [router],
  );

  return { create, loading };
}
