package com.ssafy.fint.domain.deal.dto;

import com.ssafy.fint.domain.deal.entity.Deal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DealCreateResponse(
        Long dealId,
        Long accountId,
        Long teamId,
        String title,
        LocalDate expectedClose,
        BigDecimal amount,
        Short probability,
        OffsetDateTime createdAt
) {

    public static DealCreateResponse from(Deal deal) {
        return new DealCreateResponse(
                deal.getDealId(),
                deal.getAccount().getAccountId(),
                deal.getTeam() == null ? null : deal.getTeam().getTeamId(),
                deal.getTitle(),
                deal.getExpectedClose(),
                deal.getAmount(),
                deal.getProbability(),
                deal.getCreatedAt()
        );
    }
}
