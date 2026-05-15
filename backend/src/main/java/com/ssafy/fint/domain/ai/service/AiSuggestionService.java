package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.client.NextActionAiResponse;
import com.ssafy.fint.domain.ai.client.NextActionClient;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.dto.NextActionCreateResponse;
import com.ssafy.fint.domain.ai.dto.NextActionDetailResponse;
import com.ssafy.fint.domain.ai.dto.NextActionListResponse;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
import com.ssafy.fint.domain.ai.entity.TriggerType;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.notification.service.NotificationService;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.AiErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSuggestionService {

    private static final double URGENT_NOTIFICATION_THRESHOLD = 80.0;

    private final AccountRepository accountRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final NextActionClient nextActionClient;
    private final NotificationService notificationService;

    @Transactional
    public List<NextActionCreateResponse> createNextAction(CustomUserDetails me, NextActionCreateRequest request) {
        Long tenantId = me.getTenantId();
        Long accountId = request.accountId();

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        List<NextActionAiResponse> aiResponses = nextActionClient.generate(tenantId, request);

        List<NextActionCreateResponse> results = new ArrayList<>();

        for (NextActionAiResponse aiResponse : aiResponses) {
            PipelineStage stage = pipelineStageRepository
                    .findByPipelineStageIdAndTenant_TenantId(aiResponse.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(AiErrorCode.INVALID_AI_RESPONSE));

            AiSuggestionRelatedType relatedType = resolveRelatedType(aiResponse, request);
            String category = aiResponse.category() != null ? aiResponse.category() : "GENERAL";
            int successProbability = aiResponse.successProbability() != null ? aiResponse.successProbability() : 0;
            double importanceScore = aiResponse.importanceScore() != null ? aiResponse.importanceScore() : 0.0;

            Map<String, Object> reason = new HashMap<>();
            reason.put("sources", aiResponse.sources());
            reason.put("recommendedScript", aiResponse.recommendedScript());

            AiSuggestion suggestion = AiSuggestion.builder()
                    .account(account)
                    .pipelineStage(stage)
                    .title(aiResponse.action())
                    .content(aiResponse.reason())
                    .relatedType(relatedType)
                    .category(category)
                    .successProbability(successProbability)
                    .importanceScore(importanceScore)
                    .reason(reason)
                    .build();

            AiSuggestion saved = aiSuggestionRepository.save(suggestion);
            if (saved.getImportanceScore() >= URGENT_NOTIFICATION_THRESHOLD) {
                notificationService.pushNotification(saved);
            }
            results.add(NextActionCreateResponse.from(saved));
        }

        return results;
    }

    @Transactional
    public void createNextActionBySystem(Long tenantId, NextActionCreateRequest request) {
        Long accountId = request.accountId();

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        List<NextActionAiResponse> aiResponses = nextActionClient.generate(tenantId, request);

        for (NextActionAiResponse aiResponse : aiResponses) {
            PipelineStage stage = pipelineStageRepository
                    .findByPipelineStageIdAndTenant_TenantId(aiResponse.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(AiErrorCode.INVALID_AI_RESPONSE));

            AiSuggestionRelatedType relatedType = resolveRelatedType(aiResponse, request);
            String category = aiResponse.category() != null ? aiResponse.category() : "GENERAL";
            int successProbability = aiResponse.successProbability() != null ? aiResponse.successProbability() : 0;
            double importanceScore = aiResponse.importanceScore() != null ? aiResponse.importanceScore() : 0.0;

            Map<String, Object> reason = new HashMap<>();
            reason.put("sources", aiResponse.sources());
            reason.put("recommendedScript", aiResponse.recommendedScript());

            AiSuggestion suggestion = AiSuggestion.builder()
                    .account(account)
                    .pipelineStage(stage)
                    .title(aiResponse.action())
                    .content(aiResponse.reason())
                    .relatedType(relatedType)
                    .category(category)
                    .successProbability(successProbability)
                    .importanceScore(importanceScore)
                    .reason(reason)
                    .build();

            AiSuggestion saved = aiSuggestionRepository.save(suggestion);
            if (saved.getImportanceScore() >= URGENT_NOTIFICATION_THRESHOLD) {
                notificationService.pushNotification(saved);
            }
        }
    }

    public List<NextActionListResponse> findNextActions(CustomUserDetails me, Long accountId) {
        Long tenantId = me.getTenantId();
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return aiSuggestionRepository.findAllByAccountIdAndTenantId(accountId, tenantId)
                .stream()
                .map(NextActionListResponse::from)
                .toList();
    }

    public NextActionDetailResponse findNextActionDetail(CustomUserDetails me, Long accountId, Long suggestionId) {
        Long tenantId = me.getTenantId();
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return aiSuggestionRepository.findByIdAndAccountIdAndTenantId(suggestionId, accountId, tenantId)
                .map(NextActionDetailResponse::from)
                .orElseThrow(() -> new BusinessException(AiErrorCode.AI_SUGGESTION_NOT_FOUND));
    }

    private AiSuggestionRelatedType resolveRelatedType(NextActionAiResponse aiResponse, NextActionCreateRequest request) {
        if (aiResponse.relatedType() != null) {
            try {
                return AiSuggestionRelatedType.valueOf(aiResponse.relatedType().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (request.triggerType() == TriggerType.MEETING_CREATED || request.meetingId() != null) {
            return AiSuggestionRelatedType.MEETING;
        }
        return AiSuggestionRelatedType.ACCOUNT;
    }
}
