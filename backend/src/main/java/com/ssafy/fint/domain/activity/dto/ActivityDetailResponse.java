package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.deal.dto.PipelineStageResponse;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
                activity.getTranscript(),
                activity.getSummary(),
                PipelineStageResponse.from(activity.getPipelineStage()),
                activity.getDeal() == null ? null : activity.getDeal().getDealId()
        );
    }

    public record Attendees(
            List<String> internal,
            List<String> external
    ) {

        private static final String EXTERNAL = "external";
        private static final String KEY_TYPE = "type";
        private static final String KEY_NAME = "name";

        public static Attendees from(List<Map<String, Object>> raw) {
            List<String> internal = new ArrayList<>();
            List<String> external = new ArrayList<>();
            if (raw == null) {
                return new Attendees(internal, external);
            }
            for (Map<String, Object> entry : raw) {
                if (entry == null) {
                    continue;
                }
                Object name = entry.get(KEY_NAME);
                if (name == null) {
                    continue;
                }
                Object type = entry.get(KEY_TYPE);
                if (type != null && EXTERNAL.equals(type.toString())) {
                    external.add(name.toString());
                } else {
                    internal.add(name.toString());
                }
            }
            return new Attendees(internal, external);
        }
    }
}
