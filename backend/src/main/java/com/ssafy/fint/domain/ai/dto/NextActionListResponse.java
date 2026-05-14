package com.ssafy.fint.domain.ai.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

public record NextActionListResponse(
        Long suggestionId,
        String title,
        String category,
        int successProbability,
        double importanceScore
) {

    public static NextActionListResponse from(AiSuggestion suggestion) {
        return new NextActionListResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getTitle(),
                suggestion.getCategory(),
                suggestion.getSuccessProbability(),
                suggestion.getImportanceScore()
        );
    }
}
