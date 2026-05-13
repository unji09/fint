package com.ssafy.fint.domain.ai.dto;

import jakarta.validation.constraints.NotNull;

public record NextActionCreateRequest(
        @NotNull Long accountId,
        String context
) {}
