package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.service.DashboardQueryService;
import com.ssafy.fint.domain.dashboard.service.DashboardQueryStreamService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/dashboards")
@RequiredArgsConstructor
public class DashboardQueryController implements DashboardQuerySwagger {

    private final DashboardQueryService dashboardQueryService;
    private final DashboardQueryStreamService dashboardQueryStreamService;

    @Override
    @PostMapping("/{dashboardId}/queries")
    public ApiResponse<QueryStartResponse> start(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dashboardId,
            @Valid @RequestBody QueryStartRequest request
    ) {
        return ApiResponse.ok(dashboardQueryService.start(me, dashboardId, request));
    }

    @Override
    @GetMapping(value = "/queries/{traceId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable String traceId
    ) {
        return dashboardQueryStreamService.subscribe(traceId, me.getUserId());
    }
}
