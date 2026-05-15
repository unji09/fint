package com.ssafy.fint.domain.mood.dto;

import java.util.List;
import java.util.Map;
public record MoodCallbackRequest(
    Long accountId,
    Integer moodScore,
    String reason,
    List<String> keySignals,
    Map<String, Object>summary
) {

}
