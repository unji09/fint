package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.tenant.repository.TeamRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    // TODO(LLM): LLM 기반 수주 확률 계산 도입 시 이 상수를 LLM 호출 결과로 교체.
    private static final short DUMMY_PROBABILITY = 70;

    private final DealRepository dealRepository;
    private final AccountRepository accountRepository;
    private final TeamRepository teamRepository;

    @Transactional
    public DealCreateResponse create(DealCreateRequest request) {
        Long tenantId = currentUser().getTenantId();

        Account account = accountRepository.findByIdAndTenantId(request.accountId(), tenantId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.ACCOUNT_NOT_FOUND));

        Team team = null;
        if (request.teamId() != null) {
            team = teamRepository.findByTeamIdAndTenant_TenantId(request.teamId(), tenantId)
                    .orElseThrow(() -> new BusinessException(DealErrorCode.TEAM_NOT_FOUND));
        }

        Deal deal = Deal.builder()
                .account(account)
                .team(team)
                .title(request.title())
                .expectedClose(request.expectedClose())
                .amount(request.amount())
                .probability(DUMMY_PROBABILITY)
                .build();

        Deal saved = dealRepository.save(deal);
        log.debug("[DealCreate] dealId={} tenantId={} accountId={} teamId={}",
                saved.getDealId(), tenantId, request.accountId(), request.teamId());
        return DealCreateResponse.from(saved);
    }

    private CustomUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return me;
    }
}
