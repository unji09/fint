package com.ssafy.fint.domain.activity.dto;

import java.util.List;

public record SttCallbackRequest(
    Long accountId,
    List<Segment> segments
) {
    public record Segment(
        String speakerId,
        String text,
        Integer startMs,
        Integer endMs
    ) {}
}
