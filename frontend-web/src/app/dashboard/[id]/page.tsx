'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import type { Dashboard, DashboardWidget } from '@/types/dashboard';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';
function authHeader() {
  const t = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  return t ? { Authorization: `Bearer ${t}` } : {};
}

type Step = { label: string; done: boolean; active: boolean };
const STEP_LABELS = ['사용자 의도 파악', '데이터 조회', '컴포넌트 조합 완료', '스타일링 중...'];

/* 캔버스 위젯 타입 (위치+크기 포함) */
type CanvasWidget = DashboardWidget & { px: number; py: number; pw: number; ph: number };

/* ─── 격자 배경 ─── */
function GridBg() {
  return (
    <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
      {/* 베이스 */}
      <div style={{ position: 'absolute', inset: 0, backgroundColor: '#eef1ff' }} />
      {/* 20px 선 격자 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `linear-gradient(rgba(99,118,183,0.13) 1px, transparent 1px),
          linear-gradient(90deg, rgba(99,118,183,0.13) 1px, transparent 1px)`,
          backgroundSize: '20px 20px',
        }}
      />
      {/* 입체감: 중앙→가장자리 그라디언트 오버레이 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background:
            'radial-gradient(ellipse 70% 70% at 50% 40%, transparent 40%, rgba(180,190,230,0.18) 100%)',
        }}
      />
      {/* 상단 밝은 빛 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 120,
          background: 'linear-gradient(to bottom, rgba(255,255,255,0.4), transparent)',
        }}
      />
    </div>
  );
}

/* ─── SVG 차트들 ─── */
function MiniBarSvg({ size = 'full' }: { size?: 'full' | 'mini' }) {
  const bars = [40, 48, 38, 50, 45, 62, 65, 70, 68, 74, 78, 80, 82, 90, 92, 95, 86, 84];
  const max = Math.max(...bars);
  const h = size === 'mini' ? 90 : 150;
  const bw = size === 'mini' ? 10 : 18;
  const gap = size === 'mini' ? 3 : 6;
  const W = 300,
    H = h,
    pad = size === 'mini' ? 14 : 22;
  const totalW = bars.length * (bw + gap) - gap;
  const sx = (W - totalW) / 2;
  return (
    <svg viewBox={`0 0 ${W} ${H + pad}`} style={{ width: '100%', height: h }}>
      {bars.map((v, i) => {
        const bh = (v / max) * H;
        const x = sx + i * (bw + gap);
        const isFuture = i >= 15;
        return (
          <rect
            key={i}
            x={x}
            y={H - bh + pad / 2}
            width={bw}
            height={bh}
            rx={2}
            fill={isFuture ? 'none' : '#7dd3fc'}
            stroke={isFuture ? '#06b6d4' : 'none'}
            strokeWidth={1.5}
            strokeDasharray={isFuture ? '3 2' : 'none'}
            opacity={isFuture ? 0.5 : 1}
          />
        );
      })}
      {size === 'full' &&
        ['W1', 'W4', 'W8', 'W12', 'W15'].map((l, i) => (
          <text
            key={l}
            x={sx + [0, 3, 7, 11, 14][i] * (bw + gap) + bw / 2}
            y={H + pad / 2 + 14}
            textAnchor="middle"
            fontSize="9"
            fill="#94a3b8"
            fontFamily="Pretendard"
          >
            {l}
          </text>
        ))}
    </svg>
  );
}

function LineSvg({ size = 'full' }: { size?: 'full' | 'mini' }) {
  const pts = [20, 28, 35, 32, 45, 48, 52, 58, 55, 65, 70, 68, 75, 80, 76, 85, 90, 95];
  const max = Math.max(...pts);
  const W = 300,
    H = size === 'mini' ? 80 : 140,
    pad = 12;
  const xs = pts.map((_, i) => pad + (i / (pts.length - 1)) * (W - pad * 2));
  const ys = pts.map((v) => H - pad - (v / max) * (H - pad * 2));
  const line = xs
    .map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${ys[i].toFixed(1)}`)
    .join(' ');
  const area = `${line} L${xs[xs.length - 1].toFixed(1)},${H} L${xs[0].toFixed(1)},${H} Z`;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: size === 'mini' ? 80 : 140 }}>
      <defs>
        <linearGradient id="lgg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#06b6d4" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#06b6d4" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#lgg)" />
      <path
        d={line}
        fill="none"
        stroke="#06b6d4"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={xs[7]} cy={ys[7]} r="4" fill="white" stroke="#06b6d4" strokeWidth="2.5" />
    </svg>
  );
}

function SegmentSvg() {
  const rows = [
    { label: 'Enterprise', pct: 58, color: '#06b6d4' },
    { label: 'Mid-market', pct: 30, color: '#386570' },
    { label: 'SMB', pct: 12, color: '#6d797d' },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '4px 0' }}>
      {rows.map((r) => (
        <div key={r.label}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
            <span
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontSize: 13,
                fontWeight: 500,
                color: '#171d1e',
              }}
            >
              {r.label}
            </span>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#3d494c' }}>
              {r.pct}%
            </span>
          </div>
          <div style={{ height: 7, background: '#eff4f7', borderRadius: 12, overflow: 'hidden' }}>
            <div
              style={{ height: '100%', width: `${r.pct}%`, background: r.color, borderRadius: 12 }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

/* ─── 캔버스 위젯 카드 (드래그 이동 + 리사이즈) ─── */
function CanvasWidgetCard({
  w,
  onUpdate,
  onTitleChange,
}: {
  w: CanvasWidget;
  onUpdate: (id: number, changes: Partial<CanvasWidget>) => void;
  onTitleChange: (id: number, t: string) => void;
}) {
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(w.title);
  const dragRef = useRef<{ sx: number; sy: number; ox: number; oy: number } | null>(null);
  const resizeRef = useRef<{ sx: number; sy: number; ow: number; oh: number; dir: string } | null>(
    null,
  );

  /* 카드 드래그 이동 */
  const onCardMouseDown = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).dataset.resize) return;
    if (editTitle) return;
    e.preventDefault();
    dragRef.current = { sx: e.clientX, sy: e.clientY, ox: w.px, oy: w.py };
    const onMove = (ev: MouseEvent) => {
      if (!dragRef.current) return;
      onUpdate(w.widgetId, {
        px: dragRef.current.ox + ev.clientX - dragRef.current.sx,
        py: dragRef.current.oy + ev.clientY - dragRef.current.sy,
      });
    };
    const onUp = () => {
      dragRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  /* 리사이즈 핸들 */
  const onResizeDown = (e: React.MouseEvent, dir: string) => {
    e.stopPropagation();
    e.preventDefault();
    resizeRef.current = { sx: e.clientX, sy: e.clientY, ow: w.pw, oh: w.ph, dir };
    const onMove = (ev: MouseEvent) => {
      if (!resizeRef.current) return;
      const dx = ev.clientX - resizeRef.current.sx;
      const dy = ev.clientY - resizeRef.current.sy;
      const newW = Math.max(
        240,
        resizeRef.current.ow + (dir.includes('e') ? dx : dir.includes('w') ? -dx : 0),
      );
      const newH = Math.max(
        180,
        resizeRef.current.oh + (dir.includes('s') ? dy : dir.includes('n') ? -dy : 0),
      );
      const newPx = dir.includes('w') ? w.px + (resizeRef.current.ow - newW) : w.px;
      const newPy = dir.includes('n') ? w.py + (resizeRef.current.oh - newH) : w.py;
      onUpdate(w.widgetId, { pw: newW, ph: newH, px: newPx, py: newPy });
    };
    const onUp = () => {
      resizeRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const handles: { dir: string; style: React.CSSProperties }[] = [
    { dir: 'nw', style: { top: -5, left: -5, cursor: 'nw-resize' } },
    { dir: 'ne', style: { top: -5, right: -5, cursor: 'ne-resize' } },
    { dir: 'sw', style: { bottom: -5, left: -5, cursor: 'sw-resize' } },
    { dir: 'se', style: { bottom: -5, right: -5, cursor: 'se-resize' } },
    {
      dir: 'e',
      style: { top: '50%', right: -5, transform: 'translateY(-50%)', cursor: 'e-resize' },
    },
    {
      dir: 'w',
      style: { top: '50%', left: -5, transform: 'translateY(-50%)', cursor: 'w-resize' },
    },
    {
      dir: 's',
      style: { bottom: -5, left: '50%', transform: 'translateX(-50%)', cursor: 's-resize' },
    },
    {
      dir: 'n',
      style: { top: -5, left: '50%', transform: 'translateX(-50%)', cursor: 'n-resize' },
    },
  ];

  return (
    <div
      onMouseDown={onCardMouseDown}
      style={{
        position: 'absolute',
        left: w.px,
        top: w.py,
        width: w.pw,
        height: w.ph,
        background: 'white',
        borderRadius: 10,
        border: '1px solid #dee3e6',
        boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        cursor: 'grab',
        userSelect: 'none',
      }}
    >
      {/* 헤더 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 16px 10px',
          borderBottom: '1px solid #eff4f7',
          flexShrink: 0,
          cursor: 'default',
        }}
      >
        {editTitle ? (
          <input
            autoFocus
            value={titleVal}
            onChange={(e) => setTitleVal(e.target.value)}
            onBlur={() => {
              setEditTitle(false);
              onTitleChange(w.widgetId, titleVal);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setEditTitle(false);
                onTitleChange(w.widgetId, titleVal);
              }
            }}
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            style={{
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 15,
              border: 'none',
              outline: '1px solid #06b6d4',
              borderRadius: 4,
              padding: '0 4px',
              width: '80%',
            }}
          />
        ) : (
          <span
            style={{
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 15,
              color: '#171d1e',
              cursor: 'text',
            }}
            onDoubleClick={(e) => {
              e.stopPropagation();
              setEditTitle(true);
            }}
            title="더블클릭으로 제목 수정"
          >
            {titleVal}
          </span>
        )}
        <span style={{ color: '#94a3b8', fontSize: 16, cursor: 'pointer' }}>···</span>
      </div>

      {/* 차트 */}
      <div style={{ flex: 1, overflow: 'hidden', padding: '8px 14px 10px' }}>
        {w.widgetType === 'LINE_CHART' ? (
          <LineSvg />
        ) : w.widgetType === 'SEGMENT' ? (
          <SegmentSvg />
        ) : (
          <MiniBarSvg />
        )}
      </div>

      {/* 리사이즈 핸들 */}
      {handles.map((h) => (
        <div
          key={h.dir}
          data-resize="1"
          onMouseDown={(e) => onResizeDown(e, h.dir)}
          style={{
            position: 'absolute',
            width: 10,
            height: 10,
            borderRadius: 3,
            background: '#06b6d4',
            border: '2px solid white',
            boxShadow: '0 1px 4px rgba(0,0,0,0.2)',
            zIndex: 10,
            cursor: h.style.cursor,
            ...h.style,
          }}
        />
      ))}
    </div>
  );
}

/* ─── FINT 채팅 패널 (반투명 글래스) ─── */
function FintChatPanel({
  steps,
  query,
  isLoading,
  isDone,
  widgetTitle,
  widgetType,
  onTitleChange,
  onCollapse,
  onDragStart,
}: {
  steps: Step[];
  query: string;
  isLoading: boolean;
  isDone: boolean;
  widgetTitle: string;
  widgetType: string;
  onTitleChange: (v: string) => void;
  onCollapse: () => void;
  onDragStart: (e: React.MouseEvent) => void;
}) {
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(widgetTitle);
  useEffect(() => {
    setTitleVal(widgetTitle);
  }, [widgetTitle]);

  return (
    <div
      style={{
        /* 반투명 글래스 */
        background: 'rgba(248,250,255,0.72)',
        backdropFilter: 'blur(16px) saturate(180%) brightness(1.05)',
        WebkitBackdropFilter: 'blur(16px) saturate(180%) brightness(1.05)',
        border: '1px solid rgba(255,255,255,0.9)',
        boxShadow:
          '0 8px 32px rgba(0,0,0,0.06), 0 2px 12px rgba(0,0,0,0.04), inset 0 1px 0 rgba(255,255,255,1)',
        borderRadius: 16,
        width: 390,
        marginBottom: 10,
        overflow: 'hidden',
      }}
    >
      {/* 헤더 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '11px 16px',
          borderBottom: '1px solid rgba(255,255,255,0.6)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ color: '#06b6d4', fontSize: 14 }}>✦</span>
          <span
            style={{
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 600,
              fontSize: 14,
              color: '#1d1a24',
            }}
          >
            FINT
          </span>
        </div>
        <button
          onClick={onCollapse}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            color: '#94a3b8',
            padding: 0,
            display: 'flex',
          }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <div style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {/* 유저 쿼리 */}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <div
            style={{
              background: '#06b6d4',
              color: 'white',
              borderRadius: '18px 18px 4px 18px',
              padding: '8px 14px',
              fontSize: 14,
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              maxWidth: 240,
              lineHeight: 1.4,
              boxShadow: '0 3px 10px rgba(6,182,212,0.3)',
            }}
          >
            {query}
          </div>
        </div>

        {/* 로딩: 체크리스트 */}
        {isLoading && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            {steps.map((s, i) => (
              <div key={i}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 0' }}>
                  {s.done ? (
                    <div
                      style={{
                        width: 20,
                        height: 20,
                        borderRadius: 6,
                        background: '#81dbe0',
                        flexShrink: 0,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <svg width="11" height="11" viewBox="0 0 14 14" fill="none">
                        <path
                          d="M2.5 7L5.5 10L11.5 4"
                          stroke="white"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    </div>
                  ) : s.active ? (
                    <div
                      style={{
                        width: 20,
                        height: 20,
                        borderRadius: 6,
                        flexShrink: 0,
                        borderTop: '2px solid #06b6d4',
                        borderRight: '2px solid rgba(6,182,212,0.3)',
                        borderBottom: '2px solid rgba(6,182,212,0.3)',
                        borderLeft: '2px solid rgba(6,182,212,0.3)',
                        animation: 'spin 0.8s linear infinite',
                      }}
                    />
                  ) : (
                    <div
                      style={{
                        width: 20,
                        height: 20,
                        borderRadius: 6,
                        flexShrink: 0,
                        border: '2px solid rgba(226,232,240,0.8)',
                      }}
                    />
                  )}
                  <span
                    style={{
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 700,
                      fontSize: 10,
                      letterSpacing: '0.5px',
                      textTransform: 'uppercase',
                      color: s.done || s.active ? '#64748b' : '#cbd5e1',
                    }}
                  >
                    {s.label}
                  </span>
                </div>
                {i < steps.length - 1 && (
                  <div
                    style={{
                      width: 2,
                      height: 8,
                      background: s.done ? '#81dbe0' : 'rgba(226,232,240,0.8)',
                      marginLeft: 9,
                      borderRadius: 2,
                    }}
                  />
                )}
              </div>
            ))}
          </div>
        )}

        {/* 완료: AI 응답 + 드래그 가능 미니 위젯 */}
        {isDone && (
          <>
            <p
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontSize: 13,
                color: '#475569',
                margin: 0,
                lineHeight: 1.6,
              }}
            >
              최근 활동 데이터와 DART 공시를 결합하여 분석한 결과입니다.
            </p>

            {/* 드래그 가능한 미니 위젯 */}
            <div
              onMouseDown={onDragStart}
              style={{
                background: 'rgba(255,255,255,0.85)',
                border: '1.5px solid rgba(6,182,212,0.4)',
                borderRadius: 10,
                overflow: 'hidden',
                cursor: 'grab',
                boxShadow: '0 2px 12px rgba(6,182,212,0.15)',
                userSelect: 'none',
              }}
              title="캔버스로 드래그하세요"
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '9px 14px 8px',
                  borderBottom: '1px solid rgba(241,245,249,0.8)',
                }}
              >
                {editTitle ? (
                  <input
                    autoFocus
                    value={titleVal}
                    onChange={(e) => setTitleVal(e.target.value)}
                    onBlur={() => {
                      setEditTitle(false);
                      onTitleChange(titleVal);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        setEditTitle(false);
                        onTitleChange(titleVal);
                      }
                    }}
                    onMouseDown={(e) => e.stopPropagation()}
                    style={{
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 500,
                      fontSize: 13,
                      border: 'none',
                      outline: '1px solid #06b6d4',
                      borderRadius: 3,
                      padding: '0 4px',
                      width: '80%',
                    }}
                  />
                ) : (
                  <span
                    style={{
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 500,
                      fontSize: 13,
                      color: '#171d1e',
                      cursor: 'text',
                    }}
                    onDoubleClick={(e) => {
                      e.stopPropagation();
                      setEditTitle(true);
                    }}
                    title="더블클릭으로 제목 수정"
                  >
                    {titleVal}
                  </span>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      fontSize: 11,
                      color: '#06b6d4',
                      fontFamily: 'Pretendard,sans-serif',
                      fontWeight: 500,
                    }}
                  >
                    드래그 ↗
                  </span>
                  <span style={{ color: '#94a3b8', fontSize: 14 }}>···</span>
                </div>
              </div>
              <div style={{ padding: '6px 12px 8px' }}>
                {widgetType === 'LINE_CHART' ? <LineSvg size="mini" /> : <MiniBarSvg size="mini" />}
              </div>
            </div>
          </>
        )}
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

/* ─── 검색 바 ─── */
function QueryBar({
  value,
  onChange,
  onSubmit,
  loading,
}: {
  value: string;
  onChange: (v: string) => void;
  onSubmit: (v: string) => void;
  loading: boolean;
}) {
  return (
    <div style={{ position: 'relative', width: 390 }}>
      <div
        style={{
          position: 'absolute',
          inset: -2,
          borderRadius: 32,
          background: 'linear-gradient(to right,rgba(6,182,212,0.2),rgba(0,104,122,0.2))',
          filter: 'blur(4px)',
          opacity: 0.4,
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'relative',
          background: 'rgba(248,250,255,0.78)',
          backdropFilter: 'blur(16px) saturate(180%)',
          WebkitBackdropFilter: 'blur(16px) saturate(180%)',
          borderRadius: 32,
          display: 'flex',
          alignItems: 'center',
          padding: 7,
          border: '1px solid rgba(255,255,255,0.9)',
          boxShadow: '0 6px 20px rgba(0,104,122,0.1)',
        }}
      >
        <div style={{ paddingLeft: 12, paddingRight: 8, flexShrink: 0 }}>
          <svg width="15" height="15" viewBox="0 0 18 18" fill="none">
            <circle cx="8" cy="8" r="5.5" stroke="#9CA3AF" strokeWidth="1.5" />
            <path d="M12.5 12.5L16 16" stroke="#9CA3AF" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </div>
        <input
          style={{
            flex: 1,
            height: 38,
            fontSize: 14,
            fontFamily: 'Pretendard,sans-serif',
            fontWeight: loading ? 600 : 500,
            outline: 'none',
            border: 'none',
            background: 'transparent',
            color: '#64748b',
            minWidth: 0,
          }}
          placeholder={loading ? '분석하고 있어요...' : '무엇을 분석할까요?'}
          value={loading ? '' : value}
          disabled={loading}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !loading) onSubmit(value);
          }}
        />
        <button
          onClick={() => !loading && onSubmit(value)}
          style={{
            width: 42,
            height: 42,
            borderRadius: 12,
            flexShrink: 0,
            background: 'linear-gradient(135deg,#06b6d4 0%,#0891b2 100%)',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 2px 8px rgba(6,182,212,0.3)',
          }}
        >
          {loading ? (
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="white"
              style={{ animation: 'spin 1s linear infinite' }}
            >
              <path d="M12 2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8V2z" />
            </svg>
          ) : (
            <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
              <path
                d="M8 13V3M8 3L4 7M8 3L12 7"
                stroke="white"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          )}
        </button>
      </div>
    </div>
  );
}

/* ════════════════════
   메인 페이지
════════════════════ */
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

  /* 드래그 고스트 상태 */
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
          { dashboardId: 1, title: '기본', updatedAt: '' },
          { dashboardId: 2, title: '제목없음', updatedAt: '' },
        ]),
      );
  }, [id]);

  /* 위젯 업데이트 (이동/리사이즈) */
  const updateWidget = useCallback((wid: number, changes: Partial<CanvasWidget>) => {
    setCanvasWidgets((prev) => prev.map((w) => (w.widgetId === wid ? { ...w, ...changes } : w)));
  }, []);

  const updateTitle = useCallback((wid: number, t: string) => {
    setCanvasWidgets((prev) => prev.map((w) => (w.widgetId === wid ? { ...w, title: t } : w)));
  }, []);

  /* 채팅 미니 위젯 드래그 시작 */
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

        /* 캔버스 위에서 놓았는지 확인 */
        const canvas = canvasRef.current;
        if (!canvas) return;
        const rect = canvas.getBoundingClientRect();
        if (
          ev.clientX >= rect.left &&
          ev.clientX <= rect.right &&
          ev.clientY >= rect.top &&
          ev.clientY <= rect.bottom
        ) {
          /* 캔버스 상대 좌표 계산 (스크롤 포함) */
          const px = ev.clientX - rect.left + canvas.scrollLeft - 180;
          const py = ev.clientY - rect.top + canvas.scrollTop - 100;
          const newW: CanvasWidget = {
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
    [pendingType, widgetTitle, userQuery],
  );

  /* 쿼리 실행 (mock) */
  const handleQuery = useCallback(
    (text: string) => {
      if (!text.trim() || querying) return;
      setUserQuery(text);
      setQuerying(true);
      setChatOpen(true);
      setChatDone(false);
      setQueryInput('');
      const autoTitle = text.replace(/어때\?*|보여줘|분석해줘|알려줘|\?\?*/g, '').trim() || text;
      setWidgetTitle(autoTitle);
      const wType =
        text.includes('세그먼트') || text.includes('비중')
          ? 'SEGMENT'
          : text.includes('추이') || text.includes('라인')
            ? 'LINE_CHART'
            : 'BAR_CHART';
      setPendingType(wType);
      let i = 0;
      setSteps(STEP_LABELS.map((l, idx) => ({ label: l, done: false, active: idx === 0 })));
      const t = setInterval(() => {
        i++;
        setSteps(STEP_LABELS.map((l, idx) => ({ label: l, done: idx < i, active: idx === i })));
        if (i >= STEP_LABELS.length) {
          clearInterval(t);
          setTimeout(() => {
            setQuerying(false);
            setChatDone(true);
          }, 500);
        }
      }, 850);
    },
    [querying],
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
        {/* ── 탭 바 ── */}
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

        {/* ── 캔버스 ── */}
        <div ref={canvasRef} style={{ flex: 1, position: 'relative', overflow: 'auto' }}>
          <GridBg />
          {/* 절대 좌표 위젯들 */}
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

          {/* FINT 채팅 패널 + 검색바 */}
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
          <MiniBarSvg size="mini" />
        </div>
      )}
      <style>{`@keyframes spin { to { transform: rotate(360deg); } } * { box-sizing: border-box; }`}</style>
    </>
  );
}
