'use client';

import { useEffect, useState } from 'react';
import PipelineProgress from './PipelineProgress';
import { fetchWithAuth } from '@/hooks/useAuth';
import { useConfirm, usePrompt } from '@/components/common/ConfirmDialog';
import type { Deal } from '@/types/customer';

// 백엔드 DealDetailResponse 와 1:1 매칭
interface DealDetail {
  dealId: number;
  title: string;
  amount: number | null;
  probability: number | null;
  expectedClose: string | null;
  wonAt: string | null;
  lostAt: string | null;
  lostReason: string | null;
  currentPipelineStage: string | null;
  meetingCount: number;
  contacts: ContactDetail[];
}

interface ContactDetail {
  contactId: number;
  name: string;
  title: string | null;
  email: string | null;
  phone: string | null;
  personality: string | null;
}

interface Activity {
  activityId: number;
  type: string;
  title: string;
  startAt: string;
  memo: string | null;
}

// DB pipeline_stages 와 1:1 매칭 (한글 7단계)
const PIPELINE_LABELS = ['발굴', '가치 제안', '솔루션 설계', '제안 제출', '협상', '계약 대기', '수주'];
const CONTACT_COLORS = ['#7c3aed', '#0891b2', '#059669', '#dc2626', '#d97706'];

function fmtAmount(n: number | null): string {
  if (!n) return '-';
  return `₩${n.toLocaleString('ko-KR')}`;
}

function fmtDate(s: string | null): string {
  if (!s) return '-';
  return new Date(s).toLocaleDateString('ko-KR');
}

function pipelineIndex(stage: string | null): number {
  if (!stage) return 0;
  const idx = PIPELINE_LABELS.indexOf(stage);
  return idx >= 0 ? idx : 0;
}

interface Props {
  deal: Deal;
  onDealChanged?: () => void;
}

