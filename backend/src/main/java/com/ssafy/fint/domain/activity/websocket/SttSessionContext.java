package com.ssafy.fint.domain.activity.websocket;

import org.springframework.web.socket.WebSocketSession;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실시간 STT WebSocket 세션 컨텍스트.
 * 모바일 세션 1개 ↔ FastAPI 세션 1개를 1:1로 관리한다.
 * <p>
 * transcript / transcript_refined 메시지를 range_start_ms 키로 누적한다.
 * refined 패스가 도착하면 같은 키의 draft를 교체한다.
 * 세션 종료 시 누적 세그먼트로 무드 분석을 트리거한다.
 */
public class SttSessionContext {

    /** 단일 전사 세그먼트 (fast/refined 공용). */
    public record SegmentEntry(
            int rangeStartMs,
            String speakerId,
            String text,
            int startMs,
            int endMs
    ) {}

    private final Long activityId;
    private final Long tenantId;
    private final WebSocketSession mobileSession;
    private final CompletableFuture<WebSocketSession> fastApiSessionFuture;

    // key = range_start_ms — refined 패스가 도착하면 draft를 덮어쓴다
    private final ConcurrentHashMap<Integer, SegmentEntry> segments = new ConcurrentHashMap<>();

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

    /** 세그먼트를 저장한다. 같은 range_start_ms 가 이미 있으면 덮어쓴다(refined > draft). */
    public void putSegment(SegmentEntry entry) {
        segments.put(entry.rangeStartMs(), entry);
    }

    /** range_start_ms 오름차순으로 정렬된 비어있지 않은 세그먼트 목록을 반환한다. */
    public List<SegmentEntry> getSortedSegments() {
        return segments.values().stream()
                .filter(s -> s.text() != null && !s.text().isBlank())
                .sorted(Comparator.comparingInt(SegmentEntry::rangeStartMs))
                .toList();
    }
}
