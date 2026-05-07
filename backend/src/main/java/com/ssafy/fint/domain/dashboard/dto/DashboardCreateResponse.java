package com.ssafy.fint.domain.dashboard.dto;

/**
 * 대시보드 생성 응답.
 * inputText 가 함께 들어와 자연어 처리가 트리거된 경우에만 traceId 가 채워진다.
 */
public record DashboardCreateResponse(
        Long dashboardId,
        String traceId
) {
}
