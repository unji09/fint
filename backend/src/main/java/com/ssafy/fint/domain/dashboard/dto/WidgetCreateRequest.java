package com.ssafy.fint.domain.dashboard.dto;

import jakarta.validation.constraints.NotNull;

public record WidgetCreateRequest(
        @NotNull(message = "templateId 는 필수입니다.")
        Long templateId
) {
}
