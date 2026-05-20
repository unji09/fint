'use client';

import React, { useRef, useState } from 'react';
import type { CanvasWidget } from '@/types/dashboard';
import WidgetRenderer from './WidgetRenderer';

interface Props {
  w: CanvasWidget;
  isSelected?: boolean;
  onSelect?: (id: number) => void;
  onUpdate: (id: number, changes: Partial<CanvasWidget>) => void;
  onTitleChange: (id: number, t: string) => void;
  onRemove?: (id: number) => void;
  canvasRef?: React.RefObject<HTMLDivElement | null>;
}

export default function CanvasWidgetCard({ w, isSelected, onSelect, onUpdate, onTitleChange, onRemove, canvasRef }: Props) {
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(w.title);
  const [renderError, setRenderError] = useState(false);
  const dragRef = useRef<{ sx: number; sy: number; ox: number; oy: number } | null>(null);
  const resizeRef = useRef<{ sx: number; sy: number; ow: number; oh: number; opx: number; opy: number; dir: string } | null>(null);
  const longPressRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 터치: 꾹 누르기 → 드래그 (500ms 후 진동 + 드래그 활성화, 선택 컨텍스트는 발동 안 함)
  const onCardTouchStart = (e: React.TouchEvent) => {
    if ((e.target as HTMLElement).closest?.('[data-resize]')) return;
    if ((e.target as HTMLElement).dataset.noDrag) return;
    if (editTitle) return;

    const touch = e.touches[0];
    let dragging = false;
    dragRef.current = { sx: touch.clientX, sy: touch.clientY, ox: w.px, oy: w.py };

    longPressRef.current = setTimeout(() => {
      longPressRef.current = null;
      dragging = true;
      if (navigator.vibrate) navigator.vibrate(30);
    }, 500);

    const onMove = (ev: TouchEvent) => {
      const t = ev.touches[0];
      const dist = Math.hypot(t.clientX - touch.clientX, t.clientY - touch.clientY);
      if (dist > 8 && longPressRef.current) {
        clearTimeout(longPressRef.current);
        longPressRef.current = null;
        dragRef.current = null;
        document.removeEventListener('touchmove', onMove);
        document.removeEventListener('touchend', onEnd);
        return;
      }
      if (dragging && dragRef.current) {
        ev.preventDefault();
        onUpdate(w.widgetId, {
          px: dragRef.current.ox + t.clientX - dragRef.current.sx,
          py: dragRef.current.oy + t.clientY - dragRef.current.sy,
        });
      }
    };
    const onEnd = () => {
      if (longPressRef.current) { clearTimeout(longPressRef.current); longPressRef.current = null; }
      dragging = false;
      dragRef.current = null;
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', onEnd);
    };
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', onEnd);
  };

  // 터치: 리사이즈 핸들
  const onResizeTouchStart = (e: React.TouchEvent, dir: string) => {
    e.stopPropagation();
    const touch = e.touches[0];
    resizeRef.current = { sx: touch.clientX, sy: touch.clientY, ow: w.pw, oh: w.ph, opx: w.px, opy: w.py, dir };
    const onMove = (ev: TouchEvent) => {
      if (!resizeRef.current) return;
      ev.preventDefault();
      const t = ev.touches[0];
      const dx = t.clientX - resizeRef.current.sx;
      const dy = t.clientY - resizeRef.current.sy;
      const { dir: d, ow, oh, opx, opy } = resizeRef.current;
      const newW = Math.max(140, ow + (d.includes('e') ? dx : d.includes('w') ? -dx : 0));
      const newH = Math.max(100, oh + (d.includes('s') ? dy : d.includes('n') ? -dy : 0));
      const newPx = d.includes('w') ? opx + (ow - newW) : opx;
      const newPy = d.includes('n') ? opy + (oh - newH) : opy;
      onUpdate(w.widgetId, { pw: newW, ph: newH, px: newPx, py: newPy });
    };
    const onEnd = () => {
      resizeRef.current = null;
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', onEnd);
    };
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', onEnd);
  };

  const onCardMouseDown = (e: React.MouseEvent) => {
    if (e.button !== 0) return; // 미들/우클릭은 캔버스로 버블업 (미들클릭 스크롤)
    if ((e.target as HTMLElement).dataset.resize) return;
    if ((e.target as HTMLElement).dataset.noDrag) return;
    if (editTitle) return;

    // Long press detection (500ms)
    longPressRef.current = setTimeout(() => {
      onSelect?.(w.widgetId);
    }, 500);

    e.preventDefault();
    dragRef.current = { sx: e.clientX, sy: e.clientY, ox: w.px, oy: w.py };

    const EDGE_ZONE = 40;
    const SCROLL_SPEED = 12;
    let lastMouse = { x: e.clientX, y: e.clientY };
    let scrollId: number | null = null;

    const doScroll = () => {
      const canvas = canvasRef?.current;
      if (!canvas || !dragRef.current) return;
      const rect = canvas.getBoundingClientRect();
      const mx = lastMouse.x;
      const my = lastMouse.y;
      let dx = 0;
      let dy = 0;
      if (mx < rect.left + EDGE_ZONE && mx >= rect.left) dx = -SCROLL_SPEED;
      else if (mx > rect.right - EDGE_ZONE && mx <= rect.right) dx = SCROLL_SPEED;
      if (my < rect.top + EDGE_ZONE && my >= rect.top) dy = -SCROLL_SPEED;
      else if (my > rect.bottom - EDGE_ZONE && my <= rect.bottom) dy = SCROLL_SPEED;
      if (dx || dy) {
        const prevLeft = canvas.scrollLeft;
        const prevTop = canvas.scrollTop;
        canvas.scrollBy(dx, dy);
        const actualDx = canvas.scrollLeft - prevLeft;
        const actualDy = canvas.scrollTop - prevTop;
        if (actualDx || actualDy) {
          dragRef.current.sx -= actualDx;
          dragRef.current.sy -= actualDy;
          onUpdate(w.widgetId, {
            px: dragRef.current.ox + lastMouse.x - dragRef.current.sx,
            py: dragRef.current.oy + lastMouse.y - dragRef.current.sy,
          });
        }
      }
      scrollId = requestAnimationFrame(doScroll);
    };
    if (canvasRef?.current) scrollId = requestAnimationFrame(doScroll);

    const onMove = (ev: MouseEvent) => {
      if (!dragRef.current) return;
      // 5px 이상 이동하면 롱프레스 취소
      const dist = Math.hypot(ev.clientX - dragRef.current.sx, ev.clientY - dragRef.current.sy);
      if (dist > 5 && longPressRef.current) {
        clearTimeout(longPressRef.current);
        longPressRef.current = null;
      }
      lastMouse = { x: ev.clientX, y: ev.clientY };
      onUpdate(w.widgetId, { px: dragRef.current.ox + ev.clientX - dragRef.current.sx, py: dragRef.current.oy + ev.clientY - dragRef.current.sy });
    };
    const onUp = (ev: MouseEvent) => {
      if (longPressRef.current) {
        clearTimeout(longPressRef.current);
        longPressRef.current = null;
        // 짧은 클릭 → 선택
        const dist = Math.hypot(ev.clientX - (dragRef.current?.sx ?? ev.clientX), ev.clientY - (dragRef.current?.sy ?? ev.clientY));
        if (dist < 5) onSelect?.(w.widgetId);
      }
      dragRef.current = null;
      if (scrollId !== null) cancelAnimationFrame(scrollId);
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const onResizeDown = (e: React.MouseEvent, dir: string) => {
    e.stopPropagation(); e.preventDefault();
    resizeRef.current = { sx: e.clientX, sy: e.clientY, ow: w.pw, oh: w.ph, opx: w.px, opy: w.py, dir };
    const onMove = (ev: MouseEvent) => {
      if (!resizeRef.current) return;
      const dx = ev.clientX - resizeRef.current.sx;
      const dy = ev.clientY - resizeRef.current.sy;
      const { dir: d, ow, oh, opx, opy } = resizeRef.current;
      const newW = Math.max(140, ow + (d.includes('e') ? dx : d.includes('w') ? -dx : 0));
      const newH = Math.max(100, oh + (d.includes('s') ? dy : d.includes('n') ? -dy : 0));
      const newPx = d.includes('w') ? opx + (ow - newW) : opx;
      const newPy = d.includes('n') ? opy + (oh - newH) : opy;
      onUpdate(w.widgetId, { pw: newW, ph: newH, px: newPx, py: newPy });
    };
    const onUp = () => { resizeRef.current = null; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  // 선택된 위젯 스타일
  const selectedBorder = isSelected
    ? { border: '2px solid #06b6d4', boxShadow: '0 0 0 4px rgba(6,182,212,0.18), 0 4px 16px rgba(0,0,0,0.08)' }
    : { border: '1px solid #dee3e6', boxShadow: '0 4px 16px rgba(0,0,0,0.08)' };

  return (
    <div
      data-widget-card="1"
      onMouseDown={onCardMouseDown}
      onTouchStart={onCardTouchStart}
      style={{
        position: 'absolute', left: w.px, top: w.py, width: w.pw, height: w.ph,
        background: 'white', borderRadius: 10,
        ...selectedBorder,
        overflow: 'hidden',
        display: 'flex', flexDirection: 'column', cursor: 'grab', userSelect: 'none',
        transition: 'border-color 0.15s, box-shadow 0.15s',
        zIndex: isSelected ? 2 : 1,
      }}
    >
      {/* 헤더 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px 8px', borderBottom: '1px solid #eff4f7', flexShrink: 0, minWidth: 0 }}>
        {editTitle
          ? <input autoFocus value={titleVal}
                   onChange={e => setTitleVal(e.target.value)}
                   onBlur={() => { setEditTitle(false); onTitleChange(w.widgetId, titleVal); }}
                   onKeyDown={e => { if (e.key === 'Enter') { setEditTitle(false); onTitleChange(w.widgetId, titleVal); } }}
                   onClick={e => e.stopPropagation()} onMouseDown={e => e.stopPropagation()}
                   data-no-drag="1"
                   style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 14, border: 'none', outline: '1px solid #06b6d4', borderRadius: 4, padding: '0 4px', width: '80%', minWidth: 0 }} />
          : <span
              style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 14, color: '#171d1e', cursor: 'text', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, minWidth: 0, borderBottom: '1px dashed transparent', transition: 'border-color 0.12s' }}
              title="더블클릭하면 이름을 바꿀 수 있어요"
              onMouseEnter={e => { e.currentTarget.style.borderBottomColor = 'rgba(6,182,212,0.4)'; }}
              onMouseLeave={e => { e.currentTarget.style.borderBottomColor = 'transparent'; }}
              onDoubleClick={e => { e.stopPropagation(); setEditTitle(true); }}
            >{titleVal}</span>}
        {onRemove ? (
          <button
            type="button"
            data-no-drag="1"
            onMouseDown={(e) => e.stopPropagation()}
            onClick={(e) => { e.stopPropagation(); onRemove(w.widgetId); }}
            aria-label="위젯 삭제"
            title="이 위젯 삭제"
            style={{ flexShrink: 0, marginLeft: 6, width: 22, height: 22, borderRadius: 6, border: 'none', background: 'transparent', color: '#94a3b8', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0, transition: 'background-color 0.12s, color 0.12s' }}
            onMouseOver={(e) => { e.currentTarget.style.background = 'rgba(239,68,68,0.10)'; e.currentTarget.style.color = '#ef4444'; }}
            onMouseOut={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#94a3b8'; }}
          >
            <svg width="11" height="11" viewBox="0 0 12 12" fill="none">
              <path d="M3 3l6 6M9 3l-6 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </button>
        ) : (
          <span style={{ color: '#94a3b8', fontSize: 15, flexShrink: 0, marginLeft: 6 }}>···</span>
        )}
      </div>

      {/* 컨텐츠 */}
      <div style={{ flex: 1, minHeight: 0, minWidth: 0, overflow: 'hidden', padding: '6px 10px 8px', display: 'flex', alignItems: 'stretch' }}>
        {renderError ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', width: '100%', gap: 8, padding: 12 }}>
            <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
              <circle cx="14" cy="14" r="12" stroke="#fca5a5" strokeWidth="1.5" fill="rgba(254,226,226,0.5)" />
              <path d="M14 9v6M14 18v1" stroke="#ef4444" strokeWidth="2" strokeLinecap="round" />
            </svg>
            <span style={{ fontSize: 12, fontFamily: 'Pretendard,sans-serif', color: '#ef4444', textAlign: 'center', lineHeight: 1.5 }}>
              위젯을 불러오지 못했어요.<br />다른 조건으로 수정하거나, 삭제해 보세요.
            </span>
            {onRemove && (
              <button
                data-no-drag="1"
                onMouseDown={e => e.stopPropagation()}
                onClick={e => { e.stopPropagation(); onRemove(w.widgetId); }}
                style={{ padding: '4px 12px', borderRadius: 6, border: '1px solid #fca5a5', background: 'white', color: '#ef4444', fontSize: 12, fontFamily: 'Pretendard,sans-serif', cursor: 'pointer' }}
              >삭제</button>
            )}
          </div>
        ) : (
          <ErrorBoundary onError={() => {
            console.error('[FINT][Widget Error]', w.widgetId, w.widgetType);
            setRenderError(true);
          }}>
            <WidgetRenderer
              widgetType={w.widgetType}
              config={w.config ?? {}}
              data={w.data ?? null}
              result={w.result ?? null}
            />
          </ErrorBoundary>
        )}
      </div>

      {/* 전체 테두리 투명 리사이즈 존 (시각적 표시 없음) */}
      <div data-resize="1" onMouseDown={e => onResizeDown(e, 'n')}  style={{ position: 'absolute', top: 0,    left: 12,  right: 12,  height: 6,  cursor: 'n-resize',  zIndex: 10 }} />
      <div data-resize="1" onMouseDown={e => onResizeDown(e, 's')}  style={{ position: 'absolute', bottom: 0, left: 12,  right: 12,  height: 6,  cursor: 's-resize',  zIndex: 10 }} />
      <div data-resize="1" onMouseDown={e => onResizeDown(e, 'w')}  style={{ position: 'absolute', top: 12,   bottom: 12, left: 0,    width: 6,   cursor: 'w-resize',  zIndex: 10 }} />
      <div data-resize="1" onMouseDown={e => onResizeDown(e, 'e')}  style={{ position: 'absolute', top: 12,   bottom: 12, right: 0,   width: 6,   cursor: 'e-resize',  zIndex: 10 }} />
      <div data-resize="1" onMouseDown={e => onResizeDown(e, 'nw')} style={{ position: 'absolute', top: 0,    left: 0,    width: 12,  height: 12, cursor: 'nw-resize', zIndex: 11 }} />
      <div data-resize="1" onMouseDown={e => onResizeDown(e, 'ne')} style={{ position: 'absolute', top: 0,    right: 0,   width: 12,  height: 12, cursor: 'ne-resize', zIndex: 11 }} />
      {/* SW — 데스크탑 전용 (마우스만) */}
      <div
        data-resize="1"
        onMouseDown={e => onResizeDown(e, 'sw')}
        style={{ position: 'absolute', bottom: 0, left: 0, width: 12, height: 12, cursor: 'sw-resize', zIndex: 11 }}
      />
      {/* 우하단 — 모바일 터치 포함 리사이즈 핸들 */}
      <div
        data-resize="1"
        onMouseDown={e => onResizeDown(e, 'se')}
        onTouchStart={e => onResizeTouchStart(e, 'se')}
        style={{ position: 'absolute', bottom: 0, right: 0, width: 36, height: 36, cursor: 'se-resize', zIndex: 11, display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end', padding: '5px' }}
      >
        <svg data-resize="1" width="12" height="12" viewBox="0 0 10 10" fill="none" style={{ opacity: 0.35 }}>
          <line x1="3"  y1="10" x2="10" y2="3"  stroke="#64748b" strokeWidth="1.5" strokeLinecap="round" />
          <line x1="6.5" y1="10" x2="10" y2="6.5" stroke="#64748b" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </div>
    </div>
  );
}

// React ErrorBoundary (클래스 컴포넌트 필요)
class ErrorBoundary extends React.Component<{ children: React.ReactNode; onError: () => void }, { hasError: boolean }> {
  constructor(props: { children: React.ReactNode; onError: () => void }) {
    super(props);
    this.state = { hasError: false };
  }
  static getDerivedStateFromError() { return { hasError: true }; }
  componentDidCatch(err: Error) {
    console.error('[FINT][Widget Error]', err);
    this.props.onError();
  }
  render() {
    if (this.state.hasError) return null;
    return this.props.children;
  }
}
