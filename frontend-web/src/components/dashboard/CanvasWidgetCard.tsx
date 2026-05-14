'use client';

import React, { useRef, useState } from 'react';
import type { CanvasWidget } from '@/types/dashboard';
import WidgetRenderer from './WidgetRenderer';

const RESIZE_HANDLES: { dir: string; style: React.CSSProperties }[] = [
  { dir: 'nw', style: { top: -5, left: -5, cursor: 'nw-resize' } },
  { dir: 'ne', style: { top: -5, right: -5, cursor: 'ne-resize' } },
  { dir: 'sw', style: { bottom: -5, left: -5, cursor: 'sw-resize' } },
  { dir: 'se', style: { bottom: -5, right: -5, cursor: 'se-resize' } },
  { dir: 'e',  style: { top: '50%', right: -5, transform: 'translateY(-50%)', cursor: 'e-resize' } },
  { dir: 'w',  style: { top: '50%', left: -5, transform: 'translateY(-50%)', cursor: 'w-resize' } },
  { dir: 's',  style: { bottom: -5, left: '50%', transform: 'translateX(-50%)', cursor: 's-resize' } },
  { dir: 'n',  style: { top: -5, left: '50%', transform: 'translateX(-50%)', cursor: 'n-resize' } },
];

interface Props {
  w: CanvasWidget;
  onUpdate: (id: number, changes: Partial<CanvasWidget>) => void;
  onTitleChange: (id: number, t: string) => void;
  onRemove?: (id: number) => void;
}

export default function CanvasWidgetCard({ w, onUpdate, onTitleChange, onRemove }: Props) {
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(w.title);
  const dragRef = useRef<{ sx: number; sy: number; ox: number; oy: number } | null>(null);
  const resizeRef = useRef<{ sx: number; sy: number; ow: number; oh: number; dir: string } | null>(null);

  const onCardMouseDown = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).dataset.resize) return;
    if (editTitle) return;
    e.preventDefault();
    dragRef.current = { sx: e.clientX, sy: e.clientY, ox: w.px, oy: w.py };
    const onMove = (ev: MouseEvent) => {
      if (!dragRef.current) return;
      onUpdate(w.widgetId, { px: dragRef.current.ox + ev.clientX - dragRef.current.sx, py: dragRef.current.oy + ev.clientY - dragRef.current.sy });
    };
    const onUp = () => { dragRef.current = null; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const onResizeDown = (e: React.MouseEvent, dir: string) => {
    e.stopPropagation(); e.preventDefault();
    resizeRef.current = { sx: e.clientX, sy: e.clientY, ow: w.pw, oh: w.ph, dir };
    const onMove = (ev: MouseEvent) => {
      if (!resizeRef.current) return;
      const dx = ev.clientX - resizeRef.current.sx;
      const dy = ev.clientY - resizeRef.current.sy;
      // 최소 크기를 작게 잡아서 자유로운 리사이즈를 보장. 차트는 viewBox 기반이라 알아서 fit.
      const newW = Math.max(140, resizeRef.current.ow + (dir.includes('e') ? dx : dir.includes('w') ? -dx : 0));
      const newH = Math.max(100, resizeRef.current.oh + (dir.includes('s') ? dy : dir.includes('n') ? -dy : 0));
      const newPx = dir.includes('w') ? w.px + (resizeRef.current.ow - newW) : w.px;
      const newPy = dir.includes('n') ? w.py + (resizeRef.current.oh - newH) : w.py;
      onUpdate(w.widgetId, { pw: newW, ph: newH, px: newPx, py: newPy });
    };
    const onUp = () => { resizeRef.current = null; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  return (
    <div onMouseDown={onCardMouseDown} style={{
      position: 'absolute', left: w.px, top: w.py, width: w.pw, height: w.ph,
      background: 'white', borderRadius: 10, border: '1px solid #dee3e6',
      boxShadow: '0 4px 16px rgba(0,0,0,0.08)', overflow: 'hidden',
      display: 'flex', flexDirection: 'column', cursor: 'grab', userSelect: 'none',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px 8px', borderBottom: '1px solid #eff4f7', flexShrink: 0, minWidth: 0 }}>
        {editTitle
          ? <input autoFocus value={titleVal}
                   onChange={e => setTitleVal(e.target.value)}
                   onBlur={() => { setEditTitle(false); onTitleChange(w.widgetId, titleVal); }}
                   onKeyDown={e => { if (e.key === 'Enter') { setEditTitle(false); onTitleChange(w.widgetId, titleVal); } }}
                   onClick={e => e.stopPropagation()} onMouseDown={e => e.stopPropagation()}
                   style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 14, border: 'none', outline: '1px solid #06b6d4', borderRadius: 4, padding: '0 4px', width: '80%', minWidth: 0 }} />
          : <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 14, color: '#171d1e', cursor: 'text', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, minWidth: 0, borderBottom: '1px dashed transparent', transition: 'border-color 0.12s' }}
                  title="더블클릭하면 이름을 바꿀 수 있어요"
                  onMouseEnter={e => { e.currentTarget.style.borderBottomColor = 'rgba(6,182,212,0.4)'; }}
                  onMouseLeave={e => { e.currentTarget.style.borderBottomColor = 'transparent'; }}
                  onDoubleClick={e => { e.stopPropagation(); setEditTitle(true); }}>{titleVal}</span>}
        {onRemove ? (
          <button
            type="button"
            onMouseDown={(e) => e.stopPropagation()}
            onClick={(e) => { e.stopPropagation(); onRemove(w.widgetId); }}
            aria-label="위젯 삭제"
            title="이 위젯 삭제"
            style={{
              flexShrink: 0,
              marginLeft: 6,
              width: 22,
              height: 22,
              borderRadius: 6,
              border: 'none',
              background: 'transparent',
              color: '#94a3b8',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0,
              transition: 'background-color 0.12s, color 0.12s',
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.background = 'rgba(239,68,68,0.10)';
              e.currentTarget.style.color = '#ef4444';
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.background = 'transparent';
              e.currentTarget.style.color = '#94a3b8';
            }}
          >
            <svg width="11" height="11" viewBox="0 0 12 12" fill="none">
              <path d="M3 3l6 6M9 3l-6 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </button>
        ) : (
          <span style={{ color: '#94a3b8', fontSize: 15, flexShrink: 0, marginLeft: 6 }}>···</span>
        )}
      </div>
      <div style={{ flex: 1, minHeight: 0, minWidth: 0, overflow: 'hidden', padding: '6px 10px 8px', display: 'flex', alignItems: 'stretch' }}>
        <WidgetRenderer
          widgetType={w.widgetType}
          config={w.config ?? {}}
          data={w.data ?? null}
          result={w.result ?? null}
        />
      </div>
      {RESIZE_HANDLES.map(h => (
        <div key={h.dir} data-resize="1" onMouseDown={e => onResizeDown(e, h.dir)}
             style={{ position: 'absolute', width: 10, height: 10, borderRadius: 3, background: '#06b6d4', border: '2px solid white', boxShadow: '0 1px 4px rgba(0,0,0,0.2)', zIndex: 10, ...h.style }} />
      ))}
    </div>
  );
}
