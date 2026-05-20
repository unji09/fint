'use client';

import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import SignalItem from './SignalItem';
import type { Signal } from '@/types/customer';

const F = 'Pretendard,sans-serif';

interface AllSignalsModalProps {
  open: boolean;
  onClose: () => void;
  signals: Signal[];
  accountName?: string;
}

const ACCENT_PALETTE = ['#06b6d4', '#0e7490', '#22c55e', '#f59e0b', '#a855f7', '#cbd5e1'];

type Filter = 'ALL' | 'NEWS' | 'DART';

export default function AllSignalsModal({ open, onClose, signals, accountName }: AllSignalsModalProps) {
  const [filter, setFilter] = useState<Filter>('ALL');

  // 모달을 새로 열 때 필터 초기화
  useEffect(() => {
    if (open) setFilter('ALL');
  }, [open]);

  // ESC 로 닫기
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const counts = useMemo(() => ({
    ALL: signals.length,
    NEWS: signals.filter((s) => s.type === 'NEWS').length,
    DART: signals.filter((s) => s.type === 'DART').length,
  }), [signals]);

  // 시간순(occurredAt DESC) 그대로 — BE 응답 순서 유지. 필터만 적용.
  const displaySignals = useMemo(() => {
    if (filter === 'ALL') return signals;
    return signals.filter((s) => s.type === filter);
  }, [signals, filter]);

  if (!open || typeof document === 'undefined') return null;

  // GNB(z-index 50, sticky)가 부모 stacking context 안에 있어서 모달 zIndex 만으로는 못 덮는다.
  // document.body 로 portal 해서 viewport 최상위에 마운트한다.
  return createPortal(
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.45)',
        zIndex: 1200,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: '#fff',
          borderRadius: 12,
          width: '100%',
          maxWidth: 640,
          // 항목 수에 따라 모달이 들썩이지 않도록 고정 높이 (약 10개 들어갈 분량).
          // 항목이 많으면 본문 영역이 스크롤되고, 적어도 모달 자체 크기는 유지된다.
          height: 'min(640px, 85vh)',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 20px 50px rgba(0,0,0,0.25)',
          overflow: 'hidden',
        }}
      >
        {/* 헤더 */}
        <div
          style={{
            padding: '18px 24px 12px',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ fontFamily: F, fontWeight: 600, fontSize: 16, color: '#0d1c2e' }}>
                {accountName ? `${accountName} 회사 정보` : '회사 정보'}
              </span>
              <span style={{ fontFamily: F, fontSize: 11, color: '#94a3b8' }}>
                최근 시그널 {signals.length}건 · 최신순
              </span>
            </div>
            <button
              onClick={onClose}
              aria-label="닫기"
              style={{
                background: 'none',
                border: 'none',
                fontSize: 20,
                color: '#64748b',
                cursor: 'pointer',
                padding: 4,
                lineHeight: 1,
              }}
            >
              ×
            </button>
          </div>

          {/* 필터 탭 */}
          <div style={{ display: 'flex', gap: 6 }}>
            {(['ALL', 'NEWS', 'DART'] as Filter[]).map((f) => {
              const active = filter === f;
              const label = f === 'ALL' ? '전체' : f;
              return (
                <button
                  key={f}
                  onClick={() => setFilter(f)}
                  style={{
                    padding: '5px 12px',
                    borderRadius: 999,
                    border: active ? '1px solid #06b6d4' : '1px solid #e2e8f0',
                    backgroundColor: active ? '#ecfeff' : '#fff',
                    color: active ? '#0e7490' : '#64748b',
                    fontFamily: F,
                    fontSize: 12,
                    fontWeight: active ? 600 : 500,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                >
                  <span>{label}</span>
                  <span style={{ fontSize: 10, opacity: 0.7 }}>{counts[f]}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* 본문 — 스크롤 영역 */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '12px 24px 20px',
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
          }}
        >
          {displaySignals.length === 0 ? (
            <div
              style={{
                padding: 32,
                textAlign: 'center',
                color: '#94a3b8',
                fontSize: 13,
                fontFamily: F,
              }}
            >
              {filter === 'ALL' ? '시그널이 없습니다.' : `${filter} 시그널이 없습니다.`}
            </div>
          ) : (
            displaySignals.map((s, i) => (
              <SignalItem key={i} signal={s} accent={ACCENT_PALETTE[i % ACCENT_PALETTE.length]} />
            ))
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
