package com.ssafy.fint.domain.contact.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContactUpdateRequest {

    private String name;
    private String title;
    private String phone;
    private String email;
    private String personality;
}
