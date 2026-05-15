package com.ssafy.fint.domain.activity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SttCallbackRequest(
        Long tenantId,
        Long accountId,
        List<Segment> segments
) {
    public record Segment(
            String text,
            @JsonProperty("speaker_id") String speakerId,
            @JsonProperty("start_ms") Integer startMs,
            @JsonProperty("end_ms") Integer endMs
    ) {}
}
