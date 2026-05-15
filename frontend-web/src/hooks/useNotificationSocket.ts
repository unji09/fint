'use client';
// src/hooks/useNotificationSocket.ts
//
// WebSocket(STOMP) 실시간 알림 구독.
// 명세:
//   Endpoint: {wsBase}/ws
//   STOMP Header: Authorization: Bearer {token}
//   Subscribe: /user/queue/notifications
//   Payload: NotificationItemResponse (기존 GET /notifications 와 동일 포맷)
//
// 사용 예:
//   useNotificationSocket((noti) => { ... });
//   - 로그인 상태(accessToken 존재) 면 자동 연결
//   - 컴포넌트 unmount 시 자동 해제
//   - JWT 만료/연결 끊김 시 STOMP Client 의 내장 reconnectDelay 로 재시도

import { useEffect, useRef } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import type { NotificationItem } from '@/components/common/NotificationPanel';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

// API_BASE("http://host:8080/api/v1" 또는 "https://host/api/v1") → "ws(s)://host[:port]/ws"
function deriveWsUrl(): string {
  // 1) 명시적 env 우선
  const explicit = process.env.NEXT_PUBLIC_WS_URL;
  if (explicit) return explicit;

  // 2) API_BASE 기반 추론
  if (API_BASE) {
    try {
      const u = new URL(API_BASE);
      const wsProto = u.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${wsProto}//${u.host}/ws`;
    } catch {
      /* fall through */
    }
  }

  // 3) 마지막 fallback (브라우저 환경에서만)
  if (typeof window !== 'undefined') {
    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${wsProto}//${window.location.host}/ws`;
  }
  return '';
}

export function useNotificationSocket(onMessage: (noti: NotificationItem) => void) {
  // 콜백은 ref 로 — 매 렌더마다 client 를 재생성하지 않도록 (cleanup 비용)
  const cbRef = useRef(onMessage);
  cbRef.current = onMessage;

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const token = localStorage.getItem('accessToken');
    if (!token) return; // 로그인 안 한 상태면 연결 안 함

    const wsUrl = deriveWsUrl();
    if (!wsUrl) return;

    const client = new Client({
      brokerURL: wsUrl,
      // STOMP CONNECT 시 Authorization 헤더 전달 (백엔드 JwtChannelInterceptor 등이 인증)
      connectHeaders: { Authorization: `Bearer ${token}` },
      // 토큰 만료/네트워크 단절 등으로 끊기면 5초 후 재시도 (STOMP Client 내장)
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      // 로그 보고 싶으면 process.env.NODE_ENV !== 'production' && console.log
      debug: () => { /* silent */ },
    });

    client.onConnect = () => {
      client.subscribe('/user/queue/notifications', (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body) as NotificationItem;
          cbRef.current(payload);
        } catch (e) {
          console.error('[NotificationSocket] payload parse 실패', e);
        }
      });
    };

    client.onStompError = (frame) => {
      console.warn('[NotificationSocket] STOMP error', frame.headers['message'], frame.body);
    };

    client.activate();

    return () => {
      // unmount / 로그인 사용자 변경 시 정리
      void client.deactivate();
    };
    // 한 번만 연결 — 토큰 변경(로그인/로그아웃) 시 페이지 새로고침이 일어나므로 의존성 없음
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
