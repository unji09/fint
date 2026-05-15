package com.ssafy.fint.domain.activity.websocket;

import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CompletableFuture;

/**
 * 실시간 STT WebSocket 세션 컨텍스트.
 * 모바일 세션 1개 ↔ FastAPI 세션 1개를 1:1로 관리한다.
 * <p>
 * 실시간 segment는 프론트엔드 화면 표시 전용으로 중계만 한다.
 * DB 저장은 녹음 종료 후 배치 STT 결과가 담당한다.
 */
public class SttSessionContext {

    private final Long activityId;
    private final Long tenantId;
    private final WebSocketSession mobileSession;
    private final CompletableFuture<WebSocketSession> fastApiSessionFuture;

    public SttSessionContext(
            Long activityId,
            Long tenantId,
            WebSocketSession mobileSession,
            CompletableFuture<WebSocketSession> fastApiSessionFuture
    ) {
        this.activityId = activityId;
        this.tenantId = tenantId;
        this.mobileSession = mobileSession;
        this.fastApiSessionFuture = fastApiSessionFuture;
    }

    public Long activityId() { return activityId; }
    public Long tenantId() { return tenantId; }
    public WebSocketSession mobileSession() { return mobileSession; }
    public CompletableFuture<WebSocketSession> fastApiSessionFuture() { return fastApiSessionFuture; }
}
