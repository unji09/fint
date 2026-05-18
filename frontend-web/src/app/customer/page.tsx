'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { TABLET_MAX } from '@/hooks/useBreakpoint';

export default function CustomerPage() {
  const router = useRouter();

  useEffect(() => {
    // useBreakpoint 훅 대신 window.innerWidth 직접 확인 — 훅의 초기값('desktop')이
    // effect보다 늦게 업데이트되어 모바일에서도 리다이렉트가 발생하는 경쟁 조건 방지
    if (window.innerWidth >= TABLET_MAX) router.replace('/customer/1');
  }, [router]);

  return null;
}
