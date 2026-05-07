package com.ssafy.fint.domain.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 모든 필드는 nullable 이며 null 이 아닌 필드만 반영된다.
 *
 * pipelineStageId 는 활동 등록 시 단계 자동 추적을 위해 통합된 필드다.
 */
public record DealUpdateRequest(
        String title,
        BigDecimal amount,
        LocalDate expectedClose,
        String lostReason,
        OffsetDateTime wonAt,
        OffsetDateTime lostAt,
        Long pipelineStageId
) {

    public static DealUpdateRequest pipelineStageOnly(Long pipelineStageId) {
        return new DealUpdateRequest(null, null, null, null, null, null, pipelineStageId);
    }
}
