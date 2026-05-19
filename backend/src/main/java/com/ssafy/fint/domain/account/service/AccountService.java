package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.client.CompanyEnrichApiResponse;
import com.ssafy.fint.domain.account.client.CompanyEnrichClient;
import com.ssafy.fint.domain.account.dto.AccountDealsResponse;
import com.ssafy.fint.domain.account.dto.AccountDetailResponse;
import com.ssafy.fint.domain.account.dto.AccountEnrichResponse;
import com.ssafy.fint.domain.account.dto.AccountListResponse;
import com.ssafy.fint.domain.account.dto.AccountMoodResponse;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.dto.AccountSearchableResponse;
import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.event.AccountCreatedEvent;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.signal.repository.DartDisclosureRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final DartDisclosureRepository dartDisclosureRepository;
    private final TemperatureHistoryRepository temperatureHistoryRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyEnrichClient companyEnrichClient;

    @Transactional
    public AccountRegisterResponse register(AccountRegisterRequest request) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();
        User owner = userRepository.getReferenceById(userId);

        Account account;
        boolean isNewAccount;
        if (request.existingAccountId() != null) {
            account = registerToExistingAccount(request.existingAccountId(), userId, tenantId, owner);
            isNewAccount = false;
        } else {
            account = registerNewAccount(request, owner);
            isNewAccount = true;
        }

        log.info("[AccountRegister] accountId={} userId={} mode={}",
                account.getAccountId(), userId,
                isNewAccount ? "case2" : "case1");

        if (isNewAccount) {
            eventPublisher.publishEvent(new AccountCreatedEvent(tenantId, account.getAccountId()));
        }
        return AccountRegisterResponse.of(account.getAccountId());
    }

    private Account registerToExistingAccount(Long accountId, Long userId, Long tenantId, User owner) {
        Account account = accountRepository
                .findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

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
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (request.name() != null) account.changeName(request.name());
        if (request.industry() != null) account.changeIndustry(request.industry());
        if (request.bizNo() != null) account.changeBizNo(request.bizNo());

        log.info("[AccountUpdate] accountId={} userId={} tenantId={}", accountId, userId, tenantId);
    }

    @Transactional
    public AccountEnrichResponse enrich(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        Account account = accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        CompanyEnrichApiResponse.Data result =
                companyEnrichClient.enrich(tenantId, account.getName());

        if (result.matched() && result.company() != null) {
            CompanyEnrichApiResponse.CompanyInfo company = result.company();
            if (company.bizNo() != null) {
                account.changeBizNo(company.bizNo());
            }
            if (company.industryCode() != null) {
                account.changeIndustry(company.industryCode());
            }
            log.info("[AccountEnrich] accountId={} bizNo={} industry={}",
                    accountId, company.bizNo(), company.industryCode());
        }

        return AccountEnrichResponse.from(result);
    }

    @Transactional
    public void delete(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

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
        Pageable pageable = PageRequest.of(0, limit);

        if ("DART".equals(source)) {
            return dartDisclosureRepository.findByAccountId(accountId, pageable)
                    .stream()
                    .map(AccountSignalResponse::fromDart)
                    .toList();
        }

        if ("NEWS".equals(source)) {
            return accountExternalInfoRepository
                    .findRecentByAccountAndOptionalSource(accountId, "NEWS", pageable)
                    .stream()
                    .map(AccountSignalResponse::from)
                    .toList();
        }

        // 단순 시간순 정렬로 잘라내면 최근에 한 종류(NEWS 또는 DART)가 쏟아진 경우
        // 다른 종류가 응답에서 통째로 빠진다. 클라이언트가 종류별 필터 탭을 제공하려면
        // 응답에 두 종류가 모두 포함되어야 하므로 종류별로 limit/2 를 먼저 보장하고
        // 부족한 쪽은 상대 종류로 보충해 총 limit 건을 채운다.
        int half = Math.max(1, limit / 2);
        Pageable fullPg = PageRequest.of(0, limit);

        List<AccountSignalResponse> news = accountExternalInfoRepository
                .findRecentByAccountAndOptionalSource(accountId, "NEWS", fullPg)
                .stream()
                .map(AccountSignalResponse::from)
                .toList();
        List<AccountSignalResponse> dart = dartDisclosureRepository.findByAccountId(accountId, fullPg)
                .stream()
                .map(AccountSignalResponse::fromDart)
                .toList();

        int newsTake = Math.min(half, news.size());
        int dartTake = Math.min(half, dart.size());

        int remaining = limit - newsTake - dartTake;
        if (remaining > 0) {
            int extra = Math.min(remaining, news.size() - newsTake);
            newsTake += extra;
            remaining -= extra;
        }
        if (remaining > 0) {
            int extra = Math.min(remaining, dart.size() - dartTake);
            dartTake += extra;
        }

        List<AccountSignalResponse> merged = new ArrayList<>();
        merged.addAll(news.subList(0, newsTake));
        merged.addAll(dart.subList(0, dartTake));
        merged.sort(Comparator.comparing(AccountSignalResponse::occurredAt).reversed());

        return merged;
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

    public List<AccountSearchableResponse> searchInTeam(String keyword, Integer size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

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

    public AccountListResponse findMine(String keyword, String industry, Pageable pageable) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        Page<Account> accountPage = accountRepository.findMineByFilter(
                userId, tenantId, keyword, industry, pageable);

        if (accountPage.isEmpty()) {
            return new AccountListResponse(List.of(), accountPage.getTotalElements());
        }

        List<Long> accountIds = accountPage.stream().map(Account::getAccountId).toList();
        Map<Long, Mood> latestMoodMap = new HashMap<>();
        temperatureHistoryRepository.findLatestMoodsByAccountIds(accountIds)
                .forEach(p -> latestMoodMap.putIfAbsent(p.getAccountId(), p.getMood()));

        List<AccountListResponse.Item> items = accountPage.stream()
                .map(a -> new AccountListResponse.Item(
                        a.getAccountId(), a.getName(), a.getIndustry(),
                        latestMoodMap.get(a.getAccountId())))
                .toList();

        return new AccountListResponse(items, accountPage.getTotalElements());
    }

    /**
     * 고객사 상세 조회.
     * 권한 검증 → assignedUsers · latestMood · 미팅 집계 · contacts · deals(preview 3개) 를 합성한다.
     * meetingCount / lastContactA
     * t 는 "고객사 매핑 Deal 에 속한 Activity 중 type=MEETING" 기준.
     * deals 는 데이터 스코프 정책(팀 있음→팀 deal, 팀 없음→tenant 전체) 적용된 최신 3개 preview.
     * 전체 목록은 {@link #findDealsByAccount(Long, boolean)} 으로.
     */
    public AccountDetailResponse findDetail(Long accountId) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        Account account = accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        Long callerTeamId = resolveCallerTeamId(userId);

        List<AccountDetailResponse.AssignedUser> assignedUsers = accountUserAssignmentRepository
                .findByAccountIdWithUser(accountId)
                .stream()
                .map(aua -> new AccountDetailResponse.AssignedUser(
                        aua.getUser().getUserId(), aua.getUser().getName()))
                .toList();

        Mood latestMood = temperatureHistoryRepository
                .findFirstByAccount_AccountIdOrderByCreatedAtDesc(accountId)
                .map(th -> th.getMood())
                .orElse(null);

        int meetingCount = activityRepository
                .countByDeal_Account_AccountIdAndType(accountId, ActivityType.MEETING);

        OffsetDateTime lastContactAt = activityRepository
                .findLastMeetingStartAtByAccountId(accountId)
                .orElse(null);

        List<AccountDetailResponse.ContactItem> contacts = contactRepository
                .findAllByAccount_AccountId(accountId)
                .stream()
                .map(c -> new AccountDetailResponse.ContactItem(
                        c.getContactId(), c.getName(), c.getTitle(), c.getPhone(), c.getEmail()))
                .toList();

        return new AccountDetailResponse(
                account.getAccountId(), account.getName(), account.getIndustry(),
                account.getBizNo(),
                assignedUsers, latestMood,
                meetingCount, lastContactAt,
                contacts
        );
    }

    /**
     * 고객사별 딜 목록 조회.
     * 권한 검증(본인 책임자 + 같은 tenant) → 데이터 스코프(팀/tenant) → mineOnly 필터.
     * 정렬은 updated_at desc, 페이지네이션 없음(전체 반환).
     */
    public AccountDealsResponse findDealsByAccount(Long accountId, boolean mineOnly) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        accountRepository
                .findByIdAndAssignedUserIdAndTenantId(accountId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        Long callerTeamId = resolveCallerTeamId(userId);
        Long mineOnlyUserId = mineOnly ? userId : null;

        List<Deal> deals = dealRepository.findByAccountAndScope(
                accountId, callerTeamId, mineOnlyUserId, Pageable.unpaged());

        List<AccountDealsResponse.DealItem> items = deals.stream()
                .map(d -> new AccountDealsResponse.DealItem(
                        d.getDealId(), d.getTitle(), d.getCurrentPipeline(),
                        toIntegerOrNull(d.getProbability()),
                        toLongOrNull(d.getAmount())))
                .toList();

        return new AccountDealsResponse(items);
    }

    private Long resolveCallerTeamId(Long userId) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return caller.getTeam() != null ? caller.getTeam().getTeamId() : null;
    }

    private static Integer toIntegerOrNull(Short value) {
        return value == null ? null : value.intValue();
    }

    /**
     * BigDecimal 금액을 명세상의 Long 으로 변환.
     * 소수부가 있으면 절사한다 (DB 컬럼 NUMERIC(15,2) 이지만 명세는 정수 KRW 가정).
     */
    private static Long toLongOrNull(BigDecimal value) {
        return value == null ? null : value.longValue();
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
