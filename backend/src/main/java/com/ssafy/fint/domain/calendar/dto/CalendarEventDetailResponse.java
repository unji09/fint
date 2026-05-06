package com.ssafy.fint.domain.calendar.dto;

import com.ssafy.fint.domain.activity.dto.Attendees;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.calendar.CalendarEventConstants;
import com.ssafy.fint.domain.deal.dto.PipelineStageResponse;
import com.ssafy.fint.domain.deal.entity.Deal;

import java.time.OffsetDateTime;

public record CalendarEventDetailResponse(
        String eventId,
        String source,
        String title,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String category,
        String accountName,
        Long accountId,
        Long dealId,
        String dealTitle,
        PipelineStageResponse pipelineStage,
        Long linkedActivityId,
        Attendees attendees,
        String memo
) {

    public static CalendarEventDetailResponse from(Activity activity) {
        Deal deal = activity.getDeal();
        return new CalendarEventDetailResponse(
                CalendarEventConstants.EVENT_ID_PREFIX_FINT + activity.getActivityId(),
                CalendarEventConstants.SOURCE_FINT,
                activity.getTitle(),
                activity.getStartAt(),
                activity.getEndAt(),
                categoryOf(activity.getType()),
                deal == null ? null : deal.getAccount().getName(),
                deal == null ? null : deal.getAccount().getAccountId(),
                deal == null ? null : deal.getDealId(),
                deal == null ? null : deal.getTitle(),
                PipelineStageResponse.from(activity.getPipelineStage()),
                activity.getActivityId(),
                Attendees.from(activity.getAttendees()),
                activity.getMemo()
        );
    }

    private static String categoryOf(ActivityType type) {
        return type == null ? null : type.getDisplayName();
    }
}
