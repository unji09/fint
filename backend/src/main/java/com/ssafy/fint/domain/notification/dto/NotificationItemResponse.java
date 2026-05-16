package com.ssafy.fint.domain.notification.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

import java.time.OffsetDateTime;
import java.util.Map;

public record NotificationItemResponse(
        Long notificationId,
        String title,
        String category,
        Map<String, Object> sources,
        String pipelineStage,
        String accountName,
        boolean isRead,
        OffsetDateTime createdAt
) {

    @SuppressWarnings("unchecked")
    public static NotificationItemResponse from(AiSuggestion suggestion) {
        Map<String, Object> reason = suggestion.getReason();
        return new NotificationItemResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getTitle(),
                suggestion.getCategory(),
                (Map<String, Object>) reason.get("sources"),
                suggestion.getPipelineStage().getName(),
                suggestion.getAccount().getName(),
                suggestion.isRead(),
                suggestion.getCreatedAt()
        );
    }
}
