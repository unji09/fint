package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Dashboard Query", description = "대시보드 자연어 쿼리 API")
public interface DashboardQuerySwagger {

    @Operation(
            summary = "자연어 쿼리 시작",
            description = "자연어 질의 처리를 비동기로 시작하고 traceId 를 즉시 반환한다. "
                    + "이후 진행 상태는 SSE(/dashboards/queries/{traceId}/stream) 로 push 된다."
    )
    ApiResponse<QueryStartResponse> start(CustomUserDetails me, Long dashboardId, QueryStartRequest request);

    @Operation(
            summary = "자연어 쿼리 진행 SSE 구독",
            description = "traceId 기준으로 처리 진행 상태를 server-sent events 로 push 한다. "
                    + "단계 완료 시마다 progress 이벤트(총 4번), 처리 완료 시 complete 이벤트, "
                    + "실패 시 error 이벤트를 발행하고 SSE 를 종료한다. polling 30초 timeout."
    )
    SseEmitter stream(String traceId);
}
