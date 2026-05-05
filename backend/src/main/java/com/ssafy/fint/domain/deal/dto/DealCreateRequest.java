package com.ssafy.fint.domain.deal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DealCreateRequest(

        @NotNull(message = "accountId 는 필수입니다.")
        Long accountId,

        Long teamId,

        @NotBlank(message = "title 은 필수입니다.")
        String title,

        LocalDate expectedClose,

        BigDecimal amount
) {
}
