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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSuggestionService {

    private final AccountRepository accountRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final NextActionClient nextActionClient;
    private final NotificationService notificationService;

    @Transactional
    public NextActionCreateResponse createNextAction(CustomUserDetails me, NextActionCreateRequest request) {
        Long tenantId = me.getTenantId();
        Long accountId = request.accountId();

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        NextActionAiResponse aiResponse = nextActionClient.generate(tenantId, accountId, request.context());

        PipelineStage stage = pipelineStageRepository
                .findByPipelineStageIdAndTenant_TenantId(aiResponse.pipelineStageId(), tenantId)
                .orElseThrow(() -> new BusinessException(AiErrorCode.INVALID_AI_RESPONSE));

        Map<String, Object> reason = new HashMap<>();
        reason.put("category", aiResponse.category());
        reason.put("successProbability", aiResponse.successProbability());
        reason.put("sources", aiResponse.sources());
        reason.put("recommendedScript", aiResponse.recommendedScript());
        reason.put("risk", aiResponse.risk());

        AiSuggestion suggestion = AiSuggestion.builder()
                .account(account)
                .pipelineStage(stage)
                .title(aiResponse.title())
                .content(aiResponse.description())
                .relatedType(AiSuggestionRelatedType.ACCOUNT)
                .reason(reason)
                .build();

        AiSuggestion saved = aiSuggestionRepository.save(suggestion);

        notificationService.pushNotification(saved);

        return NextActionCreateResponse.from(saved);
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
}
