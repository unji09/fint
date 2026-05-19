'use client';

import React, { useEffect, useRef, useState } from 'react';
import type { Step, WidgetResult, ChatMessage } from '@/types/dashboard';
import WidgetRenderer from './WidgetRenderer';
import useBreakpoint from '@/hooks/useBreakpoint';

interface SelectedWidget {
  id: number;
  title: string;
  type: string;
}

interface Props {
  steps: Step[];
  query: string;
  isLoading: boolean;
  isDone: boolean;
  errorMessage?: string | null;
  widgetTitle: string;
  widgetType: string;
  result?: WidgetResult | null;
  config?: Record<string, unknown>;
  data?: Record<string, unknown>[] | null;
  onTitleChange: (v: string) => void;
  onCollapse: () => void;
  onDragStart: (e: React.MouseEvent) => void;
  onSubmit?: (v: string) => void;
  chatHistory?: ChatMessage[];
  selectedWidget?: SelectedWidget | null;
  onClearSelectedWidget?: () => void;
  onWidthChange?: (w: number) => void;
  panelPosX?: number;
  panelPosY?: number;
  onPosChange?: (x: number, y: number) => void;
}

const FALLBACK_INSIGHT = '최근 활동 데이터와 DART 공시를 결합하여 분석한 결과입니다.';

// 지원 가능한 명령어 목록 (실제 동작 확인된 것만)
const SUPPORTED_QUERIES = [
  '고객사별 딜 현황',
  '이번 달 활동 요약',
  '딜 파이프라인 단계별 분포',
  '만료 임박 계약 목록',
  '정체 딜 목록',
  '팀 활동 통계',
  '고객사 매출 추이',
  '최근 미팅 목록',
  '신규 고객사 현황',
  '성공 확률 높은 딜 목록',
];

