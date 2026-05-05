package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
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
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    /**
     * 고객사 등록.
     * 현재 로그인한 사원을 owner로 지정한다. 멀티테넌트 격리는 owner의 tenant를 따른다.
     */
    @Transactional
    public AccountRegisterResponse register(AccountRegisterRequest request) {
        User owner = userRepository.getReferenceById(currentUserId());

        Account account = Account.builder()
                .user(owner)
                .name(request.name())
                .industry(request.industry())
                .bizNo(request.bizNo())
                .build();

        Account saved = accountRepository.save(account);
        log.info("[AccountRegister] accountId={} userId={}", saved.getAccountId(), owner.getUserId());

        return AccountRegisterResponse.of(saved.getAccountId());
    }

    /**
     * 고객사 소프트 삭제.
     * 본인 소유(owner == 현재 사용자) + 같은 tenant 인 account 만 삭제 가능하다.
     * 미존재 / 타 사용자 / 타 테넌트 소유는 모두 NOT_FOUND 로 통일하여 존재 여부 노출을 막는다.
     */
    @Transactional
    public void delete(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        Account account = accountRepository
                .findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(accountId, userId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[AccountDelete] not found. accountId={} userId={} tenantId={}",
                            accountId, userId, tenantId);
                    return new BusinessException(CommonErrorCode.NOT_FOUND);
                });

        account.softDelete();
        log.info("[AccountDelete] accountId={} userId={} tenantId={}", accountId, userId, tenantId);
    }

    private Long currentUserId() {
        return currentPrincipal().getUserId();
    }

    private Long currentTenantId() {
        return currentPrincipal().getTenantId();
    }

    private CustomUserDetails currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return me;
    }
}
