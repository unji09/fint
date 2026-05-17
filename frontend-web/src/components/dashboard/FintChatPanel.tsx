'use client';

import React, { useEffect, useRef, useState } from 'react';
import type { Step, WidgetResult, ChatMessage } from '@/types/dashboard';
import WidgetRenderer from './WidgetRenderer';

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
  onTitleChange: (v: string) => void;
  onCollapse: () => void;
  onDragStart: (e: React.MouseEvent) => void;
  chatHistory?: ChatMessage[];
}

const FALLBACK_INSIGHT = '최근 활동 데이터와 DART 공시를 결합하여 분석한 결과입니다.';

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
  onTitleChange,
  onCollapse,
  onDragStart,
  chatHistory = [],
}: Props) {
  const insightText = result?.insightText && result.insightText.trim().length > 0
    ? result.insightText
    : FALLBACK_INSIGHT;
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(widgetTitle);
  useEffect(() => {
    setTitleVal(widgetTitle);
  }, [widgetTitle]);

  /* 자동 스크롤 */
  const scrollEndRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    scrollEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatHistory.length, isLoading, isDone]);

  return (
    <div
      style={{
        background:
          'linear-gradient(180deg, rgba(255,255,255,0.85) 0%, rgba(244,249,255,0.78) 100%)',
        backdropFilter: 'blur(20px) saturate(180%) brightness(1.06)',
        WebkitBackdropFilter: 'blur(20px) saturate(180%) brightness(1.06)',
        border: '1px solid rgba(255,255,255,0.95)',
        boxShadow:
          '0 1px 0 rgba(255,255,255,0.9) inset, ' +
          '0 -1px 0 rgba(6,182,212,0.06) inset, ' +
          '0 2px 6px rgba(15,23,42,0.04), ' +
          '0 12px 32px rgba(15,23,42,0.10), ' +
          '0 24px 48px -12px rgba(6,182,212,0.12)',
        borderRadius: 20,
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
          padding: '12px 16px',
          background:
            'linear-gradient(180deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0) 100%)',
          borderBottom: '1px solid rgba(226,232,240,0.6)',
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

      <div
        style={{
          padding: '12px 16px',
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
          maxHeight: 420,
          overflowY: 'auto',
        }}
      >
        {/* 이전 대화 내역 — 현재 드래그 가능한 위젯은 아래 별도 블록에서 렌더링하므로 마지막 assistant 메시지 제외 */}
        {chatHistory.filter((msg, idx) => {
          // isDone 상태에서 마지막 assistant 메시지는 아래 드래그 가능 위젯 블록에서 표시
          if (!isDone || errorMessage) return true;
          if (msg.role !== 'assistant') return true;
          // 마지막 assistant 메시지인지 확인
          const lastAssistantIdx = chatHistory.reduce((acc, m, i) => m.role === 'assistant' ? i : acc, -1);
          return idx !== lastAssistantIdx;
        }).map((msg) => {
          if (msg.role === 'user') {
            return (
              <div key={msg.id} style={{ display: 'flex', justifyContent: 'flex-end' }}>
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
                  }}
                >
                  {msg.content}
                </div>
              </div>
            );
          }
          // assistant message
          if (msg.status === 'error') {
            return (
              <div
                key={msg.id}
                style={{
                  background: 'rgba(239,68,68,0.06)',
                  border: '1px solid rgba(239,68,68,0.2)',
                  borderRadius: 10,
                  padding: '12px 14px',
                }}
              >
                <p
                  style={{
                    fontFamily: 'Pretendard,sans-serif',
                    fontSize: 13,
                    color: '#dc2626',
                    margin: 0,
                    lineHeight: 1.6,
                  }}
                >
                  {msg.errorMessage ?? '쿼리 처리에 실패했습니다.'}
                </p>
              </div>
            );
          }
          // done assistant message with optional widget preview
          const msgInsight = msg.content && msg.content.trim().length > 0
            ? msg.content
            : FALLBACK_INSIGHT;
          const msgWidgetType = msg.widget?.widgetType ?? 'BAR_CHART';
          const msgWidgetData = Array.isArray(msg.widget?.data)
            ? (msg.widget.data as Record<string, unknown>[])
            : null;
          const msgWidgetResult = !msgWidgetData && msg.widget?.data
            ? { data: msg.widget.data, insightText: '' }
            : null;
          return (
            <div key={msg.id} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <p
                style={{
                  fontFamily: 'Pretendard,sans-serif',
                  fontSize: 13,
                  color: '#475569',
                  margin: 0,
                  lineHeight: 1.6,
                }}
              >
                {msgInsight}
              </p>
              {msg.widget && (
                <div
                  style={{
                    background: 'rgba(255,255,255,0.85)',
                    border: '1px solid rgba(226,232,240,0.5)',
                    borderRadius: 10,
                    overflow: 'hidden',
                    opacity: 0.85,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '7px 14px 6px',
                      borderBottom: '1px solid rgba(241,245,249,0.8)',
                    }}
                  >
                    <span
                      style={{
                        fontFamily: 'Pretendard,sans-serif',
                        fontWeight: 500,
                        fontSize: 12,
                        color: '#64748b',
                      }}
                    >
                      {msg.widget.title}
                    </span>
                  </div>
                  <div style={{ padding: '4px 10px 6px', height: 120 }}>
                    <WidgetRenderer
                      widgetType={msgWidgetType}
                      config={msg.widget.config ?? {}}
                      data={msgWidgetData}
                      result={msgWidgetResult}
                    />
                  </div>
                </div>
              )}
            </div>
          );
        })}

        {/* 현재 진행 중인 쿼리 — chatHistory에 이미 user 메시지가 있으므로 히스토리 미사용 시만 표시 */}
        {chatHistory.length === 0 && query && (
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
              }}
            >
              {query}
            </div>
          </div>
        )}

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

        {/* 에러: 메시지 표시 */}
        {isDone && errorMessage && (
          <div
            style={{
              background: 'rgba(239,68,68,0.06)',
              border: '1px solid rgba(239,68,68,0.2)',
              borderRadius: 10,
              padding: '12px 14px',
            }}
          >
            <p
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontSize: 13,
                color: '#dc2626',
                margin: 0,
                lineHeight: 1.6,
              }}
            >
              {errorMessage}
            </p>
          </div>
        )}

        {/* 완료: 미니 위젯 + 드래그 (현재 진행 중인 쿼리의 결과만 — 항상 드래그 가능) */}
        {isDone && !errorMessage && (
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
              {insightText}
            </p>
            <div
              onMouseDown={(e) => { e.stopPropagation(); onDragStart(e); }}
              style={{
                background: 'rgba(255,255,255,0.85)',
                border: '1.5px solid rgba(6,182,212,0.4)',
                borderRadius: 10,
                overflow: 'hidden',
                cursor: 'grab',
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
                  >
                    {titleVal}
                  </span>
                )}
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
              </div>
              <div style={{ padding: '6px 12px 8px', height: 140 }}>
                <WidgetRenderer
                  widgetType={widgetType}
                  config={widgetConfig}
                  data={null}
                  result={result ?? null}
                />
              </div>
            </div>
          </>
        )}

        {/* 자동 스크롤 앵커 */}
        <div ref={scrollEndRef} />
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
