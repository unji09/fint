package com.ssafy.fint.domain.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordingUpdateRequest(
        @NotBlank @Size(max = 300) String title
) {}
