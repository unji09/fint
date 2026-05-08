package com.ssafy.fint.domain.dashboard.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 위젯 부분 수정 요청 (드래그/리사이즈/필터/제목 변경 통합 진입).
 * 모든 필드 nullable 이며 하나 이상 제공되어야 한다 (Service 단에서 검증).
 * 제공된 필드만 dirty checking 으로 부분 업데이트된다.
 */
public record WidgetUpdateRequest(
            @Size(max = 100, message = "title 은 100자 이하여야 합니다.")
                String title,

        Map<String, Object> config,

        Map<String, Object> position
) {
}
