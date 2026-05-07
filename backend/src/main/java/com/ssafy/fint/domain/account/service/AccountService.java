package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountMoodResponse;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final int DEFAULT_SIGNAL_SIZE = 20;

    private final AccountRepository accountRepository;
    private final AccountUserAssignmentRepository accountUserAssignmentRepository;
    private final AccountExternalInfoRepository accountExternalInfoRepository;
    private final TemperatureHistoryRepository temperatureHistoryRepository;
    private final UserRepository userRepository;

    /**
     * 고객사 등록 (case2: 새 고객사 신규 생성).
     * 새 account 를 만들고 호출자를 책임자로 assignment row 에 즉시 매핑한다.
     * case1 (기존 account 선택, existingAccountId) 분기는 후속 단계에서 추가 예정.
     */
    @Transactional
    public AccountRegisterResponse register(AccountRegisterRequest request) {
        Long userId = currentUserId();
        User owner = userRepository.getReferenceById(userId);

        Account account = Account.builder()
                .name(request.name())
                .industry(request.industry())
                .bizNo(request.bizNo())
                .build();
        Account saved = accountRepository.save(account);

        AccountUserAssignment assignment = AccountUserAssignment.builder()
                .account(saved)
                .user(owner)
                .build();
        accountUserAssignmentRepository.save(assignment);

        log.info("[AccountRegister] accountId={} userId={}", saved.getAccountId(), userId);
        return AccountRegisterResponse.of(saved.getAccountId());
    }

    /**
     * 고객사 부분 수정.
     * 본인이 책임자로 매핑된 + 같은 tenant 인 account 만 수정 가능.
     * null 인 필드는 변경되지 않으며, 모두 null 이면 no-op.
     */
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

        if (request.name() != null) {
            account.changeName(request.name());
        }
        if (request.industry() != null) {
            account.changeIndustry(request.industry());
        }
        if (request.bizNo() != null) {
            account.changeBizNo(request.bizNo());
        }

        log.info("[AccountUpdate] accountId={} userId={} tenantId={}", accountId, userId, tenantId);
    }

    /**
     * 고객사 책임 해제 (본인 assignment row 만 제거).
     * account 본체는 유지된다 (다른 책임자 잔존 또는 0명 상태로 보존).
     * 미존재 / 타 사용자 / 타 테넌트 모두 NOT_FOUND 로 통일하여 존재 여부 노출 방지.
     */
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

    /**
     * 고객사 외부 시그널(NEWS/DART) 조회.
     * 본인 책임 + 같은 tenant 검증 후 occurred_at 내림차순으로 size 만큼 반환.
     * source 미지정 시 모든 출처 통합. size 미지정 시 기본 20 건.
     */
    public List<AccountSignalResponse> findSignals(Long accountId, String source, Integer size) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[AccountFindSignals] not found. accountId={} userId={} tenantId={}",
                            accountId, userId, tenantId);
                    return new BusinessException(CommonErrorCode.NOT_FOUND);
                });

        int limit = size != null ? size : DEFAULT_SIGNAL_SIZE;
        return accountExternalInfoRepository
                .findRecentByAccountAndOptionalSource(accountId, source, PageRequest.of(0, limit))
                .stream()
                .map(AccountSignalResponse::from)
                .toList();
    }

    /**
     * 고객 날씨(분위기) 추이 조회.
     * 본인 책임 + 같은 tenant 검증 후 created_at 내림차순으로 mood 변화 이력 반환.
     */
    public List<AccountMoodResponse> findMoodHistory(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[AccountFindMood] not found. accountId={} userId={} tenantId={}",
                            accountId, userId, tenantId);
                    return new BusinessException(CommonErrorCode.NOT_FOUND);
                });

        return temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(AccountMoodResponse::from)
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
