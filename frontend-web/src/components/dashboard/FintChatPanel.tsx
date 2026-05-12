'use client';

import React, { useEffect, useState } from 'react';
import type { Step, WidgetResult } from '@/types/dashboard';
import { BarChartSvg, LineChartSvg, SegmentChart, KpiCard, TableWidget } from './ChartWidgets';

interface Props {
  steps: Step[];
  query: string;
  isLoading: boolean;
  isDone: boolean;
  widgetTitle: string;
  widgetType: string;
  result?: WidgetResult | null;
  onTitleChange: (v: string) => void;
  onCollapse: () => void;
  onDragStart: (e: React.MouseEvent) => void;
}

const FALLBACK_INSIGHT = '최근 활동 데이터와 DART 공시를 결합하여 분석한 결과입니다.';

export default function FintChatPanel({
  steps,
  query,
  isLoading,
  isDone,
  widgetTitle,
  widgetType,
  result,
  onTitleChange,
  onCollapse,
  onDragStart,
}: Props) {
  const data = (result?.data as Record<string, unknown> | undefined) ?? {};
  const labels = Array.isArray(data.labels) ? (data.labels as string[]) : undefined;
  const values = Array.isArray(data.values) ? (data.values as number[]) : undefined;
  const insightText = result?.insightText && result.insightText.trim().length > 0
    ? result.insightText
    : FALLBACK_INSIGHT;
  const [editTitle, setEditTitle] = useState(false);
  const [titleVal, setTitleVal] = useState(widgetTitle);
  useEffect(() => {
    setTitleVal(widgetTitle);
  }, [widgetTitle]);

  return (
    <div
      style={{
        background: 'rgba(248,250,255,0.72)',
        backdropFilter: 'blur(16px) saturate(180%) brightness(1.05)',
        WebkitBackdropFilter: 'blur(16px) saturate(180%) brightness(1.05)',
        border: '1px solid rgba(255,255,255,0.9)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.06), inset 0 1px 0 rgba(255,255,255,1)',
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

        {/* 완료: 미니 위젯 + 드래그 */}
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
              {insightText}
            </p>
            <div
              onMouseDown={onDragStart}
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
              <div style={{ padding: '6px 12px 8px' }}>
                {widgetType === 'LINE_CHART' ? (
                  <LineChartSvg size="mini" values={values} labels={labels} />
                ) : widgetType === 'PIE' || widgetType === 'SEGMENT' ? (
                  <SegmentChart labels={labels} values={values} />
                ) : widgetType === 'KPI' ? (
                  <KpiCard value={values?.[0]} label={labels?.[0]} />
                ) : widgetType === 'TABLE' ? (
                  <TableWidget data={data} />
                ) : (
                  <BarChartSvg size="mini" values={values} labels={labels} />
                )}
              </div>
            </div>
          </>
        )}
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
