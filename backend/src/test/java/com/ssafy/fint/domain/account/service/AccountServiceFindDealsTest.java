package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountDealsResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 고객사별 딜 목록 조회 단위 테스트")
class AccountServiceFindDealsTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long CURRENT_TEAM_ID = 5L;
    private static final Long ACCOUNT_ID = 100L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountUserAssignmentRepository accountUserAssignmentRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountExternalInfoRepository accountExternalInfoRepository;

    @Mock
    @SuppressWarnings("unused")
    private TemperatureHistoryRepository temperatureHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    @SuppressWarnings("unused")
    private ActivityRepository activityRepository;

    @Mock
    @SuppressWarnings("unused")
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
    @DisplayName("호출자가 팀에 속하면 callerTeamId 가 Repository 에 그대로 전달된다")
    void teamCallerPassesTeamIdToRepository() {
        givenAccountAccessible();
        givenCallerWithTeam();

        ArgumentCaptor<Long> teamCaptor = ArgumentCaptor.forClass(Long.class);
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), teamCaptor.capture(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        AccountDealsResponse result = accountService.findDealsByAccount(ACCOUNT_ID, false);

        assertThat(teamCaptor.getValue()).isEqualTo(CURRENT_TEAM_ID);
        assertThat(result.deals()).isEmpty();
    }

    @Test
    @DisplayName("호출자가 팀 미배정이면 callerTeamId 로 null 이 전달된다 (tenant 전체 fallback)")
    void teamlessCallerPassesNullTeamId() {
        givenAccountAccessible();
        givenCallerWithoutTeam();

        ArgumentCaptor<Long> teamCaptor = ArgumentCaptor.forClass(Long.class);
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), teamCaptor.capture(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        AccountDealsResponse result = accountService.findDealsByAccount(ACCOUNT_ID, false);

        assertThat(teamCaptor.getValue()).isNull();
        assertThat(result.deals()).isEmpty();
    }

    @Test
    @DisplayName("mineOnly=true 면 mineOnlyUserId 로 호출자 userId 가 전달된다")
    void mineOnlyTruePassesCallerUserId() {
        givenAccountAccessible();
        givenCallerWithTeam();

        ArgumentCaptor<Long> mineCaptor = ArgumentCaptor.forClass(Long.class);
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), eq(CURRENT_TEAM_ID), mineCaptor.capture(), any(Pageable.class)))
                .willReturn(List.of());

        accountService.findDealsByAccount(ACCOUNT_ID, true);

        assertThat(mineCaptor.getValue()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("mineOnly=false 면 mineOnlyUserId 로 null 이 전달된다")
    void mineOnlyFalsePassesNullUserId() {
        givenAccountAccessible();
        givenCallerWithoutTeam();

        ArgumentCaptor<Long> mineCaptor = ArgumentCaptor.forClass(Long.class);
        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), isNull(), mineCaptor.capture(), any(Pageable.class)))
                .willReturn(List.of());

        accountService.findDealsByAccount(ACCOUNT_ID, false);

        assertThat(mineCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("Deal 이 DTO 로 매핑되어 반환된다 (probability Integer / amount Long 변환 포함)")
    void mapsDealToDto() {
        givenAccountAccessible();
        givenCallerWithoutTeam();

        Deal deal = mock(Deal.class);
        given(deal.getDealId()).willReturn(201L);
        given(deal.getTitle()).willReturn("ERP 도입 제안");
        given(deal.getCurrentPipeline()).willReturn("Proposal");
        given(deal.getProbability()).willReturn((short) 73);
        given(deal.getAmount()).willReturn(new BigDecimal("500000000.00"));

        given(dealRepository.findByAccountAndScope(
                eq(ACCOUNT_ID), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(deal));

        AccountDealsResponse result = accountService.findDealsByAccount(ACCOUNT_ID, false);

        assertThat(result.deals()).hasSize(1);
        AccountDealsResponse.DealItem item = result.deals().get(0);
        assertThat(item.dealId()).isEqualTo(201L);
        assertThat(item.title()).isEqualTo("ERP 도입 제안");
        assertThat(item.stage()).isEqualTo("Proposal");
        assertThat(item.probability()).isEqualTo(73);
        assertThat(item.amount()).isEqualTo(500000000L);
    }

    @Test
    @DisplayName("미존재 또는 타 사용자·타 테넌트 책임 account 는 NOT_FOUND 로 차단되고 Deal 조회는 실행되지 않는다")
    void rejectMissingAccount() {
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findDealsByAccount(ACCOUNT_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);

        verifyNoInteractions(dealRepository);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 INVALID_TOKEN 으로 차단된다")
    void rejectWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> accountService.findDealsByAccount(ACCOUNT_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verifyNoInteractions(accountRepository, dealRepository);
    }

    private void givenAccountAccessible() {
        Account account = mock(Account.class);
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.of(account));
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
