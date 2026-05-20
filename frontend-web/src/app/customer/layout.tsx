'use client';
// src/app/customer/layout.tsx
//
// 고객 정보 화면의 공통 컨테이너 + 사이드바.
// [id] 변경 시에도 layout 자체는 유지되므로 사이드바 unmount 가 없다.
// (이전에는 layout 이 없어 페이지 전환마다 사이드바가 깜빡였음)

import { useParams, usePathname, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import CustomerSidebar from '@/components/customer/CustomerSidebar';
import { useAccountList, useAccountDetail, useRegisterAccount, useDeleteAccount } from '@/hooks/useCustomer';
import { CustomerProvider, useCustomer } from './CustomerContext';
import { useConfirm } from '@/components/common/ConfirmDialog';
import useBreakpoint, { TABLET_MAX } from '@/hooks/useBreakpoint';

const F = 'Pretendard,sans-serif';

function CustomerLayoutInner({ children }: { children: ReactNode }) {
  const { id } = useParams<{ id: string }>();
  const pathname = usePathname();
  const router = useRouter();
  const bp = useBreakpoint();
  const isCompact = bp !== 'desktop';
  const isDetailPage = pathname !== '/customer' && pathname !== '/customer/';
  const { accounts, loading: aL, error: aE, refetch: refA } = useAccountList();
  // 모바일 사이드바에서 expand한 고객사의 contacts를 로드하기 위한 ID
  const [previewId, setPreviewId] = useState<string | null>(null);
  const detailId = id ?? (isCompact ? previewId : null);
  const { contacts, refetch: refDetail } = useAccountDetail(detailId);
  const { register: regAccount, loading: regL } = useRegisterAccount();
  const { remove: delAccount } = useDeleteAccount();
  const { setSelContact, addAccountOpen, openAddAccount, closeAddAccount } = useCustomer();
  const confirm = useConfirm();

  // 고객사 추가 모달 폼
  const [nAName, setNAName] = useState('');
  const [nAInd, setNAInd] = useState('');
  useEffect(() => {
    if (!addAccountOpen) { setNAName(''); setNAInd(''); }
  }, [addAccountOpen]);

  const selectedAccountId = accounts.find((a) => String(a.accountId) === String(id))?.accountId ?? null;

  const showSidebar = !isCompact || !isDetailPage;
  const showContent = !isCompact || isDetailPage;

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, backgroundColor: '#f8fafc', zIndex: -1 }} />
      {/* CSS media query 방어: JS 실행 전에도 모바일에서 사이드바/콘텐츠 하나만 표시 */}
      <style dangerouslySetInnerHTML={{ __html: `
        @media (max-width: ${TABLET_MAX}px) {
          .customer-sidebar-wrap { width: 100% !important; }
        }
      `}} />
      <div style={{ position: 'fixed', top: 64, left: 0, right: 0, bottom: 0, display: 'flex', overflow: 'hidden' }}>
        {showSidebar && (
          <CustomerSidebar
            accounts={accounts}
            selectedId={selectedAccountId}
            loading={aL}
            error={aE}
            onRetry={refA}
            contacts={contacts}
            onContactSelect={setSelContact}
            onContactAdded={refDetail}
            onAddAccount={openAddAccount}
            isCompact={isCompact}
            onAccountExpand={(accountId) => setPreviewId(accountId ? String(accountId) : null)}
            onDeleteAccount={async (accountId, name) => {
              const msg = `"${name}" 고객사를 삭제하시겠습니까?\n\n연결된 모든 담당자, 딜, 활동 데이터가 함께 삭제됩니다.\n이 작업은 되돌릴 수 없습니다.`;
              if (!await confirm({ message: msg, variant: 'danger' })) return;
              if (!await confirm({ message: `정말로 "${name}"을(를) 삭제합니까?`, variant: 'danger', confirmText: '삭제' })) return;
              if (await delAccount(accountId)) {
                refA();
                router.push('/customer/1');
              }
            }}
          />
        )}
        {showContent && <div style={{ flex: 1, overflow: 'hidden', minWidth: 0 }}>{children}</div>}
      </div>

      {/* 고객사 추가 모달 — layout 에 두어 sidebar 와 함께 유지. portal 로 body 마운트해 GNB 까지 덮는다. */}
      {addAccountOpen && typeof document !== 'undefined' && createPortal(
        <div style={{ position: 'fixed', inset: 0, zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div onClick={closeAddAccount} style={{ position: 'absolute', inset: 0, backgroundColor: 'rgba(0,0,0,0.35)' }} />
          <div style={{ position: 'relative', backgroundColor: '#fff', borderRadius: 12, width: isCompact ? 'calc(100% - 32px)' : 380, maxWidth: 380, padding: isCompact ? 16 : 22, boxShadow: '0 12px 40px rgba(0,0,0,0.12)', display: 'flex', flexDirection: 'column', gap: isCompact ? 10 : 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontFamily: F, fontSize: 16, fontWeight: 700, color: '#16180F' }}>고객사 추가</span>
              <button onClick={closeAddAccount} style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: 18, color: '#9CA3AF' }}>×</button>
            </div>
            <input value={nAName} onChange={(e) => setNAName(e.target.value)} placeholder="고객사명 *"
              style={{ padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5', fontSize: 13, outline: 'none', fontFamily: F }} />
            <input value={nAInd} onChange={(e) => setNAInd(e.target.value)} placeholder="업종"
              style={{ padding: '9px 12px', borderRadius: 8, border: '1px solid #E5E6DE', backgroundColor: '#F8F8F5', fontSize: 13, outline: 'none', fontFamily: F }} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={closeAddAccount} style={{ padding: '7px 14px', borderRadius: 6, border: '1px solid #E5E6DE', backgroundColor: '#fff', fontSize: 13, color: '#737880', cursor: 'pointer', fontFamily: F }}>취소</button>
              <button
                onClick={async () => {
                  if (!nAName.trim() || regL) return;
                  await regAccount({ name: nAName.trim(), industry: nAInd.trim() || undefined });
                  closeAddAccount();
                  refA();
                }}
                disabled={!nAName.trim() || regL}
                style={{ padding: '7px 16px', borderRadius: 6, border: 'none', backgroundColor: nAName.trim() ? '#06B6D4' : '#cbd5e1', fontSize: 13, fontWeight: 600, color: '#fff', cursor: nAName.trim() ? 'pointer' : 'default', fontFamily: F }}
              >
                {regL ? '등록 중...' : '등록'}
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}

export default function CustomerLayout({ children }: { children: ReactNode }) {
  return (
    <CustomerProvider>
      <CustomerLayoutInner>{children}</CustomerLayoutInner>
    </CustomerProvider>
  );
}
