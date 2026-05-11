package com.ssafy.fint.domain.dashboard.dto;

import jakarta.validation.constraints.Size;

public record DashboardUpdateRequest(
        @Size(max = 100)
        String title,

        @Size(max = 300)
        String thumbnailUrl
) {
}
