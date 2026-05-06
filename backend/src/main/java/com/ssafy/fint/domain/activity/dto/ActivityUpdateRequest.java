package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.ActivityType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ActivityUpdateRequest(
        ActivityType type,
        String title,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        List<Map<String, Object>> attendees,
        Long pipelineStageId,
        Long dealId,
        String memo
) {
}
