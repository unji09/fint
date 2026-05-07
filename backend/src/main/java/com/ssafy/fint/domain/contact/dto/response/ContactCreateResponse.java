package com.ssafy.fint.domain.contact.dto.response;

import com.ssafy.fint.domain.account.entity.Contact;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

@Getter
@Builder
public class ContactCreateResponse {

    private Long contactId;
    private Long accountId;
    private String name;
    private String title;
    private String phone;
    private String email;
    private String personality;
    private OffsetDateTime createdAt;

    public static ContactCreateResponse from(Contact contact) {
        return ContactCreateResponse.builder()
            .contactId(contact.getContactId())
            .accountId(contact.getAccount().getAccountId())
            .name(contact.getName())
            .title(contact.getTitle())
            .phone(contact.getPhone())
            .email(contact.getEmail())
            .personality(contact.getPersonality())
            .createdAt(contact.getCreatedAt())
            .build();
    }
}
