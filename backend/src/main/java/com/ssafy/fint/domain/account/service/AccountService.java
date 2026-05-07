package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountMoodResponse;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.dto.AccountSearchableResponse;
import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final int DEFAULT_SIGNAL_SIZE = 20;
    private static final int DEFAULT_SEARCHABLE_SIZE = 10;

    private final AccountRepository accountRepository;
    private final AccountUserAssignmentRepository accountUserAssignmentRepository;
    private final AccountExternalInfoRepository accountExternalInfoRepository;
    private final TemperatureHistoryRepository temperatureHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public AccountRegisterResponse register(AccountRegisterRequest request) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();
        User owner = userRepository.getReferenceById(userId);

        Account account;
        if (request.existingAccountId() != null) {
            account = registerToExistingAccount(request.existingAccountId(), userId, tenantId, owner);
        } else {
            account = registerNewAccount(request, owner);
        }

        log.info("[AccountRegister] accountId={} userId={} mode={}",
                account.getAccountId(), userId,
                request.existingAccountId() != null ? "case1" : "case2");
        return AccountRegisterResponse.of(account.getAccountId());
    }

    private Account registerToExistingAccount(Long accountId, Long userId, Long tenantId, User owner) {
        Account account = accountRepository
                .findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[AccountRegister] case1 not found. accountId={} tenantId={}",
                            accountId, tenantId);
                    return new BusinessException(CommonErrorCode.NOT_FOUND);
                });

        boolean alreadyAssigned = accountUserAssignmentRepository
                .existsByAccount_AccountIdAndUser_UserId(account.getAccountId(), userId);
        if (!alreadyAssigned) {
            accountUserAssignmentRepository.save(
                    AccountUserAssignment.builder().account(account).user(owner).build());
        }
        return account;
    }

    private Account registerNewAccount(AccountRegisterRequest request, User owner) {
        if (request.name() == null || request.industry() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Account saved = accountRepository.save(
                Account.builder()
                        .name(request.name())
                        .industry(request.industry())
                        .bizNo(request.bizNo())
                        .build());
        accountUserAssignmentRepository.save(
                AccountUserAssignment.builder().account(saved).user(owner).build());
        return saved;
    }

    @Transactional
    public void update(Long accountId, AccountUpdateRequest request) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        Account account = accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[AccountUpdate] not found. accountId={} userId={} tenantId={}",
                            accountId, userId, tenantId);
                    return new BusinessException(CommonErrorCode.NOT_FOUND);
                });

        if (request.name() != null) account.changeName(request.name());
        if (request.industry() != null) account.changeIndustry(request.industry());
        if (request.bizNo() != null) account.changeBizNo(request.bizNo());

        log.info("[AccountUpdate] accountId={} userId={} tenantId={}", accountId, userId, tenantId);
    }

    @Transactional
    public void delete(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[AccountDelete] not found. accountId={} userId={} tenantId={}",
                            accountId, userId, tenantId);
                    return new BusinessException(CommonErrorCode.NOT_FOUND);
                });

        accountUserAssignmentRepository
                .deleteByAccount_AccountIdAndUser_UserId(accountId, userId);

        log.info("[AccountDelete] accountId={} userId={} tenantId={}", accountId, userId, tenantId);
    }

    public List<AccountSignalResponse> findSignals(Long accountId, String source, Integer size) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        int limit = size != null ? size : DEFAULT_SIGNAL_SIZE;
        return accountExternalInfoRepository
                .findRecentByAccountAndOptionalSource(accountId, source, PageRequest.of(0, limit))
                .stream()
                .map(AccountSignalResponse::from)
                .toList();
    }

    public List<AccountMoodResponse> findMoodHistory(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        return temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(AccountMoodResponse::from)
                .toList();
    }

    /**
     * 고객사 팀내 검색 (등록 화면 자동완성용).
     * 같은 tenant + 같은 team(team 미지정 호출자는 tenant 전체 fallback)의 사원들이 등록한 account 중
     * name LIKE keyword 매칭. assignedToMe 로 본인 책임 여부 표시 (UI 라벨 분기용).
     */
    public List<AccountSearchableResponse> searchInTeam(String keyword, Integer size) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));
        Long callerTeamId = caller.getTeam() != null ? caller.getTeam().getTeamId() : null;

        int limit = size != null ? size : DEFAULT_SEARCHABLE_SIZE;
        List<Account> accounts = accountRepository.searchInTeam(
                keyword, callerTeamId, tenantId, PageRequest.of(0, limit));

        if (accounts.isEmpty()) {
            return List.of();
        }

        List<Long> accountIds = accounts.stream().map(Account::getAccountId).toList();
        Set<Long> myAssignedIds = new HashSet<>(
                accountUserAssignmentRepository.findAccountIdsByUserIdAndAccountIdIn(userId, accountIds));

        return accounts.stream()
                .map(a -> new AccountSearchableResponse(
                        a.getAccountId(), a.getName(), a.getIndustry(), a.getBizNo(),
                        myAssignedIds.contains(a.getAccountId())))
                .toList();
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
