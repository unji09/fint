package com.ssafy.fint.domain.deal.dto;

import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.deal.entity.Deal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DealCreateResponse(
        Long dealId,
        Long accountId,
        Long teamId,
        String title,
        LocalDate expectedClose,
        BigDecimal amount,
        Short probability,
        List<ContactDetail> contacts,
        OffsetDateTime createdAt
) {

    public record ContactDetail(
            Long contactId,
            String name,
            String title,
            String phone,
            String email,
            String personality
    ) {
        public static ContactDetail from(Contact contact) {
            return new ContactDetail(
                    contact.getContactId(),
                    contact.getName(),
                    contact.getTitle(),
                    contact.getPhone(),
                    contact.getEmail(),
                    contact.getPersonality()
            );
        }
    }

    public static DealCreateResponse from(Deal deal, List<ContactDetail> contacts) {
        return new DealCreateResponse(
                deal.getDealId(),
                deal.getAccount().getAccountId(),
                deal.getTeam() == null ? null : deal.getTeam().getTeamId(),
                deal.getTitle(),
                deal.getExpectedClose(),
                deal.getAmount(),
                deal.getProbability(),
                contacts,
                deal.getCreatedAt()
        );
    }
}
