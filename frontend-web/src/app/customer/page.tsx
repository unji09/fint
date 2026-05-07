'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function CustomerPage() {
  const router = useRouter();
  useEffect(() => {
    // 첫 번째 고객사로 리다이렉트
    router.replace('/customer/1');
  }, [router]);
  return null;
}
