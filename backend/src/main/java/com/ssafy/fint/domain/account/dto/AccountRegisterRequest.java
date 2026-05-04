package com.ssafy.fint.domain.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountRegisterRequest(

        @NotBlank(message = "고객사명은 필수입니다.")
        String name,

        @NotBlank(message = "업종은 필수입니다.")
        String industry,

        String bizNo
) {
}
