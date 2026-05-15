package com.ssafy.fint.domain.activity.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SttStreamHandler extends AbstractWebSocketHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final long FASTAPI_CONNECT_TIMEOUT_SEC = 5;

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Value("${ai.server-url}")
    private String aiServerUrl;

    private final ConcurrentHashMap<String, SttSessionContext> sessions = new ConcurrentHashMap<>();

    // ── 연결 수립 ────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession mobileSession) {
        String token = extractToken(mobileSession);
        if (token == null) {
            closeQuietly(mobileSession, CloseStatus.POLICY_VIOLATION.withReason("token required"));
            return;
        }

        Long tenantId;
        try {
            jwtTokenProvider.validate(token);
            tenantId = jwtTokenProvider.getTenantId(token);
        } catch (Exception e) {
            log.warn("[SttStream] invalid token. sessionId={}", mobileSession.getId());
            closeQuietly(mobileSession, CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
            return;
        }

        Long activityId = extractActivityId(mobileSession);
        if (activityId == null) {
            closeQuietly(mobileSession, CloseStatus.BAD_DATA.withReason("invalid path"));
            return;
        }

        String fastApiWsUrl = buildFastApiUrl(activityId, token);
        var fastApiSessionFuture = connectToFastApi(fastApiWsUrl, mobileSession);

        SttSessionContext ctx = new SttSessionContext(activityId, tenantId, mobileSession, fastApiSessionFuture);
        sessions.put(mobileSession.getId(), ctx);

        log.info("[SttStream] connected. activityId={} tenantId={} sessionId={}",
                activityId, tenantId, mobileSession.getId());
    }

    // ── 오디오 청크 수신 → FastAPI 중계 ──────────────────────────

    @Override
    protected void handleBinaryMessage(WebSocketSession mobileSession, BinaryMessage message) throws Exception {
        SttSessionContext ctx = sessions.get(mobileSession.getId());
        if (ctx == null) return;

        WebSocketSession fastApiSession;
        try {
            fastApiSession = ctx.fastApiSessionFuture().get(FASTAPI_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[SttStream] FastAPI not ready. activityId={}", ctx.activityId());
            return;
        }

        if (fastApiSession.isOpen()) {
            fastApiSession.sendMessage(message);
        }
    }

    // ── 세션 종료 → transcript 저장 ─────────────────────────────

    @Override
    public void afterConnectionClosed(WebSocketSession mobileSession, CloseStatus status) {
        SttSessionContext ctx = sessions.remove(mobileSession.getId());
        if (ctx == null) return;

        // FastAPI 연결 종료
        ctx.fastApiSessionFuture().thenAccept(fastApiSession -> {
            if (fastApiSession.isOpen()) {
                closeQuietly(fastApiSession, CloseStatus.NORMAL);
            }
        });

        log.info("[SttStream] session closed. activityId={}", ctx.activityId());
    }

    // ── FastAPI WS 연결 ──────────────────────────────────────────

    private java.util.concurrent.CompletableFuture<WebSocketSession> connectToFastApi(
            String wsUrl, WebSocketSession mobileSession) {

        var client = new StandardWebSocketClient();
        var handler = new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession fastApiSession, TextMessage message) throws Exception {
                SttSessionContext ctx = sessions.get(mobileSession.getId());
                if (ctx == null) return;

                Map<String, Object> payload = objectMapper.readValue(message.getPayload(), MAP_TYPE);
                String type = (String) payload.get("type");

                        // 모바일로 그대로 중계
                if (mobileSession.isOpen()) {
                    mobileSession.sendMessage(message);
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                log.debug("[SttStream] FastAPI WS closed. status={}", status);
            }
        };

        return client.execute(handler, null, URI.create(wsUrl));
    }

    // ── 유틸 ─────────────────────────────────────────────────────

    private String extractToken(WebSocketSession session) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    private Long extractActivityId(WebSocketSession session) {
        // path: /ws/stt/{activityId}
        try {
            String path = session.getUri().getPath();
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildFastApiUrl(Long activityId, String token) {
        String wsBase = aiServerUrl.replaceFirst("^http", "ws");
        return wsBase + "/api/v1/stt/stream/" + activityId + "?token=" + token;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (Exception ignored) {}
    }
}
