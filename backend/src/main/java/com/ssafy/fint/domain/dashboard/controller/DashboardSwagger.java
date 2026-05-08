package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Dashboard", description = "대시보드 CRUD API")
public interface DashboardSwagger {

    @Operation(
            summary = "대시보드 생성 (빈/템플릿/자연어 통합 진입)",
            description = "title 만 입력 시 빈 대시보드, templateId 입력 시 템플릿 그룹(위젯 8개)을 카피, "
                    + "inputText 입력 시 빈 대시보드 생성 후 자연어 쿼리 시작 흐름으로 이어져 traceId 가 응답에 포함된다. "
                    + "templateId 와 inputText 는 동시 입력하지 않는 운영 시나리오를 가정한다."
    )
    ApiResponse<DashboardCreateResponse> create(CustomUserDetails me, DashboardCreateRequest request);
}
