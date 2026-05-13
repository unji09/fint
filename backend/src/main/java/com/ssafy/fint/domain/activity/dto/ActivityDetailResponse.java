package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.deal.dto.PipelineStageResponse;

import java.time.OffsetDateTime;
import java.util.Map;

public record ActivityDetailResponse(
        Long activityId,
        ActivityType type,
        String title,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Attendees attendees,
        String memo,
        String sttStatus,
        String moodStatus,
        Map<String, Object> transcript,
        Map<String, Object> summary,
        PipelineStageResponse pipelineStage,
        Long dealId
) {

    public static ActivityDetailResponse from(Activity activity) {
        return new ActivityDetailResponse(
                activity.getActivityId(),
                activity.getType(),
                activity.getTitle(),
                activity.getStartAt(),
                activity.getEndAt(),
                Attendees.from(activity.getAttendees()),
                activity.getMemo(),
                activity.getSttStatus() == null ? null : activity.getSttStatus().name(),
                activity.getMoodStatus() == null ? null : activity.getMoodStatus().name(),
                activity.getTranscript(),
                activity.getSummary(),
                PipelineStageResponse.from(activity.getPipelineStage()),
                activity.getDeal() == null ? null : activity.getDeal().getDealId()
        );
    }
}
