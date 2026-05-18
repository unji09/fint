'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
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
  /** Activity Detail API 의 summary (AI 분석 결과). list 응답엔 없고 detail 호출로 보강. */
  summary?: Record<string, string | string[]> | null;
}

// summary key → 한글 라벨
const SUMMARY_LABELS: Record<string, string> = {
  keyDiscussion: '핵심 논의',
  customerNeeds: '고객 니즈',
  agreements: '합의 사항',
  actionItems: '실행 항목',
  highlights: '주요 내용',
  decisions: '결정 사항',
  nextSteps: '다음 단계',
  risks: '리스크',
  notes: '비고',
};

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
  const router = useRouter();
  const confirm = useConfirm();
  const prompt = usePrompt();
  const [detail, setDetail] = useState<DealDetail | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedMeetingId, setExpandedMeetingId] = useState<number | null>(null);

  useEffect(() => {
    if (!deal.dealId) return;
    setLoading(true);

    Promise.allSettled([
      fetchWithAuth(`/deals/${deal.dealId}`).then((r) => r.json()),
      // 미팅만 — 날짜 desc 정렬은 백엔드 기본
      fetchWithAuth(`/activities?dealId=${deal.dealId}&type=MEETING&size=20`).then((r) => r.json()),
    ]).then(async ([detailRes, actRes]) => {
      if (detailRes.status === 'fulfilled') setDetail(detailRes.value.data);
      let list: Activity[] = [];
      if (actRes.status === 'fulfilled') {
        const ad = actRes.value.data;
        list =
          (Array.isArray(ad?.data) ? ad.data : null) ??
          ad?.content ??
          (Array.isArray(ad) ? ad : []);
      }
      // 각 미팅 detail 호출 → summary 보강. 백엔드 list 응답엔 summary 없음.
      const detailedList = await Promise.all(
        list.map(async (a) => {
          try {
            const r = await fetchWithAuth(`/activities/${a.activityId}`);
            if (!r.ok) return a;
            const j = await r.json();
            const d = j?.data ?? j;
            return { ...a, summary: d?.summary ?? null } as Activity;
          } catch {
            return a;
          }
        }),
      );
      // 날짜 desc 정렬 (최신이 위)
      detailedList.sort((x, y) => new Date(y.startAt).getTime() - new Date(x.startAt).getTime());
      setActivities(detailedList);
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
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 18, color: '#1e293b' }}>미팅 내역</span>
            <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#94a3b8' }}>{activities.length}회</span>
          </div>
          {activities.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {activities.map((a) => {
                const expanded = expandedMeetingId === a.activityId;
                const hasSummary = a.summary && Object.keys(a.summary).length > 0;
                return (
                  <div
                    key={a.activityId}
                    onClick={() => setExpandedMeetingId(expanded ? null : a.activityId)}
                    style={{
                      border: `1px solid ${expanded ? '#06b6d4' : '#e2eaf0'}`,
                      borderRadius: 8,
                      padding: '12px 14px',
                      cursor: 'pointer',
                      background: '#fff',
                      transition: 'border-color 0.12s',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <span style={{ fontFamily: 'Inter,sans-serif', fontSize: 11, color: '#475569', fontVariantNumeric: 'tabular-nums', flexShrink: 0, minWidth: 90 }}>
                        {fmtDate(a.startAt)}
                      </span>
                      <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, fontWeight: expanded ? 600 : 500, color: '#1e293b', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {a.title}
                      </span>
                      {hasSummary && (
                        <span style={{ fontSize: 10, fontWeight: 600, color: '#0686d4', background: '#eef6ff', padding: '2px 7px', borderRadius: 3, flexShrink: 0 }}>
                          요약
                        </span>
                      )}
                      <svg width="12" height="7" viewBox="0 0 12 7" fill="none" style={{ flexShrink: 0, transform: expanded ? 'rotate(180deg)' : 'none', transition: 'transform 0.18s' }}>
                        <path d="M1 1l5 5 5-5" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round" />
                      </svg>
                    </div>
                    {expanded && (
                      <div onClick={(e) => e.stopPropagation()} style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #f1f5f9', display: 'flex', flexDirection: 'column', gap: 10 }}>
                        {hasSummary ? (
                          Object.entries(a.summary!).map(([k, v]) => (
                            <div key={k}>
                              <div style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 11, fontWeight: 600, color: '#94a3b8', marginBottom: 4, letterSpacing: '0.02em' }}>
                                {SUMMARY_LABELS[k] ?? k}
                              </div>
                              {Array.isArray(v) ? (
                                <ul style={{ margin: 0, padding: '0 0 0 18px', display: 'flex', flexDirection: 'column', gap: 3 }}>
                                  {v.map((item, i) => (
                                    <li key={i} style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#334155', lineHeight: 1.55 }}>{item}</li>
                                  ))}
                                </ul>
                              ) : (
                                <p style={{ margin: 0, fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#334155', lineHeight: 1.55, whiteSpace: 'pre-wrap' }}>
                                  {v}
                                </p>
                              )}
                            </div>
                          ))
                        ) : a.memo ? (
                          <div>
                            <div style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 11, fontWeight: 600, color: '#94a3b8', marginBottom: 4 }}>메모</div>
                            <p style={{ margin: 0, fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#334155', lineHeight: 1.55, whiteSpace: 'pre-wrap' }}>{a.memo}</p>
                          </div>
                        ) : (
                          <p style={{ margin: 0, fontFamily: 'Pretendard,sans-serif', fontSize: 12, color: '#94a3b8' }}>
                            AI 요약이 아직 없습니다. 녹음 후 자동 생성됩니다.
                          </p>
                        )}
                        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              const q = new URLSearchParams({
                                activityId: String(a.activityId),
                                date: a.startAt,
                              });
                              router.push(`/calendar?${q.toString()}`);
                            }}
                            style={{
                              fontFamily: 'Pretendard,sans-serif',
                              fontSize: 12,
                              fontWeight: 500,
                              color: '#0686d4',
                              background: 'transparent',
                              border: 'none',
                              padding: '4px 6px',
                              cursor: 'pointer',
                            }}
                          >
                            전체 보기 →
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <p style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 13, color: '#94a3b8' }}>미팅 내역이 없습니다.</p>
          )}
        </div>
      </div>
    </div>
  );
}
