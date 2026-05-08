package com.ssafy.fint.domain.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.dashboard.dto.QueryStreamCompleteEvent;
import com.ssafy.fint.domain.dashboard.dto.QueryStreamErrorEvent;
import com.ssafy.fint.domain.dashboard.dto.QueryStreamProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 자연어 쿼리 진행 상태를 SSE 로 push 하는 서비스.
 *
 * <p>FastAPI 가 Redis 의 {@code dashboard:query:{traceId}} 키를 단계별로 갱신하면
 * 본 서비스가 500ms 간격 polling 으로 status 변화를 감지해 클라이언트에게
 * progress / complete / error 이벤트를 발행한다. (internal-api-spec §2)
 *
 * <p>polling 정책: 500ms 간격, 30초 max. timeout / FAILED / 키 미존재 시
 * error 이벤트를 1회 push 후 SSE 종료.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardQueryStreamService {

    private static final String REDIS_KEY_PREFIX = "dashboard:query:";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final long POLL_TIMEOUT_MS = 30_000L;
    private static final long SSE_TIMEOUT_MS = 35_000L;
    private static final Duration STATE_TTL = Duration.ofSeconds(600);

    private static final String EVENT_PROGRESS = "progress";
    private static final String EVENT_COMPLETE = "complete";
    private static final String EVENT_ERROR = "error";

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DASHBOARD_ID = "dashboardId";
    private static final String FIELD_INPUT_TEXT = "inputText";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_RESULT_DATA = "data";
    private static final String FIELD_RESULT_INSIGHT = "insight_text";
    private static final String FIELD_RESULT_WIDGET_TYPE = "widget_type";
    private static final String FIELD_RESULT_TITLE = "title";
    private static final String FIELD_RESULT_CONFIG = "config";

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DashboardQueryResultWriter resultWriter;
    private final TaskScheduler dashboardPollingScheduler;

    /**
     * traceId 의 처리 진행 상태를 구독한다. 호출 즉시 SseEmitter 를 반환하고
     * 내부에서 Redis polling 을 시작한다.
     */
    public SseEmitter subscribe(String traceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;

        ScheduledFuture<?> future = dashboardPollingScheduler.scheduleAtFixedRate(
                () -> tick(traceId, emitter, lastStatus, futureRef, deadline),
                POLL_INTERVAL
        );
        futureRef.set(future);

        emitter.onCompletion(() -> future.cancel(false));
        emitter.onError(e -> future.cancel(false));
        emitter.onTimeout(() -> {
            future.cancel(false);
            sendErrorSilently(emitter, null, "처리 시간이 초과되었습니다.");
            emitter.complete();
        });

        return emitter;
    }

    private void tick(String traceId,
                      SseEmitter emitter,
                      AtomicReference<AiQueryStatus> lastStatus,
                      AtomicReference<ScheduledFuture<?>> futureRef,
                      long deadline) {
        try {
            if (System.currentTimeMillis() > deadline) {
                String message = "처리 시간이 초과되었습니다.";
                markFailedSilently(traceId, message);
                sendErrorSilently(emitter, null, message);
                cancelAndComplete(emitter, futureRef);
                return;
            }

            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + traceId);
            if (json == null) {
                sendErrorSilently(emitter, null, "유효하지 않은 traceId 입니다.");
                cancelAndComplete(emitter, futureRef);
                return;
            }

            Map<String, Object> payload = objectMapper.readValue(json, PAYLOAD_TYPE);
            AiQueryStatus status = AiQueryStatus.valueOf((String) payload.get(FIELD_STATUS));

            if (status == lastStatus.get()) {
                return;
            }
            lastStatus.set(status);

            switch (status) {
                case INTENT_PARSING, DATA_QUERYING, COMPONENT_BUILDING, STYLING -> sendProgress(emitter, status);
                case COMPLETED -> {
                    sendComplete(emitter, payload);
                    cancelAndComplete(emitter, futureRef);
                }
                case FAILED -> {
                    String message = (String) payload.getOrDefault(FIELD_ERROR, "처리에 실패했습니다.");
                    sendErrorSilently(emitter, null, message);
                    cancelAndComplete(emitter, futureRef);
                }
                case PENDING -> { /* 아직 처리 시작 전. 다음 polling 대기 */ }
            }
        } catch (Exception e) {
            log.warn("[DashboardQueryStream] traceId={} 처리 중 예외", traceId, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // 이미 종료된 emitter
            }
            ScheduledFuture<?> f = futureRef.get();
            if (f != null) {
                f.cancel(false);
            }
        }
    }

    private void sendProgress(SseEmitter emitter, AiQueryStatus status) throws IOException {
        SseProgressStep step = SseProgressStep.fromAiStatus(status);
        if (step == null) {
            return;
        }
        emitter.send(SseEmitter.event()
                .name(EVENT_PROGRESS)
                .data(new QueryStreamProgressEvent(
                        step.name(),
                        step.getStepNumber(),
                        SseProgressStep.TOTAL_STEPS
                )));
    }

    private void sendComplete(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        Long dashboardId = ((Number) payload.get(FIELD_DASHBOARD_ID)).longValue();
        String inputText = (String) payload.get(FIELD_INPUT_TEXT);
        Map<String, Object> result = asMap(payload.get(FIELD_RESULT));

        DashboardQueryResultWriter.InsertedIds ids = resultWriter.persist(dashboardId, inputText, result);

        QueryStreamCompleteEvent body = new QueryStreamCompleteEvent(
                ids.widgetId(),
                ids.queryId(),
                (String) result.get(FIELD_RESULT_WIDGET_TYPE),
                (String) result.get(FIELD_RESULT_TITLE),
                asMap(result.get(FIELD_RESULT_CONFIG)),
                null,
                new QueryStreamCompleteEvent.Result(
                        asMap(result.get(FIELD_RESULT_DATA)),
                        (String) result.get(FIELD_RESULT_INSIGHT)
                )
        );

        emitter.send(SseEmitter.event().name(EVENT_COMPLETE).data(body));
    }

    private void sendErrorSilently(SseEmitter emitter, String step, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name(EVENT_ERROR)
                    .data(new QueryStreamErrorEvent(step, message)));
        } catch (IOException ignored) {
            // 이미 종료된 emitter — 무시
        }
    }

    /**
     * Redis 의 처리 상태를 FAILED 로 덮어쓴다 (internal-api-spec §2 "timeout 시 FAILED 처리").
     * 호출 실패 자체는 SSE 흐름을 막지 않도록 swallow.
     */
    private void markFailedSilently(String traceId, String message) {
        try {
            Map<String, Object> payload = Map.of(
                    FIELD_STATUS, AiQueryStatus.FAILED.name(),
                    FIELD_ERROR, message
            );
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + traceId,
                    objectMapper.writeValueAsString(payload),
                    STATE_TTL
            );
        } catch (Exception e) {
            log.warn("[DashboardQueryStream] traceId={} FAILED 마킹 실패", traceId, e);
        }
    }

    private void cancelAndComplete(SseEmitter emitter, AtomicReference<ScheduledFuture<?>> futureRef) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 이미 종료된 emitter
        }
        ScheduledFuture<?> f = futureRef.get();
        if (f != null) {
            f.cancel(false);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
