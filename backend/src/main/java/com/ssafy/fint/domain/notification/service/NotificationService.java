package com.ssafy.fint.domain.notification.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.notification.dto.AiSuggestionCallbackRequest;
import com.ssafy.fint.domain.notification.dto.NotificationItemResponse;
import com.ssafy.fint.domain.notification.dto.NotificationListResponse;
import com.ssafy.fint.domain.notification.dto.NotificationReadAllResponse;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.exception.NotificationErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int UNREAD_LIMIT = 10;
    private static final String WS_DESTINATION = "/queue/notifications";

    private final AiSuggestionRepository aiSuggestionRepository;
    private final AccountRepository accountRepository;
    private final AccountUserAssignmentRepository accountUserAssignmentRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void receiveAiSuggestion(AiSuggestionCallbackRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        PipelineStage pipelineStage = pipelineStageRepository.findById(request.pipelineStageId())
                .orElseThrow(() -> new BusinessException(DealErrorCode.PIPELINE_STAGE_NOT_FOUND));

        AiSuggestion suggestion = AiSuggestion.builder()
                .account(account)
                .pipelineStage(pipelineStage)
                .title(request.title())
                .content(request.content())
                .relatedType(request.relatedType())
                .reason(request.reason())
                .build();

        aiSuggestionRepository.save(suggestion);

        NotificationItemResponse payload = NotificationItemResponse.from(suggestion);

        List<Long> targetUserIds = accountUserAssignmentRepository
                .findByAccountIdWithUser(account.getAccountId())
                .stream()
                .map(aua -> aua.getUser().getUserId())
                .toList();

        for (Long userId : targetUserIds) {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(), WS_DESTINATION, payload);
        }

        log.info("[AiSuggestionCallback] suggestionId={} accountId={} targetUsers={}",
                suggestion.getAiSuggestionId(), request.accountId(), targetUserIds.size());
    }

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
