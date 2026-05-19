package com.ssafy.fint.domain.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DealListResponse(
        List<DealSummary> data,
        boolean hasNext
) {

    public record DealSummary(
            Long dealId,
            String title,
            BigDecimal amount,
            LocalDate expectedClose,
            List<DealAssignee> assignees
    ) {}

    public record DealAssignee(Long userId, String name) {}
}
