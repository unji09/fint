package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountSearchablePageResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 팀내 검색 단위 테스트")
class AccountServiceSearchableTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long CURRENT_TEAM_ID = 5L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserAssignmentRepository accountUserAssignmentRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountExternalInfoRepository accountExternalInfoRepository;

    @Mock
    @SuppressWarnings("unused")
    private TemperatureHistoryRepository temperatureHistoryRepository;

    @Mock
    private UserRepository userRepository;

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
    @DisplayName("팀이 있는 호출자는 callerTeamId 가 Repository 에 그대로 전달되고 결과가 DTO 로 변환된다")
    void teamCallerPassesTeamIdToRepository() {
        User caller = mock(User.class);
        Team team = mock(Team.class);
        given(team.getTeamId()).willReturn(CURRENT_TEAM_ID);
        given(caller.getTeam()).willReturn(team);
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));

        Account a1 = mock(Account.class);
        given(a1.getAccountId()).willReturn(50L);
        given(a1.getName()).willReturn("(주)삼성전자");
        given(a1.getIndustry()).willReturn("전자");
        given(a1.getBizNo()).willReturn("124-81-00998");

        ArgumentCaptor<Long> teamCaptor = ArgumentCaptor.forClass(Long.class);
        given(accountRepository.searchInTeam(
                eq("삼성"), teamCaptor.capture(), eq(CURRENT_TENANT_ID), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(a1)));
        given(accountUserAssignmentRepository.findAccountIdsByUserIdAndAccountIdIn(
                eq(CURRENT_USER_ID), any()))
                .willReturn(List.of(50L));

        AccountSearchablePageResponse result = accountService.searchInTeam("삼성", 0, null);

        assertThat(teamCaptor.getValue()).isEqualTo(CURRENT_TEAM_ID);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).accountId()).isEqualTo(50L);
        assertThat(result.content().get(0).assignedToMe()).isTrue();
    }

    @Test
    @DisplayName("팀이 없는 호출자는 callerTeamId 로 null 이 Repository 에 전달된다 (tenant 전체 fallback)")
    void teamlessCallerPassesNullTeamId() {
        User caller = mock(User.class);
        given(caller.getTeam()).willReturn(null);
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));

        ArgumentCaptor<Long> teamCaptor = ArgumentCaptor.forClass(Long.class);
        given(accountRepository.searchInTeam(
                eq("삼성"), teamCaptor.capture(), eq(CURRENT_TENANT_ID), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        AccountSearchablePageResponse result = accountService.searchInTeam("삼성", 0, null);

        assertThat(teamCaptor.getValue()).isNull();
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("일부 account 만 본인 책임이면 assignedToMe 가 정확히 매핑된다")
    void assignedToMeMappingPartial() {
        User caller = mock(User.class);
        given(caller.getTeam()).willReturn(null);
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));

        Account a1 = mock(Account.class);
        given(a1.getAccountId()).willReturn(50L);
        given(a1.getName()).willReturn("a1");
        given(a1.getIndustry()).willReturn("IT");
        given(a1.getBizNo()).willReturn(null);

        Account a2 = mock(Account.class);
        given(a2.getAccountId()).willReturn(60L);
        given(a2.getName()).willReturn("a2");
        given(a2.getIndustry()).willReturn("IT");
        given(a2.getBizNo()).willReturn(null);

        given(accountRepository.searchInTeam(
                eq("a"), any(), eq(CURRENT_TENANT_ID), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(a1, a2)));
        given(accountUserAssignmentRepository.findAccountIdsByUserIdAndAccountIdIn(
                eq(CURRENT_USER_ID), any()))
                .willReturn(List.of(50L));

        AccountSearchablePageResponse result = accountService.searchInTeam("a", 0, null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).assignedToMe()).isTrue();
        assertThat(result.content().get(1).assignedToMe()).isFalse();
    }

    @Test
    @DisplayName("빈 keyword 로 호출하면 전체 목록을 반환한다")
    void emptyKeywordReturnsAll() {
        User caller = mock(User.class);
        given(caller.getTeam()).willReturn(null);
        given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));

        Account a1 = mock(Account.class);
        given(a1.getAccountId()).willReturn(50L);
        given(a1.getName()).willReturn("삼성전자");
        given(a1.getIndustry()).willReturn("전자");
        given(a1.getBizNo()).willReturn(null);

        given(accountRepository.searchInTeam(
                eq(""), any(), eq(CURRENT_TENANT_ID), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(a1)));
        given(accountUserAssignmentRepository.findAccountIdsByUserIdAndAccountIdIn(
                eq(CURRENT_USER_ID), any()))
                .willReturn(List.of());

        AccountSearchablePageResponse result = accountService.searchInTeam(null, 0, null);

        assertThat(result.content()).hasSize(1);
    }
}
