import type { Metadata } from 'next';
import ConfirmDialogProvider from '@/components/common/ConfirmDialog';
import LayoutShell from '@/components/common/LayoutShell';

export const metadata: Metadata = {
  title: 'F!NT — AI 기반 B2B 영업 CRM',
  description: '기록하는 CRM이 아니라, 행동을 만들어내는 CRM',
  icons: {
    icon: '/logo.png',
  },
};

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
          <LayoutShell>{children}</LayoutShell>
        </ConfirmDialogProvider>
      </body>
    </html>
  );
}
