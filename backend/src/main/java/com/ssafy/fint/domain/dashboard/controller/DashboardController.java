package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.domain.dashboard.service.DashboardService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboards")
@RequiredArgsConstructor
public class DashboardController implements DashboardSwagger {

    private final DashboardService dashboardService;

    @Override
    @PostMapping
    public ApiResponse<DashboardCreateResponse> create(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody DashboardCreateRequest request
    ) {
        return ApiResponse.created(dashboardService.create(me, request));
    }
}
