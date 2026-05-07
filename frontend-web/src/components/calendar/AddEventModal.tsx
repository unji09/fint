'use client';
// src/components/calendar/AddEventModal.tsx

import { useState, useEffect } from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  defaultDate?: Date;
}

const TEAL = '#06B6D4';
const WD = ['일', '월', '화', '수', '목', '금', '토'];
const pad = (n: number) => String(n).padStart(2, '0');

const CATS = [
  { id: '전화', color: '#378ADD', bg: '#EBF2FF' },
  { id: '미팅', color: '#7F77DD', bg: '#ECEBFA' },
  { id: '업무', color: '#1D9E75', bg: '#DDF0EA' },
  { id: '이메일', color: '#D85A30', bg: '#FEF0EC' },
];

const PIPELINE = [
  '첫 미팅 준비',
  '니즈 파악',
  '제안서 작성',
  '제안 발표',
  '협상 중',
  '계약 검토',
  '성사 / 실패',
];

function toDateVal(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function toTimeVal(d: Date) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function fmtDate(s: string) {
  if (!s) return '';
  const d = new Date(s + 'T00:00');
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WD[d.getDay()]})`;
}
function fmtTime(s: string) {
  if (!s) return '';
  const [hh, mm] = s.split(':');
  const h = parseInt(hh);
  return `${h < 12 ? '오전' : '오후'} ${h === 0 ? 12 : h > 12 ? h - 12 : h}:${mm}`;
}

// SVG 아이콘 (이모지 대체)
const IconCal = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
    <rect x="1.5" y="2.5" width="11" height="10" rx="1.5" stroke="#9CA193" strokeWidth="1.2" />
    <path
      d="M4.5 1v3M9.5 1v3M1.5 5.5h11"
      stroke="#9CA193"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
  </svg>
);
const IconClock = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
    <circle cx="7" cy="7" r="5.5" stroke="#9CA193" strokeWidth="1.2" />
    <path
      d="M7 4v3.2l2 1.2"
      stroke="#9CA193"
      strokeWidth="1.2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);
const IconSearch = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0 }}>
    <circle cx="6" cy="6" r="4" stroke="#C0C0BB" strokeWidth="1.2" />
    <path d="M9.5 9.5L12 12" stroke="#C0C0BB" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
);

function DateTimeBlock({
  date,
  time,
  onDate,
  onTime,
}: {
  date: string;
  time: string;
  onDate: (v: string) => void;
  onTime: (v: string) => void;
}) {
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <label
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '9px 12px',
          borderRadius: 8,
          border: '1px solid #E5E6DE',
          backgroundColor: '#F8F8F5',
          cursor: 'pointer',
          position: 'relative',
          transition: 'border-color 0.15s',
        }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLElement).style.borderColor = '#C0C0BB';
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLElement).style.borderColor = '#E5E6DE';
        }}
      >
        <IconCal />
        <span style={{ fontSize: 13, fontWeight: 500, color: '#16180F', flex: 1 }}>
          {fmtDate(date)}
        </span>
        <input
          type="date"
          value={date}
          onChange={(e) => onDate(e.target.value)}
          style={{ position: 'absolute', opacity: 0, width: 1, height: 1, pointerEvents: 'none' }}
        />
      </label>
      <label
        style={{
          width: 116,
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          padding: '9px 12px',
          borderRadius: 8,
          border: '1px solid #E5E6DE',
          backgroundColor: '#F8F8F5',
          cursor: 'pointer',
          position: 'relative',
          transition: 'border-color 0.15s',
          whiteSpace: 'nowrap',
        }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLElement).style.borderColor = '#C0C0BB';
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLElement).style.borderColor = '#E5E6DE';
        }}
      >
        <IconClock />
        <span style={{ fontSize: 13, fontWeight: 500, color: '#16180F' }}>{fmtTime(time)}</span>
        <input
          type="time"
          value={time}
          onChange={(e) => onTime(e.target.value)}
          style={{ position: 'absolute', opacity: 0, width: 1, height: 1, pointerEvents: 'none' }}
        />
      </label>
    </div>
  );
}

function Label({ children }: { children: string }) {
  return (
    <div style={{ fontSize: 13, fontWeight: 600, color: '#16180F', marginBottom: 7 }}>
      {children}
    </div>
  );
}

function SearchBox({
  placeholder,
  value,
  onChange,
}: {
  placeholder: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '9px 12px',
        borderRadius: 8,
        border: '1px solid #E5E6DE',
        backgroundColor: '#F8F8F5',
      }}
    >
      <IconSearch />
      <input
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{
          flex: 1,
          border: 'none',
          background: 'transparent',
          fontSize: 13,
          color: '#16180F',
          outline: 'none',
          fontFamily: 'inherit',
        }}
      />
    </div>
  );
}

export default function AddEventModal({ open, onClose, defaultDate }: Props) {
  const base = defaultDate ?? new Date();
  const endBase = new Date(base.getTime() + 3600_000);

  const [title, setTitle] = useState('');
  const [startD, setStartD] = useState(toDateVal(base));
  const [startT, setStartT] = useState(toTimeVal(base));
  const [endD, setEndD] = useState(toDateVal(endBase));
  const [endT, setEndT] = useState(toTimeVal(endBase));
  const [cat, setCat] = useState('미팅');
  const [pipe, setPipe] = useState('');
  const [deal, setDeal] = useState('');
  const [contact, setContact] = useState('');
  const [memo, setMemo] = useState('');
  const [showDeal, setShowDeal] = useState(false);
  const [showCon, setShowCon] = useState(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (open) {
      document.body.style.overflow = 'hidden';
      requestAnimationFrame(() => setVisible(true));
    } else {
      setVisible(false);
      setTimeout(() => {
        document.body.style.overflow = '';
      }, 200);
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [open]);

  if (!open && !visible) return null;

  const close = () => {
    setVisible(false);
    setTimeout(onClose, 200);
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        fontFamily: "'Pretendard',-apple-system,sans-serif",
        WebkitFontSmoothing: 'antialiased',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {/* 배경 */}
      <div
        onClick={close}
        style={{
          position: 'absolute',
          inset: 0,
          backgroundColor: visible ? 'rgba(0,0,0,0.35)' : 'rgba(0,0,0,0)',
          transition: 'background-color 0.2s',
        }}
      />

      {/* 모달 */}
      <div
        style={{
          position: 'relative',
          width: 480,
          maxHeight: '88vh',
          backgroundColor: '#fff',
          borderRadius: 14,
          boxShadow: '0 8px 40px rgba(0,0,0,0.18)',
          display: 'flex',
          flexDirection: 'column',
          opacity: visible ? 1 : 0,
          transform: visible ? 'translateY(0) scale(1)' : 'translateY(10px) scale(0.98)',
          transition: 'opacity 0.2s, transform 0.2s',
        }}
      >
        {/* 헤더 */}
        <div
          style={{
            padding: '18px 22px 14px',
            borderBottom: '1px solid #F0F0EE',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexShrink: 0,
          }}
        >
          <span style={{ fontSize: 16, fontWeight: 700, color: '#16180F' }}>일정 추가</span>
          <button
            onClick={close}
            style={{
              width: 26,
              height: 26,
              border: 'none',
              backgroundColor: 'transparent',
              borderRadius: 6,
              cursor: 'pointer',
              color: '#9CA193',
              fontSize: 18,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            ×
          </button>
        </div>

        {/* 폼 */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '18px 22px',
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
        >
          {/* 일정 제목 */}
          <div>
            <Label>일정 제목</Label>
            <input
              placeholder="일정 제목을 입력하세요"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              autoFocus
              style={{
                width: '100%',
                boxSizing: 'border-box',
                padding: '9px 12px',
                borderRadius: 8,
                border: '1px solid #E5E6DE',
                backgroundColor: '#F8F8F5',
                fontSize: 14,
                color: '#16180F',
                outline: 'none',
                fontFamily: 'inherit',
                transition: 'border-color 0.15s',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = TEAL;
                e.target.style.backgroundColor = '#fff';
              }}
              onBlur={(e) => {
                e.target.style.borderColor = '#E5E6DE';
                e.target.style.backgroundColor = '#F8F8F5';
              }}
            />
          </div>

          {/* 날짜 및 시간 */}
          <div>
            <Label>날짜 및 시간</Label>
            <DateTimeBlock date={startD} time={startT} onDate={setStartD} onTime={setStartT} />
          </div>

          {/* 마감일 */}
          <div>
            <Label>마감일</Label>
            <DateTimeBlock date={endD} time={endT} onDate={setEndD} onTime={setEndT} />
          </div>

          {/* 영업 목표 */}
          <div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 7,
              }}
            >
              <Label>영업 목표</Label>
              <button
                onClick={() => setShowDeal((v) => !v)}
                style={{
                  border: 'none',
                  background: 'none',
                  cursor: 'pointer',
                  color: TEAL,
                  fontSize: 20,
                  lineHeight: 1,
                  padding: '0 2px',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                {showDeal ? '−' : '+'}
              </button>
            </div>
            {showDeal ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <input
                  placeholder="딜 제목"
                  style={{
                    padding: '9px 12px',
                    borderRadius: 8,
                    border: '1px solid #E5E6DE',
                    backgroundColor: '#F8F8F5',
                    fontSize: 13,
                    color: '#16180F',
                    outline: 'none',
                    fontFamily: 'inherit',
                    width: '100%',
                    boxSizing: 'border-box',
                  }}
                  onFocus={(e) => {
                    e.target.style.borderColor = TEAL;
                  }}
                  onBlur={(e) => {
                    e.target.style.borderColor = '#E5E6DE';
                  }}
                />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <input
                    placeholder="예상 금액 (₩)"
                    style={{
                      padding: '9px 12px',
                      borderRadius: 8,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#F8F8F5',
                      fontSize: 13,
                      color: '#16180F',
                      outline: 'none',
                      fontFamily: 'inherit',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = TEAL;
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#E5E6DE';
                    }}
                  />
                  <input
                    type="date"
                    style={{
                      padding: '9px 12px',
                      borderRadius: 8,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#F8F8F5',
                      fontSize: 13,
                      color: '#16180F',
                      outline: 'none',
                      fontFamily: 'inherit',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = TEAL;
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#E5E6DE';
                    }}
                  />
                </div>
              </div>
            ) : (
              <SearchBox placeholder="영업 목표 검색..." value={deal} onChange={setDeal} />
            )}
          </div>

          {/* 고객사 담당자 */}
          <div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 7,
              }}
            >
              <Label>고객사 담당자</Label>
              <button
                onClick={() => setShowCon((v) => !v)}
                style={{
                  border: 'none',
                  background: 'none',
                  cursor: 'pointer',
                  color: TEAL,
                  fontSize: 20,
                  lineHeight: 1,
                  padding: '0 2px',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                {showCon ? '−' : '+'}
              </button>
            </div>
            {showCon ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <input
                    placeholder="담당자명"
                    style={{
                      padding: '9px 12px',
                      borderRadius: 8,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#F8F8F5',
                      fontSize: 13,
                      color: '#16180F',
                      outline: 'none',
                      fontFamily: 'inherit',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = TEAL;
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#E5E6DE';
                    }}
                  />
                  <input
                    placeholder="직책"
                    style={{
                      padding: '9px 12px',
                      borderRadius: 8,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#F8F8F5',
                      fontSize: 13,
                      color: '#16180F',
                      outline: 'none',
                      fontFamily: 'inherit',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = TEAL;
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#E5E6DE';
                    }}
                  />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <input
                    placeholder="전화번호"
                    style={{
                      padding: '9px 12px',
                      borderRadius: 8,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#F8F8F5',
                      fontSize: 13,
                      color: '#16180F',
                      outline: 'none',
                      fontFamily: 'inherit',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = TEAL;
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#E5E6DE';
                    }}
                  />
                  <input
                    placeholder="이메일"
                    style={{
                      padding: '9px 12px',
                      borderRadius: 8,
                      border: '1px solid #E5E6DE',
                      backgroundColor: '#F8F8F5',
                      fontSize: 13,
                      color: '#16180F',
                      outline: 'none',
                      fontFamily: 'inherit',
                    }}
                    onFocus={(e) => {
                      e.target.style.borderColor = TEAL;
                    }}
                    onBlur={(e) => {
                      e.target.style.borderColor = '#E5E6DE';
                    }}
                  />
                </div>
              </div>
            ) : (
              <SearchBox placeholder="고객 담당자 검색..." value={contact} onChange={setContact} />
            )}
          </div>

          {/* 카테고리 */}
          <div>
            <Label>카테고리</Label>
            <div style={{ display: 'flex', gap: 6 }}>
              {CATS.map((c) => {
                const sel = cat === c.id;
                return (
                  <button
                    key={c.id}
                    onClick={() => setCat(c.id)}
                    style={{
                      padding: '6px 14px',
                      borderRadius: 20,
                      cursor: 'pointer',
                      border: sel ? `1.5px solid ${c.color}` : '1.5px solid #E5E6DE',
                      backgroundColor: sel ? c.bg : '#fff',
                      fontSize: 13,
                      fontWeight: sel ? 600 : 400,
                      color: sel ? c.color : '#737880',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 5,
                      transition: 'all 0.15s',
                    }}
                  >
                    <span
                      style={{
                        width: 6,
                        height: 6,
                        borderRadius: '50%',
                        backgroundColor: c.color,
                        flexShrink: 0,
                      }}
                    />
                    {c.id}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 파이프라인 단계 */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 7 }}>
              <Label>파이프라인 단계</Label>
              <span style={{ fontSize: 11, color: '#9CA193', marginTop: -1 }}>선택사항</span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {PIPELINE.map((p) => {
                const sel = pipe === p;
                return (
                  <button
                    key={p}
                    onClick={() => setPipe(sel ? '' : p)}
                    style={{
                      padding: '5px 12px',
                      borderRadius: 8,
                      cursor: 'pointer',
                      border: sel ? `1.5px solid ${TEAL}` : '1.5px solid #E5E6DE',
                      backgroundColor: sel ? '#ECFEFF' : '#fff',
                      fontSize: 12,
                      fontWeight: sel ? 600 : 400,
                      color: sel ? TEAL : '#737880',
                      transition: 'all 0.15s',
                    }}
                  >
                    {p}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 메모 */}
          <div>
            <Label>메모</Label>
            <textarea
              placeholder="메모를 입력하세요..."
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              rows={3}
              style={{
                width: '100%',
                boxSizing: 'border-box',
                padding: '9px 12px',
                borderRadius: 8,
                border: '1px solid #E5E6DE',
                backgroundColor: '#F8F8F5',
                fontSize: 13,
                color: '#16180F',
                resize: 'vertical',
                outline: 'none',
                fontFamily: 'inherit',
                lineHeight: 1.6,
                transition: 'border-color 0.15s',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = TEAL;
                e.target.style.backgroundColor = '#fff';
              }}
              onBlur={(e) => {
                e.target.style.borderColor = '#E5E6DE';
                e.target.style.backgroundColor = '#F8F8F5';
              }}
            />
          </div>
        </div>

        {/* 푸터 */}
        <div
          style={{
            padding: '12px 22px 16px',
            borderTop: '1px solid #F0F0EE',
            display: 'flex',
            gap: 8,
            justifyContent: 'flex-end',
            flexShrink: 0,
          }}
        >
          <button
            onClick={close}
            style={{
              padding: '8px 18px',
              borderRadius: 8,
              cursor: 'pointer',
              border: '1px solid #E5E6DE',
              backgroundColor: '#fff',
              fontSize: 13,
              fontWeight: 500,
              color: '#737880',
              fontFamily: 'inherit',
            }}
          >
            취소
          </button>
          <button
            onClick={() => {
              close();
            }}
            style={{
              padding: '8px 22px',
              borderRadius: 8,
              border: 'none',
              backgroundColor: title.trim() ? TEAL : '#B8DEE8',
              fontSize: 13,
              fontWeight: 600,
              color: '#fff',
              cursor: title.trim() ? 'pointer' : 'default',
              fontFamily: 'inherit',
              transition: 'background-color 0.15s',
            }}
          >
            저장
          </button>
        </div>
      </div>
    </div>
  );
}
