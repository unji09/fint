package com.ssafy.fint.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private static final String REDIS_KEY_PREFIX = "dashboard:query:";
    private static final Duration PENDING_STATE_TTL = Duration.ofSeconds(600);
    private static final String STATUS_PENDING = "PENDING";
    private static final String ACTION_ADD = "ADD";

    private final DashboardRepository dashboardRepository;
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
        registerPendingState(traceId, dashboardId, request.inputText(), me);
        queryDispatcher.dispatch(buildDispatchCommand(traceId, dashboardId, request.inputText(), me));

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

    private DashboardQueryDispatchCommand buildDispatchCommand(
            String traceId, Long dashboardId, String inputText, CustomUserDetails me) {
        // 본 엔드포인트(/dashboards/{id}/queries)는 기존 대시보드에 위젯을 추가하는 ADD 케이스.
        // existing_widgets 의 element 스키마는 AI 측과 합의 전이므로 빈 리스트로 송신한다.
        return new DashboardQueryDispatchCommand(
                traceId,
                ACTION_ADD,
                inputText,
                dashboardId,
                me.getTenantId(),
                me.getUserId(),
                Collections.emptyList(),
                null
        );
    }
}
