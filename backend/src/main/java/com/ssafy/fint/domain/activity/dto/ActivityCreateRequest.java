package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ActivityCreateRequest(

        Long dealId,

        @NotNull(message = "type 은 필수입니다.")
        ActivityType type,

        @NotBlank(message = "title 은 필수입니다.")
        String title,

        @NotNull(message = "startAt 은 필수입니다.")
        OffsetDateTime startAt,

        @NotNull(message = "endAt 은 필수입니다.")
        OffsetDateTime endAt,

        List<Map<String, Object>> attendees,

        Long pipelineStageId,

        String memo
) {
}
