package com.ssafy.fint.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private static final String REDIS_KEY_PREFIX = "dashboard:query:";
    private static final Duration PENDING_STATE_TTL = Duration.ofSeconds(600);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_FAILED = "FAILED";
    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DashboardQueryDispatcher queryDispatcher;
    private final ObjectMapper objectMapper;

    /**
     * 자연어 쿼리 처리를 시작한다. traceId 발급 → Redis 에 PENDING 상태 등록 → FastAPI 에 위임.
     * 본 시점에 DB INSERT 는 발생하지 않으며, 처리 완료(SSE complete) 시점에 FastAPI 결과를
     * Spring 이 polling 으로 가져와 INSERT 한다 (internal-api-spec.md §2).
     */
    @Transactional(readOnly = true)
    public QueryStartResponse start(CustomUserDetails me, Long dashboardId, QueryStartRequest request) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        String traceId = UUID.randomUUID().toString();
        log.info("[SSE] query start traceId={} dashboardId={} userId={}", traceId, dashboardId, me.getUserId());
        registerPendingState(traceId, dashboardId, request.inputText(), me);

        try {
            queryDispatcher.dispatch(buildDispatchCommand(traceId, dashboardId, request.inputText(), me));
        } catch (Exception e) {
            log.error("[SSE] dispatch failed traceId={}", traceId, e);
            markDispatchFailed(traceId);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        log.info("[SSE] dispatched to FastAPI traceId={}", traceId);
        return new QueryStartResponse(traceId);
    }

    private void registerPendingState(String traceId, Long dashboardId, String inputText, CustomUserDetails me) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_PENDING);
        payload.put("dashboardId", dashboardId);
        payload.put("inputText", inputText);
        payload.put("userId", me.getUserId());
        payload.put("tenantId", me.getTenantId());
        payload.put("requestedAt", OffsetDateTime.now().toString());

        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + traceId,
                    objectMapper.writeValueAsString(payload),
                    PENDING_STATE_TTL
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR, "쿼리 상태 직렬화에 실패했습니다.");
        }
    }

    private void markDispatchFailed(String traceId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", STATUS_FAILED);
            payload.put("error", "AI 서비스 연결에 실패했습니다.");
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + traceId,
                    objectMapper.writeValueAsString(payload),
                    PENDING_STATE_TTL
            );
        } catch (Exception e) {
            log.error("[SSE] Redis FAILED 마킹 실패 traceId={}", traceId, e);
        }
    }

    private DashboardQueryDispatchCommand buildDispatchCommand(
            String traceId, Long dashboardId, String inputText, CustomUserDetails me) {
        // 위젯이 0개면 CREATE, 1개 이상이면 ADD (+ 기존 위젯 컨텍스트를 LLM 에 전달해 중복 회피 유도).
        List<DashboardWidget> widgets = dashboardWidgetRepository.findAllByDashboard_DashboardId(dashboardId);
        boolean hasWidgets = !widgets.isEmpty();
        List<Object> existingWidgets = hasWidgets
                ? widgets.stream().<Object>map(this::toAiPayload).toList()
                : List.of();
        QueryAction action = hasWidgets ? QueryAction.ADD : QueryAction.CREATE;

        return new DashboardQueryDispatchCommand(
                traceId,
                action,
                inputText,
                dashboardId,
                me.getTenantId(),
                me.getUserId(),
                existingWidgets,
                null
        );
    }

    private Map<String, Object> toAiPayload(DashboardWidget widget) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("widget_type", widget.getWidgetType() == null ? null : widget.getWidgetType().name());
        payload.put("title", widget.getTitle());
        payload.put("source_query", widget.getSourceQuery());
        payload.put("config", widget.getConfig());
        return payload;
    }
}
