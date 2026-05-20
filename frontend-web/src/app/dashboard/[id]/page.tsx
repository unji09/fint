'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import type { Dashboard, DashboardWidget, ChatMessage } from '@/types/dashboard';
import CanvasWidgetCard from '@/components/dashboard/CanvasWidgetCard';
import FintChatPanel from '@/components/dashboard/FintChatPanel';
import type { CanvasWidget, Step } from '@/types/dashboard';
import QueryBar from '@/components/dashboard/QueryBar';
import { BarChartSvg } from '@/components/dashboard/ChartWidgets';
import useBreakpoint from '@/hooks/useBreakpoint';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import {
  useCreateDashboard,
  useRenameDashboard,
  useUpdateWidget,
  useDeleteWidget,
  useThumbnailUpload,
} from '@/hooks/useDashboard';
import { useConfirm, useAlert } from '@/components/common/ConfirmDialog';
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
  const confirm = useConfirm();
  const alert = useAlert();
  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';

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
  /* MODIFY 완료 후 채팅 패널에 미리보기로 표시할 위젯 (드래그 없음) */
  const [modifyResultWidget, setModifyResultWidget] = useState<{
    widgetType: string;
    title: string;
    config: Record<string, unknown>;
    data: Record<string, unknown>[] | null;
    insightText: string;
  } | null>(null);

  /* 채팅 내역 */
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([]);

  /* 드래그 고스트 */
  const [dragging, setDragging] = useState(false);
  const [ghostPos, setGhostPos] = useState({ x: 0, y: 0 });
  const dragOrigin = useRef({ ox: 0, oy: 0 });

  /* 모바일 캔버스 핀치 줌 */
  const [canvasScale, setCanvasScale] = useState(1);
  const canvasScaleRef = useRef(1);
  canvasScaleRef.current = canvasScale;
  const pinchRef = useRef<{ dist: number; scale: number } | null>(null);

  /* 위젯 선택 상태 */
  const [selectedWidgetId, setSelectedWidgetId] = useState<number | null>(null);
  const [chatInputFocusTrigger, setChatInputFocusTrigger] = useState(0);

  // 위젯 선택 시 채팅 패널 열기 + 입력창 포커스
  useEffect(() => {
    if (selectedWidgetId !== null) {
      setChatOpen(true);
      setChatInputFocusTrigger((t) => t + 1);
    }
  }, [selectedWidgetId]);

  /* 채팅 패널 너비 동기화 (FintChatPanel ↔ QueryBar) */
  const [chatPanelWidth, setChatPanelWidth] = useState(() => {
    if (typeof window === 'undefined') return 390;
    try {
      const saved = JSON.parse(localStorage.getItem('fint:chatPanelSize') ?? '{}') as { w?: number };
      if (saved.w) return Math.max(320, Math.min(700, saved.w));
    } catch { /* ignore */ }
    return 390;
  });

  /* 채팅 패널 위치 (데스크탑 드래그 가능) */
  const [panelPos, setPanelPos] = useState<{ x: number; y: number }>(() => {
    if (typeof window === 'undefined') return { x: 20, y: 28 };
    try {
      const saved = JSON.parse(localStorage.getItem('fint:chatPanelPos') ?? '{}') as { x?: number; y?: number };
      if (typeof saved.x === 'number' && typeof saved.y === 'number') return { x: saved.x, y: saved.y };
    } catch { /* ignore */ }
    return { x: 20, y: 28 };
  });
  const panelDragRef = useRef<{ startX: number; startY: number; origX: number; origY: number } | null>(null);
  // 항상 최신 panelPos를 참조 (closure stale 방지)
  const panelPosRef = useRef(panelPos);
  panelPosRef.current = panelPos;
  // 채팅 패널 DOM 참조 — 드래그 상단 경계 계산에 사용
  const chatPanelContainerRef = useRef<HTMLDivElement>(null);


  const handlePanelHeaderDragStart = useCallback((e: React.MouseEvent) => {
    if (isMobile) return;
    e.preventDefault();
    e.stopPropagation();
    const pos = panelPosRef.current;
    panelDragRef.current = { startX: e.clientX, startY: e.clientY, origX: pos.x, origY: pos.y };
    let moved = false;
    const onMove = (ev: MouseEvent) => {
      if (!panelDragRef.current) return;
      const dx = ev.clientX - panelDragRef.current.startX;
      const dy = ev.clientY - panelDragRef.current.startY;
      if (!moved) {
        if (Math.hypot(dx, dy) < 5) return;
        moved = true;
      }
      const newX = Math.max(12, panelDragRef.current.origX + dx);
      // 탭바(GNB 64px + 탭바 36px = 100px) 위로 올라가지 않도록 maxY 제한
      const panelH = chatPanelContainerRef.current?.offsetHeight ?? 500;
      const maxY = Math.max(12, window.innerHeight - 100 - panelH);
      const newY = Math.max(12, Math.min(maxY, panelDragRef.current.origY - dy));
      setPanelPos({ x: newX, y: newY });
    };
    const onUp = () => {
      if (!panelDragRef.current) return;
      panelDragRef.current = null;
      setPanelPos((p) => {
        try { localStorage.setItem('fint:chatPanelPos', JSON.stringify(p)); } catch { /* ignore */ }
        return p;
      });
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [isMobile]);

  /* 모바일 핀치 줌 — canvasRef의 비패시브 touchmove로 preventDefault 호출 */
  useEffect(() => {
    if (!isMobile || !canvasRef.current) return;
    const el = canvasRef.current;
    const onTouchStart = (e: TouchEvent) => {
      if (e.touches.length === 2) {
        const t0 = e.touches[0];
        const t1 = e.touches[1];
        pinchRef.current = {
          dist: Math.hypot(t1.clientX - t0.clientX, t1.clientY - t0.clientY),
          scale: canvasScaleRef.current,
        };
      }
    };
    const onTouchMove = (e: TouchEvent) => {
      if (e.touches.length === 2 && pinchRef.current) {
        e.preventDefault();
        const t0 = e.touches[0];
        const t1 = e.touches[1];
        const dist = Math.hypot(t1.clientX - t0.clientX, t1.clientY - t0.clientY);
        const ratio = dist / pinchRef.current.dist;
        const next = Math.min(3, Math.max(0.25, pinchRef.current.scale * ratio));
        setCanvasScale(next);
        canvasScaleRef.current = next;
      }
    };
    const onTouchEnd = (e: TouchEvent) => {
      if (e.touches.length < 2) pinchRef.current = null;
    };
    el.addEventListener('touchstart', onTouchStart, { passive: true });
    el.addEventListener('touchmove', onTouchMove, { passive: false });
    el.addEventListener('touchend', onTouchEnd, { passive: true });
    return () => {
      el.removeEventListener('touchstart', onTouchStart);
      el.removeEventListener('touchmove', onTouchMove);
      el.removeEventListener('touchend', onTouchEnd);
    };
  }, [isMobile]);

  /* 탭 숨기기 (삭제 아님) */
  const [hiddenTabs, setHiddenTabs] = useState<number[]>(() => {
    if (typeof window === 'undefined') return [];
    try { return JSON.parse(localStorage.getItem('fint:hiddenTabs') ?? '[]') as number[]; } catch { return []; }
  });
  const hideTab = useCallback((tabId: number) => {
    setHiddenTabs((prev) => {
      const next = [...prev, tabId];
      try { localStorage.setItem('fint:hiddenTabs', JSON.stringify(next)); } catch { /* ignore */ }
      if (String(tabId) === String(id)) {
        const visible = allDashboards.filter((d) => !next.includes(d.dashboardId));
        if (visible.length > 0) router.push(`/dashboard/${visible[0].dashboardId}`);
        else router.push('/dashboard');
      }
      return next;
    });
  }, [id, allDashboards, router]);

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
      await alert('새 대시보드를 만들지 못했습니다.\n잠시 후 다시 시도해 주세요.');
    }
  }, [createDashboard, creatingDashboard]);

  useEffect(() => {
    // 1) localStorage 캐시 먼저 보여줌 (백엔드 응답 늦더라도 새로고침 후 즉시 보이게)
    let cachedList: CanvasWidget[] = [];
    try {
      const cached = localStorage.getItem(`fint:widgets:${id}`);
      if (cached) {
        const list = JSON.parse(cached) as CanvasWidget[];
        if (Array.isArray(list) && list.length > 0) {
          cachedList = list;
          setCanvasWidgets(list);
        }
      }
    } catch { /* ignore */ }

    // 2) 백엔드 응답으로 갱신 — 사용자가 드래그해서 배치한 위젯만 표시
    // (SSE 완료 시 백엔드가 위젯을 생성하지만, 드래그 전까지는 캔버스에 올리지 않음)
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
        // 캐시가 있으면 사용자가 배치한 위젯 ID만 허용 (미배치 위젯 제외)
        const cachedIds = new Set(cachedList.map((w) => w.widgetId));
        const placedWidgets = cachedIds.size > 0
          ? serverWidgets.filter((w) => cachedIds.has(w.widgetId))
          : serverWidgets; // 최초 로드(캐시 없음)는 전체 표시
        if (placedWidgets.length === 0) return; // 배치된 위젯 없으면 캐시 유지
        const next = placedWidgets.map((w, i) => {
          const cached = cachedList.find((c) => c.widgetId === w.widgetId);
          const pos = w.position;
          const isGrid = pos && pos.w <= 12 && pos.h <= 16;
          return {
            ...w,
            px: cached?.px ?? (isGrid ? PAD + pos.x * GRID_COL : (pos?.x ?? (28 + i * 30))),
            py: cached?.py ?? (isGrid ? PAD + pos.y * GRID_ROW : (pos?.y ?? (28 + i * 20))),
            pw: cached?.pw ?? (isGrid ? pos.w * GRID_COL : (pos?.w ?? 400)),
            ph: cached?.ph ?? (isGrid ? pos.h * GRID_ROW : (pos?.h ?? 260)),
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
          const resultObj = (q.result ?? {}) as Record<string, unknown>;
          // q.result is raw FastAPI JSON: insight_text (snake_case), data: {rows, columns, ...}
          const rawData = resultObj.data as ({ rows?: unknown[] } & Record<string, unknown>) | unknown[] | null | undefined;
          const dataRows: Record<string, unknown>[] | null = Array.isArray(rawData)
            ? (rawData as Record<string, unknown>[])
            : Array.isArray((rawData as { rows?: unknown[] } | null)?.rows)
              ? ((rawData as { rows: unknown[] }).rows as Record<string, unknown>[])
              : null;
          msgs.push({
            id: `assistant-${q.queryId ?? Date.now()}-${msgs.length}`,
            role: 'assistant',
            content: (resultObj.insight_text as string) ?? (isError ? '' : ''),
            widget: isError ? null : {
              widgetType: q.widgetType ?? (resultObj.widget_type as string) ?? 'BAR_CHART',
              title: q.title ?? (resultObj.title as string) ?? '',
              data: dataRows,
              config: q.config ?? (resultObj.config as Record<string, unknown>) ?? {},
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
  // 항상 최신 updateWidget 참조 (handleQuery 클로저 stale 방지)
  const updateWidgetRef = useRef(updateWidget);
  updateWidgetRef.current = updateWidget;
  // 진행 중인 쿼리가 수정 대상 위젯 ID (null이면 새 위젯 추가)
  const modifyWidgetIdRef = useRef<number | null>(null);

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
    if (!await confirm(`'${target.title}' 위젯을 삭제할까요?`)) return;
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


  /* 미들클릭 스크롤 */
  const handleCanvasMouseDown = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    if (e.button !== 1) return;
    e.preventDefault();
    const canvas = canvasRef.current;
    if (!canvas) return;
    const startX = e.clientX;
    const startY = e.clientY;
    const startLeft = canvas.scrollLeft;
    const startTop = canvas.scrollTop;
    const onMove = (ev: MouseEvent) => {
      canvas.scrollLeft = startLeft - (ev.clientX - startX);
      canvas.scrollTop = startTop - (ev.clientY - startY);
    };
    const origCursor = canvas.style.cursor;
    canvas.style.cursor = 'grabbing';
    const onUp = () => {
      canvas.style.cursor = origCursor;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, []);

  const handleDragStart = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      dragOrigin.current = { ox: e.clientX, oy: e.clientY };
      setGhostPos({ x: e.clientX, y: e.clientY });
      setDragging(true);

      const EDGE_ZONE = 40;
      const SCROLL_SPEED = 12;
      let autoScrollId: number | null = null;
      let lastMouse = { x: e.clientX, y: e.clientY };

      const doAutoScroll = () => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const rect = canvas.getBoundingClientRect();
        const mx = lastMouse.x;
        const my = lastMouse.y;
        let dx = 0;
        let dy = 0;
        if (mx < rect.left + EDGE_ZONE && mx >= rect.left) dx = -SCROLL_SPEED;
        else if (mx > rect.right - EDGE_ZONE && mx <= rect.right) dx = SCROLL_SPEED;
        if (my < rect.top + EDGE_ZONE && my >= rect.top) dy = -SCROLL_SPEED;
        else if (my > rect.bottom - EDGE_ZONE && my <= rect.bottom) dy = SCROLL_SPEED;
        if (dx || dy) canvas.scrollBy(dx, dy);
        autoScrollId = requestAnimationFrame(doAutoScroll);
      };
      autoScrollId = requestAnimationFrame(doAutoScroll);

      const onMove = (ev: MouseEvent) => {
        lastMouse = { x: ev.clientX, y: ev.clientY };
        setGhostPos({ x: ev.clientX, y: ev.clientY });
      };
      const onUp = (ev: MouseEvent) => {
        setDragging(false);
        if (autoScrollId !== null) cancelAnimationFrame(autoScrollId);
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

      // [widgetId:N] 접두사가 있으면 MODIFY 모드 — widgetId를 백엔드에 전달
      const widgetPrefixMatch = text.match(/^\[widgetId:(\d+)\]\s*/);
      const modifyWid = widgetPrefixMatch ? Number(widgetPrefixMatch[1]) : null;
      modifyWidgetIdRef.current = modifyWid;
      let aiInputText = text;
      if (modifyWid !== null) {
        // 접두사 제거 후 위젯 제목 컨텍스트 추가
        const plainText = text.slice(widgetPrefixMatch![0].length);
        const ctxWidget = canvasWidgets.find((cw) => cw.widgetId === modifyWid);
        const widgetContext = ctxWidget ? `[${ctxWidget.title}] ` : '';
        aiInputText = `${widgetContext}${plainText}`;
      }

      setUserQuery(text);
      setQuerying(true);
      setChatOpen(true);
      setChatDone(false);
      setQueryErrorMsg(null);
      setQueryInput('');
      setPendingWidget(null);
      setModifyResultWidget(null);

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
          body: JSON.stringify({
            inputText: aiInputText,
            ...(modifyWid !== null ? { widgetId: modifyWid } : {}),
          }),
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
                const newData = Array.isArray(data.result?.data)
                  ? data.result.data
                  : Array.isArray(data.result?.data?.rows)
                    ? data.result.data.rows
                    : null;
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

                const targetModifyWid = modifyWidgetIdRef.current;
                if (targetModifyWid !== null) {
                  // 기존 위젯 업데이트 (새 위젯 추가 아님)
                  modifyWidgetIdRef.current = null;
                  updateWidgetRef.current(targetModifyWid, {
                    widgetType: data.widgetType ?? 'BAR_CHART',
                    title: data.title ?? autoTitle,
                    config: data.config ?? {},
                    result: data.result ?? { data: {}, insightText: '' },
                    data: newData,
                    queryId: data.queryId ?? null,
                  });
                  // 채팅 패널에 미리보기 위젯 표시 (드래그 없음)
                  setModifyResultWidget({
                    widgetType: data.widgetType ?? 'BAR_CHART',
                    title: data.title ?? autoTitle,
                    config: (data.config ?? {}) as Record<string, unknown>,
                    data: newData,
                    insightText: data.result?.insightText ?? '',
                  });
                } else {
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
                    data: newData,
                    px: 0,
                    py: 0,
                    pw: 400,
                    ph: 260,
                  });
                }

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
        const errMsg = err instanceof Error ? err.message : '요청을 처리하지 못했어요. 잠시 후 다시 시도해 보세요.';
        setQueryErrorMsg(errMsg);
        setQuerying(false);
        setChatDone(true);
        setChatHistory((prev) => [
          ...prev,
          {
            id: `assistant-err-${Date.now()}`,
            role: 'assistant',
            content: '',
            widget: null,
            timestamp: new Date().toISOString(),
            status: 'error',
            errorMessage: errMsg,
          } as ChatMessage,
        ]);
      }
    },
    [querying, id, canvasWidgets],
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
            padding: isMobile ? '4px 8px' : '4px 16px',
            height: 36,
            background: 'rgba(255,255,255,0.88)',
            backdropFilter: 'blur(8px)',
            borderBottom: '1px solid #e2e8f0',
            overflowX: 'auto',
            position: 'relative',
            zIndex: 30,
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
              overflowX: 'auto',
              flex: '0 1 auto',
              scrollbarWidth: 'none',
            }}
          >
            {allDashboards.length === 0 ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 2, padding: '2px 4px 2px 10px', borderRadius: 4, background: '#06b6d4' }}>
                <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 600, fontSize: 13, color: 'white', padding: '2px 2px', whiteSpace: 'nowrap' }}>
                  로딩 중...
                </span>
              </div>
            ) : (
              allDashboards.filter((d) => !hiddenTabs.includes(d.dashboardId) || String(d.dashboardId) === String(id)).map((d) => {
                const active = String(d.dashboardId) === String(id);
                return (
                  <div
                    key={d.dashboardId}
                    onMouseEnter={() => { if (!active) router.prefetch(`/dashboard/${d.dashboardId}`); }}
                    className="fint-tab"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 2,
                      padding: '2px 4px 2px 10px',
                      borderRadius: 4,
                      minWidth: 80,
                      maxWidth: 160,
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
                        title={d.title}
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
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {d.title.length > 6 ? d.title.slice(0, 6) + '..' : d.title}
                      </button>
                    )}
                    {/* X 버튼: 탭에서 숨기기 (삭제 아님) */}
                    <button
                      type="button"
                      onClick={() => hideTab(d.dashboardId)}
                      aria-label={`${d.title} 탭 숨기기`}
                      title="탭에서 숨기기 (대시보드는 유지됩니다)"
                      className="fint-tab-close"
                      style={{
                        width: 18,
                        height: 18,
                        borderRadius: 4,
                        border: 'none',
                        background: 'transparent',
                        color: active ? 'rgba(255,255,255,0.85)' : '#94a3b8',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        padding: 0,
                        opacity: 0,
                        transition: 'opacity 0.15s, background-color 0.12s, color 0.12s',
                      }}
                      onMouseOver={(e) => {
                        e.currentTarget.style.background = active ? 'rgba(255,255,255,0.18)' : 'rgba(100,116,139,0.12)';
                        e.currentTarget.style.color = active ? '#fff' : '#475569';
                        e.currentTarget.style.opacity = '1';
                      }}
                      onMouseOut={(e) => {
                        e.currentTarget.style.background = 'transparent';
                        e.currentTarget.style.color = active ? 'rgba(255,255,255,0.85)' : '#94a3b8';
                        e.currentTarget.style.opacity = '0';
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
        <div
          ref={canvasRef}
          onMouseDown={handleCanvasMouseDown}
          onClick={(e) => {
            // 캔버스 빈 공간 클릭 시 위젯 선택 해제
            if ((e.target as HTMLElement).closest('[data-widget-card]') === null) {
              setSelectedWidgetId(null);
            }
          }}
          style={{
            flex: 1,
            minHeight: 0,
            position: 'relative',
            overflow: 'auto',
            backgroundImage:
              'linear-gradient(rgba(99,118,183,0.10) 1px, transparent 1px),' +
              'linear-gradient(90deg, rgba(99,118,183,0.10) 1px, transparent 1px)',
            backgroundSize: '20px 20px',
          }}
        >
          <div style={{ position: 'relative', minWidth: 3000, minHeight: 3000, ...(isMobile && canvasScale !== 1 ? { transform: `scale(${canvasScale})`, transformOrigin: '0 0' } : {}) }}>
            {canvasWidgets.map((w) => (
              <CanvasWidgetCard
                key={w.widgetId}
                w={w}
                isSelected={selectedWidgetId === w.widgetId}
                onSelect={(wid) => setSelectedWidgetId(wid)}
                onUpdate={updateWidget}
                onTitleChange={updateTitle}
                onRemove={removeWidget}
                canvasRef={canvasRef}
              />
            ))}
          </div>

          {/* FINT 채팅 패널 (데스크탑: 드래그 가능, 모바일: 고정) */}
          <div
            ref={chatPanelContainerRef}
            style={{
              position: 'fixed',
              zIndex: 20,
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
              alignItems: (isMobile && chatOpen) ? 'stretch' : 'flex-start',
              ...(isMobile
                ? { bottom: 16, left: 8, right: 8 }
                : { bottom: panelPos.y, left: panelPos.x }),
            }}
          >
            {/* 채팅 패널 — 닫혀도 DOM 유지(애니메이션용), maxHeight:0으로 공간 차지 않음 */}
            <div
              style={{
                transition: 'opacity 0.22s ease, transform 0.22s ease',
                opacity: chatOpen ? 1 : 0,
                transform: chatOpen ? 'translateY(0)' : 'translateY(10px)',
                pointerEvents: chatOpen ? 'auto' : 'none',
                maxHeight: chatOpen ? 'none' : 0,
                overflow: chatOpen ? 'visible' : 'hidden',
                ...(isMobile ? { width: '100%' } : {}),
              }}
            >
              {/* 드래그 핸들 오버레이 — 헤더 위에 투명하게 위치, 닫기버튼(우측 44px) 제외 */}
              {!isMobile && (
                <div
                  onMouseDown={handlePanelHeaderDragStart}
                  style={{ position: 'absolute', top: 0, left: 0, right: 44, height: 44, zIndex: 30, cursor: 'grab', borderRadius: '20px 20px 0 0' }}
                />
              )}
              <FintChatPanel
                steps={steps}
                query={userQuery}
                isLoading={querying}
                isDone={chatDone}
                errorMessage={queryErrorMsg}
                widgetTitle={widgetTitle}
                widgetType={pendingType}
                result={pendingWidget?.result}
                config={pendingWidget?.config ?? {}}
                data={pendingWidget?.data ?? null}
                modifyWidget={modifyResultWidget}
                onTitleChange={setWidgetTitle}
                onCollapse={() => setChatOpen(false)}
                onDragStart={handleDragStart}
                onSubmit={handleQuery}
                chatHistory={chatHistory}
                selectedWidget={selectedWidgetId != null ? (() => { const sw = canvasWidgets.find(w => w.widgetId === selectedWidgetId); return sw ? { id: sw.widgetId, title: sw.title, type: sw.widgetType } : null; })() : null}
                onClearSelectedWidget={() => setSelectedWidgetId(null)}
                onWidthChange={setChatPanelWidth}
                panelPosX={panelPos.x}
                panelPosY={panelPos.y}
                onPosChange={(x, y) => {
                  setPanelPos({ x, y });
                  try { localStorage.setItem('fint:chatPanelPos', JSON.stringify({ x, y })); } catch { /* ignore */ }
                }}
              />
            </div>

            {/* 패널 닫혔을 때 재열기 버튼 */}
            {!chatOpen && (
              <button
                onClick={() => setChatOpen(true)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 16px',
                  background: 'rgba(255,255,255,0.9)',
                  backdropFilter: 'blur(12px)',
                  border: '1px solid rgba(6,182,212,0.3)',
                  borderRadius: 12,
                  cursor: 'pointer',
                  fontFamily: 'Pretendard,sans-serif',
                  fontSize: 13,
                  fontWeight: 500,
                  color: '#1d1a24',
                  boxShadow: '0 2px 8px rgba(15,23,42,0.08)',
                }}
              >
                <span style={{ color: '#06b6d4', fontSize: 14 }}>✦</span>
                FINT 대화 열기
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" style={{ marginLeft: 2 }}>
                  <path d="M6 15l6-6 6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                </svg>
              </button>
            )}

            {/* QueryBar — 항상 표시 (패널 아래 분리된 입력창) */}
            <QueryBar
              value={queryInput}
              onChange={setQueryInput}
              onSubmit={(text) => {
                const prefixed = selectedWidgetId != null
                  ? `[widgetId:${selectedWidgetId}] ${text}`
                  : text;
                handleQuery(prefixed);
              }}
              loading={querying}
              focusTrigger={chatInputFocusTrigger}
              width={isMobile ? undefined : chatPanelWidth}
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
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        * { box-sizing: border-box; }
        .fint-tab:hover .fint-tab-close { opacity: 1 !important; }
        .fint-tab:hover { background: rgba(6,182,212,0.08); }
      `}</style>
    </>
  );
}
