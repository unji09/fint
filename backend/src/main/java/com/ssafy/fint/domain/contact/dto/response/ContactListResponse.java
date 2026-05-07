package com.ssafy.fint.domain.contact.dto.response;

import com.ssafy.fint.domain.account.entity.Contact;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContactListResponse {

    private Long contactId;
    private String name;
    private String title;
    private String phone;
    private String email;
    private String personality;

    public static ContactListResponse from(Contact contact) {
        return ContactListResponse.builder()
            .contactId(contact.getContactId())
            .name(contact.getName())
            .title(contact.getTitle())
            .phone(contact.getPhone())
            .email(contact.getEmail())
            .personality(contact.getPersonality())
            .build();
    }
}