export default function DealDetailPanel({ deal, onDealChanged }: Props) {
  const confirm = useConfirm();
  const prompt = usePrompt();
  const [detail, setDetail] = useState<DealDetail | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!deal.dealId) return;
    setLoading(true);

    Promise.allSettled([
      fetchWithAuth(`/deals/${deal.dealId}`).then((r) => r.json()),
      fetchWithAuth(`/activities?dealId=${deal.dealId}&size=5`).then((r) => r.json()),
    ]).then(([detailRes, actRes]) => {
      if (detailRes.status === 'fulfilled') setDetail(detailRes.value.data);
      if (actRes.status === 'fulfilled') {
        const ad = actRes.value.data;
        const list =
          (Array.isArray(ad?.data) ? ad.data : null) ??
          ad?.content ??
          (Array.isArray(ad) ? ad : []);
        setActivities(list);
      }
      setLoading(false);
    });
  }, [deal.dealId]);

  const d = detail;
  const stageIdx = pipelineIndex(d?.currentPipelineStage ?? null);

  if (loading) {
    return (
      <div style={{ background: 'white', border: '1px solid #e2eaf0', borderRadius: 12, padding: 40, textAlign: 'center', color: '#94a3b8', fontSize: 14 }}>
        딜 정보를 불러오는 중...
      </div>
    );
  }

  return (
    <div style={{ background: 'white', border: '1px solid #e2eaf0', borderRadius: 12, boxShadow: '0 2px 4px rgba(0,0,0,0.05)', padding: 24, display: 'flex', gap: 24 }}>
      {/* 왼쪽: 딜 기본 정보 */}
      <div style={{ width: 240, flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div>
          {d?.currentPipelineStage && (
            <span style={{ background: '#eff6ff', border: '1px solid #dbeafe', borderRadius: 100, padding: '4px 10px', fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 11, color: '#2563eb' }}>
              {d.currentPipelineStage}
            </span>
          )}
          <h3 style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 22, color: '#1e293b', margin: '10px 0 4px' }}>
            {deal.title}
          </h3>
          <p style={{ fontFamily: 'Inter,sans-serif', fontSize: 13, color: '#475569', margin: 0 }}>
            예상일: {fmtDate(d?.expectedClose ?? deal.expectedCloseDate ?? null)}
          </p>
        </div>

        {d?.probability != null && (
          <span style={{ background: '#f5f7fa', border: '1px solid #e2eaf0', borderRadius: 100, padding: '4px 12px', fontFamily: 'Inter,sans-serif', fontWeight: 600, fontSize: 12, color: '#1e293b', display: 'inline-block', width: 'fit-content' }}>
            성공 확률 : {d.probability}%
          </span>
        )}

        {(d?.contacts ?? []).length > 0 && (
          <div>
            <p style={{ fontFamily: 'Inter,sans-serif', fontWeight: 600, fontSize: 11, color: '#94a3b8', letterSpacing: '0.5px', textTransform: 'uppercase', margin: '8px 0 6px' }}>KEY CONTACTS</p>
            {d!.contacts.map((c, i) => (
              <div key={c.contactId} style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#f5f7fa', border: '1px solid #e2eaf0', borderRadius: 100, padding: '6px 12px', marginBottom: 6 }}>
                <div style={{ width: 20, height: 20, borderRadius: '50%', background: CONTACT_COLORS[i % CONTACT_COLORS.length], flexShrink: 0 }} />
                <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, fontSize: 13, color: '#475569' }}>
                  {c.name}{c.title ? ` / ${c.title}` : ''}
                </span>
              </div>
            ))}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 0', fontSize: 13 }}>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, color: '#94a3b8' }}>수주 일시</span>
          <span style={{ fontFamily: 'Pretendard,sans-serif', color: '#475569', textAlign: 'right' }}>{fmtDate(d?.wonAt ?? null)}</span>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 500, color: '#94a3b8' }}>미팅 횟수</span>
          <span style={{ fontFamily: 'Pretendard,sans-serif', color: '#475569', textAlign: 'right' }}>{activities.length}회</span>
        </div>

        {/* 딜 완료 / 실패 / 삭제 */}
        {!d?.wonAt && !d?.lostAt && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 8 }}>
            <div style={{ display: 'flex', gap: 6 }}>
              <button onClick={async () => {
                if (!await confirm('이 딜을 완료 처리할까요?')) return;
                // DealUpdateRequest: pipelineStageId(7=수주) + wonAt 동시 갱신
                try {
                  await fetchWithAuth(`/deals/${deal.dealId}`, {
                    method: 'PATCH',
                    body: JSON.stringify({ pipelineStageId: 7, wonAt: new Date().toISOString() }),
                  });
                  onDealChanged?.();
                } catch { /* */ }
              }} style={{ flex: 1, padding: '7px 0', borderRadius: 6, border: '1px solid #86efac', backgroundColor: '#f0fdf4', color: '#166534', fontFamily: 'Pretendard,sans-serif', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                딜 완료
              </button>
              <button onClick={async () => {
                const reason = await prompt({ message: '실주 사유를 입력하세요', placeholder: '사유 입력 (선택)', variant: 'danger', confirmText: '실패 처리' });
                if (reason === null) return;
                // lostAt 도 함께 갱신 (백엔드가 자동 갱신하지 않을 수 있어 명시)
                try {
                  await fetchWithAuth(`/deals/${deal.dealId}`, {
                    method: 'PATCH',
                    body: JSON.stringify({ lostReason: reason || '실패 처리', lostAt: new Date().toISOString() }),
                  });
                  onDealChanged?.();
                } catch { /* */ }
              }} style={{ flex: 1, padding: '7px 0', borderRadius: 6, border: '1px solid #fca5a5', backgroundColor: '#fef2f2', color: '#991b1b', fontFamily: 'Pretendard,sans-serif', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                딜 실패
              </button>
            </div>
            <button onClick={async () => {
              if (!await confirm({ message: '이 딜을 삭제할까요? 복구할 수 없습니다.', variant: 'danger' })) return;
              try { await fetchWithAuth(`/deals/${deal.dealId}`, { method: 'DELETE' }); onDealChanged?.(); } catch { /* */ }
            }} style={{ padding: '6px 0', borderRadius: 6, border: '1px solid #e2eaf0', backgroundColor: '#fff', color: '#94a3b8', fontFamily: 'Pretendard,sans-serif', fontSize: 11, cursor: 'pointer' }}>
              딜 삭제
            </button>
          </div>
        )}
        {d?.wonAt && <div style={{ padding: '8px 12px', borderRadius: 6, background: '#DCFCE7', color: '#16A34A', fontFamily: 'Pretendard,sans-serif', fontSize: 13, fontWeight: 600, textAlign: 'center', marginTop: 8 }}>수주 완료</div>}
        {d?.lostAt && <div style={{ padding: '8px 12px', borderRadius: 6, background: '#FEF2F2', color: '#DC2626', fontFamily: 'Pretendard,sans-serif', fontSize: 13, fontWeight: 600, textAlign: 'center', marginTop: 8 }}>실패</div>}
      </div>

      {/* 오른쪽: 금액 + 파이프라인 + 미팅 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16, padding: '0 16px' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#94a3b8' }}>예상 금액</span>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontWeight: 700, fontSize: 20, color: '#1e293b' }}>
            {fmtAmount(d?.amount ?? deal.expectedAmount ?? null)}
          </span>
        </div>

        <PipelineProgress current={stageIdx} onStageClick={async (idx, stageName) => {
          if (!await confirm(`파이프라인을 "${stageName}" 단계로 변경할까요?`)) return;
          const stageId = idx + 1; // pipeline_stages PK: 1=발굴, 2=가치제안, ...7=수주
          try { await fetchWithAuth(`/deals/${deal.dealId}`, { method: 'PATCH', body: JSON.stringify({ pipelineStageId: stageId }) }); onDealChanged?.(); } catch { /* */ }
        }} />

        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 18, color: '#1e293b' }}>활동 내역</span>
          </div>
          {activities.length > 0 ? (
            <div style={{ border: '1px solid #e2eaf0', borderRadius: 8, overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: 'Pretendard,sans-serif', fontSize: 13 }}>
                <thead>
                  <tr style={{ background: '#f9fafb', borderBottom: '1px solid #e2eaf0' }}>
                    {['날짜', '유형', '제목'].map((h) => (
                      <th key={h} style={{ padding: '10px 14px', textAlign: 'left', fontFamily: 'Inter,sans-serif', fontWeight: 600, fontSize: 11, color: '#475569', letterSpacing: '0.5px' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {activities.map((a, i) => (
                    <tr key={a.activityId} style={{ borderTop: i > 0 ? '1px solid #e2eaf0' : 'none' }}>
                      <td style={{ padding: '12px 14px', color: '#64748b', whiteSpace: 'nowrap' }}>{fmtDate(a.startAt)}</td>
                      <td style={{ padding: '12px 14px', color: '#64748b', whiteSpace: 'nowrap' }}>{a.type}</td>
                      <td style={{ padding: '12px 14px', color: '#1e293b' }}>{a.title}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#94a3b8' }}>활동 내역이 없습니다.</p>
          )}
        </div>
      </div>
    </div>
  );
}
