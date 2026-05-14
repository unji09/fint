package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Activity;

public record RecordingResponse(
    Long activityId,
    String sttStatus,
    String moodStatus
) {
    public static RecordingResponse from(Activity activity) {
        return new RecordingResponse(
            activity.getActivityId(),
            activity.getSttStatus().name(),
            activity.getMoodStatus().name()
        );
    }
}
