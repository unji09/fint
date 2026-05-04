package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.deal.dto.PipelineStageResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ActivityCreateResponse(
        Long activityId,
        Long userId,
        ActivityType type,
        String title,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        List<Map<String, Object>> attendees,
        String memo,
        PipelineStageResponse pipelineStage,
        Long dealId
) {

    public static ActivityCreateResponse from(Activity activity) {
        return new ActivityCreateResponse(
                activity.getActivityId(),
                activity.getUser().getUserId(),
                activity.getType(),
                activity.getTitle(),
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getAttendees(),
                activity.getMemo(),
                PipelineStageResponse.from(activity.getPipelineStage()),
                activity.getDeal() == null ? null : activity.getDeal().getDealId()
        );
    }
}
