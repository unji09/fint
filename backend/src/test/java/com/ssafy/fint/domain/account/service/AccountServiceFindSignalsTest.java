package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountExternalInfo;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 고객사 외부 시그널 조회(GET /accounts/{accountId}/signals) 단위 테스트.
 * 본인 소유 검증 → 동적 source 필터 → DTO 변환 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 시그널 조회 단위 테스트")
class AccountServiceFindSignalsTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
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
    @DisplayName("정상 조회 시 AccountExternalInfo 가 AccountSignalResponse 로 변환되어 반환된다.")
    void returnsSignalListMappedToDto() {
        Account account = mock(Account.class);
        AccountExternalInfo info = mock(AccountExternalInfo.class);
        OffsetDateTime occurredAt = OffsetDateTime.now();

        given(info.getSource()).willReturn("NEWS");
        given(info.getTitle()).willReturn("title-1");
        given(info.getContent()).willReturn("content-1");
        given(info.getUrl()).willReturn("https://example.com");
        given(info.getOccurredAt()).willReturn(occurredAt);

        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));
        when(accountExternalInfoRepository.findRecentByAccountAndOptionalSource(
                eq(ACCOUNT_ID), eq("NEWS"), any(Pageable.class)))
                .thenReturn(List.of(info));

        List<AccountSignalResponse> result = accountService.findSignals(ACCOUNT_ID, "NEWS", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("NEWS");
        assertThat(result.get(0).title()).isEqualTo("title-1");
        assertThat(result.get(0).content()).isEqualTo("content-1");
        assertThat(result.get(0).url()).isEqualTo("https://example.com");
        assertThat(result.get(0).occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("source 가 null 이면 Repository 에 null 이 그대로 전달된다 (동적 필터).")
    void passesNullSourceWhenNotProvided() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));
        when(accountExternalInfoRepository.findRecentByAccountAndOptionalSource(
                eq(ACCOUNT_ID), eq(null), any(Pageable.class)))
                .thenReturn(List.of());

        List<AccountSignalResponse> result = accountService.findSignals(ACCOUNT_ID, null, 10);

        assertThat(result).isEmpty();
        verify(accountExternalInfoRepository).findRecentByAccountAndOptionalSource(
                eq(ACCOUNT_ID), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("size 미지정 시 기본 20 이 Pageable size 로 적용된다.")
    void appliesDefaultSizeWhenNotProvided() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(accountExternalInfoRepository.findRecentByAccountAndOptionalSource(
                eq(ACCOUNT_ID), eq(null), captor.capture()))
                .thenReturn(List.of());

        accountService.findSignals(ACCOUNT_ID, null, null);

        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("미존재 또는 타 사용자·타 테넌트 소유 account 는 NOT_FOUND 로 차단되고 signal 조회는 실행되지 않는다.")
    void rejectMissingAccount() {
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findSignals(ACCOUNT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);

        verifyNoInteractions(accountExternalInfoRepository);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 INVALID_TOKEN 으로 차단된다.")
    void rejectWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> accountService.findSignals(ACCOUNT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verifyNoInteractions(accountRepository, accountExternalInfoRepository);
    }
}
