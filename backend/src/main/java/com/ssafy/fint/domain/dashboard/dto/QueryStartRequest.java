package com.ssafy.fint.domain.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryStartRequest(
        @NotBlank(message = "inputText 는 필수입니다.")
        @Size(max = 500, message = "inputText 는 500자 이하여야 합니다.")
        String inputText,
        Long widgetId  // optional — non-null means MODIFY existing widget
) {
}
