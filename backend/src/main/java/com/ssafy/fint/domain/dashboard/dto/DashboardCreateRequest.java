package com.ssafy.fint.domain.dashboard.dto;

import jakarta.validation.constraints.Size;

/**
 * 대시보드 생성 요청. 모든 필드 nullable — 빈/템플릿/자연어 통합 진입.
 *
 * <ul>
 *   <li>{@code title} : 대시보드 제목. 미입력 시 "제목없음" 으로 저장.</li>
 *   <li>{@code templateId} : 템플릿 그룹 번호. 그룹 1개당 위젯 8개씩 묶여 있으며
 *       템플릿 그룹 N → dashboard_templates 의 ID (N-1)*8+1 ~ N*8 범위 위젯이
 *       대시보드의 위젯으로 카피된다. (그룹 크기는 추후 변동 가능 상수)</li>
 *   <li>{@code inputText} : 자연어 입력. 있으면 빈 대시보드 생성 후 자연어 쿼리 시작 API
 *       흐름으로 이어져 traceId 가 응답에 포함된다.</li>
 * </ul>
 */
public record DashboardCreateRequest(
        @Size(max = 100, message = "title 은 100자 이하여야 합니다.")
        String title,

        Long templateId,

        @Size(max = 500, message = "inputText 는 500자 이하여야 합니다.")
        String inputText
) {
}
