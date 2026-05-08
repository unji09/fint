'use client';

import type { QueryStep } from '@/app/dashboard/[id]/page';

interface AiProgressPanelProps {
  steps: QueryStep[];
  userQuery: string;
}

export default function AiProgressPanel({ steps, userQuery }: AiProgressPanelProps) {
  return (
    /* 피그마: absolute 왼쪽 패널, 반투명 흰색, rounded-12, shadow */
    <div
      style={{
        position: 'absolute',
        top: 24,
        left: 24,
        width: 320,
        zIndex: 10,
      }}
    >
      {/* 진행 카드 */}
      <div
        style={{
          background: 'rgba(255,255,255,0.75)',
          backdropFilter: 'blur(12px)',
          borderRadius: 12,
          boxShadow: '0px 0px 4px 0px rgba(0,0,0,0.25)',
          padding: '20px 20px 16px',
          marginBottom: 12,
          /* 피그마: gradient+blur border */
          border: '1px solid rgba(6,182,212,0.15)',
        }}
      >
        {/* 유저 쿼리 말풍선 */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
          <div
            style={{
              background: '#06b6d4',
              color: 'white',
              borderRadius: '24px 24px 24px 4px',
              padding: '10px 16px',
              fontSize: 14,
              fontFamily: 'Pretendard, sans-serif',
              fontWeight: 600,
              maxWidth: 200,
              boxShadow: '0px 4px 12px rgba(6,182,212,0.3)',
            }}
          >
            {userQuery}
          </div>
        </div>

        {/* 체크리스트 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {steps.map((step, i) => (
            <div key={i}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0' }}>
                {/* 아이콘 */}
                {step.done ? (
                  /* 피그마: #81dbe0 체크 아이콘 */
                  <div
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: 8,
                      flexShrink: 0,
                      background: '#81dbe0',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                      <path
                        d="M2.5 7L5.5 10L11.5 4"
                        stroke="white"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </div>
                ) : step.active ? (
                  /* 스피너 */
                  <div
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: 8,
                      flexShrink: 0,
                      border: '2px solid #06b6d4',
                      borderTopColor: 'transparent',
                      animation: 'spin 0.8s linear infinite',
                    }}
                  />
                ) : (
                  /* 미완료 원 */
                  <div
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: 8,
                      flexShrink: 0,
                      border: '2px solid #e2e8f0',
                    }}
                  />
                )}
                {/* 레이블 */}
                <span
                  style={{
                    fontFamily: 'Pretendard, sans-serif',
                    fontWeight: 700,
                    fontSize: 11,
                    letterSpacing: '0.55px',
                    textTransform: 'uppercase' as const,
                    color: step.done || step.active ? '#64748b' : '#cbd5e1',
                  }}
                >
                  {step.label}
                </span>
              </div>

              {/* 구분선 */}
              {i < steps.length - 1 && (
                <div
                  style={{
                    width: 2,
                    height: 12,
                    background: step.done ? '#81dbe0' : '#e2e8f0',
                    marginLeft: 11,
                    borderRadius: 2,
                  }}
                />
              )}
            </div>
          ))}
        </div>
      </div>

      {/* 스피너 keyframe CSS */}
      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}
