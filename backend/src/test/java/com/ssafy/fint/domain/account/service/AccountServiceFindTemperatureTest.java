package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountTemperatureResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 고객 온도 추이 조회(GET /accounts/{accountId}/temperature) 단위 테스트.
 * 본인 소유 검증 → DTO 변환(createdAt → recordedAt) 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 온도 추이 조회 단위 테스트")
class AccountServiceFindTemperatureTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TemperatureHistoryRepository temperatureHistoryRepository;

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
    @DisplayName("정상 조회 시 TemperatureHistory 가 AccountTemperatureResponse 로 변환되어 반환된다.")
    void returnsTemperatureListMappedToDto() {
        Account account = mock(Account.class);
        TemperatureHistory history = mock(TemperatureHistory.class);
        OffsetDateTime recordedAt = OffsetDateTime.now();

        given(history.getCreatedAt()).willReturn(recordedAt);
        given(history.getTemperature()).willReturn(75);
        given(history.getReason()).willReturn("미팅 후 관심도 상승");

        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));
        when(temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .thenReturn(List.of(history));

        List<AccountTemperatureResponse> result = accountService.findTemperatureHistory(ACCOUNT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).recordedAt()).isEqualTo(recordedAt);
        assertThat(result.get(0).temperature()).isEqualTo(75);
        assertThat(result.get(0).reason()).isEqualTo("미팅 후 관심도 상승");
    }

    @Test
    @DisplayName("이력이 없으면 빈 리스트를 반환한다.")
    void returnsEmptyListWhenNoHistory() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));
        when(temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .thenReturn(List.of());

        List<AccountTemperatureResponse> result = accountService.findTemperatureHistory(ACCOUNT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("미존재 또는 타 사용자·타 테넌트 소유 account 는 NOT_FOUND 로 차단되고 temperature 조회는 실행되지 않는다.")
    void rejectMissingAccount() {
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findTemperatureHistory(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);

        verifyNoInteractions(temperatureHistoryRepository);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 INVALID_TOKEN 으로 차단된다.")
    void rejectWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> accountService.findTemperatureHistory(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verifyNoInteractions(accountRepository, temperatureHistoryRepository);
    }
}
