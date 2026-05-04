package com.ssafy.fint.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

    @NotBlank(message = "회사 코드는 필수입니다.")
    String companyCode,

    @NotBlank(message = "사번은 필수입니다.")
    String empNo,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {}
