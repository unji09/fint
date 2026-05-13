package com.ssafy.fint.domain.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SSE 진행 스트림 서비스 단위 테스트.
 * subscribe() 의 polling tick 분기를 ReflectionTestUtils 로 직접 호출해
 * deadline / null / PENDING / progress / COMPLETED / FAILED / dedup / 예외 분기를 모두 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardQueryStreamServiceTest {

    private static final String TRACE_ID = "trace-abc";
    private static final String REDIS_KEY = "dashboard:query:" + TRACE_ID;
    private static final long DEADLINE_NEVER = Long.MAX_VALUE;
    private static final Long USER_ID = 7L;
    private static final Long TENANT_ID = 1L;

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private DashboardQueryResultWriter resultWriter;
    @Mock private TaskScheduler scheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DashboardQueryStreamService service;

    @BeforeEach
    void setUp() {
        service = new DashboardQueryStreamService(
                redisTemplate, objectMapper, resultWriter, scheduler);
    }

    @Test
    @DisplayName("subscribe 호출 시 SseEmitter 발급 + scheduler 에 500ms 주기 polling tick 등록.")
    void subscribeSchedulesTick() throws Exception {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(
                objectMapper.writeValueAsString(Map.of("status", "PENDING", "userId", USER_ID, "tenantId", TENANT_ID)));

        SseEmitter emitter = service.subscribe(TRACE_ID, USER_ID);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(35_000L);
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(500)));
    }

    @Test
    @DisplayName("subscribe 시 userId 불일치 → polling 미시작.")
    void subscribeRejectsWrongUserId() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(
                objectMapper.writeValueAsString(Map.of("status", "PENDING", "userId", USER_ID, "tenantId", TENANT_ID)));

        SseEmitter emitter = service.subscribe(TRACE_ID, 999L);

        assertThat(emitter).isNotNull();
        verifyNoInteractions(scheduler);
    }

    @Test
    @DisplayName("deadline 초과 → Redis FAILED 마킹 + error 이벤트 + emitter complete + future cancel.")
    void tickDeadlineExceeded() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        invokeTick(emitter, lastStatus, futureRef, 0L);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(REDIS_KEY), jsonCaptor.capture(), eq(Duration.ofSeconds(600)));
        assertThat(jsonCaptor.getValue()).contains("FAILED").contains("처리 시간이 초과");
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
    }

    @Test
    @DisplayName("Redis 에 traceId 키가 없으면 error 이벤트 후 emitter complete + future cancel.")
    void tickRedisKeyMissing() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("PENDING → 이벤트 발행 없이 다음 polling 대기 (lastStatus 만 갱신).")
    void tickPendingDoesNothing() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        stubRedisStatus("PENDING", null);

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, never()).complete();
        assertThat(lastStatus.get()).isEqualTo(AiQueryStatus.PENDING);
    }

    @Test
    @DisplayName("INTENT_PARSING/DATA_QUERYING/COMPONENT_BUILDING/STYLING → progress 이벤트 1회 + lastStatus 갱신.")
    void tickProgressBranches() throws Exception {
        for (AiQueryStatus status : new AiQueryStatus[]{
                AiQueryStatus.INTENT_PARSING,
                AiQueryStatus.DATA_QUERYING,
                AiQueryStatus.COMPONENT_BUILDING,
                AiQueryStatus.STYLING}) {
            SseEmitter emitter = mock(SseEmitter.class);
            AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
            AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
            stubRedisStatus(status.name(), null);

            invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, never()).complete();
            assertThat(lastStatus.get()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("같은 status 가 두 번 들어오면 dedup 되어 send/complete 가 호출되지 않는다.")
    void tickDeduplicatesSameStatus() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>(AiQueryStatus.INTENT_PARSING);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        stubRedisStatus("INTENT_PARSING", null);

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName("COMPLETED → resultWriter.persist + complete 이벤트 + emitter complete + future cancel.")
    void tickCompletedPersistsAndCompletes() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);

        Map<String, Object> result = Map.of(
                "widget_type", "BAR_CHART",
                "title", "주간 매출",
                "config", Map.of("colors", "#A8BFA9"),
                "data", Map.of("labels", "W1"),
                "insight_text", "최근 매출이 늘었습니다."
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "COMPLETED");
        payload.put("dashboardId", 5);
        payload.put("tenantId", TENANT_ID);
        payload.put("inputText", "주간 매출 추이");
        payload.put("result", result);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));
        when(resultWriter.persist(eq(TENANT_ID), eq(5L), eq("주간 매출 추이"), any()))
                .thenReturn(new DashboardQueryResultWriter.InsertedIds(20L, 10L));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(resultWriter).persist(eq(TENANT_ID), eq(5L), eq("주간 매출 추이"), any());
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
    }

    @Test
    @DisplayName("FAILED → error 이벤트 + emitter complete + future cancel.")
    void tickFailed() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);
        Map<String, Object> payload = Map.of("status", "FAILED", "error", "AI 처리 오류");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
        verifyNoInteractions(resultWriter);
    }

    @Test
    @DisplayName("FAILED + error 필드 없음 → 디폴트 메시지로 error 이벤트 발행.")
    void tickFailedWithoutErrorFieldUsesDefaultMessage() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);
        Map<String, Object> payload = Map.of("status", "FAILED");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());
        verify(emitter).complete();
        verify(future).cancel(false);
        verifyNoInteractions(resultWriter);
    }

    @Test
    @DisplayName("COMPLETED 후 lastStatus=COMPLETED 로 갱신되어 이후 polling 에서 dedup 된다.")
    void tickCompletedUpdatesLastStatus() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);

        Map<String, Object> result = Map.of(
                "widget_type", "KPI",
                "title", "test",
                "config", Map.of(),
                "data", Map.of(),
                "insight_text", "ok"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "COMPLETED");
        payload.put("dashboardId", 5);
        payload.put("tenantId", TENANT_ID);
        payload.put("inputText", "test");
        payload.put("result", result);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));
        when(resultWriter.persist(eq(TENANT_ID), eq(5L), eq("test"), any()))
                .thenReturn(new DashboardQueryResultWriter.InsertedIds(1L, 1L));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        assertThat(lastStatus.get()).isEqualTo(AiQueryStatus.COMPLETED);
    }

    @Test
    @DisplayName("Redis 페이로드 파싱 실패 등 예외 발생 시 emitter.completeWithError + future cancel.")
    void tickExceptionInBranch() {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn("not-a-json");

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).completeWithError(any(Throwable.class));
        verify(future).cancel(false);
    }

    @Test
    @DisplayName("COMPLETED payload 에 dashboardId 누락 → error 이벤트 + emitter complete.")
    void tickCompletedWithMissingDashboardIdSendsError() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "COMPLETED");
        payload.put("inputText", "test");
        payload.put("result", Map.of("widget_type", "KPI", "title", "t"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
        verifyNoInteractions(resultWriter);
    }

    @Test
    @DisplayName("COMPLETED payload 에 tenantId 누락 → error 이벤트 + emitter complete.")
    void tickCompletedWithMissingTenantIdSendsError() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "COMPLETED");
        payload.put("dashboardId", 5);
        payload.put("inputText", "test");
        payload.put("result", Map.of("widget_type", "KPI", "title", "t"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
        verifyNoInteractions(resultWriter);
    }

    @Test
    @DisplayName("COMPLETED payload 에 result 누락 → error 이벤트 + emitter complete.")
    void tickCompletedWithMissingResultSendsError() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "COMPLETED");
        payload.put("dashboardId", 5);
        payload.put("tenantId", TENANT_ID);
        payload.put("inputText", "test");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
        verifyNoInteractions(resultWriter);
    }

    @Test
    @DisplayName("Redis 에 알 수 없는 status 문자열 → error 이벤트 + emitter complete.")
    void tickUnknownStatusSendsError() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<AiQueryStatus> lastStatus = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(future);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(
                objectMapper.writeValueAsString(Map.of("status", "UNKNOWN_STATUS")));

        invokeTick(emitter, lastStatus, futureRef, DEADLINE_NEVER);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
    }

    private void stubRedisStatus(String status, Map<String, Object> result) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("dashboardId", 5);
        payload.put("tenantId", TENANT_ID);
        payload.put("inputText", "test");
        payload.put("userId", USER_ID);
        if (result != null) {
            payload.put("result", result);
        }
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(payload));
    }

    private void invokeTick(SseEmitter emitter,
                            AtomicReference<AiQueryStatus> lastStatus,
                            AtomicReference<ScheduledFuture<?>> futureRef,
                            long deadline) {
        ReflectionTestUtils.invokeMethod(
                service, "tick", TRACE_ID, emitter, lastStatus, futureRef, deadline);
    }
}
