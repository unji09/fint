'use client';

import { usePathname } from 'next/navigation';
import GNB from '@/components/common/GNB';

const GNB_H = 64;
// GNB·메인 컨테이너 없이 풀스크린으로 렌더할 경로. 인증 전 / 풀스크린 화면.
const STANDALONE_PATHS = ['/login'];

export default function LayoutShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const standalone = STANDALONE_PATHS.some((p) => pathname?.startsWith(p));

  if (standalone) return <>{children}</>;

  return (
    <>
      <GNB />
      <main
        style={{
          height: `calc(100vh - ${GNB_H}px)`,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {children}
      </main>
    </>
  );
}
