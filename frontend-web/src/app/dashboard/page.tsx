'use client';

import { useCallback } from 'react';
import { useDashboardList, useDashboardTemplates, useCreateDashboard } from '@/hooks/useDashboard';
import DashboardHero from '@/components/dashboard/DashboardHero';
import TemplateCardGrid from '@/components/dashboard/TemplateCardGrid';
import DashboardList from '@/components/dashboard/DashboardList';

export default function DashboardPage() {
  const { dashboards, loading: listLoading } = useDashboardList();
  const { templates, loading: templatesLoading } = useDashboardTemplates();
  const { create, loading: creating } = useCreateDashboard();

  const handleQuerySubmit = useCallback(
    async (inputText: string) => {
      await create({ inputText });
    },
    [create],
  );
  const handleTemplateSelect = useCallback(
    async (templateId: number) => {
      await create({ templateId });
    },
    [create],
  );
  const handleCreateNew = useCallback(async () => {
    await create({ title: '제목없음' });
  }, [create]);

  return (
    <>
      {/* 전체 배경 — GNB 포함 흰 공간 없이 덮음 */}
      <div style={{ position: 'fixed', inset: 0, backgroundColor: '#f1f3ff', zIndex: -1 }} />

      {/*
       * GNB 아래 남은 뷰포트 전체를 flex column으로 채움
       * → 스크롤 없이 한 화면에 딱 맞게 분배
       */}
      <div
        style={{
          position: 'fixed',
          top: 90,
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            flex: 1,
            maxWidth: 1200,
            width: '100%',
            margin: '0 auto',
            padding: '28px 32px 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: 20,
            minHeight: 0 /* flex child overflow 방지 */,
          }}
        >
          {/* Hero — 고정 높이, shrink 금지 */}
          <div style={{ display: 'flex', justifyContent: 'center', flexShrink: 0 }}>
            <DashboardHero onSubmit={handleQuerySubmit} loading={creating} />
          </div>

          {/* 템플릿 카드 — 남은 공간의 절반 차지 */}
          <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <TemplateCardGrid
              templates={templates}
              onSelect={handleTemplateSelect}
              loading={templatesLoading}
            />
          </div>

          {/* 대시보드 목록 — 남은 공간의 절반 차지 */}
          <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <DashboardList
              dashboards={dashboards}
              loading={listLoading}
              onCreateNew={handleCreateNew}
            />
          </div>
        </div>
      </div>
    </>
  );
}
