'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import type { Dashboard, DashboardWidget, ChatMessage } from '@/types/dashboard';
import GridBg from '@/components/dashboard/GridBg';
import CanvasWidgetCard from '@/components/dashboard/CanvasWidgetCard';
import FintChatPanel from '@/components/dashboard/FintChatPanel';
import type { CanvasWidget, Step } from '@/types/dashboard';
import QueryBar from '@/components/dashboard/QueryBar';
import { BarChartSvg } from '@/components/dashboard/ChartWidgets';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import {
  useDeleteDashboard,
  useCreateDashboard,
  useRenameDashboard,
  useUpdateWidget,
  useDeleteWidget,
  useThumbnailUpload,
} from '@/hooks/useDashboard';
import html2canvas from 'html2canvas';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';
const STEP_LABELS = ['사용자 의도 파악', '데이터 조회', '컴포넌트 조합 완료', '스타일링 중...'];

function authHeader() {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}` } : {};
}

export default function DashboardDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();

  // SSR/hydration mismatch 방지: 초기값은 항상 [], 마운트 후 useEffect에서 캐시 복원
  const [allDashboards, setAllDashboards] = useState<Dashboard[]>([]);
  useEffect(() => {
    try {
      const cached = sessionStorage.getItem('fint:allDashboards');
      if (cached) setAllDashboards(JSON.parse(cached) as Dashboard[]);
    } catch { /* ignore */ }
  }, []);
  const [canvasWidgets, setCanvasWidgets] = useState<CanvasWidget[]>([]);
  const canvasRef = useRef<HTMLDivElement>(null);

  /* 썸네일 캡처 & 업로드 — 위젯 CUD 시 5분 throttle */
  const { upload: uploadThumbnail } = useThumbnailUpload();
  const lastThumbnailUploadRef = useRef<number>(0);
  const THUMBNAIL_COOLDOWN = 5 * 60 * 1000;

  const captureAndUpload = useCallback(async () => {
    const canvas = canvasRef.current;
    if (!canvas || !id) return;
    const now = Date.now();
    if (now - lastThumbnailUploadRef.current < THUMBNAIL_COOLDOWN) return;
    try {
      const shot = await html2canvas(canvas, {
        useCORS: true,
        backgroundColor: '#f2f5ff',
        scale: 0.5,
      });
      const blob = await new Promise<Blob | null>((res) =>
        shot.toBlob(res, 'image/png', 0.8),
      );
      if (blob) {
        await uploadThumbnail(Number(id), blob);
        lastThumbnailUploadRef.current = Date.now();
      }
    } catch {
      /* 캡처 실패 시 lastUploadedAt 갱신 안 함 → 다음 CUD에서 재시도 */
    }
  }, [id, uploadThumbnail]);

  /* 채팅 상태 */
  const [querying, setQuerying] = useState(false);
  const [chatOpen, setChatOpen] = useState(true);
  const [chatDone, setChatDone] = useState(false);
  const [queryErrorMsg, setQueryErrorMsg] = useState<string | null>(null);
  const [steps, setSteps] = useState<Step[]>([]);
  const [userQuery, setUserQuery] = useState('');
  const [queryInput, setQueryInput] = useState('');
  const [widgetTitle, setWidgetTitle] = useState('');
  const [pendingType, setPendingType] = useState('BAR_CHART');
  /* SSE complete 시 받아둔 위젯 데이터 */
  const [pendingWidget, setPendingWidget] = useState<CanvasWidget | null>(null);

  /* 채팅 내역 */
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([]);

  /* 드래그 고스트 */
  const [dragging, setDragging] = useState(false);
  const [ghostPos, setGhostPos] = useState({ x: 0, y: 0 });
  const dragOrigin = useRef({ ox: 0, oy: 0 });

  /* 채팅창 위치 — 좌측 하단 고정 (드래그 불가) */

  /* 대시보드 탭 삭제 */
  const { remove: deleteDashboard, loading: deletingDashboard } = useDeleteDashboard();
  /* 대시보드 이름 변경 (탭 더블클릭) */
  const { rename: renameDashboard } = useRenameDashboard();
  const [renamingId, setRenamingId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState('');
  const beginRename = useCallback((dashboardId: number, current: string) => {
    setRenamingId(dashboardId);
    setRenameDraft(current);
  }, []);
  const commitRename = useCallback(async () => {
    if (renamingId === null) return;
    const targetId = renamingId;
    const next = renameDraft.trim();
    setRenamingId(null);
    if (!next) return;
    const original = allDashboards.find((d) => d.dashboardId === targetId)?.title;
    if (next === original) return;
    // 낙관적 업데이트 + localStorage override (백엔드 실패해도 사용자 화면 유지)
    setAllDashboards((prev) => {
      const updated = prev.map((d) => (d.dashboardId === targetId ? { ...d, title: next } : d));
      try { sessionStorage.setItem('fint:allDashboards', JSON.stringify(updated)); } catch { /* ignore */ }
      return updated;
    });
    try {
      const map = JSON.parse(localStorage.getItem('fint:dashboardTitles') ?? '{}') as Record<string, string>;
      map[String(targetId)] = next;
      localStorage.setItem('fint:dashboardTitles', JSON.stringify(map));
    } catch { /* ignore */ }
    // 백엔드 시도 — 실패해도 화면은 유지하고 콘솔 경고만
    const ok = await renameDashboard(targetId, next);
    if (!ok) {
      console.warn('[FINT] 대시보드 이름 백엔드 저장 실패 (화면은 유지됨)');
    }
  }, [renamingId, renameDraft, allDashboards, renameDashboard]);
  /* 새 대시보드 생성 (탭바 + 버튼) */
  const { create: createDashboard, loading: creatingDashboard } = useCreateDashboard();
  const handleAddDashboard = useCallback(async () => {
    if (creatingDashboard) return;
    try {
      // useCreateDashboard 내부에서 router.push(`/dashboard/{newId}`)로 이동함
      await createDashboard({ title: '제목없음' });
      // 다음 페이지 마운트 시 새 목록을 새로 받도록 캐시 무효화
      try { sessionStorage.removeItem('fint:allDashboards'); } catch { /* ignore */ }
    } catch {
      window.alert('새 대시보드를 만들지 못했습니다.\n잠시 후 다시 시도해 주세요.');
    }
  }, [createDashboard, creatingDashboard]);

  const handleDeleteDashboard = useCallback(
    async (targetId: number, title: string) => {
      if (deletingDashboard) return;
      const proceed = window.confirm(
        `'${title}' 대시보드를 삭제할까요?\n\n` +
        `이 대시보드 안의 위젯과 분석 내용이 모두 사라지며,\n` +
        `한 번 삭제하면 복구할 수 없습니다.`,
      );
      if (!proceed) return;
      const ok = await deleteDashboard(targetId);
      if (!ok) {
        window.alert(`'${title}' 대시보드를 삭제하지 못했습니다.\n잠시 후 다시 시도해 주세요.`);
        return;
      }
      setAllDashboards((prev) => {
        const next = prev.filter((d) => d.dashboardId !== targetId);
        try { sessionStorage.setItem('fint:allDashboards', JSON.stringify(next)); } catch { /* ignore */ }
        if (String(targetId) === String(id)) {
          if (next.length > 0) router.push(`/dashboard/${next[0].dashboardId}`);
          else router.push('/dashboard');
        }
        return next;
      });
      window.alert(`'${title}' 대시보드를 삭제했어요.`);
    },
    [deleteDashboard, deletingDashboard, id, router],
  );

  useEffect(() => {
    // 1) localStorage 캐시 먼저 보여줌 (백엔드 응답 늦더라도 새로고침 후 즉시 보이게)
    try {
      const cached = localStorage.getItem(`fint:widgets:${id}`);
      if (cached) {
        const list = JSON.parse(cached) as CanvasWidget[];
        if (Array.isArray(list) && list.length > 0) setCanvasWidgets(list);
      }
    } catch { /* ignore */ }

    // 2) 백엔드 응답으로 덮어쓰기 (성공한 경우에만)
    fetch(`${API_BASE}/dashboards/${id}`, { headers: authHeader() as HeadersInit })
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => {
        if (!j) return; // 401/404 등 → 캐시 유지
        const d = j.data ?? j;
        const serverWidgets = (d.widgets ?? []) as DashboardWidget[];
        if (serverWidgets.length === 0) return; // 백엔드에 위젯 없으면 캐시 유지
        const GRID_COL = 100;
        const GRID_ROW = 80;
        const PAD = 28;
        const next = serverWidgets.map((w, i) => {
          const pos = w.position;
          const isGrid = pos && pos.w <= 12 && pos.h <= 16;
          return {
            ...w,
            px: isGrid ? PAD + pos.x * GRID_COL : (pos?.x ?? (28 + i * 30)),
            py: isGrid ? PAD + pos.y * GRID_ROW : (pos?.y ?? (28 + i * 20)),
            pw: isGrid ? pos.w * GRID_COL : (pos?.w ?? 400),
            ph: isGrid ? pos.h * GRID_ROW : (pos?.h ?? 260),
          };
        });
        setCanvasWidgets(next);
        try { localStorage.setItem(`fint:widgets:${id}`, JSON.stringify(next)); } catch { /* ignore */ }
      })
      .catch((err) => { console.warn('[FINT] GET /dashboards/{id} 실패', err); });

    // 3) 이전 대화 내역 로드
    fetch(`${API_BASE}/dashboards/${id}/queries`, { headers: authHeader() as HeadersInit })
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => {
        if (!j) return;
        const queries = (j.data ?? j) as Array<{
          queryId?: number;
          inputText?: string;
          widgetType?: string;
          title?: string;
          result?: { data?: unknown; insightText?: string };
          config?: Record<string, unknown>;
          createdAt?: string;
          status?: string;
          errorMessage?: string;
        }>;
        if (!Array.isArray(queries) || queries.length === 0) return;
        const msgs: ChatMessage[] = [];
        for (const q of queries) {
          const ts = q.createdAt ?? new Date().toISOString();
          // user message
          msgs.push({
            id: `user-${q.queryId ?? Date.now()}-${msgs.length}`,
            role: 'user',
            content: q.inputText ?? '',
            widget: null,
            timestamp: ts,
            status: 'done',
          });
          // assistant message
          const isError = q.status === 'ERROR' || q.status === 'FAILED';
          msgs.push({
            id: `assistant-${q.queryId ?? Date.now()}-${msgs.length}`,
            role: 'assistant',
            content: q.result?.insightText ?? (isError ? '' : ''),
            widget: isError ? null : {
              widgetType: q.widgetType ?? 'BAR_CHART',
              title: q.title ?? '',
              data: q.result?.data ?? {},
              config: q.config ?? {},
            },
            timestamp: ts,
            status: isError ? 'error' : 'done',
            errorMessage: isError ? (q.errorMessage ?? '쿼리 처리에 실패했습니다.') : undefined,
          });
        }
        setChatHistory(msgs);
      })
      .catch((err) => { console.warn('[FINT] GET /dashboards/{id}/queries 실패', err); });

    fetch(`${API_BASE}/dashboards`, { headers: authHeader() as HeadersInit })
      .then(async (r) => {
        // 인증 실패는 강제 redirect 막기 위해 따로 표시해 둔다
        if (r.status === 401 || r.status === 403) {
          return { __unauthorized: true } as const;
        }
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((j) => {
        if (j && (j as { __unauthorized?: boolean }).__unauthorized) {
          // 인증 실패: 현재 화면 그대로 두고 빈 목록만 표시 (강제 redirect 금지)
          setAllDashboards([]);
          return;
        }
        const raw = ((j.data ?? []) as Dashboard[])
          .slice()
          .sort((a, b) => a.dashboardId - b.dashboardId);
        // localStorage 의 사용자 변경 이름이 있으면 그걸 우선
        let titleMap: Record<string, string> = {};
        try { titleMap = JSON.parse(localStorage.getItem('fint:dashboardTitles') ?? '{}'); } catch { /* ignore */ }
        const list = raw.map((d) => (titleMap[String(d.dashboardId)] ? { ...d, title: titleMap[String(d.dashboardId)] } : d));
        setAllDashboards(list);
        try { sessionStorage.setItem('fint:allDashboards', JSON.stringify(list)); } catch { /* ignore */ }
        // 응답이 정상이고 목록도 받았을 때에만 redirect 보호 코드 작동
        if (list.length === 0) {
          router.replace('/dashboard');
        } else if (!list.some((d) => String(d.dashboardId) === String(id))) {
          router.replace(`/dashboard/${list[0].dashboardId}`);
        }
      })
      .catch(() =>
        setAllDashboards([
          { dashboardId: 1, title: '기본', thumbnailKey: null, lastAccessedAt: null },
          { dashboardId: 2, title: '제목없음', thumbnailKey: null, lastAccessedAt: null },
        ]),
      );
  }, [id, router]);

  /* 위젯 영속화 — PATCH 백엔드 시도 + localStorage 캐시로 새로고침 보장 */
  const { update: patchWidget } = useUpdateWidget();
  const persistTimersRef = useRef<Record<number, ReturnType<typeof setTimeout>>>({});

  const cacheKey = id ? `fint:widgets:${id}` : null;
  const cacheWidgets = useCallback((widgets: CanvasWidget[]) => {
    if (!cacheKey) return;
    try { localStorage.setItem(cacheKey, JSON.stringify(widgets)); } catch { /* ignore */ }
  }, [cacheKey]);

  const widgetToBody = useCallback((w: CanvasWidget): Record<string, unknown> => ({
    title: w.title,
    widgetType: w.widgetType,
    config: w.config,
    position: {
      x: Math.round(w.px),
      y: Math.round(w.py),
      w: Math.round(w.pw),
      h: Math.round(w.ph),
    },
  }), []);

  const persistWidget = useCallback(async (w: CanvasWidget) => {
    if (!id) return;
    const ok = await patchWidget(Number(id), w.widgetId, widgetToBody(w));
    console.log('[FINT] persistWidget', { widgetId: w.widgetId, ok });
    if (ok) void captureAndUpload();
  }, [id, patchWidget, widgetToBody, captureAndUpload]);

  const schedulePersist = useCallback((w: CanvasWidget) => {
    const timers = persistTimersRef.current;
    if (timers[w.widgetId]) clearTimeout(timers[w.widgetId]);
    timers[w.widgetId] = setTimeout(() => {
      delete timers[w.widgetId];
      void persistWidget(w);
    }, 400);
  }, [persistWidget]);

  const updateWidget = useCallback((wid: number, changes: Partial<CanvasWidget>) => {
    setCanvasWidgets((prev) => {
      const next = prev.map((w) => (w.widgetId === wid ? { ...w, ...changes } : w));
      const updated = next.find((w) => w.widgetId === wid);
      if (updated) schedulePersist(updated);
      cacheWidgets(next);
      return next;
    });
  }, [schedulePersist, cacheWidgets]);

  const updateTitle = useCallback((wid: number, t: string) => {
    setCanvasWidgets((prev) => {
      const next = prev.map((w) => (w.widgetId === wid ? { ...w, title: t } : w));
      const updated = next.find((w) => w.widgetId === wid);
      if (updated) schedulePersist(updated);
      cacheWidgets(next);
      return next;
    });
  }, [schedulePersist, cacheWidgets]);

  /* 위젯 삭제 */
  const { remove: deleteWidgetApi } = useDeleteWidget();
  const removeWidget = useCallback(async (wid: number) => {
    const target = canvasWidgets.find((w) => w.widgetId === wid);
    if (!target) return;
    if (!window.confirm(`'${target.title}' 위젯을 삭제할까요?`)) return;
    // 낙관적 제거 + 캐시 갱신
    setCanvasWidgets((prev) => {
      const next = prev.filter((w) => w.widgetId !== wid);
      cacheWidgets(next);
      return next;
    });
    // 백엔드 시도 — 실패해도 화면은 유지
    if (id) {
      const ok = await deleteWidgetApi(Number(id), wid);
      console.log('[FINT] deleteWidget', { widgetId: wid, ok });
      if (ok) void captureAndUpload();
    }
  }, [canvasWidgets, deleteWidgetApi, id, cacheWidgets, captureAndUpload]);


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
                data: null,
                px: Math.max(0, px),
                py: Math.max(0, py),
                pw: 400,
                ph: 260,
              };
          setCanvasWidgets((prev) => {
            const merged = [...prev, newW];
            cacheWidgets(merged);
            return merged;
          });
          void persistWidget(newW);
          setChatDone(false);
          setPendingWidget(null);
        }
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    },
    [pendingType, widgetTitle, userQuery, pendingWidget, persistWidget, cacheWidgets],
  );

  const handleQuery = useCallback(
    async (text: string) => {
      if (!text.trim() || querying) return;
      setUserQuery(text);
      setQuerying(true);
      setChatOpen(true);
      setChatDone(false);
      setQueryErrorMsg(null);
      setQueryInput('');
      setPendingWidget(null);

      const autoTitle = text.replace(/어때\?*|보여줘|분석해줘|알려줘|\?\?*/g, '').trim() || text;
      setWidgetTitle(autoTitle);

      setSteps(STEP_LABELS.map((l, idx) => ({ label: l, done: false, active: idx === 0 })));

      // 채팅 내역에 사용자 메시지 추가
      const userMsgId = `user-${Date.now()}`;
      const userMsg: ChatMessage = {
        id: userMsgId,
        role: 'user',
        content: text,
        widget: null,
        timestamp: new Date().toISOString(),
        status: 'done',
      };
      setChatHistory((prev) => [...prev, userMsg]);

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
                  data: null,
                  px: 0,
                  py: 0,
                  pw: 400,
                  ph: 260,
                });
                setSteps(STEP_LABELS.map((l) => ({ label: l, done: true, active: false })));
                setQuerying(false);
                setChatDone(true);

                // 채팅 내역에 어시스턴트 메시지 추가
                const assistantMsg: ChatMessage = {
                  id: `assistant-${Date.now()}`,
                  role: 'assistant',
                  content: data.result?.insightText ?? '',
                  widget: {
                    widgetType: data.widgetType ?? 'BAR_CHART',
                    title: data.title ?? autoTitle,
                    data: data.result?.data ?? {},
                    config: data.config ?? {},
                  },
                  timestamp: new Date().toISOString(),
                  status: 'done',
                };
                setChatHistory((prev) => [...prev, assistantMsg]);

                ctrl.abort();
                return;
              }
              if (ev.event === 'error') {
                console.error('Query error:', data.message);
                const errMsg = data.message ?? '쿼리 처리에 실패했습니다.';
                setQueryErrorMsg(errMsg);
                setQuerying(false);
                setChatDone(true);

                // 채팅 내역에 에러 어시스턴트 메시지 추가
                const errorAssistantMsg: ChatMessage = {
                  id: `assistant-err-${Date.now()}`,
                  role: 'assistant',
                  content: '',
                  widget: null,
                  timestamp: new Date().toISOString(),
                  status: 'error',
                  errorMessage: errMsg,
                };
                setChatHistory((prev) => [...prev, errorAssistantMsg]);

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

  // 처음 화면(/dashboard) 검색바·칩에서 인계된 질문을 마운트 후 1회 자동 실행.
  // 두 경로 모두 지원:
  //   (a) URL search param  /dashboard/{id}?q=...
  //   (b) sessionStorage    fint:pendingQuery  (새 대시보드 생성 직후 진입)
  const consumedQueryRef = useRef<string | null>(null);
  useEffect(() => {
    let q: string | null = searchParams.get('q');
    const fromUrl = !!q;
    if (!q) {
      try { q = sessionStorage.getItem('fint:pendingQuery'); } catch { /* ignore */ }
      if (q) {
        try { sessionStorage.removeItem('fint:pendingQuery'); } catch { /* ignore */ }
      }
    }
    if (!q) return;
    if (consumedQueryRef.current === q) return;
    consumedQueryRef.current = q;
    if (fromUrl) {
      router.replace(`/dashboard/${id}`, { scroll: false });
    }
    handleQuery(q);
  }, [searchParams, handleQuery, router, id]);

  return (
    <>
      {/* 원래 컨셉 라벤더 + 부드러운 입체감 */}
      <div style={{ position: 'fixed', inset: 0, zIndex: -2, backgroundColor: '#f2f5ff' }} />
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
      <div
        style={{
          position: 'fixed',
          top: 64,
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
            gap: 8,
            padding: '4px 16px',
            height: 36,
            background: 'rgba(255,255,255,0.88)',
            backdropFilter: 'blur(8px)',
            borderBottom: '1px solid #e2e8f0',
          }}
        >
          {/* 대시보드 목록·템플릿 화면으로 돌아가기 — 아이콘 단독 */}
          <button
            onClick={() => router.push('/dashboard')}
            onMouseEnter={() => router.prefetch('/dashboard')}
            aria-label="대시보드 목록·템플릿으로"
            title="대시보드 목록·템플릿"
            style={{
              width: 28,
              height: 28,
              borderRadius: 6,
              border: 'none',
              background: 'transparent',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#94a3b8',
              transition: 'background-color 0.12s, color 0.12s',
              flexShrink: 0,
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.background = 'rgba(6,182,212,0.10)';
              e.currentTarget.style.color = '#06b6d4';
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.background = 'transparent';
              e.currentTarget.style.color = '#94a3b8';
            }}
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M8.5 2.5L4.5 7L8.5 11.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>

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
            {allDashboards.length === 0 ? (
              <div style={{ height: 24, width: 60, background: '#f1f5f9', borderRadius: 4 }} />
            ) : (
              allDashboards.map((d) => {
                const active = String(d.dashboardId) === String(id);
                return (
                  <div
                    key={d.dashboardId}
                    onMouseEnter={() => { if (!active) router.prefetch(`/dashboard/${d.dashboardId}`); }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 2,
                      padding: '2px 4px 2px 10px',
                      borderRadius: 4,
                      background: active ? '#06b6d4' : 'transparent',
                      transition: 'background-color 0.15s',
                    }}
                  >
                    {renamingId === d.dashboardId ? (
                      <input
                        autoFocus
                        value={renameDraft}
                        onChange={(e) => setRenameDraft(e.target.value)}
                        onBlur={commitRename}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') commitRename();
                          else if (e.key === 'Escape') setRenamingId(null);
                        }}
                        style={{
                          padding: '2px 4px',
                          border: 'none',
                          background: active ? 'rgba(255,255,255,0.2)' : '#fff',
                          outline: active ? '1px solid rgba(255,255,255,0.6)' : '1px solid #06b6d4',
                          borderRadius: 3,
                          fontFamily: 'Pretendard,sans-serif',
                          fontWeight: active ? 600 : 500,
                          fontSize: 13,
                          color: active ? 'white' : '#1e293b',
                          width: Math.max(60, renameDraft.length * 9 + 16),
                        }}
                      />
                    ) : (
                      <button
                        onClick={() => { if (!active) router.push(`/dashboard/${d.dashboardId}`); }}
                        onDoubleClick={(e) => { e.stopPropagation(); beginRename(d.dashboardId, d.title); }}
                        title="더블클릭하면 이름을 바꿀 수 있어요"
                        style={{
                          padding: '2px 2px',
                          border: 'none',
                          background: 'transparent',
                          cursor: active ? 'default' : 'pointer',
                          fontFamily: 'Pretendard,sans-serif',
                          fontWeight: active ? 600 : 400,
                          fontSize: 13,
                          color: active ? 'white' : '#6d797d',
                          transition: 'color 0.15s',
                        }}
                      >
                        {d.title}
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => handleDeleteDashboard(d.dashboardId, d.title)}
                      aria-label={`${d.title} 대시보드 삭제`}
                      title="이 대시보드 삭제"
                      disabled={deletingDashboard}
                      style={{
                        width: 18,
                        height: 18,
                        borderRadius: 4,
                        border: 'none',
                        background: 'transparent',
                        color: active ? 'rgba(255,255,255,0.85)' : '#94a3b8',
                        cursor: deletingDashboard ? 'wait' : 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        padding: 0,
                        transition: 'background-color 0.12s, color 0.12s',
                      }}
                      onMouseOver={(e) => {
                        e.currentTarget.style.background = active ? 'rgba(255,255,255,0.18)' : 'rgba(239,68,68,0.10)';
                        e.currentTarget.style.color = active ? '#fff' : '#ef4444';
                      }}
                      onMouseOut={(e) => {
                        e.currentTarget.style.background = 'transparent';
                        e.currentTarget.style.color = active ? 'rgba(255,255,255,0.85)' : '#94a3b8';
                      }}
                    >
                      <svg width="10" height="10" viewBox="0 0 12 12" fill="none">
                        <path d="M3 3l6 6M9 3l-6 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                      </svg>
                    </button>
                  </div>
                );
              })
            )}
            <button
              onClick={handleAddDashboard}
              disabled={creatingDashboard}
              aria-label="새 대시보드 추가"
              title="새 대시보드 추가"
              style={{
                width: 26,
                height: 26,
                borderRadius: 4,
                border: 'none',
                background: 'transparent',
                cursor: creatingDashboard ? 'wait' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#6d797d',
                fontSize: 18,
                marginLeft: 2,
                transition: 'background-color 0.12s, color 0.12s',
              }}
              onMouseOver={(e) => {
                if (creatingDashboard) return;
                e.currentTarget.style.background = 'rgba(6,182,212,0.10)';
                e.currentTarget.style.color = '#06b6d4';
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = '#6d797d';
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
                onRemove={removeWidget}
              />
            ))}
          </div>

          {/* FINT 채팅 + 검색바 (좌측 하단 고정) */}
          <div
            style={{
              position: 'fixed',
              zIndex: 20,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'flex-start',
              bottom: 20,
              left: 20,
            }}
          >
            {chatOpen && (
              <FintChatPanel
                steps={steps}
                query={userQuery}
                isLoading={querying}
                isDone={chatDone}
                errorMessage={queryErrorMsg}
                widgetTitle={widgetTitle}
                widgetType={pendingType}
                result={pendingWidget?.result}
                onTitleChange={setWidgetTitle}
                onCollapse={() => setChatOpen(false)}
                onDragStart={handleDragStart}
                chatHistory={chatHistory}
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
