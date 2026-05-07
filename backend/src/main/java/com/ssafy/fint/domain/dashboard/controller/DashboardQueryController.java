package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.service.DashboardQueryService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboards/{dashboardId}/queries")
@RequiredArgsConstructor
public class DashboardQueryController implements DashboardQuerySwagger {

    private final DashboardQueryService dashboardQueryService;

    @Override
    @PostMapping
    public ApiResponse<QueryStartResponse> start(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dashboardId,
            @Valid @RequestBody QueryStartRequest request
    ) {
        return ApiResponse.ok(dashboardQueryService.start(me, dashboardId, request));
    }
}
