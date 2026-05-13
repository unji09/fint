package com.ssafy.fint.domain.notification.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;

import java.util.Map;

public record AiSuggestionCallbackRequest(
        Long accountId,
        Long pipelineStageId,
        String title,
        String content,
        AiSuggestionRelatedType relatedType,
        Map<String, Object> reason
) {
}
