package com.ssafy.fint.domain.contact.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContactCreateRequest {

    @NotNull(message = "accountId는 필수입니다.")
    private Long accountId;

    @NotBlank(message = "담당자명은 필수입니다.")
    private String name;

    private String title;
    private String phone;
    private String email;
    private String personality;
}
