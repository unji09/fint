package com.ssafy.fint.domain.dashboard.dto;

import jakarta.validation.constraints.NotNull;

public record WidgetBatchCreateRequest(
        @NotNull(message = "templateGroupId 는 필수입니다.")
        Long templateGroupId
) {
}
