package com.ssafy.fint.domain.ai.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

import java.time.OffsetDateTime;
import java.util.Map;

public record NextActionCreateResponse(
        Long nextActionId,
        Long accountId,
        String accountName,
        String action,
        String reason,
        String category,
        String relatedType,
        double importanceScore,
        int successProbability,
        Map<String, Object> sources,
        String recommendedScript,
        OffsetDateTime createdAt
) {

    @SuppressWarnings("unchecked")
    public static NextActionCreateResponse from(AiSuggestion suggestion) {
        Map<String, Object> reasonMap = suggestion.getReason();
        return new NextActionCreateResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getAccount().getAccountId(),
                suggestion.getAccount().getName(),
                suggestion.getTitle(),
                suggestion.getContent(),
                suggestion.getCategory(),
                suggestion.getRelatedType().name().toLowerCase(),
                suggestion.getImportanceScore(),
                suggestion.getSuccessProbability(),
                (Map<String, Object>) reasonMap.get("sources"),
                (String) reasonMap.get("recommendedScript"),
                suggestion.getCreatedAt()
        );
    }
}
