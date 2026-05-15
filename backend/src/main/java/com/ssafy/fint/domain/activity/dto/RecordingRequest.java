package com.ssafy.fint.domain.activity.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordingRequest(
    @NotBlank String fileKey,
    Integer duration,
    String title
) {}
