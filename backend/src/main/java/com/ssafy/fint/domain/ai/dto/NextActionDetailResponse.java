package com.ssafy.fint.domain.ai.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

import java.util.Map;

public record NextActionDetailResponse(
        Long suggestionId,
        String title,
        String category,
        Integer successProbability,
        Map<String, Object> sources,
        String recommendedScript,
        String caution
) {

    @SuppressWarnings("unchecked")
    public static NextActionDetailResponse from(AiSuggestion suggestion) {
        Map<String, Object> reason = suggestion.getReason();
        return new NextActionDetailResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getTitle(),
                (String) reason.get("category"),
                toInt(reason.get("successProbability")),
                (Map<String, Object>) reason.get("sources"),
                (String) reason.get("recommendedScript"),
                (String) reason.get("caution")
        );
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
