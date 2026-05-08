package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountListResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.LatestMoodProjection;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 본인 목록 조회 단위 테스트")
class AccountServiceFindMineTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TemperatureHistoryRepository temperatureHistoryRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountUserAssignmentRepository accountUserAssignmentRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountExternalInfoRepository accountExternalInfoRepository;

    @Mock
    @SuppressWarnings("unused")
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
    @DisplayName("정상 조회 시 Page 가 AccountListResponse 로 변환되고 latestMood 가 매핑된다")
    void returnsListWithLatestMoodMapping() {
        Pageable pageable = PageRequest.of(0, 20);
        Account a1 = mock(Account.class);
        given(a1.getAccountId()).willReturn(50L);
        given(a1.getName()).willReturn("(주)삼성전자");
        given(a1.getIndustry()).willReturn("전자");
        Account a2 = mock(Account.class);
        given(a2.getAccountId()).willReturn(60L);
        given(a2.getName()).willReturn("(주)LG전자");
        given(a2.getIndustry()).willReturn("전자");

        Page<Account> page = new PageImpl<>(List.of(a1, a2), pageable, 2);
        given(accountRepository.findMineByFilter(
                eq(CURRENT_USER_ID), eq(CURRENT_TENANT_ID), eq(null), eq(null), any(Pageable.class)))
                .willReturn(page);

        LatestMoodProjection p1 = mock(LatestMoodProjection.class);
        given(p1.getAccountId()).willReturn(50L);
        given(p1.getMood()).willReturn(Mood.SUNNY);
        // a2 는 mood 이력 없음 → 결과에서 latestMood = null
        given(temperatureHistoryRepository.findLatestMoodsByAccountIds(any()))
                .willReturn(List.of(p1));

        AccountListResponse result = accountService.findMine(null, null, pageable);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).accountId()).isEqualTo(50L);
        assertThat(result.content().get(0).latestMood()).isEqualTo(Mood.SUNNY);
        assertThat(result.content().get(1).accountId()).isEqualTo(60L);
        assertThat(result.content().get(1).latestMood()).isNull();
    }

    @Test
    @DisplayName("결과 0건이면 빈 content + totalElements 0 반환")
    void returnsEmpty() {
        Pageable pageable = PageRequest.of(0, 20);
        given(accountRepository.findMineByFilter(
                eq(CURRENT_USER_ID), eq(CURRENT_TENANT_ID), any(), any(), any(Pageable.class)))
                .willReturn(Page.empty(pageable));

        AccountListResponse result = accountService.findMine(null, null, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
