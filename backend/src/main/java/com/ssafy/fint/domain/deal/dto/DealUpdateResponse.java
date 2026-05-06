package com.ssafy.fint.domain.deal.dto;

import com.ssafy.fint.domain.deal.entity.Deal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DealUpdateResponse(
        Long dealId,
        Long accountId,
        String title,
        BigDecimal amount,
        LocalDate expectedClose,
        String lostReason,
        OffsetDateTime wonAt,
        OffsetDateTime lostAt,
        String currentPipelineStage,
        OffsetDateTime updatedAt
) {

    public static DealUpdateResponse from(Deal deal) {
        return new DealUpdateResponse(
                deal.getDealId(),
                deal.getAccount().getAccountId(),
                deal.getTitle(),
                deal.getAmount(),
                deal.getExpectedClose(),
                deal.getLostReason(),
                deal.getWonAt(),
                deal.getLostAt(),
                deal.getCurrentPipeline(),
                deal.getUpdatedAt()
        );
    }
}
