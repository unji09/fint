package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityListResponse(
        List<Item> content,
        long totalElements
) {

    public static ActivityListResponse from(Page<Activity> page) {
        List<Item> items = page.getContent().stream().map(Item::from).toList();
        return new ActivityListResponse(items, page.getTotalElements());
    }

    public record Item(
            Long activityId,
            ActivityType type,
            String title,
            OffsetDateTime startAt,
            String memo,
            PipelineStageResponse pipelineStage,
            Long dealId
    ) {

        public static Item from(Activity activity) {
            return new Item(
                    activity.getActivityId(),
                    activity.getType(),
                    activity.getTitle(),
                    activity.getStartAt(),
                    activity.getMemo(),
                    PipelineStageResponse.from(activity.getPipelineStage()),
                    activity.getDeal() == null ? null : activity.getDeal().getDealId()
            );
        }
    }

    public record PipelineStageResponse(
            Long stageId,
            String stageName
    ) {

        public static PipelineStageResponse from(com.ssafy.fint.domain.deal.entity.PipelineStage stage) {
            if (stage == null) {
                return null;
            }
            return new PipelineStageResponse(stage.getPipelineStageId(), stage.getName());
        }
    }
}
