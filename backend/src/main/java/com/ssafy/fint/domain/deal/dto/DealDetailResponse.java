package com.ssafy.fint.domain.deal.dto;

import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.deal.entity.Deal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;


public record DealDetailResponse(
        Long dealId,
        String title,
        BigDecimal amount,
        Short probability,
        LocalDate expectedClose,
        OffsetDateTime wonAt,
        OffsetDateTime lostAt,
        String lostReason,
        String currentPipelineStage,
        List<ContactDetail> contacts
) {

    public static DealDetailResponse of(Deal deal, List<ContactDetail> contacts) {
        return new DealDetailResponse(
                deal.getDealId(),
                deal.getTitle(),
                deal.getAmount(),
                deal.getProbability(),
                deal.getExpectedClose(),
                deal.getWonAt(),
                deal.getLostAt(),
                deal.getLostReason(),
                deal.getCurrentPipeline(),
                contacts
        );
    }

    public record ContactDetail(
            Long contactId,
            String name,
            String title,
            String email,
            String phone,
            String personality
    ) {

        public static ContactDetail from(Contact contact) {
            return new ContactDetail(
                    contact.getContactId(),
                    contact.getName(),
                    contact.getTitle(),
                    contact.getEmail(),
                    contact.getPhone(),
                    contact.getPersonality()
            );
        }
    }
}
