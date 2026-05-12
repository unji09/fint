package com.ssafy.fint.domain.mood.dto;

import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import com.ssafy.fint.domain.mood.MoodStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record MoodAnalysisResponse (
    Long activityId,
    String moodStatus,
    String mood,
    Integer moodScore,
    String reason,
    List<String> keySignals,
    OffsetDateTime analyzedAt
    ) {

    public static MoodAnalysisResponse pending(Long activityId, MoodStatus moodStatus){
        return new MoodAnalysisResponse(
            activityId,
            moodStatus.name(),
            null,
            null,
            null,
            null,
            null
        );
    }

    public static MoodAnalysisResponse from(Long activityId, TemperatureHistory history) {
        return new MoodAnalysisResponse(
            activityId,
            MoodStatus.COMPLETED.name(),
            history.getMood().name(),
            history.getMoodScore(),
            history.getReason(),
            history.getKeySignals(),
            history.getCreatedAt()
        );
    }

    public static MoodAnalysisResponse failed(Long activityId) {
        return new MoodAnalysisResponse(
            activityId,
            MoodStatus.FAILED.name(),
            null,
            null,
            null,
            null,
            null
        );
    }
}
