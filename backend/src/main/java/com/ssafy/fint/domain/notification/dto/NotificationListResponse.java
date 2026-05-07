package com.ssafy.fint.domain.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItemResponse> content
) {
}
