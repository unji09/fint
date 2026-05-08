import PipelineProgress from './PipelineProgress';

const MEETINGS = [
  {
    date: '2024.05.20',
    title: '제안서 제출 및 설명회',
    summary: '최종 제안서 리뷰 및 질의응답 진행.',
  },
  {
    date: '2024.05.15',
    title: '솔루션 아키텍처 논의',
    summary: '시스템 연동 방안 및 보안 요구사항 확인.',
  },
  {
    date: '2024.05.02',
    title: '요구사항 청취 미팅',
    summary: '기존 시스템 페인포인트 파악 및 개선 방향 도출.',
  },
];

export default function DealDetailPanel() {
  return (
    <div
      style={{
        background: 'white',
        border: '1px solid #e2eaf0',
        borderRadius: 12,
        boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
        padding: 24,
        display: 'flex',
        gap: 24,
      }}
    >
      {/* 왼쪽: 딜 기본 정보 */}
      <div style={{ width: 240, flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div>
          <span
            style={{
              background: '#eff6ff',
              border: '1px solid #dbeafe',
              borderRadius: 100,
              padding: '4px 10px',
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 11,
              color: '#2563eb',
            }}
          >
            현재 파이프라인 단계
          </span>
          <h3
            style={{
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 22,
              color: '#1e293b',
              margin: '10px 0 4px',
            }}
          >
            AI 솔루션 연장 계약
          </h3>
          <p style={{ fontFamily: 'Inter,sans-serif', fontSize: 13, color: '#475569', margin: 0 }}>
            예상일: 2024-06-30
          </p>
        </div>
        <span
          style={{
            background: '#f5f7fa',
            border: '1px solid #e2eaf0',
            borderRadius: 100,
            padding: '4px 12px',
            fontFamily: 'Inter,sans-serif',
            fontWeight: 600,
            fontSize: 12,
            color: '#1e293b',
            display: 'inline-block',
            width: 'fit-content',
          }}
        >
          성공 확률 : 80%
        </span>
        <div>
          <p
            style={{
              fontFamily: 'Inter,sans-serif',
              fontWeight: 600,
              fontSize: 11,
              color: '#94a3b8',
              letterSpacing: '0.5px',
              textTransform: 'uppercase',
              margin: '8px 0 6px',
            }}
          >
            KEY CONTACTS
          </p>
          {[
            { name: '김지영 상무 / CTO', color: '#7c3aed' },
            { name: '이태안 팀장 / IT 구매', color: '#0891b2' },
          ].map((c, i) => (
            <div
              key={i}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                background: '#f5f7fa',
                border: '1px solid #e2eaf0',
                borderRadius: 100,
                padding: '6px 12px',
                marginBottom: 6,
              }}
            >
              <div
                style={{
                  width: 20,
                  height: 20,
                  borderRadius: '50%',
                  background: c.color,
                  flexShrink: 0,
                }}
              />
              <span
                style={{
                  fontFamily: 'Pretendard,sans-serif',
                  fontWeight: 500,
                  fontSize: 13,
                  color: '#475569',
                }}
              >
                {c.name}
              </span>
            </div>
          ))}
        </div>
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 0', fontSize: 13 }}
        >
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, color: '#94a3b8' }}>
            수주 일시
          </span>
          <span
            style={{ fontFamily: 'Pretendard,sans-serif', color: '#475569', textAlign: 'right' }}
          >
            -
          </span>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, color: '#94a3b8' }}>
            미팅 총 횟수
          </span>
          <span
            style={{ fontFamily: 'Pretendard,sans-serif', color: '#475569', textAlign: 'right' }}
          >
            8회
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
          <button
            style={{
              flex: 1,
              background: '#22c55e',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              padding: '8px 12px',
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 13,
              cursor: 'pointer',
            }}
          >
            딜완료
          </button>
          <button
            style={{
              flex: 1,
              background: '#ef4444',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              padding: '8px 12px',
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 500,
              fontSize: 13,
              cursor: 'pointer',
            }}
          >
            딜실패
          </button>
        </div>
      </div>

      {/* 오른쪽: 금액 + 파이프라인 + 미팅 */}
      <div
        style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16, padding: '0 16px' }}
      >
        <div>
          <p
            style={{
              fontFamily: 'Pretendard,sans-serif',
              fontWeight: 600,
              fontSize: 11,
              color: '#94a3b8',
              textTransform: 'uppercase',
              letterSpacing: '0.5px',
              margin: '0 0 4px',
            }}
          >
            예상 금액
          </p>
          <p
            style={{
              fontFamily: 'Inter,sans-serif',
              fontWeight: 700,
              fontSize: 26,
              color: '#0e7490',
              margin: 0,
            }}
          >
            ₩850,000,000
          </p>
        </div>
        <PipelineProgress current={3} />
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 18, color: '#1e293b' }}>
              미팅 내역
            </span>
            <span
              style={{
                fontFamily: 'Pretendard,sans-serif',
                fontSize: 13,
                color: '#0e7490',
                cursor: 'pointer',
              }}
            >
              전체 보기 →
            </span>
          </div>
          <div style={{ border: '1px solid #e2eaf0', borderRadius: 8, overflow: 'hidden' }}>
            <table
              style={{
                width: '100%',
                borderCollapse: 'collapse',
                fontFamily: 'Pretendard,sans-serif',
                fontSize: 13,
              }}
            >
              <thead>
                <tr style={{ background: '#f9fafb', borderBottom: '1px solid #e2eaf0' }}>
                  {['날짜', '제목', '요약'].map((h) => (
                    <th
                      key={h}
                      style={{
                        padding: '10px 14px',
                        textAlign: 'left',
                        fontFamily: 'Inter,sans-serif',
                        fontWeight: 600,
                        fontSize: 11,
                        color: '#475569',
                        letterSpacing: '0.5px',
                      }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {MEETINGS.map((r, i) => (
                  <tr key={i} style={{ borderTop: i > 0 ? '1px solid #e2eaf0' : 'none' }}>
                    <td style={{ padding: '12px 14px', color: '#64748b', whiteSpace: 'nowrap' }}>
                      {r.date}
                    </td>
                    <td style={{ padding: '12px 14px', color: '#1e293b', whiteSpace: 'nowrap' }}>
                      {r.title}
                    </td>
                    <td style={{ padding: '12px 14px', color: '#475569' }}>{r.summary}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* 딜 수정 */}
      <div style={{ alignSelf: 'flex-start' }}>
        <button
          style={{
            background: 'white',
            border: '1px solid #e2eaf0',
            borderRadius: 6,
            padding: '6px 16px',
            fontFamily: 'Pretendard,sans-serif',
            fontWeight: 500,
            fontSize: 15,
            color: '#1e293b',
            cursor: 'pointer',
            boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
          }}
        >
          딜 수정
        </button>
      </div>
    </div>
  );
}
