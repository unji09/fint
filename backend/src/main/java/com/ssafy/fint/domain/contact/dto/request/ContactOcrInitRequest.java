package com.ssafy.fint.domain.contact.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContactOcrInitRequest {

    @NotBlank(message = "고객사명은 필수입니다.")
    private String accountName;

    @NotBlank(message = "담당자명은 필수입니다.")
    private String name;

    private String title;
    private String phone;
    private String email;
}
