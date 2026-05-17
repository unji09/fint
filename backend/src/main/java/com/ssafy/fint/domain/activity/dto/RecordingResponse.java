package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Recording;

import java.time.OffsetDateTime;
import java.util.Map;

public record RecordingResponse(
    Long recordingId,
    Long activityId,
    String fileKey,
    String title,
    int duration,
    String sttStatus,
    Map<String, Object> transcript,
    OffsetDateTime createdAt
) {
    public static RecordingResponse from(Recording recording) {
        return new RecordingResponse(
            recording.getRecordingId(),
            recording.getActivity().getActivityId(),
            recording.getFileKey(),
            recording.getTitle(),
            recording.getDuration(),
            recording.getSttStatus().name(),
            recording.getTranscript(),
            recording.getCreatedAt()
        );
    }
}
