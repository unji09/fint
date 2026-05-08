'use client';
// src/components/calendar/EventItem.tsx

import type { CalendarEvent } from './types';
import { CATEGORY_COLOR, CATEGORY_BG, SOURCE_STYLE } from './types';
import { formatTime } from './utils';

interface Props {
  event: CalendarEvent;
  onClick?: () => void;
  /** compact: 월간 달력 셀 내 작은 칩 */
  compact?: boolean;
}

export default function EventItem({ event, onClick, compact = false }: Props) {
  const catColor = event.category ? CATEGORY_COLOR[event.category] : '#0686D4';
  const catBg = event.category ? CATEGORY_BG[event.category] : '#EBF5FF';
  const src = SOURCE_STYLE[event.source];

  /* ── compact: 월간 그리드 내 이벤트 칩 ── */
  if (compact) {
    return (
      <button
        onClick={(e) => {
          e.stopPropagation();
          onClick?.();
        }}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          padding: '2px 5px',
          borderRadius: 4,
          border: 'none',
          cursor: 'pointer',
          backgroundColor: catBg,
          textAlign: 'left',
          minWidth: 0,
          transition: 'opacity .15s',
        }}
        onMouseEnter={(e) => ((e.currentTarget as HTMLButtonElement).style.opacity = '0.75')}
        onMouseLeave={(e) => ((e.currentTarget as HTMLButtonElement).style.opacity = '1')}
      >
        <span
          style={{
            width: 6,
            height: 6,
            borderRadius: '50%',
            backgroundColor: catColor,
            flexShrink: 0,
          }}
        />
        <span
          style={{
            fontSize: 11,
            color: catColor,
            fontWeight: 500,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            flex: 1,
          }}
        >
          {event.title}
        </span>
      </button>
    );
  }

  /* ── full: 일정 목록 / 사이드 패널 카드 ── */
  return (
    <button
      onClick={onClick}
      style={{
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        padding: '10px 12px',
        borderRadius: 10,
        border: '1px solid var(--color-border)',
        borderLeft: `3px solid ${catColor}`,
        cursor: 'pointer',
        backgroundColor: 'var(--color-bg-card)',
        textAlign: 'left',
        transition: 'box-shadow .15s, border-color .15s',
      }}
      onMouseEnter={(e) =>
        ((e.currentTarget as HTMLButtonElement).style.boxShadow = '0 2px 8px rgba(0,0,0,.08)')
      }
      onMouseLeave={(e) =>
        ((e.currentTarget as HTMLButtonElement).style.boxShadow = 'none')
      }
    >
      {/* 상단: source 뱃지 + category 칩 + 시간 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexWrap: 'wrap' }}>
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 3,
            fontSize: 10,
            fontWeight: 500,
            color: src.dot,
            padding: '1px 6px',
            borderRadius: 4,
            backgroundColor: src.bg,
            flexShrink: 0,
          }}
        >
          <span
            style={{ width: 5, height: 5, borderRadius: '50%', backgroundColor: src.dot }}
          />
          {src.label}
        </span>

        {event.category && (
          <span
            style={{
              fontSize: 10,
              color: catColor,
              backgroundColor: catBg,
              padding: '1px 6px',
              borderRadius: 4,
              fontWeight: 500,
            }}
          >
            {event.category}
          </span>
        )}

        <span
          style={{
            fontSize: 11,
            color: 'var(--color-text-muted)',
            marginLeft: 'auto',
            whiteSpace: 'nowrap',
          }}
        >
          {formatTime(event.startAt)} – {formatTime(event.endAt)}
        </span>
      </div>

      {/* 제목 */}
      <div
        style={{
          fontSize: 13,
          fontWeight: 600,
          color: 'var(--color-text-primary)',
          lineHeight: 1.35,
        }}
      >
        {event.title}
      </div>

      {/* 고객사 + 파이프라인 스테이지 */}
      {event.accountName && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ fontSize: 11, color: 'var(--color-text-secondary)' }}>
            {event.accountName}
          </span>
          {event.pipelineStage && (
            <span
              style={{
                fontSize: 10,
                color: '#0686D4',
                backgroundColor: '#EBF5FF',
                padding: '1px 6px',
                borderRadius: 4,
                fontWeight: 500,
              }}
            >
              {event.pipelineStage.stageName}
            </span>
          )}
        </div>
      )}
    </button>
  );
}
