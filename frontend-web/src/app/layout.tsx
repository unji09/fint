import type { Metadata } from 'next';
import GNB from '@/components/common/GNB';
import ConfirmDialogProvider from '@/components/common/ConfirmDialog';

export const metadata: Metadata = {
  title: 'F!NT — AI 기반 B2B 영업 CRM',
  description: '기록하는 CRM이 아니라, 행동을 만들어내는 CRM',
  icons: {
    icon: '/logo.png',
  },
};

const GNB_H = 64;

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
        <ConfirmDialogProvider>
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
        </ConfirmDialogProvider>
      </body>
    </html>
  );
}
