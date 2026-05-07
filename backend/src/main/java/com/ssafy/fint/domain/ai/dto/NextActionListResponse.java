package com.ssafy.fint.domain.ai.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

import java.util.Map;

public record NextActionListResponse(
        Long suggestionId,
        String title,
        String category,
        Integer successProbability
) {

    public static NextActionListResponse from(AiSuggestion suggestion) {
        Map<String, Object> reason = suggestion.getReason();
        return new NextActionListResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getTitle(),
                (String) reason.get("category"),
                toInt(reason.get("successProbability"))
        );
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
