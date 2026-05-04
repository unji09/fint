import type { Metadata, Viewport } from 'next';
import '@/styles/globals.css';

export const metadata: Metadata = {
  title: 'F!NT',
  description: 'F!NT — 행동을 만드는 B2B CRM',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
