package com.ssafy.fint.domain.ai.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

import java.util.Map;

public record NextActionDetailResponse(
        Long suggestionId,
        String title,
        String category,
        int successProbability,
        double importanceScore,
        Map<String, Object> sources,
        String recommendedScript
) {

    @SuppressWarnings("unchecked")
    public static NextActionDetailResponse from(AiSuggestion suggestion) {
        Map<String, Object> reason = suggestion.getReason();
        return new NextActionDetailResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getTitle(),
                suggestion.getCategory(),
                suggestion.getSuccessProbability(),
                suggestion.getImportanceScore(),
                (Map<String, Object>) reason.get("sources"),
                (String) reason.get("recommendedScript")
        );
    }
}
