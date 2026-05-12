package com.ssafy.fint.domain.notification.service;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.notification.dto.NotificationItemResponse;
import com.ssafy.fint.domain.notification.dto.NotificationListResponse;
import com.ssafy.fint.domain.notification.dto.NotificationReadAllResponse;
import com.ssafy.fint.domain.notification.exception.NotificationErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int UNREAD_LIMIT = 10;

    private final AiSuggestionRepository aiSuggestionRepository;

    public NotificationListResponse findUnreadNotifications(CustomUserDetails me) {
        List<NotificationItemResponse> items = aiSuggestionRepository
                .findUnreadByUserId(me.getUserId(), PageRequest.of(0, UNREAD_LIMIT))
                .stream()
                .map(NotificationItemResponse::from)
                .toList();
        return new NotificationListResponse(items);
    }

    @Transactional
    public NotificationReadAllResponse markAllAsRead(CustomUserDetails me) {
        int count = aiSuggestionRepository.markAllAsReadByUserId(me.getUserId());
        return new NotificationReadAllResponse(count);
    }

    @Transactional
    public void markAsRead(Long notificationId, CustomUserDetails me) {
        AiSuggestion suggestion = aiSuggestionRepository
                .findByIdAndUserId(notificationId, me.getUserId())
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        suggestion.markAsRead();
    }
}
