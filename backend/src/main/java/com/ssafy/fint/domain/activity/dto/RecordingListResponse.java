package com.ssafy.fint.domain.activity.dto;

import com.ssafy.fint.domain.activity.entity.Recording;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RecordingListResponse(List<Item> recordings) {

    public record Item(
        Long recordingId,
        String fileKey,
        String title,
        int duration,
        String sttStatus,
        Map<String, Object> transcript,
        OffsetDateTime createdAt
    ) {
        public static Item from(Recording r) {
            return new Item(
                r.getRecordingId(),
                r.getFileKey(),
                r.getTitle(),
                r.getDuration(),
                r.getSttStatus().name(),
                r.getTranscript(),
                r.getCreatedAt()
            );
        }
    }

    public static RecordingListResponse from(List<Recording> recordings) {
        return new RecordingListResponse(recordings.stream().map(Item::from).toList());
    }
}
