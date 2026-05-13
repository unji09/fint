package com.ssafy.fint.domain.mood.dto;

import java.util.List;

public record MoodCallbackRequest(
    Long accountId,
    Integer moodScore,
    String reason,
    List<String> keySignals
) {

}
