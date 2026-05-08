package com.ssafy.fint.domain.dashboard.controller;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.domain.dashboard.dto.WidgetUpdateRequest;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Dashboard", description = "대시보드 CRUD API")
public interface DashboardSwagger {

    @Operation(
            summary = "대시보드 생성 (빈/템플릿/자연어 통합 진입)",
            description = "title 만 입력 시 빈 대시보드, templateId 입력 시 템플릿 그룹(위젯 8개)을 카피, "
                    + "inputText 입력 시 빈 대시보드 생성 후 자연어 쿼리 시작 흐름으로 이어져 traceId 가 응답에 포함된다. "
                    + "templateId 와 inputText 는 동시 입력하지 않는 운영 시나리오를 가정한다."
    )
    ApiResponse<DashboardCreateResponse> create(CustomUserDetails me, DashboardCreateRequest request);

    @Operation(
            summary = "위젯 수정 (드래그/리사이즈/필터/제목 통합)",
            description = "title/config/position 중 제공된 필드만 부분 업데이트. 셋 다 null 이면 400. "
                    + "자연어 쿼리로 새로 생성된 위젯의 위치/크기를 사용자가 처음 배치할 때도 본 API 를 활용한다."
    )
    ResponseEntity<Void> updateWidget(
            CustomUserDetails me,
            Long dashboardId,
            Long widgetId,
            WidgetUpdateRequest request
    );
}
