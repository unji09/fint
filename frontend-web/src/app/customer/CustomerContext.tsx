'use client';
// src/app/customer/CustomerContext.tsx
//
// layout.tsx 에 위치한 사이드바와 [id]/page.tsx 가 공유하는 선택 상태.
// 사이드바를 layout 으로 이동시켜 페이지 전환 시 사이드바 unmount 를 방지하면서,
// 사이드바 ↔ 페이지 사이 양방향 데이터(선택된 담당자 / 고객사 추가 모달 트리거)는
// Context 로 공유한다.

import { createContext, useContext, useState, type ReactNode } from 'react';
import type { ContactInfo } from '@/types/customer';

interface CustomerCtx {
  selContact: ContactInfo | null;
  setSelContact: (c: ContactInfo | null) => void;
  addAccountOpen: boolean;
  openAddAccount: () => void;
  closeAddAccount: () => void;
}

const Ctx = createContext<CustomerCtx | null>(null);

export function CustomerProvider({ children }: { children: ReactNode }) {
  const [selContact, setSelContact] = useState<ContactInfo | null>(null);
  const [addAccountOpen, setAddAccountOpen] = useState(false);
  return (
    <Ctx.Provider
      value={{
        selContact,
        setSelContact,
        addAccountOpen,
        openAddAccount: () => setAddAccountOpen(true),
        closeAddAccount: () => setAddAccountOpen(false),
      }}
    >
      {children}
    </Ctx.Provider>
  );
}

export function useCustomer(): CustomerCtx {
  const c = useContext(Ctx);
  if (!c) throw new Error('useCustomer must be used within CustomerProvider');
  return c;
}
