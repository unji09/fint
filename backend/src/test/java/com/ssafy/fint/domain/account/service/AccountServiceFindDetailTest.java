package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountDetailResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 상세 조회 단위 테스트")
class AccountServiceFindDetailTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long CURRENT_TEAM_ID = 5L;
    private static final Long ACCOUNT_ID = 50L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserAssignmentRepository accountUserAssignmentRepository;

    @Mock
    private TemperatureHistoryRepository temperatureHistoryRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountExternalInfoRepository accountExternalInfoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(
                CURRENT_USER_ID, CURRENT_TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정상 조회 시 기본 정보 + assignedUsers + latestMood 가 매핑되어 반환된다")
    void returnsDetailWithAssignedUsersAndLatestMood() {
        Account account = mock(Account.class);
        given(account.getAccountId()).willReturn(ACCOUNT_ID);
        given(account.getName()).willReturn("(주)삼성전자");
        given(account.getIndustry()).willReturn("전자");
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.of(account));

        givenCallerWithoutTeam();

        User user1 = mock(User.class);
        given(user1.getUserId()).willReturn(10L);
        given(user1.getName()).willReturn("홍길동");
        AccountUserAssignment aua1 = mock(AccountUserAssignment.class);
        given(aua1.getUser()).willReturn(user1);
        given(accountUserAssignmentRepository.findByAccountIdWithUser(ACCOUNT_ID))
                .willReturn(List.of(aua1));

        TemperatureHistory th = mock(TemperatureHistory.class);
        given(th.getMood()).willReturn(Mood.SUNNY);
        given(temperatureHistoryRepository
                .findFirstByAccount_AccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .willReturn(Optional.of(th));

        given(activityRepository.countByDeal_Account_AccountIdAndType(
                ACCOUNT_ID, ActivityType.MEETING)).willReturn(0);
        given(activityRepository.findLastMeetingStartAtByAccountId(ACCOUNT_ID))
                .willReturn(Optional.empty());
        given(contactRepository.findAllByAccount_AccountId(ACCOUNT_ID))
                .willReturn(List.of());
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        AccountDetailResponse result = accountService.findDetail(ACCOUNT_ID);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.name()).isEqualTo("(주)삼성전자");
        assertThat(result.industry()).isEqualTo("전자");
        assertThat(result.assignedUsers()).hasSize(1);
        assertThat(result.assignedUsers().get(0).userId()).isEqualTo(10L);
        assertThat(result.assignedUsers().get(0).name()).isEqualTo("홍길동");
        assertThat(result.latestMood()).isEqualTo(Mood.SUNNY);
        assertThat(result.meetingCount()).isZero();
        assertThat(result.lastContactAt()).isNull();
        assertThat(result.contacts()).isEmpty();
        assertThat(result.deals()).isEmpty();
    }

    @Test
    @DisplayName("미존재 또는 타 사용자·타 테넌트 책임 account 는 NOT_FOUND 로 차단된다")
    void rejectMissingAccount() {
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findDetail(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("mood 이력이 없으면 latestMood 는 null 이다")
    void latestMoodNullWhenNoHistory() {
        Account account = mock(Account.class);
        given(account.getAccountId()).willReturn(ACCOUNT_ID);
        given(account.getName()).willReturn("(주)삼성");
        given(account.getIndustry()).willReturn("전자");
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.of(account));

        givenCallerWithoutTeam();

        given(accountUserAssignmentRepository.findByAccountIdWithUser(ACCOUNT_ID))
                .willReturn(List.of());
        given(temperatureHistoryRepository
                .findFirstByAccount_AccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .willReturn(Optional.empty());
        given(activityRepository.countByDeal_Account_AccountIdAndType(
                ACCOUNT_ID, ActivityType.MEETING)).willReturn(0);
        given(activityRepository.findLastMeetingStartAtByAccountId(ACCOUNT_ID))
                .willReturn(Optional.empty());
        given(contactRepository.findAllByAccount_AccountId(ACCOUNT_ID))
                .willReturn(List.of());
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        AccountDetailResponse result = accountService.findDetail(ACCOUNT_ID);

        assertThat(result.latestMood()).isNull();
        assertThat(result.assignedUsers()).isEmpty();
    }

    @Test
    @DisplayName("미팅 카운트·마지막 미팅 시각·contacts·deals(preview) 가 합성되어 반환된다")
    void aggregatesMeetingAndContactsAndDeals() {
        Account account = mock(Account.class);
        given(account.getAccountId()).willReturn(ACCOUNT_ID);
        given(account.getName()).willReturn("(주)카카오");
        given(account.getIndustry()).willReturn("IT");
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.of(account));

        givenCallerWithTeam();

        given(accountUserAssignmentRepository.findByAccountIdWithUser(ACCOUNT_ID))
                .willReturn(List.of());
        given(temperatureHistoryRepository
                .findFirstByAccount_AccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .willReturn(Optional.empty());

        OffsetDateTime lastMeeting = OffsetDateTime.parse("2026-05-01T09:30:00Z");
        given(activityRepository.countByDeal_Account_AccountIdAndType(
                ACCOUNT_ID, ActivityType.MEETING)).willReturn(3);
        given(activityRepository.findLastMeetingStartAtByAccountId(ACCOUNT_ID))
                .willReturn(Optional.of(lastMeeting));

        Contact contact = mock(Contact.class);
        given(contact.getContactId()).willReturn(101L);
        given(contact.getName()).willReturn("김담당");
        given(contact.getTitle()).willReturn("팀장");
        given(contact.getPhone()).willReturn("010-1111-2222");
        given(contact.getEmail()).willReturn("kim@kakao.com");
        given(contactRepository.findAllByAccount_AccountId(ACCOUNT_ID))
                .willReturn(List.of(contact));

        Deal deal = mock(Deal.class);
        given(deal.getDealId()).willReturn(201L);
        given(deal.getTitle()).willReturn("플랫폼 라이선스");
        given(deal.getCurrentPipeline()).willReturn("PROPOSAL");
        given(deal.getProbability()).willReturn((short) 70);
        given(deal.getAmount()).willReturn(new BigDecimal("12345678.90"));

        ArgumentCaptor<Long> teamCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> mineCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), teamCaptor.capture(), mineCaptor.capture(), pageableCaptor.capture()))
                .willReturn(List.of(deal));

        AccountDetailResponse result = accountService.findDetail(ACCOUNT_ID);

        assertThat(result.meetingCount()).isEqualTo(3);
        assertThat(result.lastContactAt()).isEqualTo(lastMeeting);

        assertThat(result.contacts()).hasSize(1);
        AccountDetailResponse.ContactItem contactItem = result.contacts().get(0);
        assertThat(contactItem.contactId()).isEqualTo(101L);
        assertThat(contactItem.name()).isEqualTo("김담당");
        assertThat(contactItem.title()).isEqualTo("팀장");
        assertThat(contactItem.phone()).isEqualTo("010-1111-2222");
        assertThat(contactItem.email()).isEqualTo("kim@kakao.com");

        assertThat(result.deals()).hasSize(1);
        AccountDetailResponse.DealItem dealItem = result.deals().get(0);
        assertThat(dealItem.dealId()).isEqualTo(201L);
        assertThat(dealItem.title()).isEqualTo("플랫폼 라이선스");
        assertThat(dealItem.stage()).isEqualTo("PROPOSAL");
        assertThat(dealItem.probability()).isEqualTo(70);
        assertThat(dealItem.amount()).isEqualTo(12345678L);

        // preview: caller team 전달 / mineOnly 미적용(null) / size = 3
        assertThat(teamCaptor.getValue()).isEqualTo(CURRENT_TEAM_ID);
        assertThat(mineCaptor.getValue()).isNull();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    private void givenCallerWithoutTeam() {
        User caller = mock(User.class);
        given(caller.getTeam()).willReturn(null);
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
    }

    private void givenCallerWithTeam() {
        Team team = mock(Team.class);
        given(team.getTeamId()).willReturn(CURRENT_TEAM_ID);
        User caller = mock(User.class);
        given(caller.getTeam()).willReturn(team);
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
    }
}
