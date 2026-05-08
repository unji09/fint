import type { Metadata } from 'next';
import GNB from '@/components/common/GNB';

export const metadata: Metadata = {
  title: 'F!NT — AI 기반 B2B 영업 CRM',
  description: '기록하는 CRM이 아니라, 행동을 만들어내는 CRM',
};

const GNB_H = 80;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body
        style={{
          margin: 0,
          padding: 0,
          fontFamily: "'Pretendard', -apple-system, sans-serif",
          height: '100vh',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
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
      </body>
    </html>
  );
}
