package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.domain.dashboard.dto.DashboardUpdateRequest;
import com.ssafy.fint.domain.dashboard.dto.WidgetUpdateRequest;
import com.ssafy.fint.domain.dashboard.service.DashboardService;
import com.ssafy.fint.domain.dashboard.service.DashboardWidgetService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboards")
@RequiredArgsConstructor
public class DashboardController implements DashboardSwagger {

    private final DashboardService dashboardService;
    private final DashboardWidgetService dashboardWidgetService;

    @Override
    @PostMapping
    public ApiResponse<DashboardCreateResponse> create(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody DashboardCreateRequest request
    ) {
        return ApiResponse.created(dashboardService.create(me, request));
    }

    @Override
    @PatchMapping("/{dashboardId}")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dashboardId,
            @Valid @RequestBody DashboardUpdateRequest request
    ) {
        dashboardService.update(me, dashboardId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{dashboardId}/widgets/{widgetId}")
    public ResponseEntity<Void> updateWidget(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dashboardId,
            @PathVariable Long widgetId,
            @Valid @RequestBody WidgetUpdateRequest request
    ) {
        dashboardWidgetService.update(me, dashboardId, widgetId, request);
        return ResponseEntity.noContent().build();
    }
}
