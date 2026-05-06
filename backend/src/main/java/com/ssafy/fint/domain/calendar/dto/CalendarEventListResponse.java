package com.ssafy.fint.domain.calendar.dto;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.deal.dto.PipelineStageResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;

public record CalendarEventListResponse(
        List<Item> content,
        long totalElements
) {

    public static CalendarEventListResponse from(Page<Activity> page) {
        List<Item> items = page.getContent().stream().map(Item::fromActivity).toList();
        return new CalendarEventListResponse(items, page.getTotalElements());
    }

    public record Item(
            String eventId,
            String source,
            String title,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String category,
            String accountName,
            Long accountId,
            PipelineStageResponse pipelineStage,
            Long linkedActivityId
    ) {

        private static final String SOURCE_FINT = "FINT";
        private static final String EVENT_ID_PREFIX_FINT = "act-";

        public static Item fromActivity(Activity activity) {
            Deal deal = activity.getDeal();
            return new Item(
                    EVENT_ID_PREFIX_FINT + activity.getActivityId(),
                    SOURCE_FINT,
                    activity.getTitle(),
                    activity.getStartAt(),
                    activity.getEndAt(),
                    categoryOf(activity.getType()),
                    deal == null ? null : deal.getAccount().getName(),
                    deal == null ? null : deal.getAccount().getAccountId(),
                    PipelineStageResponse.from(activity.getPipelineStage()),
                    activity.getActivityId()
            );
        }

        private static String categoryOf(ActivityType type) {
            return type == null ? null : type.getDisplayName();
        }
    }
}
