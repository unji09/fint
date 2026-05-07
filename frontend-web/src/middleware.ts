import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const PUBLIC_PATHS = ['/login'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 공개 경로는 통과
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // 토큰 확인 (쿠키 기반으로도 지원 가능하나 현재는 localStorage → 클라이언트에서 처리)
  // Next.js middleware는 서버에서 실행되므로 localStorage 접근 불가
  // → 클라이언트 리다이렉트는 AuthGuard 컴포넌트로 처리
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next|favicon.ico|.*\\..*).*)'],
};
