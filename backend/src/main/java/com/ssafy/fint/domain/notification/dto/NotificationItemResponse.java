package com.ssafy.fint.domain.notification.dto;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;

import java.time.OffsetDateTime;
import java.util.Map;

public record NotificationItemResponse(
        Long notificationId,
        String title,
        String category,
        String signalSummary,
        String signalTypeBadge,
        String pipelineStage,
        String accountName,
        boolean isRead,
        OffsetDateTime createdAt
) {

    public static NotificationItemResponse from(AiSuggestion suggestion) {
        Map<String, Object> reason = suggestion.getReason();
        return new NotificationItemResponse(
                suggestion.getAiSuggestionId(),
                suggestion.getTitle(),
                (String) reason.get("activityType"),
                (String) reason.get("signalSummary"),
                (String) reason.get("signalTypeBadge"),
                suggestion.getPipelineStage().getName(),
                suggestion.getAccount().getName(),
                suggestion.isRead(),
                suggestion.getCreatedAt()
        );
    }
}