function pickRandom<T>(arr: T[], n: number): T[] {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

function loadPanelSize(isMobile: boolean) {
  if (isMobile) return { w: 0, h: 320 };
  try {
    const saved = JSON.parse(localStorage.getItem('fint:chatPanelSize') ?? '{}') as { w?: number; h?: number };
    if (saved.w && saved.h) return { w: Math.max(320, Math.min(700, saved.w)), h: Math.max(250, Math.min(700, saved.h)) };
  } catch { /* ignore */ }
  return { w: 390, h: 420 };
}

export default function FintChatPanel({
  steps,
  query,
  isLoading,
  isDone,
  errorMessage,
  widgetTitle,
  widgetType,
  result,
  config: widgetConfig = {},
  data: widgetData = null,
  onTitleChange,
  onCollapse,
  onDragStart,
  onSubmit,
  chatHistory = [],
  selectedWidget,
  onClearSelectedWidget,
  onWidthChange,
  panelPosX,
  panelPosY,
  onPosChange,
}: Props) {
  const bp = useBreakpoint();
  const isMobile = bp === 'mobile';
  const insightText = result?.insightText && result.insightText.trim().length > 0
    ? result.insightText
    : FALLBACK_INSIGHT;
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(widgetTitle);

  // 패널 크기
  const [panelSize, setPanelSize] = useState(() => loadPanelSize(isMobile));
  const resizing = useRef(false);
  const resizeStart = useRef({ x: 0, y: 0, w: 390, h: 420, posX: 20, posY: 28 });

  // 패널 크기 저장 + 부모 너비 동기화
  useEffect(() => {
    if (!isMobile) {
      try { localStorage.setItem('fint:chatPanelSize', JSON.stringify(panelSize)); } catch { /* ignore */ }
      onWidthChange?.(panelSize.w);
    }
  }, [panelSize, isMobile, onWidthChange]);

  const handleResizeStart = (e: React.MouseEvent, dir: 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w' | 'nw') => {
    if (isMobile) return;
    e.preventDefault();
    e.stopPropagation();
    resizing.current = true;
    resizeStart.current = {
      x: e.clientX, y: e.clientY,
      w: panelSize.w, h: panelSize.h,
      posX: panelPosX ?? 20, posY: panelPosY ?? 28,
    };
    const MIN_W = 280, MAX_W = 700, MIN_H = 180, MAX_H = 700;
    const onMove = (ev: MouseEvent) => {
      if (!resizing.current) return;
      const { x: sx, y: sy, w: sw, h: sh, posX: spx, posY: spy } = resizeStart.current;
      const dx = ev.clientX - sx;
      const dy = ev.clientY - sy;
      let newW = sw, newH = sh, newPosX = spx, newPosY = spy;

      if (dir.includes('e')) {
        newW = Math.max(MIN_W, Math.min(MAX_W, sw + dx));
      }
      if (dir.includes('w')) {
        const rightEdge = spx + sw;
        newPosX = Math.max(12, Math.min(rightEdge - MIN_W, spx + dx));
        newW = Math.max(MIN_W, rightEdge - newPosX);
      }
      if (dir.includes('n')) {
        newH = Math.max(MIN_H, Math.min(MAX_H, sh - dy));
      }
      if (dir.includes('s')) {
        newH = Math.max(MIN_H, Math.min(MAX_H, sh + dy));
        newPosY = Math.max(12, spy - (newH - sh));
      }

      setPanelSize({ w: newW, h: newH });
      if (dir.includes('w') || dir.includes('s')) {
        onPosChange?.(newPosX, newPosY);
      }
    };
    const onUp = () => {
      resizing.current = false;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const effectiveWidth = isMobile ? undefined : panelSize.w;

  useEffect(() => { setTitleVal(widgetTitle); }, [widgetTitle]);

  // 자동 스크롤
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const scrollToBottom = (instant?: boolean) => {
    const el = scrollContainerRef.current;
    if (!el) return;
    if (instant) { el.scrollTop = el.scrollHeight; }
    else { el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' }); }
  };
  useEffect(() => { scrollToBottom(); }, [chatHistory.length, isLoading]);
  useEffect(() => { if (isDone) scrollToBottom(true); }, [isDone]);

  const suggestedOnError = useRef<string[]>([]);
  const [initialSuggestions, setInitialSuggestions] = useState<string[]>([]);
  useEffect(() => {
    suggestedOnError.current = pickRandom(SUPPORTED_QUERIES, 3);
    setInitialSuggestions(pickRandom(SUPPORTED_QUERIES, 4));
  }, []);

  return (
    <div style={{ position: 'relative', width: isMobile ? '100%' : effectiveWidth, marginBottom: 0 }}>
      {/* 리사이즈 핸들 — 8방향 */}
      {!isMobile && (
        <>
          {/* 4개 테두리 엣지 */}
          <div onMouseDown={(e) => handleResizeStart(e, 'n')}  style={{ position: 'absolute', left: 16, right: 16, top: -5,    height: 10, cursor: 'n-resize',  zIndex: 12 }} />
          <div onMouseDown={(e) => handleResizeStart(e, 's')}  style={{ position: 'absolute', left: 16, right: 16, bottom: -5, height: 10, cursor: 's-resize',  zIndex: 12 }} />
          <div onMouseDown={(e) => handleResizeStart(e, 'e')}  style={{ position: 'absolute', top: 16, bottom: 16, right: -5,  width: 10,  cursor: 'e-resize',  zIndex: 12 }} />
          <div onMouseDown={(e) => handleResizeStart(e, 'w')}  style={{ position: 'absolute', top: 16, bottom: 16, left: -5,   width: 10,  cursor: 'w-resize',  zIndex: 12 }} />
          {/* 4개 코너 */}
          <div onMouseDown={(e) => handleResizeStart(e, 'ne')} style={{ position: 'absolute', top: -6, right: -6, width: 18, height: 18, cursor: 'ne-resize', zIndex: 13, display: 'flex', alignItems: 'flex-start', justifyContent: 'flex-end', padding: 3 }}>
            <svg width="8" height="8" viewBox="0 0 8 8" fill="none" style={{ pointerEvents: 'none', opacity: 0.5 }}><circle cx="6" cy="2" r="2" fill="#06b6d4"/></svg>
          </div>
          <div onMouseDown={(e) => handleResizeStart(e, 'nw')} style={{ position: 'absolute', top: -6, left: -6,  width: 18, height: 18, cursor: 'nw-resize', zIndex: 13, display: 'flex', alignItems: 'flex-start', justifyContent: 'flex-start', padding: 3 }}>
            <svg width="8" height="8" viewBox="0 0 8 8" fill="none" style={{ pointerEvents: 'none', opacity: 0.5 }}><circle cx="2" cy="2" r="2" fill="#06b6d4"/></svg>
          </div>
          <div onMouseDown={(e) => handleResizeStart(e, 'se')} style={{ position: 'absolute', bottom: -6, right: -6, width: 18, height: 18, cursor: 'se-resize', zIndex: 13, display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end', padding: 3 }}>
            <svg width="8" height="8" viewBox="0 0 8 8" fill="none" style={{ pointerEvents: 'none', opacity: 0.5 }}><circle cx="6" cy="6" r="2" fill="#06b6d4"/></svg>
          </div>
          <div onMouseDown={(e) => handleResizeStart(e, 'sw')} style={{ position: 'absolute', bottom: -6, left: -6,  width: 18, height: 18, cursor: 'sw-resize', zIndex: 13, display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-start', padding: 3 }}>
            <svg width="8" height="8" viewBox="0 0 8 8" fill="none" style={{ pointerEvents: 'none', opacity: 0.5 }}><circle cx="2" cy="6" r="2" fill="#06b6d4"/></svg>
          </div>
        </>
      )}

      <div style={{
        background: 'linear-gradient(180deg, rgba(255,255,255,0.85) 0%, rgba(244,249,255,0.78) 100%)',
        backdropFilter: 'blur(20px) saturate(180%) brightness(1.06)',
        WebkitBackdropFilter: 'blur(20px) saturate(180%) brightness(1.06)',
        border: '1px solid rgba(255,255,255,0.95)',
        boxShadow: '0 1px 0 rgba(255,255,255,0.9) inset, 0 -1px 0 rgba(6,182,212,0.06) inset, 0 2px 6px rgba(15,23,42,0.04), 0 12px 32px rgba(15,23,42,0.10), 0 24px 48px -12px rgba(6,182,212,0.12)',
        borderRadius: 20,
        width: '100%',
        overflow: 'hidden',
        position: 'relative' as const,
      }}>

        {/* 헤더 (드래그 영역은 부모 컨테이너에서 처리) */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', background: 'linear-gradient(180deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0) 100%)', borderBottom: '1px solid rgba(226,232,240,0.6)', cursor: 'default' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ color: '#06b6d4', fontSize: 14 }}>✦</span>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 600, fontSize: 14, color: '#1d1a24' }}>FINT</span>
          </div>
          <button onClick={onCollapse} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', padding: 4, display: 'flex', position: 'relative', zIndex: 31 }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>
        </div>

        {/* 선택 위젯 컨텍스트 배너 */}
        {selectedWidget && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 16px', background: 'rgba(6,182,212,0.08)', borderBottom: '1px solid rgba(6,182,212,0.15)' }}>
            <span style={{ color: '#06b6d4', fontSize: 12 }}>✦</span>
            <span style={{ flex: 1, fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#0e7490' }}>
              <strong>{selectedWidget.title}</strong> 기반으로 수정
            </span>
            <button
              onClick={onClearSelectedWidget}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', padding: 0, fontSize: 16, lineHeight: 1 }}
            >×</button>
          </div>
        )}

        <div ref={scrollContainerRef} style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 10, height: panelSize.h, overflowY: 'auto' }}>

          {/* 첫 화면 — 예시 명령어 */}
          {chatHistory.length === 0 && !isLoading && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#94a3b8', margin: 0, textAlign: 'center' }}>
                무엇이든 물어보세요. 예를 들면:
              </p>
              {initialSuggestions.map((q) => (
                <button
                  key={q}
                  onClick={() => onSubmit?.(q)}
                  style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid rgba(6,182,212,0.3)', background: 'rgba(6,182,212,0.05)', cursor: 'pointer', fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#0e7490', textAlign: 'left', transition: 'background 0.12s' }}
                  onMouseOver={e => { e.currentTarget.style.background = 'rgba(6,182,212,0.12)'; }}
                  onMouseOut={e => { e.currentTarget.style.background = 'rgba(6,182,212,0.05)'; }}
                >{q}</button>
              ))}
            </div>
          )}

          {/* 이전 대화 내역 */}
          {chatHistory.filter((msg, idx) => {
            if (!isDone) return true;
            if (msg.role !== 'assistant') return true;
            const lastAssistantIdx = chatHistory.reduce((acc, m, i) => m.role === 'assistant' ? i : acc, -1);
            return idx !== lastAssistantIdx;
          }).map((msg) => {
            if (msg.role === 'user') {
              return (
                <div key={msg.id} style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <div style={{ background: '#06b6d4', color: 'white', borderRadius: '18px 18px 4px 18px', padding: '8px 14px', fontSize: 14, fontFamily: 'Pretendard,sans-serif', fontWeight: 500, maxWidth: 240, lineHeight: 1.4 }}>
                    {msg.content}
                  </div>
                </div>
              );
            }
            if (msg.status === 'error') {
              return (
                <div key={msg.id} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 10, padding: '12px 14px' }}>
                    <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#dc2626', margin: 0, lineHeight: 1.6 }}>
                      {msg.errorMessage ?? '쿼리 처리에 실패했습니다.'}
                    </p>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 11, color: '#94a3b8' }}>이런 건 물어볼 수 있어요</span>
                    {suggestedOnError.current.map((q) => (
                      <button key={q} onClick={() => onSubmit?.(q)} style={{ padding: '6px 10px', borderRadius: 8, border: '1px solid rgba(6,182,212,0.25)', background: 'rgba(6,182,212,0.04)', cursor: 'pointer', fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#0e7490', textAlign: 'left' }}>
                        {q}
                      </button>
                    ))}
                  </div>
                </div>
              );
            }
            const msgInsight = msg.content && msg.content.trim().length > 0 ? msg.content : FALLBACK_INSIGHT;
            const msgWidgetType = msg.widget?.widgetType ?? 'BAR_CHART';
            const msgWidgetData = Array.isArray(msg.widget?.data) ? (msg.widget.data as Record<string, unknown>[]) : null;
            const msgWidgetResult = !msgWidgetData && msg.widget?.data ? { data: msg.widget.data, insightText: '' } : null;
            return (
              <div key={msg.id} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#475569', margin: 0, lineHeight: 1.6 }}>{msgInsight}</p>
                {msg.widget && (
                  <div style={{ background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(226,232,240,0.5)', borderRadius: 10, overflow: 'hidden', opacity: 0.85 }}>
                    <div style={{ display: 'flex', alignItems: 'center', padding: '7px 14px 6px', borderBottom: '1px solid rgba(241,245,249,0.8)' }}>
                      <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 12, color: '#64748b' }}>{msg.widget.title}</span>
                    </div>
                    <div style={{ padding: '4px 10px 6px', height: 120 }}>
                      <WidgetRenderer widgetType={msgWidgetType} config={msg.widget.config ?? {}} data={msgWidgetData} result={msgWidgetResult} />
                    </div>
                  </div>
                )}
              </div>
            );
          })}

          {/* 현재 진행 중인 쿼리 */}
          {chatHistory.length === 0 && query && (
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <div style={{ background: '#06b6d4', color: 'white', borderRadius: '18px 18px 4px 18px', padding: '8px 14px', fontSize: 14, fontFamily: 'Pretendard,sans-serif', fontWeight: 500, maxWidth: 240, lineHeight: 1.4 }}>
                {query}
              </div>
            </div>
          )}

          {/* 로딩 */}
          {isLoading && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
              {steps.map((s, i) => (
                <div key={i}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 0' }}>
                    {s.done ? (
                      <div style={{ width: 20, height: 20, borderRadius: 6, background: '#81dbe0', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <svg width="11" height="11" viewBox="0 0 14 14" fill="none"><path d="M2.5 7L5.5 10L11.5 4" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
                      </div>
                    ) : s.active ? (
                      <div style={{ width: 20, height: 20, borderRadius: 6, flexShrink: 0, borderTop: '2px solid #06b6d4', borderRight: '2px solid rgba(6,182,212,0.3)', borderBottom: '2px solid rgba(6,182,212,0.3)', borderLeft: '2px solid rgba(6,182,212,0.3)', animation: 'spin 0.8s linear infinite' }} />
                    ) : (
                      <div style={{ width: 20, height: 20, borderRadius: 6, flexShrink: 0, border: '2px solid rgba(226,232,240,0.8)' }} />
                    )}
                    <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 700, fontSize: 10, letterSpacing: '0.5px', textTransform: 'uppercase', color: s.done || s.active ? '#64748b' : '#cbd5e1' }}>
                      {s.label}
                    </span>
                  </div>
                  {i < steps.length - 1 && (
                    <div style={{ width: 2, height: 8, background: s.done ? '#81dbe0' : 'rgba(226,232,240,0.8)', marginLeft: 9, borderRadius: 2 }} />
                  )}
                </div>
              ))}
            </div>
          )}

          {/* 에러 */}
          {isDone && errorMessage && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 10, padding: '12px 14px' }}>
                <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#dc2626', margin: 0, lineHeight: 1.6 }}>{errorMessage}</p>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 11, color: '#94a3b8' }}>이런 건 물어볼 수 있어요</span>
                {suggestedOnError.current.map((q) => (
                  <button key={q} onClick={() => onSubmit?.(q)} style={{ padding: '6px 10px', borderRadius: 8, border: '1px solid rgba(6,182,212,0.25)', background: 'rgba(6,182,212,0.04)', cursor: 'pointer', fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#0e7490', textAlign: 'left' }}>
                    {q}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* 완료 인사이트 */}
          {isDone && !errorMessage && (
            <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#475569', margin: 0, lineHeight: 1.6 }}>
              {insightText}
            </p>
          )}
        </div>

        {/* 드래그 위젯 — MODIFY(선택 위젯 수정)가 아닌 CREATE/ADD일 때만 표시 */}
        {isDone && !errorMessage && widgetData !== null && (
          <div
            onMouseDown={(e) => { e.stopPropagation(); onDragStart(e); }}
            style={{ margin: '0 12px 10px', background: 'rgba(255,255,255,0.85)', border: '1.5px solid rgba(6,182,212,0.4)', borderRadius: 10, overflow: 'hidden', cursor: 'grab', userSelect: 'none' }}
            title="캔버스로 드래그하세요"
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '9px 14px 8px', borderBottom: '1px solid rgba(241,245,249,0.8)' }}>
              {editTitle ? (
                <input autoFocus value={titleVal} onChange={(e) => setTitleVal(e.target.value)}
                  onBlur={() => { setEditTitle(false); onTitleChange(titleVal); }}
                  onKeyDown={(e) => { if (e.key === 'Enter') { setEditTitle(false); onTitleChange(titleVal); } }}
                  onMouseDown={(e) => e.stopPropagation()}
                  style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 13, border: 'none', outline: '1px solid #06b6d4', borderRadius: 3, padding: '0 4px', width: '80%' }} />
              ) : (
                <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 13, color: '#171d1e', cursor: 'text' }}
                  onDoubleClick={(e) => { e.stopPropagation(); setEditTitle(true); }}>
                  {titleVal}
                </span>
              )}
              <span style={{ fontSize: 11, color: '#06b6d4', fontFamily: 'Pretendard,sans-serif', fontWeight: 500 }}>드래그 ↗</span>
            </div>
            <div style={{ padding: '6px 12px 8px', height: 140 }}>
              <WidgetRenderer widgetType={widgetType} config={widgetConfig} data={widgetData} result={result ?? null} />
            </div>
          </div>
        )}

      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
