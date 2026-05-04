package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
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

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return me.getUserId();
    }
}
