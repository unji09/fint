package com.ssafy.fint.domain.contact.dto.response;

import com.ssafy.fint.domain.account.entity.Contact;

import java.time.OffsetDateTime;

public record ContactOcrInitResponse(
        Long contactId,
        Long accountId,
        String name,
        String title,
        String phone,
        String email,
        OffsetDateTime createdAt
) {

    public static ContactOcrInitResponse from(Contact contact) {
        return new ContactOcrInitResponse(
                contact.getContactId(),
                contact.getAccount().getAccountId(),
                contact.getName(),
                contact.getTitle(),
                contact.getPhone(),
                contact.getEmail(),
                contact.getCreatedAt()
        );
    }
}