'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import type { Dashboard, DashboardWidget } from '@/types/dashboard';
import GridBg from '@/components/dashboard/GridBg';
import CanvasWidgetCard from '@/components/dashboard/CanvasWidgetCard';
import FintChatPanel from '@/components/dashboard/FintChatPanel';
import type { CanvasWidget, Step } from '@/types/dashboard';
import QueryBar from '@/components/dashboard/QueryBar';
import { BarChartSvg } from '@/components/dashboard/ChartWidgets';
import { fetchEventSource } from '@microsoft/fetch-event-source';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';
const STEP_LABELS = ['사용자 의도 파악', '데이터 조회', '컴포넌트 조합 완료', '스타일링 중...'];

function authHeader() {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}` } : {};
}

export default function DashboardDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  const [allDashboards, setAllDashboards] = useState<Dashboard[]>([]);
  const [loadingPage, setLoadingPage] = useState(true);
  const [canvasWidgets, setCanvasWidgets] = useState<CanvasWidget[]>([]);
  const canvasRef = useRef<HTMLDivElement>(null);

  /* 채팅 상태 */
  const [querying, setQuerying] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [chatDone, setChatDone] = useState(false);
  const [steps, setSteps] = useState<Step[]>([]);
  const [userQuery, setUserQuery] = useState('');
  const [queryInput, setQueryInput] = useState('');
  const [widgetTitle, setWidgetTitle] = useState('');
  const [pendingType, setPendingType] = useState('BAR_CHART');
  /* SSE complete 시 받아둔 위젯 데이터 */
  const [pendingWidget, setPendingWidget] = useState<CanvasWidget | null>(null);

  /* 드래그 고스트 */
  const [dragging, setDragging] = useState(false);
  const [ghostPos, setGhostPos] = useState({ x: 0, y: 0 });
  const dragOrigin = useRef({ ox: 0, oy: 0 });

  useEffect(() => {
    fetch(`${API_BASE}/dashboards/${id}`, { headers: authHeader() as HeadersInit })
      .then((r) => r.json())
      .then((j) => {
        const d = j.data ?? j;
        setCanvasWidgets(
          (d.widgets ?? []).map((w: DashboardWidget, i: number) => ({
            ...w,
            px: 28 + i * 30,
            py: 28 + i * 20,
            pw: 400,
            ph: 260,
          })),
        );
      })
      .catch(() => {})
      .finally(() => setLoadingPage(false));

    fetch(`${API_BASE}/dashboards`, { headers: authHeader() as HeadersInit })
      .then((r) => r.json())
      .then((j) => setAllDashboards(j.data ?? []))
      .catch(() =>
        setAllDashboards([
          { dashboardId: 1, title: '기본', thumbnailUrl: null, lastAccessedAt: null },
          { dashboardId: 2, title: '제목없음', thumbnailUrl: null, lastAccessedAt: null },
        ]),
      );
  }, [id]);

  const updateWidget = useCallback((wid: number, changes: Partial<CanvasWidget>) => {
    setCanvasWidgets((prev) => prev.map((w) => (w.widgetId === wid ? { ...w, ...changes } : w)));
  }, []);

  const updateTitle = useCallback((wid: number, t: string) => {
    setCanvasWidgets((prev) => prev.map((w) => (w.widgetId === wid ? { ...w, title: t } : w)));
  }, []);

  const handleDragStart = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      dragOrigin.current = { ox: e.clientX, oy: e.clientY };
      setGhostPos({ x: e.clientX, y: e.clientY });
      setDragging(true);

      const onMove = (ev: MouseEvent) => setGhostPos({ x: ev.clientX, y: ev.clientY });
      const onUp = (ev: MouseEvent) => {
        setDragging(false);
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
        const canvas = canvasRef.current;
        if (!canvas) return;
        const rect = canvas.getBoundingClientRect();
        if (
          ev.clientX >= rect.left &&
          ev.clientX <= rect.right &&
          ev.clientY >= rect.top &&
          ev.clientY <= rect.bottom
        ) {
          const px = ev.clientX - rect.left + canvas.scrollLeft - 180;
          const py = ev.clientY - rect.top + canvas.scrollTop - 100;
          const newW: CanvasWidget = pendingWidget
            ? {
                ...pendingWidget,
                px: Math.max(0, px),
                py: Math.max(0, py),
              }
            : {
                widgetId: Date.now(),
                widgetType: pendingType,
                title: widgetTitle,
                config: {},
                position: { x: 0, y: 0, w: 6, h: 4 },
                queryId: null,
                inputText: userQuery,
                result: { data: {}, insightText: '' },
                px: Math.max(0, px),
                py: Math.max(0, py),
                pw: 400,
                ph: 260,
              };
          setCanvasWidgets((prev) => [...prev, newW]);
          setChatOpen(false);
          setChatDone(false);
        }
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    },
    [pendingType, widgetTitle, userQuery, pendingWidget],
  );

  const handleQuery = useCallback(
    async (text: string) => {
      if (!text.trim() || querying) return;
      setUserQuery(text);
      setQuerying(true);
      setChatOpen(true);
      setChatDone(false);
      setQueryInput('');
      setPendingWidget(null);

      const autoTitle = text.replace(/어때\?*|보여줘|분석해줘|알려줘|\?\?*/g, '').trim() || text;
      setWidgetTitle(autoTitle);

      setSteps(STEP_LABELS.map((l, idx) => ({ label: l, done: false, active: idx === 0 })));

      try {
        const startRes = await fetch(`${API_BASE}/dashboards/${id}/queries`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(authHeader() as Record<string, string>),
          },
          body: JSON.stringify({ inputText: text }),
        });
        if (!startRes.ok) throw new Error(`HTTP ${startRes.status}`);
        const startJson = await startRes.json();
        const traceId = startJson.data?.traceId ?? startJson.traceId;

        const ctrl = new AbortController();
        fetchEventSource(`${API_BASE}/dashboards/queries/${traceId}/stream`, {
          method: 'GET',
          headers: { ...(authHeader() as Record<string, string>) },
          signal: ctrl.signal,
          openWhenHidden: true,
          onmessage(ev) {
            try {
              const data = JSON.parse(ev.data);
              if (ev.event === 'progress') {
                const stepNum = data.stepNumber ?? 0;
                setSteps(
                  STEP_LABELS.map((l, idx) => ({
                    label: l,
                    done: idx < stepNum,
                    active: idx === stepNum,
                  })),
                );
                return;
              }
              if (ev.event === 'complete') {
                setWidgetTitle(data.title ?? autoTitle);
                setPendingType(data.widgetType ?? 'BAR_CHART');
                setPendingWidget({
                  widgetId: data.widgetId ?? Date.now(),
                  widgetType: data.widgetType ?? 'BAR_CHART',
                  title: data.title ?? autoTitle,
                  config: data.config ?? {},
                  position: data.position ?? { x: 0, y: 0, w: 6, h: 4 },
                  queryId: data.queryId ?? null,
                  inputText: text,
                  result: data.result ?? { data: {}, insightText: '' },
                  px: 0,
                  py: 0,
                  pw: 400,
                  ph: 260,
                });
                setSteps(STEP_LABELS.map((l) => ({ label: l, done: true, active: false })));
                setQuerying(false);
                setChatDone(true);
                ctrl.abort();
                return;
              }
              if (ev.event === 'error') {
                console.error('Query error:', data.message);
                setQuerying(false);
                setChatDone(true);
                ctrl.abort();
              }
            } catch {
              /* ignore parse error */
            }
          },
          onerror(err) {
            console.error('SSE connection error:', err);
            setQuerying(false);
            setChatDone(true);
            // throw 로 fetchEventSource 의 자동 재시도 차단
            throw err;
          },
        }).catch(() => {
          /* abort 로 인한 reject 무시 */
        });
      } catch (err) {
        console.error('Query start failed:', err);
        setQuerying(false);
        setChatDone(true);
      }
    },
    [querying, id],
  );

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, zIndex: -1, backgroundColor: '#f2f5ff' }} />
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
        {/* 탭 바 */}
        <div
          style={{
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            padding: '4px 16px',
            height: 36,
            background: 'rgba(255,255,255,0.88)',
            backdropFilter: 'blur(8px)',
            borderBottom: '1px solid #e2e8f0',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              background: 'rgba(241,243,255,0.9)',
              borderRadius: 6,
              padding: '3px 4px',
              border: '1px solid #f1f3ff',
              boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
            }}
          >
            {loadingPage ? (
              <div style={{ height: 24, width: 60, background: '#f1f5f9', borderRadius: 4 }} />
            ) : (
              allDashboards.map((d) => (
                <button
                  key={d.dashboardId}
                  onClick={() => router.push(`/dashboard/${d.dashboardId}`)}
                  style={{
                    padding: '4px 12px',
                    borderRadius: 4,
                    border: 'none',
                    cursor: 'pointer',
                    fontFamily: 'Pretendard,sans-serif',
                    fontWeight: 400,
                    fontSize: 13,
                    background: String(d.dashboardId) === String(id) ? '#06b6d4' : 'transparent',
                    color: String(d.dashboardId) === String(id) ? 'white' : '#6d797d',
                    transition: 'all 0.15s',
                  }}
                >
                  {d.title}
                </button>
              ))
            )}
            <button
              onClick={() => router.push('/dashboard')}
              style={{
                width: 26,
                height: 26,
                borderRadius: 4,
                border: 'none',
                background: 'transparent',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#6d797d',
                fontSize: 18,
                marginLeft: 2,
              }}
            >
              +
            </button>
          </div>
        </div>

        {/* 캔버스 */}
        <div ref={canvasRef} style={{ flex: 1, position: 'relative', overflow: 'auto' }}>
          <GridBg />
          <div style={{ position: 'relative', minWidth: '100%', minHeight: '100%' }}>
            {canvasWidgets.map((w) => (
              <CanvasWidgetCard
                key={w.widgetId}
                w={w}
                onUpdate={updateWidget}
                onTitleChange={updateTitle}
              />
            ))}
          </div>

          {/* FINT 채팅 + 검색바 */}
          <div
            style={{
              position: 'fixed',
              bottom: 20,
              left: 20,
              zIndex: 20,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'flex-start',
            }}
          >
            {chatOpen && (
              <FintChatPanel
                steps={steps}
                query={userQuery}
                isLoading={querying}
                isDone={chatDone}
                widgetTitle={widgetTitle}
                widgetType={pendingType}
                result={pendingWidget?.result}
                onTitleChange={setWidgetTitle}
                onCollapse={() => setChatOpen(false)}
                onDragStart={handleDragStart}
              />
            )}
            <QueryBar
              value={queryInput}
              onChange={setQueryInput}
              onSubmit={handleQuery}
              loading={querying}
            />
          </div>
        </div>
      </div>

      {/* 드래그 고스트 */}
      {dragging && (
        <div
          style={{
            position: 'fixed',
            left: ghostPos.x - 160,
            top: ghostPos.y - 60,
            width: 320,
            pointerEvents: 'none',
            zIndex: 9999,
            opacity: 0.85,
            background: 'rgba(255,255,255,0.9)',
            backdropFilter: 'blur(12px)',
            border: '1.5px solid #06b6d4',
            borderRadius: 10,
            boxShadow: '0 8px 24px rgba(6,182,212,0.3)',
            padding: '10px 14px',
            transform: 'rotate(2deg)',
          }}
        >
          <div
            style={{
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 13,
              color: '#171d1e',
              marginBottom: 6,
            }}
          >
            {widgetTitle}
          </div>
          <BarChartSvg size="mini" />
        </div>
      )}
      <style>{`@keyframes spin { to { transform: rotate(360deg); } } * { box-sizing: border-box; }`}</style>
    </>
  );
}
