package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.dto.NextActionDetailResponse;
import com.ssafy.fint.domain.ai.dto.NextActionListResponse;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.AiErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSuggestionService {

    private final AccountRepository accountRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

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
