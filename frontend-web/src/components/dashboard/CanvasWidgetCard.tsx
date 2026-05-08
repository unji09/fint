'use client';

import React, { useRef, useState } from 'react';
import type { CanvasWidget } from '@/types/dashboard';
import { BarChartSvg, LineChartSvg, SegmentChart } from './ChartWidgets';

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
}

export default function CanvasWidgetCard({ w, onUpdate, onTitleChange }: Props) {
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
      const newW = Math.max(240, resizeRef.current.ow + (dir.includes('e') ? dx : dir.includes('w') ? -dx : 0));
      const newH = Math.max(180, resizeRef.current.oh + (dir.includes('s') ? dy : dir.includes('n') ? -dy : 0));
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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px 10px', borderBottom: '1px solid #eff4f7', flexShrink: 0 }}>
        {editTitle
          ? <input autoFocus value={titleVal}
                   onChange={e => setTitleVal(e.target.value)}
                   onBlur={() => { setEditTitle(false); onTitleChange(w.widgetId, titleVal); }}
                   onKeyDown={e => { if (e.key === 'Enter') { setEditTitle(false); onTitleChange(w.widgetId, titleVal); } }}
                   onClick={e => e.stopPropagation()} onMouseDown={e => e.stopPropagation()}
                   style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 15, border: 'none', outline: '1px solid #06b6d4', borderRadius: 4, padding: '0 4px', width: '80%' }} />
          : <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 15, color: '#171d1e', cursor: 'text' }}
                  onDoubleClick={e => { e.stopPropagation(); setEditTitle(true); }}>{titleVal}</span>}
        <span style={{ color: '#94a3b8', fontSize: 16 }}>···</span>
      </div>
      <div style={{ flex: 1, overflow: 'hidden', padding: '8px 14px 10px' }}>
        {w.widgetType === 'LINE_CHART' ? <LineChartSvg /> : w.widgetType === 'SEGMENT' ? <SegmentChart /> : <BarChartSvg />}
      </div>
      {RESIZE_HANDLES.map(h => (
        <div key={h.dir} data-resize="1" onMouseDown={e => onResizeDown(e, h.dir)}
             style={{ position: 'absolute', width: 10, height: 10, borderRadius: 3, background: '#06b6d4', border: '2px solid white', boxShadow: '0 1px 4px rgba(0,0,0,0.2)', zIndex: 10, ...h.style }} />
      ))}
    </div>
  );
}
