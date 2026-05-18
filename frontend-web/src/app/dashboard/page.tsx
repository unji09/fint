'use client';

import { useCallback } from 'react';
import useBreakpoint from '@/hooks/useBreakpoint';
import { useConfirm, useAlert } from '@/components/common/ConfirmDialog';
import {
  useDashboardList,
  useDashboardTemplates,
  useCreateDashboard,
  useDeleteDashboard,
} from '@/hooks/useDashboard';
import DashboardHero from '@/components/dashboard/DashboardHero';
import TemplateCardGrid from '@/components/dashboard/TemplateCardGrid';
import DashboardList from '@/components/dashboard/DashboardList';

export default function DashboardPage() {
  const { dashboards, loading: listLoading, refetch: refetchList } = useDashboardList();
  const { groups: templateGroups, loading: templatesLoading } = useDashboardTemplates();
  const { create, loading: creating } = useCreateDashboard();
  const { remove: deleteDashboard, loading: deleting } = useDeleteDashboard();
  const confirm = useConfirm();
  const alert = useAlert();

  // 자연어 검색 — 새 대시보드를 만들어서 그 화면에서 분석을 시작한다.
  // 502 (LLM/외부 API 장애) 같은 케이스에서는 빈 대시보드 생성으로 fallback,
  // 사용자는 새 대시보드 화면 진입 후 다시 시도할 수 있다. pendingQuery 도 유지해
  // dashboard/[id] 마운트 시 자동 재시도된다.
  const handleQuerySubmit = useCallback(
    async (inputText: string) => {
      const trimmed = inputText.trim();
      if (!trimmed) return;
      try { sessionStorage.setItem('fint:pendingQuery', trimmed); } catch { /* ignore */ }
      try { sessionStorage.removeItem('fint:allDashboards'); } catch { /* ignore */ }
      try {
        await create({ inputText: trimmed });
        return;
      } catch (err) {
        const msg = err instanceof Error ? err.message : '';
        if (msg === 'UNAUTHORIZED') {
          try { sessionStorage.removeItem('fint:pendingQuery'); } catch { /* ignore */ }
          await alert('로그인 세션이 만료되었어요.\n다시 로그인한 뒤 시도해 주세요.');
          return;
        }
        // 502 (외부 API 장애) 또는 그 외 HTTP 에러 → 빈 대시보드 fallback
        if (msg.startsWith('HTTP_')) {
          try {
            await create({ title: '제목없음' });
            return;
          } catch { /* fallback도 실패하면 아래 alert */ }
        }
        try { sessionStorage.removeItem('fint:pendingQuery'); } catch { /* ignore */ }
        if (msg.startsWith('HTTP_')) {
          await alert(
            `분석 서버에서 응답을 받지 못했어요. (코드: ${msg.replace('HTTP_', '')})\n` +
            '잠시 후 다시 시도해 주세요.',
          );
        } else {
          await alert('서버에 연결할 수 없습니다.\n네트워크 상태를 확인해 주세요.');
        }
      }
    },
    [create],
  );
  // 추천 템플릿 그룹 카드 클릭 — 그 그룹의 위젯 8개로 새 대시보드를 생성한다.
  const handleTemplateSelect = useCallback(
    async (groupId: number) => {
      try {
        try { sessionStorage.removeItem('fint:allDashboards'); } catch { /* ignore */ }
        await create({ templateId: groupId });
      } catch (err) {
        const msg = err instanceof Error ? err.message : '';
        if (msg === 'UNAUTHORIZED') {
          await alert('로그인 세션이 만료되었어요.\n다시 로그인한 뒤 시도해 주세요.');
        } else if (msg.startsWith('HTTP_')) {
          await alert(`서버에서 응답을 받지 못했어요. (코드: ${msg.replace('HTTP_', '')})`);
        } else {
          await alert('템플릿으로 대시보드를 만들지 못했어요.');
        }
      }
    },
    [create],
  );
  const handleCreateNew = useCallback(async () => {
    await create({ title: '제목없음' });
  }, [create]);

  const handleDelete = useCallback(
    async (dashboardId: number, title: string) => {
      if (deleting) return;
      const ok = await confirm({
        message: `'${title}' 대시보드를 삭제할까요?\n\n이 대시보드 안의 위젯과 분석 내용이 모두 사라지며,\n한 번 삭제하면 복구할 수 없습니다.`,
        variant: 'danger',
        confirmText: '삭제',
      });
      if (!ok) return;
      const success = await deleteDashboard(dashboardId);
      if (success) {
        await refetchList();
        try { sessionStorage.removeItem('fint:allDashboards'); } catch { /* ignore */ }
        await alert(`'${title}' 대시보드를 삭제했어요.`);
      } else {
        await alert(`'${title}' 대시보드를 삭제하지 못했습니다.\n잠시 후 다시 시도해 주세요.`);
      }
    },
    [deleteDashboard, deleting, refetchList],
  );

  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';
  const isTablet = bp === 'tablet';

  return (
    <>
      {/* 베이스 — 원래 컨셉 라벤더 */}
      <div style={{ position: 'fixed', inset: 0, zIndex: -2, backgroundColor: '#f1f3ff' }} />
      {/* 입체감 — 위쪽 부드러운 highlight + 가장자리 미세한 비네팅 (질감/색 추가 없이) */}
      <div
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: -1,
          background:
            'radial-gradient(ellipse 75% 60% at 50% 10%, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0) 65%),' +
            'radial-gradient(ellipse 110% 110% at 50% 55%, transparent 60%, rgba(15,23,42,0.06) 100%)',
          pointerEvents: 'none',
        }}
      />

      {/*
       * GNB 아래 남은 뷰포트 전체를 flex column으로 채움
       * → 스크롤 없이 한 화면에 딱 맞게 분배
       */}
      <div
        style={{
          position: 'fixed',
          top: 64,
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          flexDirection: 'column',
          overflowY: 'auto',
        }}
      >
        <div
          style={{
            maxWidth: 1440,
            width: '100%',
            boxSizing: 'border-box',
            margin: '0 auto',
            padding: isMobile ? '20px 16px 16px' : isTablet ? '24px 24px 20px' : '28px 40px 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: 20,
          }}
        >
          {/* Hero — 고정 높이, shrink 금지 */}
          <div style={{ display: 'flex', justifyContent: 'center', flexShrink: 0 }}>
            <DashboardHero onSubmit={handleQuerySubmit} loading={creating} />
          </div>

          {/* 템플릿 카드 — 최소 320px 확보, 카드 미리보기가 짜부라지지 않도록 */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              flexShrink: 0,
              minHeight: isMobile ? 0 : 320,
            }}
          >
            <TemplateCardGrid
              groups={templateGroups}
              onSelect={handleTemplateSelect}
              loading={templatesLoading}
            />
          </div>

          {/* 대시보드 목록 — 컨텐츠 기반 높이 + 카드 많아지면 자체 maxHeight 안에서 스크롤 */}
          <div style={{ flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
            <DashboardList
              dashboards={dashboards}
              loading={listLoading}
              onCreateNew={handleCreateNew}
              onDelete={handleDelete}
              deleting={deleting}
            />
          </div>
        </div>
      </div>
    </>
  );
}
